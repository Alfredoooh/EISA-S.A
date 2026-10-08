const express = require('express');
const router = express.Router();
const { extractArticle } = require('../utils/scraper');
const cache = require('../utils/cache');

router.get('/', async (req, res) => {
  const { url } = req.query;
  if (!url) return res.status(400).json({ status: 'error', error: 'Parâmetro url é obrigatório' });

  let parsed;
  try {
    parsed = new URL(url);
    if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('protocolo inválido');
  } catch {
    return res.status(400).json({ status: 'error', error: 'URL HTTP/HTTPS inválida' });
  }

  const cacheKey = `article_v3_${Buffer.from(parsed.toString()).toString('base64url').slice(0, 48)}`;
  const cached = cache.get(cacheKey);
  if (cached) return res.json({ status: 'ok', cached: true, article: cached });

  try {
    const article = await extractArticle(parsed.toString());
    cache.set(cacheKey, article, Number(process.env.ARTICLE_CACHE_TTL || 1800));
    res.json({ status: 'ok', cached: false, article });
  } catch (err) {
    console.error('[Article]', err.message);
    res.status(422).json({ status: 'error', error: err.message });
  }
});

module.exports = router;
