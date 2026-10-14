const express = require('express');
const router = express.Router();
const { extractArticle } = require('../utils/scraper');
const cache = require('../utils/cache');
const { excludedDomain } = require('../utils/news');

router.get('/', async (req, res) => {
  const target = String(req.query.url || '').trim();
  if (!target) return res.status(400).json({ status: 'error', error: 'Parâmetro url é obrigatório' });
  let parsed;
  try {
    parsed = new URL(target);
    if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('protocolo inválido');
  } catch {
    return res.status(400).json({ status: 'error', error: 'URL HTTP/HTTPS inválida' });
  }
  if (excludedDomain(parsed.hostname)) return res.status(403).json({ status: 'error', error: 'Fonte bloqueada pela política internacional.' });

  const key = `article_v2_${Buffer.from(parsed.toString()).toString('base64url').slice(0, 48)}`;
  const cached = cache.get(key);
  if (cached) return res.json({ status: 'ok', cached: true, article: cached });
  try {
    const article = await extractArticle(parsed.toString());
    cache.set(key, article, Number(process.env.ARTICLE_CACHE_TTL) || 1800);
    return res.json({ status: 'ok', cached: false, article });
  } catch (error) {
    console.error('[Article]', error.message);
    return res.status(422).json({ status: 'error', error: error.message });
  }
});
module.exports = router;
