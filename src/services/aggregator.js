const crypto = require('crypto');
const gdelt = require('./gdelt');
const gnews = require('./gnews');
const currents = require('./currents');
const rss = require('./rss');
const searxng = require('./searxng');
const cache = require('../utils/cache');
const { CATEGORY_QUERIES, CATEGORIES, LEGACY_CATEGORY_ALIASES } = require('../config');
const { finalizeArticles, extractDomain, isInternational, parseDate, dedupeArticles, rankArticles } = require('../utils/news');

const RETENTION_HOURS = Math.max(24, Number(process.env.NEWS_RETENTION_HOURS) || 168);
const RETENTION_SECONDS = RETENTION_HOURS * 3600;
const FETCH_CACHE_SECONDS = 60;
const MAX_ARCHIVE_ARTICLES = 800;

function normalizeCategory(value) {
  const key = String(value || 'world').trim().toLowerCase().normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '').replace(/[\s-]+/g, '_').replace(/[^a-z0-9_]/g, '');
  const normalized = LEGACY_CATEGORY_ALIASES[key] || key;
  return CATEGORIES.includes(normalized) ? normalized : normalized;
}
function safeLanguage(lang) { return /^[a-z]{2}$/i.test(String(lang || '')) ? String(lang).toLowerCase() : 'en'; }
function boundedFreshness(value, fallback = 24) { return Math.min(72, Math.max(1, Number(value) || fallback)); }

async function fetchForCategory(category, lang, maxAgeHours, pageSize) {
  const query = CATEGORY_QUERIES[category] || CATEGORY_QUERIES.world;
  const gdeltQuery = buildGdeltTopicQuery(query);
  const tasks = [
    // Keep GDELT as a first-class source on the homepage and every category.
    gdelt.fetchNews({ query: gdeltQuery, timespan: `${maxAgeHours}h`, maxRecords: 100, internationalOnly: true }),
    gnews.fetchNews({ category, lang, max: Math.min(50, Math.max(20, pageSize * 2)) }),
    currents.fetchNews({ category, lang, max: Math.min(100, Math.max(30, pageSize * 2)) }),
    rss.fetchByCategory(category),
    searxng.searchNews({ query: `${query} latest news`, language: lang, category, timeRange: 'day', max: 40 })
  ];
  const results = await Promise.allSettled(tasks);
  const collected = [];
  for (const result of results) {
    if (result.status === 'fulfilled' && Array.isArray(result.value)) collected.push(...result.value);
    else if (result.status === 'rejected') console.warn('[Aggregator provider]', result.reason?.message || result.reason);
  }
  return collected;
}

async function categoryNews({ category = 'world', lang = 'en', maxAgeHours = 24, page = 1, pageSize = 20 } = {}) {
  const normalizedCategory = normalizeCategory(category);
  if (!CATEGORIES.includes(normalizedCategory)) throw new Error(`Categoria inválida: ${category}`);
  const safeLang = safeLanguage(lang);
  const freshness = boundedFreshness(maxAgeHours);
  const safePage = Math.max(1, Number.parseInt(page, 10) || 1);
  const safeSize = Math.min(50, Math.max(1, Number.parseInt(pageSize, 10) || 20));
  const recentKey = `feed_recent_v2_${normalizedCategory}_${safeLang}_${freshness}`;
  const archiveKey = `feed_archive_v2_${normalizedCategory}_${safeLang}`;
  let recent = cache.get(recentKey);
  let cacheHit = Boolean(recent);
  if (!recent) {
    const fetched = await fetchForCategory(normalizedCategory, safeLang, freshness, safeSize);
    recent = finalizeArticles(fetched, {
      category: normalizedCategory, maxAgeHours: freshness,
      minimumFresh: Math.min(12, safeSize), allowFallback: true
    });
    // Short cache only: refresh from providers frequently to keep adding new news.
    cache.set(recentKey, recent, FETCH_CACHE_SECONDS);
  }
  const archive = mergeArchive(archiveKey, recent, normalizedCategory, '', RETENTION_HOURS);
  const start = (safePage - 1) * safeSize;
  return {
    category: normalizedCategory,
    label: normalizedCategory,
    page: safePage,
    pageSize: safeSize,
    total: archive.articles.length,
    totalPages: Math.ceil(archive.articles.length / safeSize),
    generatedAt: new Date().toISOString(),
    cacheHit,
    cacheRetentionHours: RETENTION_HOURS,
    newlyAdded: archive.added,
    internationalOnly: true,
    articles: archive.articles.slice(start, start + safeSize).map(publicArticle)
  };
}

async function searchNews({ query, lang = 'en', maxAgeHours = 24, page = 1, pageSize = 20 } = {}) {
  const q = String(query || '').trim();
  if (!q) throw new Error('query é obrigatório');
  const safeLang = safeLanguage(lang);
  const freshness = boundedFreshness(maxAgeHours);
  const safePage = Math.max(1, Number.parseInt(page, 10) || 1);
  const safeSize = Math.min(50, Math.max(1, Number.parseInt(pageSize, 10) || 20));
  const hash = crypto.createHash('sha1').update(q.toLowerCase()).digest('hex').slice(0, 18);
  const recentKey = `search_recent_v2_${hash}_${safeLang}_${freshness}`;
  const archiveKey = `search_archive_v2_${hash}_${safeLang}`;
  let recent = cache.get(recentKey);
  const cacheHit = Boolean(recent);
  if (!recent) {
    const now = Date.now();
    const from = new Date(now - freshness * 3600000).toISOString();
    const to = new Date(now + 10 * 60000).toISOString();
    const results = await Promise.allSettled([
      gdelt.fetchNews({ query: q, timespan: `${freshness}h`, maxRecords: 120, internationalOnly: true }),
      gnews.searchNews({ query: q, lang: safeLang, max: 40, from, to }),
      currents.searchNews({ keywords: q, lang: safeLang, max: 40, from, to }),
      searxng.searchNews({ query: `${q} latest news`, language: safeLang, category: 'world', timeRange: 'day', max: 50 })
    ]);
    const collected = [];
    for (const result of results) {
      if (result.status === 'fulfilled' && Array.isArray(result.value)) collected.push(...result.value);
      else if (result.status === 'rejected') console.warn('[Aggregator search provider]', result.reason?.message || result.reason);
    }
    recent = finalizeArticles(collected, { query: q, maxAgeHours: freshness, minimumFresh: 8, allowFallback: true });
    cache.set(recentKey, recent, FETCH_CACHE_SECONDS);
  }
  const archive = mergeArchive(archiveKey, recent, null, q, RETENTION_HOURS);
  const start = (safePage - 1) * safeSize;
  return {
    query: q,
    page: safePage,
    pageSize: safeSize,
    total: archive.articles.length,
    totalPages: Math.ceil(archive.articles.length / safeSize),
    generatedAt: new Date().toISOString(),
    cacheRetentionHours: RETENTION_HOURS,
    newlyAdded: archive.added,
    cacheHit,
    internationalOnly: true,
    articles: archive.articles.slice(start, start + safeSize).map(publicArticle)
  };
}

async function latestNews({ lang = 'en', maxAgeHours = 12, page = 1, pageSize = 20 } = {}) {
  return categoryNews({ category: 'world', lang, maxAgeHours, page, pageSize });
}

function mergeArchive(key, incoming, category, query, retentionHours) {
  const now = Date.now();
  const old = cache.get(key);
  const previous = Array.isArray(old) ? old : [];
  const minimumTime = now - retentionHours * 3600000;
  const combined = previous.concat(Array.isArray(incoming) ? incoming : []).filter(article => {
    if (!article || !article.url || !article.title || !isInternational(article)) return false;
    const date = parseDate(article.publishedAt);
    return date && date.getTime() >= minimumTime && date.getTime() <= now + 10 * 60000;
  });
  const unique = dedupeArticles(combined);
  const ranked = rankArticles(unique, { category: category || undefined, query: query || undefined, now }).slice(0, MAX_ARCHIVE_ARTICLES);
  const previousUrls = new Set(previous.map(article => article?.url).filter(Boolean));
  const added = ranked.reduce((count, article) => count + (previousUrls.has(article.url) ? 0 : 1), 0);
  // Rolling archive is independent of the short provider/request caches.
  cache.set(key, ranked, retentionHours * 3600);
  return { articles: ranked, added };
}

function publicArticle(article) {
  const parsed = parseDate(article.publishedAt);
  return {
    ...article,
    domain: extractDomain(article.url),
    ageHours: parsed ? Number(((Date.now() - parsed.getTime()) / 3600000).toFixed(2)) : null
  };
}
function buildGdeltTopicQuery(query) {
  const tokens = String(query || '').split(/\s+/).map(token => token.trim()).filter(Boolean).slice(0, 16);
  if (!tokens.length) return 'world news';
  // OR-based topic expansion is more productive for GDELT than requiring every word.
  return `(${tokens.map(token => token.replace(/[()]/g, '')).join(' OR ')})`;
}

module.exports = { normalizeCategory, categoryNews, latestNews, searchNews, buildGdeltTopicQuery, RETENTION_HOURS };
