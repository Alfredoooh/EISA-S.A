// src/services/aggregator.js
const crypto = require('crypto');
const guardian = require('./guardian');
const nytimes = require('./nytimes');
const gnews = require('./gnews');
const newsdata = require('./newsdata');
const cache = require('../utils/cache');
const { CATEGORIES, CATEGORY_LABELS, LEGACY_CATEGORY_ALIASES, CATEGORY_QUERIES } = require('../config');
const { finalizeArticles, extractDomain, isInternational, parseDate, dedupeArticles, rankArticles } = require('../utils/news');

const RETENTION_HOURS = Math.max(24, Number(process.env.NEWS_RETENTION_HOURS) || 168);
const FETCH_CACHE_SECONDS = 60;
const MAX_ARCHIVE = 800;

function normalizeCategory(value) {
  const key = String(value || 'world').trim().toLowerCase()
    .normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[\s-]+/g, '_').replace(/[^a-z0-9_]/g, '');
  const resolved = LEGACY_CATEGORY_ALIASES[key] || key;
  return CATEGORIES.includes(resolved) ? resolved : 'world';
}

// Fetch from all four sources in parallel; each returns EN-only articles.
async function fetchForCategory(category, pageSize) {
  const tasks = [
    guardian.fetchNews({ category, max: Math.min(50, pageSize * 2) }),
    nytimes.fetchTopStories({ category, max: Math.min(50, pageSize * 2) }),
    nytimes.fetchNewswire({ category, max: Math.min(50, pageSize * 2) }),
    gnews.fetchNews({ category, max: Math.min(50, pageSize * 2) }),
    newsdata.fetchNews({ category, max: Math.min(50, pageSize * 2) }),
  ];
  const results = await Promise.allSettled(tasks);
  const collected = [];
  for (const r of results) {
    if (r.status === 'fulfilled' && Array.isArray(r.value)) collected.push(...r.value);
    else if (r.status === 'rejected') console.warn('[Aggregator]', r.reason?.message || r.reason);
  }
  return collected;
}

async function categoryNews({ category = 'world', maxAgeHours = 24, page = 1, pageSize = 20 } = {}) {
  const cat = normalizeCategory(category);
  const freshness = Math.min(72, Math.max(1, Number(maxAgeHours) || 24));
  const safePage = Math.max(1, parseInt(page, 10) || 1);
  const safeSize = Math.min(50, Math.max(1, parseInt(pageSize, 10) || 20));
  
  const recentKey = `feed_recent_v3_${cat}_${freshness}`;
  const archiveKey = `feed_archive_v3_${cat}`;
  
  let recent = cache.get(recentKey);
  let cacheHit = Boolean(recent);
  if (!recent) {
    const fetched = await fetchForCategory(cat, safeSize);
    recent = finalizeArticles(fetched, { maxAgeHours: freshness, minimumFresh: 12, allowFallback: true });
    cache.set(recentKey, recent, FETCH_CACHE_SECONDS);
  }
  
  const archive = mergeArchive(archiveKey, recent, cat);
  const start = (safePage - 1) * safeSize;
  return {
    category: cat,
    label: CATEGORY_LABELS[cat] || cat,
    page: safePage,
    pageSize: safeSize,
    total: archive.articles.length,
    totalPages: Math.ceil(archive.articles.length / safeSize),
    generatedAt: new Date().toISOString(),
    language: 'en',
    cacheHit,
    newlyAdded: archive.added,
    articles: archive.articles.slice(start, start + safeSize).map(publicArticle),
  };
}

async function latestNews({ maxAgeHours = 12, page = 1, pageSize = 20 } = {}) {
  return categoryNews({ category: 'world', maxAgeHours, page, pageSize });
}

async function searchNews({ query, maxAgeHours = 24, page = 1, pageSize = 20 } = {}) {
  const q = String(query || '').trim();
  if (!q) throw new Error('query is required');
  const freshness = Math.min(72, Math.max(1, Number(maxAgeHours) || 24));
  const safePage = Math.max(1, parseInt(page, 10) || 1);
  const safeSize = Math.min(50, Math.max(1, parseInt(pageSize, 10) || 20));
  const hash = crypto.createHash('sha1').update(q.toLowerCase()).digest('hex').slice(0, 18);
  const recentKey = `search_recent_v3_${hash}_${freshness}`;
  const archiveKey = `search_archive_v3_${hash}`;
  
  let recent = cache.get(recentKey);
  const cacheHit = Boolean(recent);
  if (!recent) {
    const results = await Promise.allSettled([
      guardian.searchNews({ query: q, max: 40 }),
      nytimes.searchNews({ query: q, max: 40 }),
      gnews.searchNews({ query: q, max: 40 }),
      newsdata.searchNews({ query: q, max: 40 }),
    ]);
    const collected = [];
    for (const r of results) {
      if (r.status === 'fulfilled' && Array.isArray(r.value)) collected.push(...r.value);
      else if (r.status === 'rejected') console.warn('[Aggregator search]', r.reason?.message);
    }
    recent = finalizeArticles(collected, { maxAgeHours: freshness, minimumFresh: 8, allowFallback: true });
    cache.set(recentKey, recent, FETCH_CACHE_SECONDS);
  }
  
  const archive = mergeArchive(archiveKey, recent, null);
  const start = (safePage - 1) * safeSize;
  return {
    query,
    page: safePage,
    pageSize: safeSize,
    total: archive.articles.length,
    totalPages: Math.ceil(archive.articles.length / safeSize),
    generatedAt: new Date().toISOString(),
    language: 'en',
    cacheHit,
    newlyAdded: archive.added,
    articles: archive.articles.slice(start, start + safeSize).map(publicArticle),
  };
}

function mergeArchive(key, incoming, category) {
  const now = Date.now();
  const old = cache.get(key);
  const previous = Array.isArray(old) ? old : [];
  const minTime = now - RETENTION_HOURS * 3600000;
  const combined = previous.concat(Array.isArray(incoming) ? incoming : []).filter(a => {
    if (!a?.url || !a?.title || !isInternational(a)) return false;
    const d = parseDate(a.publishedAt);
    return d && d.getTime() >= minTime && d.getTime() <= now + 600000;
  });
  const unique = dedupeArticles(combined);
  const ranked = rankArticles(unique, { now }).slice(0, MAX_ARCHIVE);
  const prevSet = new Set(previous.map(a => a?.url).filter(Boolean));
  const added = ranked.reduce((n, a) => n + (prevSet.has(a.url) ? 0 : 1), 0);
  cache.set(key, ranked, RETENTION_HOURS * 3600);
  return { articles: ranked, added };
}

function publicArticle(article) {
  const d = parseDate(article.publishedAt);
  return {
    ...article,
    domain: extractDomain(article.url),
    ageHours: d ? Number(((Date.now() - d.getTime()) / 3600000).toFixed(2)) : null,
  };
}

module.exports = { normalizeCategory, categoryNews, latestNews, searchNews };