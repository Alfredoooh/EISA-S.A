const express = require('express');
const router = express.Router();
const { getCountryFromCoords } = require('../utils/geolocate');
const gdelt = require('../services/gdelt');
const currents = require('../services/currents');
const gnews = require('../services/gnews');
const { dedupeArticles, rankArticles, parseDate } = require('../utils/news');

// Endpoint mantido para conteúdo estritamente local. A home /news continua internacional.
router.get('/', async (req, res) => {
  const { lat, lon } = req.query;
  const latitude = Number(lat); const longitude = Number(lon);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
    return res.status(400).json({ status: 'error', error: 'lat e lon devem ser coordenadas válidas' });
  }

  try {
    const { code, info } = await getCountryFromCoords(latitude, longitude);
    const [gde, cur, gne] = await Promise.allSettled([
      gdelt.fetchNews({ query: info.gdeltCountry || info.name, timespan: '24h', maxRecords: 80, internationalOnly: false }),
      currents.fetchNews({ category: 'internacional', lang: info.lang, country: code, max: 30 }),
      gnews.fetchNews({ category: 'internacional', lang: info.lang, country: String(code || '').toLowerCase(), max: 20 })
    ]);
    let articles = [
      ...(gde.status === 'fulfilled' ? gde.value : []),
      ...(cur.status === 'fulfilled' ? cur.value : []),
      ...(gne.status === 'fulfilled' ? gne.value : [])
    ];
    articles = dedupeArticles(articles)
      .filter(a => { const d = parseDate(a.publishedAt); return d && d.getTime() <= Date.now() + 10 * 60 * 1000 && Date.now() - d.getTime() <= 72 * 3600000; })
      .map(a => ({ ...a, category: 'local' }));
    articles = rankArticles(articles, { category: 'local' }).slice(0, 40);

    res.json({
      status: 'ok',
      internationalOnly: false,
      location: { lat: latitude, lon: longitude, countryCode: code, countryName: info.name, language: info.lang },
      total: articles.length,
      generatedAt: new Date().toISOString(),
      articles
    });
  } catch (err) {
    res.status(500).json({ status: 'error', error: err.message });
  }
});

module.exports = router;
