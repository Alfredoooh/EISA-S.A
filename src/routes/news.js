const express = require('express');
const crypto = require('crypto');
const router = express.Router();
const gdelt = require('../services/gdelt');
const currents = require('../services/currents');
const gnews = require('../services/gnews');
const rss = require('../services/rss');
const cache = require('../utils/cache');
const {
  CATEGORY_LIST,
  GNEWS_CATEGORIES,
  filterInternationalQuality,
  isBlockedDomain,
  qualityScore
} = require('../config/newsPolicy');

const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 50;
const DEFAULT_FRESH_HOURS = Number(process.env.FRESHNESS_HOURS) || 48;
// Rolling archive: keep cached articles and refresh cache expiry on each request (30 days idle by default).
const RETENTION_HOURS = Math.max(24, Number(process.env.NEWS_RETENTION_HOURS) || 720);
const RETENTION_SECONDS = RETENTION_HOURS * 60 * 60;
const MAX_CACHED_ARTICLES = 800;
const MIN_SOURCE_QUALITY = Number(process.env.MIN_SOURCE_QUALITY) || 80;

const CATEGORY_QUERIES = {
  world: 'international OR world OR geopolitics OR diplomacy OR conflict OR summit',
  business: 'business OR economy OR finance OR markets OR companies OR banking OR trade',
  technology: 'technology OR artificial intelligence OR cybersecurity OR software OR innovation',
  entertainment: 'entertainment OR film OR cinema OR music OR television OR culture',
  sports: 'sports OR football OR soccer OR tennis OR basketball OR athletics OR olympics',
  science: 'science OR research OR discovery OR space OR biology OR physics',
  health: 'health OR medicine OR medical research OR public health OR hospitals',
  society: 'society OR communities OR demographics OR social policy',
  politics_government: 'politics OR government OR parliament OR election OR diplomacy OR legislation',
  lifestyle_leisure: 'lifestyle OR travel OR food OR fashion OR leisure',
  human_interest: 'human interest OR people OR community',
  crime_law_justice: 'law OR justice OR courts OR legal OR crime investigation',
  education: 'education OR universities OR schools OR students OR learning',
  environment: 'environment OR climate OR biodiversity OR conservation OR sustainability',
  labour: 'labour OR employment OR jobs OR workplace OR workers',
  automotive: 'automotive OR cars OR electric vehicles OR transport industry',
  real_estate: 'real estate OR housing OR property OR construction'
};

// Accept the old frontend IDs while publishing only canonical provider categories.
const CATEGORY_ALIASES = {
  international: 'world', internacional: 'world', general: 'world',
  economy: 'business', economia: 'business', finances: 'business', finance: 'business',
  financas: 'business', stocks: 'business', shares: 'business', acoes: 'business',
  markets: 'business', mercados: 'business', crypto: 'business', cripto: 'business',
  business_news: 'business', negocios: 'business', startups: 'business',
  tecnologia: 'technology', ai: 'technology', artificial_intelligence: 'technology',
  inteligencia_artificial: 'technology', biotech: 'science', biotechnology: 'science',
  biotecnologia: 'science', ciencia: 'science', medicine: 'health', saude: 'health',
  politics: 'politics_government', politica: 'politics_government', government: 'politics_government',
  sports_news: 'sports', sport: 'sports', desporto: 'sports', football: 'sports',
  soccer: 'sports', futebol: 'sports', cinema: 'entertainment', film: 'entertainment',
  music: 'entertainment', musica: 'entertainment', gaming: 'entertainment', cultura: 'entertainment',
  ambiente: 'environment', climate: 'environment', carreira: 'labour', jobs: 'labour',
  emprego: 'labour', empregos: 'labour', automovel: 'automotive', cars: 'automotive',
  imobiliario: 'real_estate', property: 'real_estate', travel: 'lifestyle_leisure',
  viagens: 'lifestyle_leisure', food: 'lifestyle_leisure', gastronomia: 'lifestyle_leisure',
  fashion: 'lifestyle_leisure', moda: 'lifestyle_leisure', educacao: 'education',
  law: 'crime_law_justice', direito: 'crime_law_justice', security: 'crime_law_justice',
  seguranca: 'crime_law_justice'
};

router.get('/categories', (req, res) => {
  res.json({
    status: 'ok',
    mode: 'international_only',
    cacheRetentionHours: RETENTION_HOURS,
    categories: CATEGORY_LIST.map(c => ({
      id: c.id,
      label: c.label,
      // GDELT must stay present for all feed categories, not only search.
      providers: Array.from(new Set(['gdelt'].concat(c.providers || []))),
      providerCategories: {
        gnews: c.gnews || null,
        currents: c.currents || null,
        gdelt: CATEGORY_QUERIES[c.id] || CATEGORY_QUERIES.world
      }
    }))
  });
});

router.get('/sources', (req, res) => {
  const sources = Object.entries(require('../config/newsPolicy').QUALITY_SOURCES)
    .sort((a, b) => b[1] - a[1])
    .map(([domain, score]) => ({ domain, qualityScore: score }));
  res.json({
    status: 'ok', mode: 'international_only',
    blockedDomains: ['bbc.com', 'bbc.co.uk', 'bbci.co.uk'], sources
  });
});

router.get('/latest', async (req, res) => {
  const page = clampInt(req.query.page, 1, 180);
  const pageSize = clampInt(req.query.pageSize || req.query.max, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
  const freshHours = clampInt(req.query.maxAgeHours, DEFAULT_FRESH_HOURS, 168);
  const key = 'rolling_news:category:world';
  try {
    const incoming = await fetchCategoryArticles('world', page, pageSize);
    const merged = mergeRollingCache(key, incoming, freshHours);
    res.json(buildResponse({ articles: merged.articles, page, pageSize, category: 'world', freshHours,
      newArticles: merged.added, cacheCount: merged.articles.length }));
  } catch (err) {
    const saved = keepWithinRetention(cache.get(key) || []);
    if (saved.length) return res.json(buildResponse({ articles: saved, page, pageSize, category: 'world',
      freshHours, newArticles: 0, cacheCount: saved.length, upstreamError: true }));
    res.status(500).json({ error: err.message });
  }
});

router.get('/search', async (req, res) => {
  const q = String(req.query.q || '').trim();
  const page = clampInt(req.query.page, 1, 180);
  const pageSize = clampInt(req.query.pageSize || req.query.max, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
  if (!q) return res.status(400).json({ error: 'Parâmetro q é obrigatório' });
  if (q.length > 300) return res.status(400).json({ error: 'q é demasiado longo' });
  return searchHandler(res, q, page, pageSize);
});

// Compatibility route: supports canonical IDs and the previous frontend's IDs.
router.get('/', async (req, res) => {
  const categoryId = normalizeCategoryId(req.query.category || 'world');
  const page = clampInt(req.query.page, 1, 180);
  const pageSize = clampInt(req.query.pageSize || req.query.max, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
  const freshHours = clampInt(req.query.maxAgeHours, DEFAULT_FRESH_HOURS, 168);
  const q = String(req.query.q || '').trim();
  if (q) return searchHandler(res, q, page, pageSize);

  if (!GNEWS_CATEGORIES[categoryId]) {
    return res.status(400).json({ error: 'Categoria inválida', requestedCategory: req.query.category || 'world',
      availableCategories: CATEGORY_LIST.map(c => c.id), legacyAliasesSupported: true });
  }

  const key = 'rolling_news:category:' + categoryId;
  try {
    const incoming = await fetchCategoryArticles(categoryId, page, pageSize);
    const merged = mergeRollingCache(key, incoming, freshHours);
    res.json(buildResponse({ articles: merged.articles, page, pageSize, category: categoryId, freshHours,
      newArticles: merged.added, cacheCount: merged.articles.length }));
  } catch (err) {
    const saved = keepWithinRetention(cache.get(key) || []);
    if (saved.length) return res.json(buildResponse({ articles: saved, page, pageSize, category: categoryId,
      freshHours, newArticles: 0, cacheCount: saved.length, upstreamError: true }));
    res.status(500).json({ error: err.message });
  }
});

async function searchHandler(res, q, page, pageSize) {
  const key = 'rolling_news:search:' + crypto.createHash('sha1').update(q.toLowerCase()).digest('hex').slice(0, 20);
  try {
    const jobs = [
      gdelt.fetchNews({ query: q, lang: 'english', timespan: '72h', maxRecords: 100 }),
      gnews.searchNews({ query: q, lang: 'en', page, max: pageSize }),
      currents.searchNews({ keywords: q, lang: 'en', page, pageSize: Math.max(30, pageSize) })
    ];
    const results = await Promise.allSettled(jobs);
    const incoming = [];
    for (const result of results) {
      if (result.status === 'fulfilled' && Array.isArray(result.value)) incoming.push(...result.value);
      else if (result.status === 'rejected') logProviderError(result.reason);
    }
    const merged = mergeRollingCache(key, incoming, 72);
    return res.json(buildResponse({ articles: merged.articles, page, pageSize, category: null, freshHours: 72,
      q, newArticles: merged.added, cacheCount: merged.articles.length }));
  } catch (err) {
    const saved = keepWithinRetention(cache.get(key) || []);
    if (saved.length) return res.json(buildResponse({ articles: saved, page, pageSize, category: null,
      freshHours: 72, q, newArticles: 0, cacheCount: saved.length, upstreamError: true }));
    return res.status(500).json({ error: err.message });
  }
}

async function fetchCategoryArticles(categoryId, page, pageSize) {
  const category = GNEWS_CATEGORIES[categoryId];
  const query = CATEGORY_QUERIES[categoryId] || CATEGORY_QUERIES.world;
  const jobs = [
    // GDELT restored to latest and every category feed; it remains in search as well.
    gdelt.fetchNews({ query, lang: 'english', timespan: '72h', maxRecords: 100 })
  ];

  if (category && category.gnews) jobs.push(gnews.fetchNews({
    category: category.gnews, lang: 'en', page, max: Math.max(20, pageSize)
  }));

  if (categoryId === 'world') jobs.push(currents.fetchLatest({
    lang: 'en', page, pageSize: Math.max(40, pageSize)
  }));
  else if (category && category.currents) jobs.push(currents.fetchNews({
    category: category.currents, lang: 'en', page, pageSize: Math.max(30, pageSize)
  }));

  if (['world', 'business', 'technology', 'entertainment', 'sports', 'science'].includes(categoryId)) {
    jobs.push(rss.fetchByCategory(categoryId));
  }

  const results = await Promise.allSettled(jobs);
  const incoming = [];
  for (const result of results) {
    if (result.status === 'fulfilled' && Array.isArray(result.value)) {
      for (const article of result.value) incoming.push(Object.assign({}, article, {
        category: article.category || categoryId
      }));
    } else if (result.status === 'rejected') logProviderError(result.reason);
  }
  return incoming;
}

function mergeRollingCache(key, incoming, freshHours) {
  const now = Date.now();
  const oldValue = cache.get(key);
  const oldArticles = Array.isArray(oldValue) ? oldValue : [];
  const cutoff = now - Math.max(1, freshHours) * 3600000;
  const fresh = (incoming || []).filter(article => {
    const time = articleTimestamp(article);
    return time !== null && time >= cutoff && time <= now + 2 * 3600000;
  });
  const byUrl = new Map();

  for (const article of oldArticles.concat(fresh)) {
    if (!article || !article.title || !article.url || isBlockedDomain(article.url)) continue;
    const time = articleTimestamp(article);
    // Do not age-prune an article already stored in the rolling cache. The cutoff
    // above applies only to newly fetched items; existing stories remain available.
    if (time === null || time > now + 2 * 3600000) continue;
    const url = canonicalUrl(article.url);
    if (!url) continue;
    const candidate = Object.assign({}, article, { url });
    const previous = byUrl.get(url);
    if (!previous) {
      byUrl.set(url, candidate);
      continue;
    }
    const oldScore = qualityScore(previous.url) || previous.qualityScore || 0;
    const newScore = qualityScore(candidate.url) || candidate.qualityScore || 0;
    const preferred = newScore >= oldScore ? candidate : previous;
    const other = preferred === candidate ? previous : candidate;
    byUrl.set(url, Object.assign({}, other, preferred, {
      image: preferred.image || other.image || '',
      description: preferred.description || other.description || '',
      sourceUrl: preferred.sourceUrl || other.sourceUrl || '',
      publishedAt: articleTimestamp(preferred) >= articleTimestamp(other)
        ? preferred.publishedAt : other.publishedAt
    }));
  }

  let articles = filterInternationalQuality(Array.from(byUrl.values()), MIN_SOURCE_QUALITY);
  articles.sort((a, b) => (articleTimestamp(b) || 0) - (articleTimestamp(a) || 0)
    || qualityScore(b.url) - qualityScore(a.url));
  const seenTitles = new Set();
  articles = articles.filter(article => {
    const title = normalizeTitle(article.title);
    if (!title || seenTitles.has(title)) return false;
    seenTitles.add(title);
    return true;
  }).slice(0, MAX_CACHED_ARTICLES);

  // Rolling archive TTL is 30 days by default; this differs from the providers' short cache TTL.
  cache.set(key, articles, RETENTION_SECONDS);
  const previousUrls = new Set(oldArticles.map(article => canonicalUrl(article.url)).filter(Boolean));
  const added = articles.reduce((n, article) => n + (previousUrls.has(article.url) ? 0 : 1), 0);
  return { articles, added };
}

function keepWithinRetention(articles) {
  const now = Date.now();
  return filterInternationalQuality(Array.isArray(articles) ? articles : [], MIN_SOURCE_QUALITY)
    .filter(article => {
      const time = articleTimestamp(article);
      // Cached stories do not disappear merely because their publication date is old.
      return time !== null && time <= now + 2 * 3600000;
    }).sort((a, b) => (articleTimestamp(b) || 0) - (articleTimestamp(a) || 0));
}

function normalizeCategoryId(value) {
  const normalized = String(value || 'world').trim().toLowerCase().normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '').replace(/[\s-]+/g, '_').replace(/[^a-z0-9_]/g, '');
  return CATEGORY_ALIASES[normalized] || normalized;
}

function articleTimestamp(article) {
  if (!article || !article.publishedAt) return null;
  const time = new Date(article.publishedAt).getTime();
  return Number.isFinite(time) ? time : null;
}

function canonicalUrl(value) {
  try {
    const url = new URL(value);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return '';
    url.hash = '';
    ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'gclid', 'fbclid', 'mc_cid', 'mc_eid', 'ref', 'ref_src']
      .forEach(key => url.searchParams.delete(key));
    url.searchParams.sort();
    return url.toString();
  } catch {
    return '';
  }
}

function normalizeTitle(title) {
  return String(title || '').toLowerCase().normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, ' ').trim().slice(0, 240);
}

function buildResponse({ articles, page, pageSize, category, freshHours, q, newArticles, cacheCount, upstreamError }) {
  const start = (page - 1) * pageSize;
  const total = articles.length;
  return {
    status: 'ok', mode: 'international_only', category,
    query: q || undefined, freshness: freshHours + 'h', cacheRetentionHours: RETENTION_HOURS,
    page, pageSize, total, totalPages: Math.ceil(total / pageSize),
    newArticles: newArticles || 0, cachedArticles: cacheCount === undefined ? total : cacheCount,
    usingCacheAfterProviderError: Boolean(upstreamError),
    articles: articles.slice(start, start + pageSize)
  };
}

function clampInt(value, fallback, max) {
  const number = Number.parseInt(value, 10);
  if (!Number.isFinite(number)) return fallback;
  return Math.max(1, Math.min(max, number));
}

function logProviderError(error) {
  console.error('[News provider]', error && error.message ? error.message : error);
}

module.exports = router;
