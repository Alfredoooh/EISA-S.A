"""Estado em memória das notícias e atualização periódica das fontes."""
from __future__ import annotations

import logging
import os
import threading
import time
import unicodedata
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone

import fetcher

log = logging.getLogger("nexa.store")

REFRESH_SECONDS = max(120, int(os.environ.get("REFRESH_SECONDS", "600")))
MAX_WORKERS = max(1, int(os.environ.get("MAX_WORKERS", "8")))
MAX_AGE_DAYS = int(os.environ.get("MAX_AGE_DAYS", "7"))
MAX_ARTICLES = int(os.environ.get("MAX_ARTICLES", "3000"))
PER_SOURCE_KEEP = 120        # máximo de notícias guardadas por fonte
MIN_KEEP_PER_SOURCE = 8      # mínimo mantido por fonte, mesmo que antigas
RELEARN_AFTER = 6 * 3600     # volta a procurar RSS em fontes que só funcionam por HTML/Google News
MANUAL_REFRESH_GAP = 60      # segundos mínimos entre atualizações manuais
FIRST_SEEN_LIMIT = 20000
MIN_TS = 946684800.0         # 2000-01-01: datas anteriores são tratadas como inválidas


def norm(text: str) -> str:
    """Minúsculas e sem acentos, para pesquisa."""
    text = unicodedata.normalize("NFKD", (text or "").casefold())
    return "".join(ch for ch in text if not unicodedata.combining(ch))


def public(article: dict) -> dict:
    return {
        "id": article["id"],
        "title": article["title"],
        "link": article["link"],
        "summary": article["summary"],
        "image": article["image"],
        "source_id": article["source_id"],
        "source": article["source"],
        "region": article["region"],
        "category": article["category"],
        "lang": article["lang"],
        "ts": int(article["ts"]),
        "published": datetime.fromtimestamp(article["ts"], timezone.utc).isoformat(timespec="seconds"),
        "dated": article["dated"],
    }


class NewsStore:
    def __init__(self, sources: list[dict]):
        self._sources = {s["id"]: s for s in sources}
        self._lock = threading.RLock()
        self._refresh_lock = threading.Lock()
        self._started = False
        self._by_source: dict[str, list[dict]] = {}
        self._first_seen: dict[str, float] = {}
        self._hints: dict[str, tuple[dict, float]] = {}
        self._flat: list[dict] = []
        self._status = {sid: self._initial_status(s) for sid, s in self._sources.items()}
        self.updated_at = 0.0
        self.refreshing = False

    # ── estado das fontes ────────────────────────────────────────────────────
    @staticmethod
    def _initial_status(source: dict) -> dict:
        return {
            "id": source["id"],
            "name": source["name"],
            "region": source["region"],
            "category": source["category"],
            "url": source["url"],
            "enabled": bool(source.get("enabled", True) and source.get("url")),
            "ok": None,
            "method": None,
            "feed": None,
            "count": 0,
            "latest": None,
            "checked_at": None,
            "ms": None,
            "error": "" if source.get("url") else "URL por confirmar",
            "fails": 0,
            "next_try": 0.0,
        }

    def sources_status(self) -> list[dict]:
        with self._lock:
            return [
                {k: v for k, v in status.items() if k not in ("fails", "next_try")}
                for status in self._status.values()
            ]

    def meta(self) -> dict:
        with self._lock:
            enabled = [s for s in self._status.values() if s["enabled"]]
            return {
                "updated_at": int(self.updated_at) or None,
                "refreshing": self.refreshing,
                "articles": len(self._flat),
                "sources_total": len(enabled),
                "sources_ok": sum(1 for s in enabled if s["ok"]),
            }

    # ── atualização ──────────────────────────────────────────────────────────
    def _is_due(self, sid: str, now: float, force: bool) -> bool:
        status = self._status[sid]
        if not status["enabled"]:
            return False
        return force or now >= status["next_try"]

    def _fetch_one(self, source: dict) -> tuple:
        started = time.time()
        with self._lock:
            saved = self._hints.get(source["id"])
        hint = None
        if saved:
            strategy, learned_at = saved
            if strategy["type"] == "feed" or started - learned_at < RELEARN_AFTER:
                hint = strategy
        articles, strategy, error = fetcher.fetch_source(source, hint)
        return articles, strategy, error, time.time() - started

    def _finalize(self, articles: list[dict], now: float) -> None:
        for index, article in enumerate(articles):
            ts = article.get("ts")
            if ts is None:
                # sem data na fonte: usa a primeira vez que a vimos, mantendo a ordem da página
                ts = self._first_seen.setdefault(article["id"], now - index * 90)
                article["dated"] = False
            else:
                article["dated"] = True
            article["ts"] = min(max(float(ts), MIN_TS), now)
            article["search"] = norm(f'{article["title"]} {article["summary"]} {article["source"]}')
        if len(self._first_seen) > FIRST_SEEN_LIMIT:
            newest = sorted(self._first_seen.items(), key=lambda kv: kv[1], reverse=True)
            self._first_seen = dict(newest[: FIRST_SEEN_LIMIT // 2])

    def _ingest(self, source: dict, result: tuple) -> None:
        articles, strategy, error, elapsed = result
        now = time.time()
        sid = source["id"]
        with self._lock:
            status = self._status[sid]
            status["checked_at"] = int(now)
            status["ms"] = int(elapsed * 1000)
            if articles:
                self._finalize(articles, now)
                merged = {a["id"]: a for a in self._by_source.get(sid, [])}
                merged.update({a["id"]: a for a in articles})
                ordered = sorted(merged.values(), key=lambda a: a["ts"], reverse=True)
                cutoff = now - MAX_AGE_DAYS * 86400
                kept = [a for i, a in enumerate(ordered) if i < MIN_KEEP_PER_SOURCE or a["ts"] >= cutoff]
                self._by_source[sid] = kept[:PER_SOURCE_KEEP]
                status.update(
                    ok=True,
                    error="",
                    fails=0,
                    next_try=0.0,
                    method=strategy["type"],
                    feed=strategy.get("url"),
                )
                saved = self._hints.get(sid)
                if not saved or saved[0] != strategy:
                    self._hints[sid] = (strategy, now)
            else:
                status["fails"] += 1
                status["ok"] = False
                status["error"] = error or "sem resultados"
                status["next_try"] = now + min(3600, 60 * 2 ** status["fails"])
            items = self._by_source.get(sid, [])
            status["count"] = len(items)
            status["latest"] = int(items[0]["ts"]) if items else None

    def _rebuild(self) -> None:
        with self._lock:
            seen: set[str] = set()
            flat: list[dict] = []
            for items in self._by_source.values():
                for article in items:
                    if article["id"] not in seen:
                        seen.add(article["id"])
                        flat.append(article)
            flat.sort(key=lambda a: a["ts"], reverse=True)
            self._flat = flat[:MAX_ARTICLES]

    def refresh(self, force: bool = False) -> bool:
        """Atualiza as fontes. Devolve False se já havia uma atualização em curso."""
        if not self._refresh_lock.acquire(blocking=False):
            return False
        self.refreshing = True
        started = time.time()
        try:
            due = [s for s in self._sources.values() if self._is_due(s["id"], started, force)]
            with ThreadPoolExecutor(max_workers=MAX_WORKERS, thread_name_prefix="fetch") as pool:
                futures = {pool.submit(self._fetch_one, s): s for s in due}
                for future in as_completed(futures):
                    source = futures[future]
                    try:
                        result = future.result()
                    except Exception as exc:  # noqa: BLE001
                        log.exception("falha inesperada em %s", source["id"])
                        result = ([], None, exc.__class__.__name__, 0.0)
                    self._ingest(source, result)
                    self._rebuild()  # as primeiras notícias aparecem logo, sem esperar pelas restantes
            self.updated_at = time.time()
            ok = sum(1 for s in self._status.values() if s["ok"])
            log.info(
                "Atualização concluída: %d/%d fontes OK, %d notícias, %.1fs",
                ok, len(self._status), len(self._flat), time.time() - started,
            )
            return True
        finally:
            self.refreshing = False
            self._refresh_lock.release()

    def refresh_async(self) -> str:
        if self.refreshing:
            return "busy"
        if time.time() - self.updated_at < MANUAL_REFRESH_GAP:
            return "recent"
        threading.Thread(target=self.refresh, kwargs={"force": True}, name="manual-refresh", daemon=True).start()
        return "started"

    def ensure_fresh(self) -> None:
        """Se o processo esteve parado (plano gratuito), atualiza quando alguém aparece."""
        if self.refreshing or not self.updated_at:
            return
        if time.time() - self.updated_at > REFRESH_SECONDS * 1.5:
            threading.Thread(target=self.refresh, name="stale-refresh", daemon=True).start()

    def start_background(self) -> None:
        if self._started:
            return
        self._started = True
        threading.Thread(target=self._loop, name="news-loop", daemon=True).start()

    def _loop(self) -> None:
        while True:
            try:
                self.refresh()
            except Exception:  # noqa: BLE001
                log.exception("erro na atualização periódica")
            time.sleep(REFRESH_SECONDS)

    # ── consulta ─────────────────────────────────────────────────────────────
    def query(self, region="", category="", source="", lang="", q="", limit=30, offset=0):
        items = self._flat
        regions = {x.strip().upper() for x in region.split(",") if x.strip()}
        if regions:
            items = [a for a in items if a["region"] in regions]
        categories = {x.strip().casefold() for x in category.split(",") if x.strip()}
        if categories:
            items = [a for a in items if a["category"].casefold() in categories]
        sources = {x.strip() for x in source.split(",") if x.strip()}
        if sources:
            items = [a for a in items if a["source_id"] in sources]
        langs = {x.strip().lower() for x in lang.split(",") if x.strip()}
        if langs:
            items = [a for a in items if a["lang"] in langs]
        tokens = norm(q).split()
        if tokens:
            items = [a for a in items if all(t in a["search"] for t in tokens)]
        total = len(items)
        return total, [public(a) for a in items[offset: offset + limit]]
