const express = require('express');
const router = express.Router();
const currents = require('../services/currents');
const gnews = require('../services/gnews');
const rss = require('../services/rss');
const { filterInternationalQuality } = require('../config/newsPolicy');

// Kept for client compatibility. This server is now international-only,
// so this endpoint no longer returns country/local news.
router.get('/', async (req, res) => {
  try {
    const [currentsResults, gnewsResults, rssResults] = await Promise.allSettled([
      currents.fetchLatest({ lang: 'en', page: 1, pageSize: 50 }),
      gnews.fetchNews({ category: 'world', lang: 'en', page: 1, max: 25 }),
      rss.fetchByCategory('world')
    ]);
    let articles = [];
    if (currentsResults.status === 'fulfilled') articles.push(...currentsResults.value);
    if (gnewsResults.status === 'fulfilled') articles.push(...gnewsResults.value);
    if (rssResults.status === 'fulfilled') articles.push(...rssResults.value);
    articles = filterInternationalQuality(articles, Number(process.env.MIN_SOURCE_QUALITY) || 80)
      .filter(a => a.publishedAt && ((Date.now() - new Date(a.publishedAt).getTime()) / 3600000) <= 48)
      .slice(0, 40);
    res.json({
      status: 'ok',
      mode: 'international_only',
      legacyEndpoint: true,
      message: 'A API deixou de devolver notícias locais/country-specific. Este endpoint devolve notícias internacionais para compatibilidade.',
      total: articles.length,
      articles
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
