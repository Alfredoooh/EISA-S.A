const axios = require('axios');
const cache = require('../utils/cache');
const { EXCLUDED_COUNTRIES, EXCLUDED_DOMAINS } = require('../config');

const GDELT_BASE = 'https://api.gdeltproject.org/api/v2/doc/doc';
const RATE_LIMIT_MS = Number(process.env.GDELT_MIN_INTERVAL_MS || 5500);
let lastRequest = 0;

async function waitRateLimit() {
  const now = Date.now();
  const elapsed = now - lastRequest;
  if (elapsed < RATE_LIMIT_MS && lastRequest > 0) {
    await new Promise(resolve => setTimeout(resolve, RATE_LIMIT_MS - elapsed));
  }
  lastRequest = Date.now();
}

function sourceIsExcluded(article) {
  const country = String(article.sourcecountry || '').trim();
  const domain = String(article.domain || '').toLowerCase().replace(/^www\./, '');
  return EXCLUDED_COUNTRIES.has(country) || EXCLUDED_DOMAINS.has(domain) || domain.endsWith('.com.br') || domain.endsWith('.br');
}

function formatGdeltDate(dateStr) {
  if (!dateStr) return null;
  if (/^\d{14}$/.test(dateStr)) {
    const y = dateStr.slice(0, 4); const m = dateStr.slice(4, 6); const d = dateStr.slice(6, 8);
    const h = dateStr.slice(8, 10); const min = dateStr.slice(10, 12); const sec = dateStr.slice(12, 14);
    return `${y}-${m}-${d}T${h}:${min}:${sec}Z`;
  }
  const y = dateStr.slice(0, 4); const m = dateStr.slice(4, 6); const d = dateStr.slice(6, 8);
  const h = dateStr.slice(9, 11); const min = dateStr.slice(11, 13);
  return `${y}-${m}-${d}T${h}:${min}:00Z`;
}

async function fetchNews({ query = 'news', timespan = '24h', maxRecords = 100, internationalOnly = true } = {}) {
  const safeQuery = String(query || 'news').slice(0, 180).trim();
  const cacheKey = `gdelt_v2_${safeQuery}_${timespan}_${internationalOnly}_${maxRecords}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  await waitRateLimit();

  try {
    const response = await axios.get(GDELT_BASE, {
      params: {
        query: safeQuery,
        mode: 'artlist',
        maxrecords: Math.min(Number(maxRecords) || 100, 250),
        timespan,
        sort: 'DateDesc',
        format: 'json'
      },
      timeout: 18000,
      headers: { 'User-Agent': 'NewsAPI/2.0 (+https://render.com)' }
    });

    const text = response.data;
    if (typeof text === 'string' && text.toLowerCase().includes('please limit requests')) {
      throw new Error('GDELT rate limit atingido');
    }

    const articles = (text.articles || [])
      .filter(a => a.url && a.title && (!internationalOnly || !sourceIsExcluded(a)))
      .map(a => ({
        id: Buffer.from(a.url).toString('base64').slice(0, 22),
        title: a.title.trim(),
        description: '',
        url: a.url,
        image: a.socialimage || '',
        source: String(a.domain || '').replace(/^www\./, ''),
        sourceCountry: a.sourcecountry || '',
        country: a.sourcecountry || '',
        language: a.language || '',
        publishedAt: formatGdeltDate(a.seendate),
        tone: Number.isFinite(Number(a.tone)) ? Number(a.tone) : 0,
        category: 'internacional',
        provider: 'gdelt'
      }));

    cache.set(cacheKey, articles, 90);
    return articles;
  } catch (err) {
    console.error('[GDELT]', err.message);
    return [];
  }
}

module.exports = { fetchNews, formatGdeltDate };
