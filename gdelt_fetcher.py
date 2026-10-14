"""Recolha de notícias via GDELT DOC 2.0 API (https://api.gdeltproject.org/api/v2/doc/doc).

A GDELT não tem "categorias" fixas como um CMS: a pesquisa é por tema (`theme`),
país, idioma ou texto livre. Os temas abaixo são códigos GKG reais e estáveis,
reagrupados em categorias legíveis para a interface. Cobre até 3 meses de
histórico por pedido; aqui paginamos por janelas de data para trazer semanas
anteriores sem perder as mais recentes.
"""
from __future__ import annotations

import logging
import time
from datetime import datetime, timedelta, timezone
from urllib.parse import urlencode

import fetcher  # reutiliza http_get, clean_text, norm_url, make_article

log = logging.getLogger("nexa.gdelt")

API_URL = "https://api.gdeltproject.org/api/v2/doc/doc"
MAX_RECORDS = 75          # máximo aceite pela API por pedido
WINDOW_HOURS = (6, 24, 72, 168, 336, 720)  # 6h, 1d, 3d, 7d, 14d, 30d: janelas crescentes
REQUEST_GAP = 1.1         # segundos entre pedidos, para não sobrecarregar a API pública
TIMESPAN_SUFFIX = "h"

# id interno -> (rótulo mostrado, query GDELT)
# GKGTHEME são os códigos oficiais do GDELT Global Knowledge Graph.
GDELT_CATEGORIES = {
    "geral":        ("Geral",        ""),
    "economia":     ("Economia",     "theme:ECON_ECONOMY OR theme:ECON_STOCKMARKET OR theme:ECON_INFLATION OR theme:ECON_BANKRUPTCY OR theme:ECON_SUBSIDIES OR theme:ECON_TRADE_DISPUTE OR theme:ECON_CENTRALBANK OR theme:TAX_FNCACT_ECONOMIST"),
    "politica":     ("Política",     "theme:TAX_FNCACT_PRESIDENT OR theme:TAX_FNCACT_MINISTER OR theme:GOV_LEGISLATION OR theme:ELECTION OR theme:GENERAL_GOVERNMENT OR theme:LEADER"),
    "desporto":     ("Desporto",     "theme:SOCCER OR theme:SPORTS"),
    "saude":        ("Saúde",        "theme:HEALTH_PANDEMIC OR theme:MEDICAL OR theme:EPU_CATS_HEALTHCARE OR theme:WHO"),
    "ambiente":     ("Ambiente",     "theme:ENV_CLIMATECHANGE OR theme:NATURAL_DISASTER OR theme:ENV_DISASTER OR theme:DROUGHT"),
    "tecnologia":   ("Tecnologia",   "theme:TECHNOLOGY OR theme:CYBER_ATTACK OR theme:SCIENCE"),
    "energia":      ("Energia",      "theme:ENERGY OR theme:OIL_AND_GAS OR theme:ALTERNATIVE_ENERGY"),
    "seguranca":    ("Segurança",    "theme:ARMEDCONFLICT OR theme:TERROR OR theme:MILITARY OR theme:KILL"),
    "justica":      ("Justiça",      "theme:CRIME_ILLEGAL_DRUGS OR theme:CORRUPTION OR theme:TRIAL OR theme:LEGISLATION"),
    "educacao":     ("Educação",     "theme:EDUCATION"),
    "protestos":    ("Protestos",    "theme:PROTEST OR theme:UNREST_GENERAL"),
    "direitoshum":  ("Direitos Humanos", "theme:HUMAN_RIGHTS OR theme:REFUGEES"),
    "negocios":     ("Negócios",     "theme:BUSINESS OR theme:MANMADE_DISASTER_IMPLIED OR theme:TAX_FNCACT_EXECUTIVE"),
}

# código ISO do país GDELT (FIPS, usado na API) por região do agregador
REGION_COUNTRY = {
    "AO": "AO",  # Angola
    "PT": "PO",  # Portugal (FIPS)
    "BR": "BR",  # Brasil
    "AF": "",    # África: sem filtro único de país, mantém-se por tema
}
REGION_LANG = {"AO": "por", "PT": "por", "BR": "por"}


def _window_bounds(hours: int, now: datetime) -> tuple[str, str]:
    start = now - timedelta(hours=hours)
    fmt = "%Y%m%d%H%M%S"
    return start.strftime(fmt), now.strftime(fmt)


def _build_query(cat_query: str, country: str) -> str:
    parts = [cat_query] if cat_query else ["sourcelang:por"]
    if country:
        parts.append(f"sourcecountry:{country}")
    return " ".join(p for p in parts if p)


def _request(params: dict) -> list[dict]:
    url = API_URL + "?" + urlencode(params)
    body, _ = fetcher.http_get(url)
    try:
        import json
        data = json.loads(body)
    except ValueError:
        return []
    return data.get("articles") or []


def _to_article(raw: dict, source: dict, cat_id: str, cat_label: str) -> dict | None:
    title = fetcher.clean_text(raw.get("title"), 300)
    link = (raw.get("url") or "").strip()
    if not title or not link.startswith(("http://", "https://")):
        return None
    ts = None
    seendate = (raw.get("seendate") or "").strip()
    if len(seendate) >= 14:
        try:
            ts = datetime.strptime(seendate[:14], "%Y%m%d%H%M%S").replace(tzinfo=timezone.utc).timestamp()
        except ValueError:
            ts = None
    article = fetcher.make_article(
        source,
        title,
        link,
        summary="",
        image=raw.get("socialimage") or "",
        ts=ts,
    )
    article["source"] = (raw.get("domain") or source["name"]).strip() or source["name"]
    article["category"] = cat_label
    article["gdelt_category"] = cat_id
    return article


def fetch_gdelt(source: dict, max_items: int) -> list[dict]:
    """Varre as janelas de tempo (recente -> antiga) até reunir max_items, sem repetir ligações."""
    cat_id = source.get("gdelt_category", "geral")
    cat_label, cat_query = GDELT_CATEGORIES.get(cat_id, GDELT_CATEGORIES["geral"])
    country = REGION_COUNTRY.get(source["region"], "")
    query = _build_query(cat_query, country)

    now = datetime.now(timezone.utc)
    seen_links: set[str] = set()
    out: list[dict] = []

    for i, hours in enumerate(WINDOW_HOURS):
        if len(out) >= max_items:
            break
        if i > 0:
            time.sleep(REQUEST_GAP)
        start, end = _window_bounds(hours, now)
        params = {
            "query": query,
            "mode": "ArtList",
            "format": "json",
            "maxrecords": str(MAX_RECORDS),
            "sort": "DateDesc",
            "startdatetime": start,
            "enddatetime": end,
        }
        try:
            raw_items = _request(params)
        except fetcher.FetchError as exc:
            log.warning("gdelt %s/%s janela %dh falhou: %s", source["region"], cat_id, hours, exc)
            continue
        for raw in raw_items:
            article = _to_article(raw, source, cat_id, cat_label)
            if article is None:
                continue
            key = fetcher.norm_url(article["link"])
            if key in seen_links:
                continue
            seen_links.add(key)
            out.append(article)
            if len(out) >= max_items:
                break
    return out


def gdelt_sources(regions: list[str]) -> list[dict]:
    """Uma fonte sintética por (região, categoria) para aparecer no painel de estado."""
    sources = []
    for region in regions:
        for cat_id, (cat_label, _) in GDELT_CATEGORIES.items():
            sources.append({
                "id": f"gdelt-{region.lower()}-{cat_id}",
                "name": f"GDELT · {cat_label}",
                "region": region,
                "category": cat_label,
                "url": "https://api.gdeltproject.org/api/v2/doc/doc",
                "feeds": [],
                "lang": REGION_LANG.get(region, "pt"),
                "gnews_only": False,
                "enabled": True,
                "gdelt": True,
                "gdelt_category": cat_id,
            })
    return sources