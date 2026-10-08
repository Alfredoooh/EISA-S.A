const express = require('express');
const router = express.Router();
const { extractArticle } = require('../utils/scraper');
const cache = require('../utils/cache');
const { qualityScore, isBlockedDomain } = require('../config/newsPolicy');
const MIN_SOURCE_QUALITY = Number(process.env.MIN_SOURCE_QUALITY) || 80;

router.get('/', async (req, res) => {
  const { url } = req.query;
  if (!url) return res.status(400).json({ error: 'Parâmetro url é obrigatório' });

  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    return res.status(400).json({ error: 'URL inválida' });
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    return res.status(400).json({ error: 'Apenas URLs HTTP/HTTPS são aceites' });
  }
  if (isBlockedDomain(parsed.hostname)) {
    return res.status(403).json({ error: 'Esta fonte está bloqueada pela política editorial.' });
  }
  if (qualityScore(parsed.hostname) < MIN_SOURCE_QUALITY) {
    return res.status(403).json({ error: 'Esta fonte não pertence à lista de publishers aprovados.' });
  }

  const cacheKey = `article_${Buffer.from(parsed.toString()).toString('base64').slice(0, 64)}`;
  const cached = cache.get(cacheKey);
  if (cached) return res.json({ status: 'ok', cached: true, article: cached });

  try {
    const article = await extractArticle(parsed.toString());
    cache.set(cacheKey, article, 1800);
    res.json({ status: 'ok', cached: false, article });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

module.exports = router;
