const express = require('express');
const router = express.Router();
const gdelt = require('../services/gdelt');
const currents = require('../services/currents');
const gnews = require('../services/gnews');
const rss = require('../services/rss');

const CATEGORIES = [
  'general', 'technology', 'business',
  'sports', 'health', 'science',
  'entertainment', 'politics', 'local'
];

// GET /news/categories
router.get('/categories', (req, res) => {
  res.json({
    categories: CATEGORIES.map(c => ({
      id: c,
      label: categoryLabel(c)
    }))
  });
});

// GET /news/sources
router.get('/sources', (req, res) => {
  const sources = Object.entries(rss.RSS_SOURCES).flatMap(([region, feeds]) =>
    feeds.map(f => ({
      name: f.name,
      region,
      category: f.category,
      country: f.country,
      language: f.lang
    }))
  );
  res.json({ sources });
});

// GET /news?category=general&lang=pt&country=PT&page=1&q=pesquisa
router.get('/', async (req, res) => {
  const {
    category = 'general',
    lang = 'pt',
    country = '',
    page = 1,
    q = '',
    timespan = '24h'
  } = req.query;

  try {
    let articles = [];

    if (q) {
      // Modo pesquisa
      const [gdeltResults, gNewsResults] = await Promise.allSettled([
        gdelt.fetchNews({ query: q, lang, timespan }),
        gnews.searchNews({ query: q, lang })
      ]);

      if (gdeltResults.status === 'fulfilled') articles.push(...gdeltResults.value);
      if (gNewsResults.status === 'fulfilled') articles.push(...gNewsResults.value);

    } else {
      // Modo categoria
      const [currentsResults, gNewsResults, rssResults] = await Promise.allSettled([
        currents.fetchNews({ category, lang, country, page }),
        gnews.fetchNews({ category, lang, country: country.toLowerCase() }),
        rss.fetchByCategory(category)
      ]);

      if (currentsResults.status === 'fulfilled') articles.push(...currentsResults.value);
      if (gNewsResults.status === 'fulfilled') articles.push(...gNewsResults.value);
      if (rssResults.status === 'fulfilled') articles.push(...rssResults.value);
    }

    // Deduplica por URL
    const seen = new Set();
    articles = articles.filter(a => {
      if (!a.url || seen.has(a.url)) return false;
      seen.add(a.url);
      return true;
    });

    // Ordena por data
    articles.sort((a, b) => {
      const da = new Date(a.publishedAt || 0);
      const db = new Date(b.publishedAt || 0);
      return db - da;
    });

    // Paginação
    const pageNum = parseInt(page) || 1;
    const pageSize = 20;
    const start = (pageNum - 1) * pageSize;
    const paginated = articles.slice(start, start + pageSize);

    res.json({
      status: 'ok',
      total: articles.length,
      page: pageNum,
      pageSize,
      totalPages: Math.ceil(articles.length / pageSize),
      category,
      articles: paginated
    });

  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

function categoryLabel(c) {
  const labels = {
    general: 'Geral',
    technology: 'Tecnologia',
    business: 'Negócios',
    sports: 'Desporto',
    health: 'Saúde',
    science: 'Ciência',
    entertainment: 'Entretenimento',
    politics: 'Política',
    local: 'Local'
  };
  return labels[c] || c;
}

module.exports = router;
