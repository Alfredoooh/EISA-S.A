"""Nexa News: API + página web.  Arranque local: python app.py"""
import hmac
import logging
import os

from flask import Flask, jsonify, request, send_from_directory

from news_sources import CATEGORIES, REGIONS, SOURCES
from store import NewsStore

logging.basicConfig(
    level=os.environ.get("LOG_LEVEL", "INFO"),
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
STATIC_DIR = os.path.join(BASE_DIR, "static")

app = Flask(__name__, static_folder=STATIC_DIR, static_url_path="/static")
app.json.ensure_ascii = False
app.json.sort_keys = False

store = NewsStore(SOURCES)
if os.environ.get("DISABLE_BACKGROUND") != "1":
    store.start_background()


def _int_arg(name: str, default: int, low: int, high: int) -> int:
    try:
        value = int(request.args.get(name, default))
    except (TypeError, ValueError):
        return default
    return max(low, min(high, value))


@app.get("/")
def index():
    response = send_from_directory(STATIC_DIR, "index.html")
    response.headers["Cache-Control"] = "no-cache"
    return response


@app.get("/health")
def health():
    return jsonify(status="ok", **store.meta())


@app.get("/api/news")
def api_news():
    store.ensure_fresh()
    limit = _int_arg("limit", 30, 1, 100)
    offset = _int_arg("offset", 0, 0, 100_000)
    total, items = store.query(
        region=request.args.get("region", ""),
        category=request.args.get("category", ""),
        source=request.args.get("source", ""),
        lang=request.args.get("lang", ""),
        q=request.args.get("q", ""),
        limit=limit,
        offset=offset,
    )
    return jsonify(meta=store.meta(), total=total, limit=limit, offset=offset, items=items)


@app.get("/api/sources")
def api_sources():
    return jsonify(
        meta=store.meta(),
        regions=REGIONS,
        categories=CATEGORIES,
        sources=store.sources_status(),
    )


@app.post("/api/refresh")
def api_refresh():
    token = os.environ.get("REFRESH_TOKEN", "")
    if token:
        sent = request.headers.get("X-Refresh-Token") or request.args.get("token", "")
        if not hmac.compare_digest(sent.encode("utf-8"), token.encode("utf-8")):
            return jsonify(error="não autorizado"), 401
    result = store.refresh_async()
    return jsonify(result=result, refreshing=store.refreshing), 202


@app.errorhandler(404)
def not_found(_error):
    if request.path.startswith("/api/"):
        return jsonify(error="não encontrado"), 404
    return "Não encontrado", 404


@app.after_request
def add_headers(response):
    if request.path.startswith("/api/") or request.path == "/health":
        response.headers["Access-Control-Allow-Origin"] = os.environ.get("CORS_ORIGINS", "*")
        response.headers["Access-Control-Allow-Headers"] = "Content-Type, X-Refresh-Token"
        response.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
        response.headers["Cache-Control"] = "no-store"
    response.headers.setdefault("X-Content-Type-Options", "nosniff")
    response.headers.setdefault("Referrer-Policy", "strict-origin-when-cross-origin")
    return response


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "5000")), debug=False)
