"""Recolha de notícias de uma fonte.

Ordem de tentativas:
  1. estratégia que já funcionou antes (memorizada pelo servidor)
  2. feeds RSS/Atom conhecidos (campo "feeds")
  3. descoberta automática do feed na página inicial + caminhos comuns (/feed, /rss ...)
  4. leitura dos títulos no HTML da página inicial
  5. Google News (pesquisa site:domínio) como último recurso
  6. GDELT DOC API, para fontes marcadas como gdelt=True (delega em gdelt_fetcher)
"""
from __future__ import annotations

import calendar
import hashlib
import logging
import os
import re
import time
import warnings
from datetime import datetime, timedelta, timezone
from urllib.parse import quote_plus, urljoin, urlparse

import bs4
import feedparser
import requests
import urllib3
from bs4 import BeautifulSoup

for _name in ("MarkupResemblesLocatorWarning", "XMLParsedAsHTMLWarning"):
    _category = getattr(bs4, _name, None)
    if _category is not None:
        warnings.filterwarnings("ignore", category=_category)
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

log = logging.getLogger("nexa.fetcher")

USER_AGENT = os.environ.get("USER_AGENT") or (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)
HEADERS = {
    "User-Agent": USER_AGENT,
    "Accept": (
        "application/rss+xml, application/atom+xml, application/xml;q=0.9, "
        "text/xml;q=0.9, text/html;q=0.8, */*;q=0.5"
    ),
    "Accept-Language": "pt-PT,pt;q=0.9,en;q=0.7",
}
CONNECT_TIMEOUT = 6
READ_TIMEOUT = 12
DEADLINE = 25  # segundos máximos por descarga
MAX_BYTES = 2_000_000
MAX_ITEMS = int(os.environ.get("MAX_ITEMS_PER_SOURCE", "40"))
SUMMARY_CHARS = 280
MIN_HTML_ITEMS = 3
MAX_FEED_CANDIDATES = 8
ALLOW_INSECURE = os.environ.get("ALLOW_INSECURE_FALLBACK", "1") == "1"
GNEWS_FALLBACK = os.environ.get("GNEWS_FALLBACK", "1") == "1"
COMMON_FEED_PATHS = ("feed/", "rss", "rss.xml", "feed.xml", "atom.xml", "index.xml")
UTC_OFFSETS = {"AO": 1, "PT": 0, "BR": -3}

_WS = re.compile(r"\s+")
_WP_TAIL = re.compile(
    r"\s*(?:The|O|A)\s+(?:post|article|artigo|publicação)\s.{0,300}?"
    r"(?:appeared first on|apareceu primeiro (?:em|no|na))\s.*$",
    re.I,
)
_ELLIPSIS = re.compile(r"\s*\[(?:…|\.{3})\]\s*$")
_TRACKING = re.compile(r"^(?:utm_|fbclid|gclid|mc_|cmpid|xtor|ito$|ref$)", re.I)
_BAD_PATH = re.compile(
    r"/(?:tag|tags|categoria|categorias|category|categories|autor|autores|author|page|pagina|"
    r"search|pesquisa|login|registo|contactos?|contact|sobre|about|privacidade|privacy|termos|"
    r"terms|cookies|newsletter|rss)(?:/|$)",
    re.I,
)
_BAD_TITLE = (
    "política de privacidade", "termos e condições", "ler mais", "ver mais",
    "subscrever", "newsletter", "publicidade", "cookies",
)
_BAD_IMG = re.compile(r"(?:logo|icon|sprite|avatar|pixel|blank|placeholder|spacer|1x1)", re.I)


class FetchError(Exception):
    """Erro de rede/HTTP já com mensagem legível."""


# ── HTTP ─────────────────────────────────────────────────────────────────────
def http_get(url: str) -> tuple[bytes, str]:
    """Descarrega um URL. Devolve (corpo, url_final)."""
    verify = True
    while True:
        started = time.monotonic()
        try:
            with requests.get(
                url,
                headers=HEADERS,
                timeout=(CONNECT_TIMEOUT, READ_TIMEOUT),
                stream=True,
                allow_redirects=True,
                verify=verify,
            ) as resp:
                if resp.status_code >= 400:
                    raise FetchError(f"HTTP {resp.status_code}")
                chunks, size = [], 0
                for chunk in resp.iter_content(chunk_size=65536):
                    chunks.append(chunk)
                    size += len(chunk)
                    if size >= MAX_BYTES:
                        break
                    if time.monotonic() - started > DEADLINE:
                        raise FetchError("resposta demasiado lenta")
                return b"".join(chunks), resp.url
        except requests.exceptions.SSLError as exc:
            if verify and ALLOW_INSECURE:
                verify = False
                continue
            raise FetchError("erro SSL") from exc
        except requests.exceptions.Timeout as exc:
            raise FetchError("tempo esgotado") from exc
        except requests.exceptions.RequestException as exc:
            raise FetchError(exc.__class__.__name__) from exc


# ── Limpeza de texto, URLs e datas ───────────────────────────────────────────
def clean_text(value, limit: int = SUMMARY_CHARS) -> str:
    if not value:
        return ""
    text = str(value)
    if "<" in text or "&" in text:
        text = BeautifulSoup(text, "html.parser").get_text(" ", strip=True)
    text = _WS.sub(" ", text).strip()
    text = _WP_TAIL.sub("", text)
    text = _ELLIPSIS.sub("…", text).strip()
    if len(text) > limit:
        text = text[: limit - 1].rsplit(" ", 1)[0].rstrip(" ,.;:-–—") + "…"
    return text


def norm_url(url: str) -> str:
    try:
        parts = urlparse(url.strip())
    except ValueError:
        return url
    query = "&".join(
        piece
        for piece in parts.query.split("&")
        if piece and not _TRACKING.match(piece.split("=", 1)[0])
    )
    path = parts.path.rstrip("/") or "/"
    return f"{parts.scheme}://{parts.netloc.lower()}{path}" + (f"?{query}" if query else "")


def clean_image(url, base: str = "") -> str:
    url = (url or "").strip()
    if not url:
        return ""
    if base:
        url = urljoin(base, url)
    if url.startswith("http://"):
        url = "https://" + url[len("http://"):]
    return url if url.startswith("https://") else ""


def parse_iso(value: str, default_tz=timezone.utc) -> float | None:
    value = (value or "").strip()
    if not value:
        return None
    try:
        moment = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    if moment.tzinfo is None:
        moment = moment.replace(tzinfo=default_tz)
    return moment.timestamp()


def make_article(source: dict, title: str, link: str, summary: str = "", image: str = "", ts=None) -> dict:
    return {
        "id": hashlib.sha1(norm_url(link).encode("utf-8")).hexdigest()[:16],
        "title": title,
        "link": link,
        "summary": summary,
        "image": clean_image(image, link),
        "ts": ts,  # None = a fonte não indica data
        "source_id": source["id"],
        "source": source["name"],
        "region": source["region"],
        "category": source["category"],
        "lang": source.get("lang", "pt"),
    }


# ── RSS / Atom ───────────────────────────────────────────────────────────────
def entry_ts(entry) -> float | None:
    for key in ("published_parsed", "updated_parsed", "created_parsed"):
        value = entry.get(key)
        if value:
            try:
                return float(calendar.timegm(value))
            except (TypeError, ValueError, OverflowError):
                continue
    return None


def _is_image(item) -> bool:
    medium = (item.get("medium") or "").lower()
    mime = (item.get("type") or "").lower()
    if medium and medium != "image":
        return False
    return not mime or mime.startswith("image")


def entry_image(entry) -> str:
    for key in ("media_content", "media_thumbnail"):
        for item in entry.get(key) or []:
            url = item.get("url")
            if url and _is_image(item):
                return url
    for item in list(entry.get("enclosures") or []) + list(entry.get("links") or []):
        if (item.get("type") or "").lower().startswith("image") and item.get("href"):
            return item["href"]
    blobs = [c.get("value", "") for c in entry.get("content") or []]
    blobs.append(entry.get("summary") or "")
    for blob in blobs:
        if "<img" in blob.lower():
            img = BeautifulSoup(blob, "html.parser").find("img", src=True)
            if img is not None:
                return img["src"]
    return ""


def parse_feed(source: dict, body: bytes, gnews: bool = False) -> list[dict]:
    parsed = feedparser.parse(body)
    articles, seen = [], set()
    for entry in parsed.entries:
        link = (entry.get("link") or "").strip()
        title = clean_text(entry.get("title"), 300)
        if not title or not link.startswith(("http://", "https://")):
            continue
        if gnews:
            publisher = entry.get("source")
            publisher = publisher.get("title", "") if hasattr(publisher, "get") else ""
            suffix = f" - {publisher}"
            if publisher and title.endswith(suffix):
                title = title[: -len(suffix)].rstrip()
            summary, image = "", ""
        else:
            raw = entry.get("summary") or ""
            if not raw and entry.get("content"):
                raw = entry["content"][0].get("value", "")
            summary = clean_text(raw)
            if summary.casefold() == title.casefold():
                summary = ""
            image = entry_image(entry)
        article = make_article(source, title, link, summary, image, entry_ts(entry))
        if article["id"] in seen:
            continue
        seen.add(article["id"])
        articles.append(article)
        if len(articles) >= MAX_ITEMS:
            break
    return articles


def discover_feeds(soup: BeautifulSoup, base_url: str) -> list[str]:
    found: list[str] = []
    for link in soup.find_all("link", href=True):
        rel = link.get("rel") or []
        rel = " ".join(rel) if isinstance(rel, list) else str(rel)
        kind = (link.get("type") or "").lower()
        if "alternate" in rel.lower() and ("rss" in kind or "atom" in kind):
            url = urljoin(base_url, link["href"].strip())
            if "comment" not in url.lower() and url not in found:
                found.append(url)
    return found


# ── Leitura de títulos no HTML ───────────────────────────────────────────────
def _anchor_title(anchor) -> str:
    heading = anchor.find(["h1", "h2", "h3", "h4", "h5"])
    text = heading.get_text(" ", strip=True) if heading else anchor.get_text(" ", strip=True)
    text = _WS.sub(" ", text).strip()
    if not text:
        text = _WS.sub(" ", anchor.get("title") or anchor.get("aria-label") or "").strip()
    return text


def _plausible_title(title: str) -> bool:
    if not 25 <= len(title) <= 240 or len(title.split()) < 4:
        return False
    low = title.casefold()
    return not any(bad in low for bad in _BAD_TITLE)


def _find_card(anchor):
    node = anchor
    for _ in range(4):
        parent = node.parent
        if parent is None or parent.name in ("body", "html", "main", "ul", "ol", "section", "[document]"):
            break
        if len(parent.find_all("a", href=True)) > 6:
            break
        node = parent
        if node.name in ("article", "li"):
            break
    return node


def _img_url(img) -> str:
    for attr in ("data-src", "data-lazy-src", "data-original", "src"):
        value = (img.get(attr) or "").strip()
        if value and not value.startswith("data:"):
            return value
    for attr in ("data-srcset", "srcset"):
        value = (img.get(attr) or "").strip()
        if value:
            return value.split(",")[0].strip().split(" ")[0]
    return ""


def scrape_homepage(source: dict, soup: BeautifulSoup, base_url: str) -> list[dict]:
    for tag in soup(["script", "style", "noscript", "nav", "footer", "aside", "form", "svg", "template"]):
        tag.decompose()
    base_host = urlparse(base_url).netloc.lower().removeprefix("www.")
    offset = timezone(timedelta(hours=UTC_OFFSETS.get(source["region"], 0)))
    articles, seen = [], set()
    for anchor in soup.find_all("a", href=True):
        href = anchor["href"].strip()
        if not href or href.startswith(("#", "javascript:", "mailto:", "tel:")):
            continue
        link = urljoin(base_url, href)
        parts = urlparse(link)
        host = parts.netloc.lower().removeprefix("www.")
        if parts.scheme not in ("http", "https") or not (host == base_host or host.endswith("." + base_host)):
            continue
        path = parts.path.strip("/")
        if not path or _BAD_PATH.search(parts.path):
            continue
        if "/" not in path and "-" not in path:
            continue
        title = _anchor_title(anchor)
        if not _plausible_title(title):
            continue
        key = norm_url(link)
        if key in seen:
            continue
        seen.add(key)

        card = _find_card(anchor)
        image = ""
        img = card.find("img")
        if img is not None:
            candidate = _img_url(img)
            if candidate and not _BAD_IMG.search(candidate) and not candidate.lower().split("?")[0].endswith(".svg"):
                image = urljoin(link, candidate)
        ts = None
        time_tag = card.find("time")
        if time_tag is not None:
            ts = parse_iso(time_tag.get("datetime", ""), offset)

        articles.append(make_article(source, title, link, "", image, ts))
        if len(articles) >= MAX_ITEMS:
            break
    return articles


# ── Google News (último recurso) ─────────────────────────────────────────────
def gnews_url(source: dict) -> str:
    parts = urlparse(source["url"])
    scope = parts.netloc.lower().removeprefix("www.")
    if len(parts.path) > 1:
        scope += parts.path.rstrip("/")
    lang, region = source.get("lang", "pt"), source.get("region", "PT")
    if lang == "en":
        hl, gl, ceid = "en-US", "US", "US:en"
    elif lang == "fr":
        hl, gl, ceid = "fr", "FR", "FR:fr"
    elif region == "BR":
        hl, gl, ceid = "pt-BR", "BR", "BR:pt-419"
    else:
        hl, gl, ceid = "pt-PT", "PT", "PT:pt-150"
    query = quote_plus(f"site:{scope} when:2d")
    return f"https://news.google.com/rss/search?q={query}&hl={hl}&gl={gl}&ceid={ceid}"


# ── Estratégias ──────────────────────────────────────────────────────────────
def run_strategy(source: dict, strategy: dict) -> list[dict]:
    kind = strategy.get("type")
    if kind == "feed":
        body, _ = http_get(strategy["url"])
        return parse_feed(source, body)
    if kind == "html":
        body, final_url = http_get(source["url"])
        items = scrape_homepage(source, BeautifulSoup(body, "html.parser"), final_url)
        return items if len(items) >= MIN_HTML_ITEMS else []
    if kind == "gnews":
        body, _ = http_get(gnews_url(source))
        return parse_feed(source, body, gnews=True)
    if kind == "gdelt":
        import gdelt_fetcher
        return gdelt_fetcher.fetch_gdelt(source, MAX_ITEMS)
    raise FetchError(f"estratégia desconhecida: {kind}")


def fetch_source(source: dict, hint: dict | None = None) -> tuple[list[dict], dict | None, str]:
    """Devolve (artigos, estratégia que funcionou, mensagem de erro)."""
    if source.get("gdelt"):
        strategy = {"type": "gdelt"}
        try:
            items = run_strategy(source, strategy)
        except FetchError as exc:
            return [], None, str(exc)
        return (items, strategy, "") if items else ([], None, "sem resultados")

    if not source.get("url"):
        return [], None, "URL por confirmar"

    errors: list[str] = []
    tried: set[tuple[str, str]] = set()

    def attempt(strategy: dict) -> list[dict]:
        key = (strategy["type"], strategy.get("url", ""))
        if key in tried:
            return []
        tried.add(key)
        try:
            items = run_strategy(source, strategy)
        except FetchError as exc:
            errors.append(f"{strategy['type']}: {exc}")
            return []
        except Exception as exc:  # noqa: BLE001 - uma fonte nunca deve derrubar as outras
            log.warning("%s: estratégia %s falhou: %r", source["id"], strategy["type"], exc)
            errors.append(f"{strategy['type']}: {exc.__class__.__name__}")
            return []
        if not items:
            errors.append(f"{strategy['type']}: sem artigos")
        return items

    # 1) o que funcionou da última vez
    if hint:
        items = attempt(hint)
        if items:
            return items, hint, ""

    # só Google News (Reuters, AP...)
    if source.get("gnews_only"):
        strategy = {"type": "gnews"}
        items = attempt(strategy)
        if items:
            return items, strategy, ""
        return [], None, "; ".join(errors[-3:]) or "sem resultados"

    # 2) feeds conhecidos
    for url in source.get("feeds", []):
        strategy = {"type": "feed", "url": url}
        items = attempt(strategy)
        if items:
            return items, strategy, ""

    # 3) e 4) página inicial: descobrir RSS, depois ler o HTML
    try:
        body, final_url = http_get(source["url"])
    except FetchError as exc:
        errors.append(f"página inicial: {exc}")
        body = None

    if body is not None:
        soup = BeautifulSoup(body, "html.parser")
        origin = "{0.scheme}://{0.netloc}/".format(urlparse(final_url))
        candidates = discover_feeds(soup, final_url) + [urljoin(origin, p) for p in COMMON_FEED_PATHS]
        for url in candidates[:MAX_FEED_CANDIDATES]:
            strategy = {"type": "feed", "url": url}
            items = attempt(strategy)
            if items:
                return items, strategy, ""
        try:
            items = scrape_homepage(source, soup, final_url)
        except Exception as exc:  # noqa: BLE001
            log.warning("%s: leitura do HTML falhou: %r", source["id"], exc)
            items = []
        if len(items) >= MIN_HTML_ITEMS:
            return items, {"type": "html"}, ""
        errors.append("html: poucos títulos encontrados")

    # 5) Google News
    if GNEWS_FALLBACK:
        strategy = {"type": "gnews"}
        items = attempt(strategy)
        if items:
            return items, strategy, ""

    return [], None, "; ".join(errors[-3:]) or "sem resultados"