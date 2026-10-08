const axios = require('axios');
const cache = require('../utils/cache');
const { filterInternationalQuality } = require('../config/newsPolicy');
const MIN_SOURCE_QUALITY = Number(process.env.MIN_SOURCE_QUALITY) || 80;

const GDELT_BASE = 'https://api.gdeltproject.org/api/v2/doc/doc';
const RATE_LIMIT_MS = 5500;
let lastRequest = 0;

async function waitRateLimit() {
  const now = Date.now();
  const elapsed = now - lastRequest;
  if (elapsed < RATE_LIMIT_MS && lastRequest > 0) {
    await new Promise(resolve => setTimeout(resolve, RATE_LIMIT_MS - elapsed));
  }
  lastRequest = Date.now();
}

async function fetchNews({ query = 'news', lang = '', timespan = '24h', maxRecords = 75 }) {
  const cacheKey = `gdelt_${query}_${lang}_${timespan}_${maxRecords}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  await waitRateLimit();

  let q = query;
  if (lang) q += ` sourcelang:${lang}`;

  try {
    const response = await axios.get(GDELT_BASE, {
      params: {
        query: q,
        mode: 'artlist',
        maxrecords: maxRecords,
        timespan,
        sort: 'DateDesc',
        format: 'json'
      },
      timeout: 15000,
      validateStatus: s => s >= 200 && s < 500
    });

    if (response.status >= 400) throw new Error(`GDELT HTTP ${response.status}`);
    const text = response.data;
    if (typeof text === 'string' && text.includes('Please limit requests')) {
      throw new Error('GDELT rate limit atingido');
    }

    const articles = (text.articles || []).map(a => ({
      id: a.url,
      title: a.title || '',
      url: a.url || '',
      image: a.socialimage || '',
      source: a.domain || '',
      sourceUrl: a.url || '',
      country: a.sourcecountry || '',
      language: a.language || '',
      publishedAt: formatGdeltDate(a.seendate),
      tone: a.tone ? parseFloat(a.tone) : 0,
      provider: 'gdelt'
    }));

    const quality = filterInternationalQuality(articles, MIN_SOURCE_QUALITY);
    cache.set(cacheKey, quality);
    return quality;
  } catch (err) {
    console.error('[GDELT]', err.message);
    return [];
  }
}

function formatGdeltDate(dateStr) {
  if (!dateStr) return null;
  const y = dateStr.slice(0, 4), m = dateStr.slice(4, 6), d = dateStr.slice(6, 8);
  const h = dateStr.slice(9, 11), min = dateStr.slice(11, 13);
  return `${y}-${m}-${d}T${h}:${min}:00Z`;
}

module.exports = { fetchNews };
