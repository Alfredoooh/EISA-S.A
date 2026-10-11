const express = require('express');
const router = express.Router();
const { getCountryFromCoords } = require('../utils/geolocate');
const gdelt = require('../services/gdelt');
const currents = require('../services/currents');
const gnews = require('../services/gnews');
const { dedupeArticles, rankArticles, parseDate } = require('../utils/news');

// Kept as a separate opt-in endpoint. The main /news feed remains international.
router.get('/', async (req, res) => {
  const latitude = Number(req.query.lat); const longitude = Number(req.query.lon);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
    return res.status(400).json({ status: 'error', error: 'lat e lon devem ser coordenadas válidas' });
  }
  try {
    const { code, info } = await getCountryFromCoords(latitude, longitude);
    const [gdeltResult, currentsResult, gnewsResult] = await Promise.allSettled([
      gdelt.fetchNews({ query: info.gdeltCountry || info.name, timespan: '24h', maxRecords: 80, internationalOnly: false }),
      currents.fetchNews({ category: 'world', lang: info.lang, country: code, max: 30 }),
      gnews.fetchNews({ category: 'world', lang: info.lang, country: String(code || '').toLowerCase(), max: 20 })
    ]);
    let articles = [
      ...(gdeltResult.status === 'fulfilled' ? gdeltResult.value : []),
      ...(currentsResult.status === 'fulfilled' ? currentsResult.value : []),
      ...(gnewsResult.status === 'fulfilled' ? gnewsResult.value : [])
    ];
    articles = dedupeArticles(articles).filter(article => {
      const date = parseDate(article.publishedAt);
      return date && date.getTime() <= Date.now() + 10 * 60000 && Date.now() - date.getTime() <= 72 * 3600000;
    }).map(article => ({ ...article, category: 'local' }));
    articles = rankArticles(articles, { category: 'local' }).slice(0, 40);
    return res.json({
      status: 'ok', internationalOnly: false,
      location: { lat: latitude, lon: longitude, countryCode: code, countryName: info.name, language: info.lang },
      total: articles.length, generatedAt: new Date().toISOString(), articles
    });
  } catch (error) {
    return res.status(500).json({ status: 'error', error: error.message });
  }
});
module.exports = router;
