const express = require('express');
const router = express.Router();
const aggregator = require('../services/aggregator');
const { CATEGORIES, CATEGORY_LABELS, CATEGORY_QUERIES, INTERNATIONAL_RSS_SOURCES, QUALITY_SOURCES } = require('../config');
const { CURRENTS_SUPPORTED } = require('../services/currents');
const cache = require('../utils/cache');

router.get('/categories', (req, res) => {
  res.json({
    status: 'ok', total: CATEGORIES.length, internationalOnly: true,
    categories: CATEGORIES.map(id => ({
      id, label: CATEGORY_LABELS[id], query: CATEGORY_QUERIES[id],
      providers: providerList(id)
    }))
  });
});

router.get('/sources', (req, res) => {
  res.json({
    status: 'ok', internationalOnly: true,
    blockedSources: ['BBC', 'Brazilian domains'],
    providers: [
      { id: 'gdelt', type: 'global-aggregator', enabled: true },
      { id: 'gnews', type: 'news-api', enabled: Boolean(process.env.GNEWS_API_KEY) },
      { id: 'currents', type: 'news-api-v2', enabled: Boolean(process.env.CURRENTS_API_KEY) },
      { id: 'rss', type: 'direct-rss', enabled: true, count: INTERNATIONAL_RSS_SOURCES.length },
      { id: 'searxng', type: 'metasearch', enabled: true }
    ],
    qualityDomainsForRanking: QUALITY_SOURCES,
    rss: INTERNATIONAL_RSS_SOURCES.map(source => ({ name: source.name, category: source.category, country: source.country, language: source.lang, url: source.url }))
  });
});

router.get('/latest', async (req, res) => {
  try {
    const data = await aggregator.latestNews({
      lang: req.query.lang || 'en',
      maxAgeHours: parseFreshness(req.query.maxAgeHours, 12),
      page: req.query.page || 1,
      pageSize: req.query.pageSize || req.query.max || 20
    });
    res.json({ status: 'ok', ...data, categoryLabel: 'Internacional' });
  } catch (error) {
    res.status(500).json({ status: 'error', error: error.message });
  }
});

router.get('/search', async (req, res) => {
  const query = String(req.query.q || '').trim();
  if (!query) return res.status(400).json({ status: 'error', error: 'Parâmetro q é obrigatório' });
  if (query.length > 300) return res.status(400).json({ status: 'error', error: 'A pesquisa é demasiado longa' });
  try {
    const data = await aggregator.searchNews({
      query,
      lang: req.query.lang || 'en',
      maxAgeHours: parseFreshness(req.query.maxAgeHours, 24),
      page: req.query.page || 1,
      pageSize: req.query.pageSize || req.query.max || 20
    });
    res.json({ status: 'ok', ...data });
  } catch (error) {
    res.status(500).json({ status: 'error', error: error.message });
  }
});

router.get('/', async (req, res) => {
  const requested = String(req.query.category || 'world');
  const category = aggregator.normalizeCategory(requested);
  if (!CATEGORIES.includes(category)) {
    return res.status(400).json({
      status: 'error', error: 'Categoria inválida',
      availableCategories: CATEGORIES.map(id => ({ id, label: CATEGORY_LABELS[id] })),
      message: 'Categorias antigas continuam a ser aceites por alias quando correspondem a uma categoria suportada.'
    });
  }
  try {
    const data = await aggregator.categoryNews({
      category,
      lang: req.query.lang || 'en',
      maxAgeHours: parseFreshness(req.query.maxAgeHours, 24),
      page: req.query.page || 1,
      pageSize: req.query.pageSize || req.query.max || 20
    });
    res.json({ status: 'ok', ...data, categoryLabel: CATEGORY_LABELS[category] || category });
  } catch (error) {
    res.status(500).json({ status: 'error', error: error.message });
  }
});

router.post('/cache/flush', (req, res) => {
  if (process.env.ALLOW_CACHE_FLUSH !== 'true') return res.status(403).json({ status: 'error', error: 'Limpeza de cache desativada' });
  cache.flush();
  res.json({ status: 'ok', cache: 'flushed', timestamp: new Date().toISOString() });
});

function parseFreshness(value, fallback) {
  const n = Number(value);
  return Number.isFinite(n) ? Math.min(72, Math.max(1, n)) : fallback;
}
function providerList(id) {
  const list = ['gdelt', 'rss', 'searxng'];
  if (process.env.GNEWS_API_KEY) list.push('gnews');
  if (process.env.CURRENTS_API_KEY && CURRENTS_SUPPORTED.has(id)) list.push('currents');
  return list;
}

module.exports = router;
