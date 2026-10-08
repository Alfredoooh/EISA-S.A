const express = require('express');
const router = express.Router();
const gdelt = require('../services/gdelt');
const currents = require('../services/currents');
const gnews = require('../services/gnews');
const rss = require('../services/rss');
const {
  CATEGORY_LIST,
  GNEWS_CATEGORIES,
  filterInternationalQuality,
  normalizeDomain,
  isBlockedDomain,
  qualityScore
} = require('../config/newsPolicy');

const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 50;
const DEFAULT_FRESH_HOURS = Number(process.env.FRESHNESS_HOURS) || 48;
const MIN_SOURCE_QUALITY = Number(process.env.MIN_SOURCE_QUALITY) || 80;

router.get('/categories', (req, res) => {
  res.json({
    status: 'ok',
    mode: 'international_only',
    categories: CATEGORY_LIST.map(c => ({
      id: c.id,
      label: c.label,
      providers: c.providers,
      providerCategories: {
        gnews: c.gnews || null,
        currents: c.currents || null
      }
    }))
  });
});

router.get('/sources', (req, res) => {
  const sources = Object.entries(require('../config/newsPolicy').QUALITY_SOURCES)
    .sort((a, b) => b[1] - a[1])
    .map(([domain, score]) => ({ domain, qualityScore: score }));
  res.json({
    status: 'ok',
    mode: 'international_only',
    blockedDomains: ['bbc.com', 'bbc.co.uk', 'bbci.co.uk'],
    sources
  });
});

router.get('/latest', async (req, res) => {
  const page = clampInt(req.query.page, 1, 180);
  const pageSize = clampInt(req.query.pageSize || req.query.max, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
  const freshHours = clampInt(req.query.maxAgeHours, DEFAULT_FRESH_HOURS, 168);

  try {
    const [currentsResults, gnewsResults, rssResults] = await Promise.allSettled([
      currents.fetchLatest({ lang: 'en', page, pageSize: Math.max(30, pageSize) }),
      gnews.fetchNews({ category: 'world', lang: 'en', page, max: pageSize }),
      rss.fetchByCategory('world')
    ]);

    let articles = [];
    if (currentsResults.status === 'fulfilled') articles.push(...currentsResults.value);
    if (gnewsResults.status === 'fulfilled') articles.push(...gnewsResults.value);
    if (rssResults.status === 'fulfilled') articles.push(...rssResults.value);

    articles = finalize(articles, freshHours);
    res.json(buildResponse({ articles, page, pageSize, category: 'world', freshHours }));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

router.get('/search', async (req, res) => {
  const q = String(req.query.q || '').trim();
  const page = clampInt(req.query.page, 1, 180);
  const pageSize = clampInt(req.query.pageSize || req.query.max, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);

  if (!q) return res.status(400).json({ error: 'Parâmetro q é obrigatório' });
  if (q.length > 300) return res.status(400).json({ error: 'q é demasiado longo' });

  try {
    const [gdeltResults, gNewsResults, currentsResults] = await Promise.allSettled([
      gdelt.fetchNews({ query: q, lang: 'english', timespan: '72h', maxRecords: 75 }),
      gnews.searchNews({ query: q, lang: 'en', page, max: pageSize }),
      currents.searchNews({ keywords: q, lang: 'en', page, pageSize: Math.max(30, pageSize) })
    ]);

    let articles = [];
    if (gdeltResults.status === 'fulfilled') articles.push(...gdeltResults.value);
    if (gNewsResults.status === 'fulfilled') articles.push(...gNewsResults.value);
    if (currentsResults.status === 'fulfilled') articles.push(...currentsResults.value);

    articles = finalize(articles, 72);
    res.json(buildResponse({ articles, page, pageSize, category: null, freshHours: 72, q }));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Compatibility route: remains global/international rather than local or country-specific.
router.get('/', async (req, res) => {
  const categoryId = String(req.query.category || 'world');
  const page = clampInt(req.query.page, 1, 180);
  const pageSize = clampInt(req.query.pageSize || req.query.max, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
  const freshHours = clampInt(req.query.maxAgeHours, DEFAULT_FRESH_HOURS, 168);
  const q = String(req.query.q || '').trim();

  if (q) {
    req.query.q = q;
    return searchHandler(req, res);
  }

  const category = GNEWS_CATEGORIES[categoryId];
  if (!category) {
    return res.status(400).json({
      error: 'Categoria inválida',
      availableCategories: CATEGORY_LIST.map(c => c.id)
    });
  }

  try {
    const jobs = [];

    if (category.gnews) {
      jobs.push(gnews.fetchNews({ category: category.gnews, lang: 'en', page, max: Math.max(10, pageSize) }));
    }
    if (category.currents) {
      jobs.push(currents.fetchNews({ category: category.currents, lang: 'en', page, pageSize: Math.max(30, pageSize) }));
    }
    // RSS is only attached to the matching canonical feed groups.
    if (['world', 'business', 'technology', 'entertainment', 'sports', 'science'].includes(categoryId)) {
      jobs.push(rss.fetchByCategory(categoryId));
    }

    const results = await Promise.allSettled(jobs);
    let articles = [];
    for (const result of results) if (result.status === 'fulfilled') articles.push(...result.value);

    articles = finalize(articles, freshHours);
    res.json(buildResponse({ articles, page, pageSize, category: categoryId, freshHours }));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

async function searchHandler(req, res) {
  const q = String(req.query.q || '').trim();
  const page = clampInt(req.query.page, 1, 180);
  const pageSize = clampInt(req.query.pageSize || req.query.max, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
  if (!q) return res.status(400).json({ error: 'Parâmetro q é obrigatório' });

  const [gdeltResults, gNewsResults, currentsResults] = await Promise.allSettled([
    gdelt.fetchNews({ query: q, lang: 'english', timespan: '72h', maxRecords: 75 }),
    gnews.searchNews({ query: q, lang: 'en', page, max: pageSize }),
    currents.searchNews({ keywords: q, lang: 'en', page, pageSize: Math.max(30, pageSize) })
  ]);

  let articles = [];
  if (gdeltResults.status === 'fulfilled') articles.push(...gdeltResults.value);
  if (gNewsResults.status === 'fulfilled') articles.push(...gNewsResults.value);
  if (currentsResults.status === 'fulfilled') articles.push(...currentsResults.value);
  articles = finalize(articles, 72);
  return res.json(buildResponse({ articles, page, pageSize, category: null, freshHours: 72, q }));
}

function finalize(articles, freshHours) {
  const now = Date.now();
  let quality = filterInternationalQuality(articles, MIN_SOURCE_QUALITY).filter(a => {
    const t = new Date(a.publishedAt || 0).getTime();
    if (!Number.isFinite(t)) return false;
    const ageHours = (now - t) / 3600000;
    return ageHours >= -2 && ageHours <= freshHours;
  });

  const seenUrls = new Set();
  const seenTitles = new Set();
  quality = quality.filter(a => {
    const u = canonicalUrl(a.url);
    const t = normalizeTitle(a.title);
    if (!u || seenUrls.has(u) || seenTitles.has(t)) return false;
    seenUrls.add(u);
    seenTitles.add(t);
    a.url = u;
    return true;
  });

  quality.sort((a, b) => {
    const ta = new Date(a.publishedAt).getTime();
    const tb = new Date(b.publishedAt).getTime();
    const qa = qualityScore(a.url) || a.qualityScore || 0;
    const qb = qualityScore(b.url) || b.qualityScore || 0;
    const da = (now - ta) / 3600000;
    const db = (now - tb) / 3600000;
    const scoreA = (qa * 0.18) + Math.max(0, 72 - da);
    const scoreB = (qb * 0.18) + Math.max(0, 72 - db);
    return scoreB - scoreA || tb - ta;
  });

  return diversityCap(quality, 5);
}

function diversityCap(articles, maxPerSource) {
  const counts = new Map();
  const firstPass = [];
  const overflow = [];
  for (const article of articles) {
    const domain = normalizeDomain(article.url);
    const count = counts.get(domain) || 0;
    if (count < maxPerSource) {
      firstPass.push(article);
      counts.set(domain, count + 1);
    } else {
      overflow.push(article);
    }
  }
  return firstPass.concat(overflow);
}

function canonicalUrl(url) {
  try {
    const u = new URL(url);
    u.hash = '';
    ['utm_source','utm_medium','utm_campaign','utm_term','utm_content','gclid','fbclid'].forEach(k => u.searchParams.delete(k));
    return u.toString();
  } catch {
    return '';
  }
}

function normalizeTitle(title) {
  return String(title || '').toLowerCase().normalize('NFKD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, ' ').trim().slice(0, 240);
}

function buildResponse({ articles, page, pageSize, category, freshHours, q }) {
  const start = (page - 1) * pageSize;
  const paginated = articles.slice(start, start + pageSize);
  return {
    status: 'ok',
    mode: 'international_only',
    category,
    query: q || undefined,
    freshness: `${freshHours}h`,
    page,
    pageSize,
    total: articles.length,
    totalPages: Math.ceil(articles.length / pageSize),
    articles: paginated
  };
}

function clampInt(value, fallback, max) {
  const n = Number.parseInt(value, 10);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(1, Math.min(max, n));
}

module.exports = router;
