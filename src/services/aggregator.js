const gdelt = require('./gdelt');
const gnews = require('./gnews');
const currents = require('./currents');
const rss = require('./rss');
const searxng = require('./searxng');
const cache = require('../utils/cache');
const { CATEGORY_QUERIES } = require('../config');
const { CURRENTS_SUPPORTED } = require('../services/currents');
const { finalizeArticles, extractDomain } = require('../utils/news');

function normalizeCategory(category) {
  const aliases = {
    general: 'internacional',
    world: 'internacional',
    business: 'negocios',
    technology: 'tecnologia',
    sports: 'desporto',
    football: 'futebol',
    science: 'ciencia',
    health: 'saude',
    politics: 'politica',
    entertainment: 'entretenimento',
    ai: 'inteligencia-artificial',
    'inteligência-artificial': 'inteligencia-artificial',
    'inteligencia artificial': 'inteligencia-artificial',
    'biotechnology': 'biotecnologia',
    'real estate': 'imobiliario',
    'stock market': 'acoes',
    'stocks': 'acoes',
    'crypto': 'cripto'
  };
  const key = String(category || 'internacional').trim().toLowerCase();
  return aliases[key] || key;
}

function isoWindow(hours) {
  const now = Date.now();
  return {
    now,
    from: new Date(now - hours * 3600000).toISOString(),
    to: new Date(now + 10 * 60 * 1000).toISOString()
  };
}

async function categoryNews({ category = 'internacional', lang = 'en', maxAgeHours = 24, page = 1, pageSize = 20 } = {}) {
  const normalizedCategory = normalizeCategory(category);
  const safeLang = /^[a-z]{2}$/i.test(String(lang)) ? String(lang).toLowerCase() : 'en';
  const cacheKey = `feed_v3_${normalizedCategory}_${safeLang}_${maxAgeHours}`;
  const cached = cache.get(cacheKey);

  let all;
  let cacheHit = false;
  if (cached) {
    all = cached;
    cacheHit = true;
  } else {
    const query = CATEGORY_QUERIES[normalizedCategory] || CATEGORY_QUERIES.internacional;
    const gdeltQuery = buildGdeltTopicQuery(query);
    const tasks = [
      gdelt.fetchNews({ query: gdeltQuery, timespan: `${Math.min(Math.max(Number(maxAgeHours) || 24, 1), 72)}h`, maxRecords: 100, internationalOnly: true }),
      gnews.fetchNews({ category: normalizedCategory, lang: safeLang, max: 30 }),
      ...(CURRENTS_SUPPORTED.has(normalizedCategory) ? [currents.fetchNews({ category: normalizedCategory, lang: safeLang, max: 30 })] : []),
      rss.fetchByCategory(normalizedCategory),
      searxng.searchNews({ query: `${query} latest`, language: safeLang, category: normalizedCategory, timeRange: 'day', max: 40 })
    ];
    const results = await Promise.allSettled(tasks);
    all = results.filter(r => r.status === 'fulfilled').flatMap(r => r.value);
    all = finalizeArticles(all, {
      category: normalizedCategory,
      maxAgeHours: Math.min(Math.max(Number(maxAgeHours) || 24, 1), 72),
      minimumFresh: Math.min(pageSize * 2, 12),
      allowFallback: true
    });
    cache.set(cacheKey, all, 60);
  }

  const safePage = Math.max(1, Number.parseInt(page, 10) || 1);
  const safeSize = Math.min(50, Math.max(1, Number.parseInt(pageSize, 10) || 20));
  const start = (safePage - 1) * safeSize;
  const articles = all.slice(start, start + safeSize).map(publicArticle);

  return {
    category: normalizedCategory,
    label: normalizedCategory,
    page: safePage,
    pageSize: safeSize,
    total: all.length,
    totalPages: Math.ceil(all.length / safeSize),
    generatedAt: new Date().toISOString(),
    cacheHit,
    internationalOnly: true,
    articles
  };
}

async function searchNews({ query, lang = 'en', maxAgeHours = 24, page = 1, pageSize = 20 } = {}) {
  const q = String(query || '').trim();
  if (!q) throw new Error('query é obrigatório');
  const safeLang = /^[a-z]{2}$/i.test(String(lang)) ? String(lang).toLowerCase() : 'en';
  const window = isoWindow(Math.min(Math.max(Number(maxAgeHours) || 24, 1), 72));

  const tasks = [
    gdelt.fetchNews({ query: q, timespan: `${Math.min(Math.max(Number(maxAgeHours) || 24, 1), 72)}h`, maxRecords: 120, internationalOnly: true }),
    gnews.searchNews({ query: q, lang: safeLang, max: 30, from: window.from, to: window.to }),
    searxng.searchNews({ query: `${q} latest news`, language: safeLang, category: 'internacional', timeRange: 'day', max: 50 })
  ];
  const results = await Promise.allSettled(tasks);
  const all = results.filter(r => r.status === 'fulfilled').flatMap(r => r.value);
  const final = finalizeArticles(all, {
    query: q,
    maxAgeHours: Math.min(Math.max(Number(maxAgeHours) || 24, 1), 72),
    minimumFresh: Math.min(pageSize * 2, 12),
    allowFallback: true
  });

  const safePage = Math.max(1, Number.parseInt(page, 10) || 1);
  const safeSize = Math.min(50, Math.max(1, Number.parseInt(pageSize, 10) || 20));
  const start = (safePage - 1) * safeSize;
  return {
    query: q,
    page: safePage,
    pageSize: safeSize,
    total: final.length,
    totalPages: Math.ceil(final.length / safeSize),
    generatedAt: new Date().toISOString(),
    internationalOnly: true,
    articles: final.slice(start, start + safeSize).map(publicArticle)
  };
}

async function latestNews({ lang = 'en', maxAgeHours = 12, page = 1, pageSize = 20 } = {}) {
  return categoryNews({ category: 'internacional', lang, maxAgeHours, page, pageSize });
}

function publicArticle(a) {
  return {
    ...a,
    domain: extractDomain(a.url),
    ageHours: a.publishedAt ? Number(((Date.now() - new Date(a.publishedAt).getTime()) / 3600000).toFixed(2)) : null,
    ranked: undefined
  };
}

function buildGdeltTopicQuery(query) {
  const tokens = String(query || '').split(/\s+/).map(x => x.trim()).filter(Boolean).slice(0, 14);
  if (!tokens.length) return 'news';
  // GDELT funciona melhor para tópicos quando os termos são alternativas, evitando
  // exigir que uma notícia contenha todos os termos ao mesmo tempo.
  return `(${tokens.map(token => token.replace(/[()]/g, '')).join(' OR ')})`;
}

module.exports = { normalizeCategory, categoryNews, latestNews, searchNews, buildGdeltTopicQuery };
