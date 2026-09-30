const express = require('express');
const router = express.Router();
const { getCountryFromCoords } = require('../utils/geolocate');
const gdelt = require('../services/gdelt');
const currents = require('../services/currents');
const rss = require('../services/rss');

// GET /local?lat=-8.8368&lon=13.2343
router.get('/', async (req, res) => {
  const { lat, lon } = req.query;

  if (!lat || !lon) {
    return res.status(400).json({ error: 'Parâmetros lat e lon são obrigatórios' });
  }

  const latitude = parseFloat(lat);
  const longitude = parseFloat(lon);

  if (isNaN(latitude) || isNaN(longitude)) {
    return res.status(400).json({ error: 'lat e lon devem ser números' });
  }

  try {
    const { code, info } = await getCountryFromCoords(latitude, longitude);

    const [gdeltResults, currentsResults, rssResults] = await Promise.allSettled([
      gdelt.fetchNews({
        query: info.gdeltCountry,
        lang: info.lang,
        timespan: '24h'
      }),
      currents.fetchNews({
        category: 'general',
        lang: info.lang,
        country: code
      }),
      rss.fetchByCountry(code)
    ]);

    let articles = [];
    if (gdeltResults.status === 'fulfilled') articles.push(...gdeltResults.value);
    if (currentsResults.status === 'fulfilled') articles.push(...currentsResults.value);
    if (rssResults.status === 'fulfilled') articles.push(...rssResults.value);

    // Deduplica
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

    res.json({
      status: 'ok',
      location: {
        lat: latitude,
        lon: longitude,
        countryCode: code,
        countryName: info.name,
        language: info.lang
      },
      total: articles.length,
      articles: articles.slice(0, 40)
    });

  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
