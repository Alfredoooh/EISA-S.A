const express = require('express');
const router = express.Router();
const { extractArticle } = require('../utils/scraper');
const cache = require('../utils/cache');

// GET /article?url=https://...
router.get('/', async (req, res) => {
  const { url } = req.query;

  if (!url) {
    return res.status(400).json({ error: 'Parâmetro url é obrigatório' });
  }

  try {
    new URL(url); // valida URL
  } catch {
    return res.status(400).json({ error: 'URL inválida' });
  }

  const cacheKey = `article_${Buffer.from(url).toString('base64').slice(0, 32)}`;
  const cached = cache.get(cacheKey);
  if (cached) {
    return res.json({ status: 'ok', cached: true, article: cached });
  }

  try {
    const article = await extractArticle(url);
    cache.set(cacheKey, article, 3600); // cache 1 hora
    res.json({ status: 'ok', cached: false, article });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
