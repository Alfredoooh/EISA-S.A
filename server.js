'use strict';

const crypto = require('crypto');
const path = require('path');
const express = require('express');

const { CATEGORIES } = require('./categories');
const gdelt = require('./sources/gdelt');
const guardian = require('./sources/guardian');
const nyt = require('./sources/nyt');
const gnews = require('./sources/gnews');
const newsdata = require('./sources/newsdata');

const PORT = process.env.PORT || 5000;
const REFRESH_SECONDS = Math.max(120, parseInt(process.env.REFRESH_SECONDS || '600', 10));
const MAX_ITEMS_PER_COMBO = parseInt(process.env.MAX_ITEMS_PER_COMBO || '60', 10);
const MAX_AGE_DAYS = parseInt(process.env.MAX_AGE_DAYS || '30', 10);
const MAX_ARTICLES = parseInt(process.env.MAX_ARTICLES || '8000', 10);
const COMBO_CONCURRENCY = Math.max(1, parseInt(process.env.COMBO_CONCURRENCY || '4', 10));
const REFRESH_TOKEN = process.env.REFRESH_TOKEN || '';
const CORS_ORIGIN = process.env.CORS_ORIGINS || '*';

const PROVIDERS = {
  gdelt: { label: 'GDELT', fetchCategory: gdelt.fetchCategory },
  guardian: { label: 'The Guardian', fetchCategory: guardian.fetchCategory },
  nyt: { label: 'NYT', fetchCategory: nyt.fetchCategory },
  gnews: { label: 'GNews', fetchCategory: gnews.fetchCategory },
  newsdata: { label: 'NewsData', fetchCategory: newsdata.fetchCategory },
};

const app = express();
app.use(express.static(path.join(__dirname, 'public'), { etag: false, cacheControl: false }));

const state = {
  updatedAt: 0,
  refreshing: false,
  articlesById: new Map(),
  comboStatus: new Map(), // "gdelt:economia" -> status
};

function hashId(link) {
  return crypto.createHash('sha1').update(link.toLowerCase()).digest('hex').slice(0, 16);
}

function norm(text) {
  return String(text || '')
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
}

function comboKey(provider, catId) {
  return `${provider}:${catId}`;
}

function allCombos() {
  const out = [];
  for (const provider of Object.keys(PROVIDERS)) {
    for (const catId of Object.keys(CATEGORIES)) {
      out.push({ provider, catId });
    }
  }
  return out;
}

for (const { provider, catId } of allCombos()) {
  state.comboStatus.set(comboKey(provider, catId), {
    provider,
    catId,
    label: CATEGORIES[catId].label,
    ok: null,
    count: 0,
    error: '',
    checkedAt: null,
    latest: null,
    ms: null,
  });
}

async function refreshCombo(provider, catId) {
  const key = comboKey(provider, catId);
  const status = state.comboStatus.get(key);
  const startedAt = Date.now();
  try {
    const items = await PROVIDERS[provider].fetchCategory(catId, MAX_ITEMS_PER_COMBO);
    const now = Date.now() / 1000;
    let latest = null;

    for (const item of items) {
      const id = hashId(item.link);
      const ts = item.ts !== null && item.ts !== undefined ? item.ts : now;
      const existing = state.articlesById.get(id);
      if (existing && existing.ts >= ts) continue;
      state.articlesById.set(id, {
        id,
        title: item.title,
        link: item.link,
        summary: item.summary,
        image: item.image,
        source: item.source,
        provider: item.provider,
        category: item.category,
        categoryId: item.categoryId,
        lang: item.lang || '',
        ts,
        search: norm(`${item.title} ${item.source}`),
      });
      if (latest === null || ts > latest) latest = ts;
    }

    status.ok = items.length > 0;
    status.count = items.length;
    status.error = items.length > 0 ? '' : 'sem resultados';
    status.latest = latest;
    status.checkedAt = Math.floor(Date.now() / 1000);
    status.ms = Date.now() - startedAt;
  } catch (err) {
    status.ok = false;
    status.error = err.message || 'erro desconhecido';
    status.checkedAt = Math.floor(Date.now() / 1000);
    status.ms = Date.now() - startedAt;
    console.warn(`combo ${key} falhou: ${status.error}`);
  }
}

async function runPool(items, worker, concurrency) {
  let cursor = 0;
  async function next() {
    while (cursor < items.length) {
      const idx = cursor;
      cursor += 1;
      await worker(items[idx]);
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, next));
}

function pruneOldArticles() {
  const cutoff = Date.now() / 1000 - MAX_AGE_DAYS * 86400;
  for (const [id, article] of state.articlesById) {
    if (article.ts < cutoff) state.articlesById.delete(id);
  }
  if (state.articlesById.size > MAX_ARTICLES) {
    const sorted = [...state.articlesById.values()].sort((a, b) => b.ts - a.ts);
    state.articlesById = new Map(sorted.slice(0, MAX_ARTICLES).map((a) => [a.id, a]));
  }
}

let refreshPromise = null;

function refreshAll() {
  if (refreshPromise) return refreshPromise;
  state.refreshing = true;
  refreshPromise = (async () => {
    const combos = allCombos();
    await runPool(combos, ({ provider, catId }) => refreshCombo(provider, catId), COMBO_CONCURRENCY);
    pruneOldArticles();
    state.updatedAt = Math.floor(Date.now() / 1000);
    const okCount = [...state.comboStatus.values()].filter((s) => s.ok).length;
    console.log(
      `Atualização concluída: ${okCount}/${state.comboStatus.size} combinações OK, ${state.articlesById.size} notícias em memória.`
    );
  })()
    .catch((err) => console.error('erro na atualização:', err))
    .finally(() => {
      state.refreshing = false;
      refreshPromise = null;
    });
  return refreshPromise;
}

function startBackgroundLoop() {
  refreshAll();
  setInterval(refreshAll, REFRESH_SECONDS * 1000);
}

function ensureFresh() {
  if (state.refreshing || !state.updatedAt) return;
  const staleFor = Date.now() / 1000 - state.updatedAt;
  if (staleFor > REFRESH_SECONDS * 1.5) refreshAll();
}

function queryArticles({ category, provider, q, limit, offset }) {
  let items = [...state.articlesById.values()];

  const categories = new Set((category || '').split(',').map((s) => norm(s.trim())).filter(Boolean));
  if (categories.size) items = items.filter((a) => categories.has(norm(a.category)));

  const providers = new Set((provider || '').split(',').map((s) => s.trim().toLowerCase()).filter(Boolean));
  if (providers.size) items = items.filter((a) => providers.has(a.provider));

  const tokens = norm(q).split(/\s+/).filter(Boolean);
  if (tokens.length) items = items.filter((a) => tokens.every((t) => a.search.includes(t)));

  items.sort((a, b) => b.ts - a.ts);
  const total = items.length;
  const page = items.slice(offset, offset + limit).map((a) => ({
    id: a.id,
    title: a.title,
    link: a.link,
    summary: a.summary,
    image: a.image,
    source: a.source,
    provider: a.provider,
    category: a.category,
    lang: a.lang,
    ts: Math.floor(a.ts),
    published: new Date(a.ts * 1000).toISOString(),
  }));
  return { total, items: page };
}

function meta() {
  const combos = [...state.comboStatus.values()];
  return {
    updated_at: state.updatedAt || null,
    refreshing: state.refreshing,
    articles: state.articlesById.size,
    sources_total: combos.length,
    sources_ok: combos.filter((s) => s.ok).length,
  };
}

function intArg(req, name, def, lo, hi) {
  const v = parseInt(req.query[name], 10);
  if (Number.isNaN(v)) return def;
  return Math.max(lo, Math.min(hi, v));
}

app.get('/health', (req, res) => {
  res.json({ status: 'ok', ...meta() });
});

app.get('/api/news', (req, res) => {
  ensureFresh();
  const limit = intArg(req, 'limit', 30, 1, 100);
  const offset = intArg(req, 'offset', 0, 0, 100000);
  const result = queryArticles({
    category: req.query.category || '',
    provider: req.query.provider || '',
    q: req.query.q || '',
    limit,
    offset,
  });
  res.json({ meta: meta(), total: result.total, limit, offset, items: result.items });
});

app.get('/api/sources', (req, res) => {
  const sources = [...state.comboStatus.values()].map((s) => ({
    id: comboKey(s.provider, s.catId),
    name: `${PROVIDERS[s.provider].label} · ${s.label}`,
    provider: s.provider,
    category: s.label,
    ok: s.ok,
    count: s.count,
    latest: s.latest,
    checked_at: s.checkedAt,
    ms: s.ms,
    error: s.error,
  }));
  res.json({
    meta: meta(),
    providers: Object.entries(PROVIDERS).map(([id, p]) => ({ id, label: p.label })),
    categories: Object.values(CATEGORIES).map((c) => c.label),
    sources,
  });
});

app.post('/api/refresh', (req, res) => {
  if (REFRESH_TOKEN) {
    const sent = req.get('X-Refresh-Token') || req.query.token || '';
    const a = Buffer.from(String(sent));
    const b = Buffer.from(REFRESH_TOKEN);
    const authorized = a.length === b.length && crypto.timingSafeEqual(a, b);
    if (!authorized) return res.status(401).json({ error: 'não autorizado' });
  }
  if (state.refreshing) {
    res.status(202).json({ result: 'busy', refreshing: true });
  } else {
    refreshAll();
    res.status(202).json({ result: 'started', refreshing: true });
  }
});

app.use((req, res, next) => {
  if (req.path.startsWith('/api/') || req.path === '/health') {
    res.set('Access-Control-Allow-Origin', CORS_ORIGIN);
    res.set('Access-Control-Allow-Headers', 'Content-Type, X-Refresh-Token');
    res.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.set('Cache-Control', 'no-store');
  }
  res.set('X-Content-Type-Options', 'nosniff');
  res.set('Referrer-Policy', 'strict-origin-when-cross-origin');
  next();
});

app.use((req, res) => {
  if (req.path.startsWith('/api/')) return res.status(404).json({ error: 'não encontrado' });
  res.status(404).send('Não encontrado');
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Nexa News (Node) a correr na porta ${PORT}`);
  if (process.env.DISABLE_BACKGROUND !== '1') startBackgroundLoop();
});