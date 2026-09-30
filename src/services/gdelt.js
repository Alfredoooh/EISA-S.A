const axios = require('axios');
const cache = require('../utils/cache');

const GDELT_BASE = 'https://api.gdeltproject.org/api/v2/doc/doc';
const RATE_LIMIT_MS = 5500;
let lastRequest = 0;

async function waitRateLimit() {
  const now = Date.now();
  const elapsed = now - lastRequest;
  if (elapsed < RATE_LIMIT_MS && lastRequest > 0) {
    await new Promise(r => setTimeout(r, RATE_LIMIT_MS - elapsed));
  }
  lastRequest = Date.now();
}

async function fetchNews({ query = 'news', lang = '', country = '', timespan = '24h', maxRecords = 75 }) {
  const cacheKey = `gdelt_${query}_${lang}_${country}_${timespan}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  await waitRateLimit();

  let q = query;
  if (lang) q += ` sourcelang:${lang}`;
  if (country) q += ` sourcecountry:${country}`;

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
      timeout: 15000
    });

    const text = response.data;
    if (typeof text === 'string' && text.includes('Please limit requests')) {
      throw new Error('GDELT rate limit atingido');
    }

    const articles = (text.articles || []).map(a => ({
      id: Buffer.from(a.url || '').toString('base64').slice(0, 16),
      title: a.title || '',
      url: a.url || '',
      image: a.socialimage || '',
      source: (a.domain || '').replace(/^www\./, ''),
      country: a.sourcecountry || '',
      language: a.language || '',
      publishedAt: formatGdeltDate(a.seendate),
      tone: a.tone ? parseFloat(a.tone) : 0,
      provider: 'gdelt'
    }));

    cache.set(cacheKey, articles);
    return articles;

  } catch (err) {
    console.error('[GDELT]', err.message);
    return [];
  }
}

function formatGdeltDate(dateStr) {
  if (!dateStr) return null;
  const y = dateStr.slice(0, 4);
  const m = dateStr.slice(4, 6);
  const d = dateStr.slice(6, 8);
  const h = dateStr.slice(9, 11);
  const min = dateStr.slice(11, 13);
  return `${y}-${m}-${d}T${h}:${min}:00Z`;
}

module.exports = { fetchNews };
