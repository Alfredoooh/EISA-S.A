const express = require('express');
const router = express.Router();
const aggregator = require('../services/aggregator');
const { CATEGORIES, CATEGORY_LABELS, CATEGORY_QUERIES } = require('../config');
const rss = require('../services/rss');
const cache = require('../utils/cache');

router.get('/categories', (req, res) => {
  res.json({
    status: 'ok',
    total: CATEGORIES.length,
    internationalOnly: true,
    categories: CATEGORIES.map(id => ({ id, label: CATEGORY_LABELS[id], query: CATEGORY_QUERIES[id] }))
  });
});

router.get('/sources', (req, res) => {
  res.json({
    status: 'ok',
    internationalOnly: true,
    providers: [
      { id: 'gdelt', type: 'global-aggregator', enabled: true },
      { id: 'gnews', type: 'news-api', enabled: Boolean(process.env.GNEWS_API_KEY) },
      { id: 'currents', type: 'news-api', enabled: Boolean(process.env.CURRENTS_API_KEY) },
      { id: 'rss', type: 'direct-rss', enabled: true, count: rss.INTERNATIONAL_RSS_SOURCES.length },
      { id: 'searxng', type: 'metasearch', enabled: true }
    ],
    rss: rss.INTERNATIONAL_RSS_SOURCES.map(source => ({
      name: source.name,
      category: source.category,
      country: source.country,
      language: source.lang,
      url: source.url
    }))
  });
});

router.get('/latest', async (req, res) => {
  const lang = req.query.lang || 'en';
  const maxAgeHours = parseFreshness(req.query.maxAgeHours, 12);
  const page = req.query.page || 1;
  const pageSize = req.query.pageSize || 20;
  try {
    const data = await aggregator.latestNews({ lang, maxAgeHours, page, pageSize });
    res.json(data);
  } catch (err) {
    res.status(500).json({ status: 'error', error: err.message });
  }
});

router.get('/search', async (req, res) => {
  const query = String(req.query.q || '').trim();
  if (!query) return res.status(400).json({ status: 'error', error: 'Parâmetro q é obrigatório' });
  const lang = req.query.lang || 'en';
  const maxAgeHours = parseFreshness(req.query.maxAgeHours, 24);
  const page = req.query.page || 1;
  const pageSize = req.query.pageSize || 20;
  try {
    res.json(await aggregator.searchNews({ query, lang, maxAgeHours, page, pageSize }));
  } catch (err) {
    res.status(500).json({ status: 'error', error: err.message });
  }
});

// GET /news?category=tecnologia&lang=en&page=1&pageSize=20&maxAgeHours=24
router.get('/', async (req, res) => {
  const category = aggregator.normalizeCategory(req.query.category || 'internacional');
  const lang = req.query.lang || 'en';
  const page = req.query.page || 1;
  const pageSize = req.query.pageSize || 20;
  const maxAgeHours = parseFreshness(req.query.maxAgeHours, 24);

  try {
    const data = await aggregator.categoryNews({ category, lang, maxAgeHours, page, pageSize });
    res.json({
      status: 'ok',
      ...data,
      categoryLabel: CATEGORY_LABELS[data.category] || data.category,
      dataFreshness: `até ${maxAgeHours}h de idade, com tolerância controlada até 72h somente quando faltarem artigos recentes`
    });
  } catch (err) {
    res.status(500).json({ status: 'error', error: err.message });
  }
});

router.post('/cache/flush', (req, res) => {
  if (process.env.ALLOW_CACHE_FLUSH !== 'true') {
    return res.status(403).json({ status: 'error', error: 'Limpeza de cache desativada' });
  }
  cache.flush();
  res.json({ status: 'ok', cache: 'flushed', timestamp: new Date().toISOString() });
});

function parseFreshness(value, fallback) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(72, Math.max(1, parsed));
}

module.exports = router;
