const axios = require('axios');
const cache = require('../utils/cache');
const { EXCLUDED_COUNTRIES, EXCLUDED_DOMAINS } = require('../config');

const GDELT_BASE = 'https://api.gdeltproject.org/api/v2/doc/doc';
const RATE_LIMIT_MS = Number(process.env.GDELT_MIN_INTERVAL_MS || 5500);
let lastRequest = 0;

async function waitRateLimit() {
  const elapsed = Date.now() - lastRequest;
  if (lastRequest > 0 && elapsed < RATE_LIMIT_MS) {
    await new Promise(resolve => setTimeout(resolve, RATE_LIMIT_MS - elapsed));
  }
  lastRequest = Date.now();
}

function domainExcluded(value) {
  let domain = String(value || '').toLowerCase().replace(/^https?:\/\//, '').split('/')[0].replace(/^www\./, '');
  if (!domain) return false;
  for (const blocked of EXCLUDED_DOMAINS) {
    if (domain === blocked || domain.endsWith('.' + blocked)) return true;
  }
  return domain.endsWith('.br') || domain.endsWith('.com.br');
}

function sourceIsExcluded(article) {
  const country = String(article.sourcecountry || '').trim().toUpperCase();
  const domain = String(article.domain || article.url || '').toLowerCase();
  return EXCLUDED_COUNTRIES.has(country) || domainExcluded(domain);
}
function formatGdeltDate(value) {
  if (!value) return null;
  if (/^\d{14}$/.test(value)) {
    return `${value.slice(0,4)}-${value.slice(4,6)}-${value.slice(6,8)}T${value.slice(8,10)}:${value.slice(10,12)}:${value.slice(12,14)}Z`;
  }
  if (/^\d{8}T\d{6}Z?$/.test(value)) {
    return `${value.slice(0,4)}-${value.slice(4,6)}-${value.slice(6,8)}T${value.slice(9,11)}:${value.slice(11,13)}:${value.slice(13,15)}Z`;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

async function fetchNews({ query = 'world news', timespan = '24h', maxRecords = 100, internationalOnly = true } = {}) {
  const safeQuery = String(query || 'world news').slice(0, 240).trim();
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
      headers: { 'User-Agent': 'InternationalNewsAPI/2.1 (+https://render.com)' }
    });
    const data = response.data;
    if (typeof data === 'string' && data.toLowerCase().includes('please limit requests')) {
      throw new Error('GDELT rate limit reached');
    }
    const articles = (data.articles || [])
      .filter(a => a.url && a.title && (!internationalOnly || !sourceIsExcluded(a)))
      .map(a => ({
        id: Buffer.from(a.url).toString('base64').slice(0, 22),
        title: String(a.title || '').trim(),
        description: '',
        url: a.url,
        image: a.socialimage || '',
        source: String(a.domain || '').replace(/^www\./, ''),
        sourceUrl: a.url,
        sourceCountry: a.sourcecountry || '',
        country: a.sourcecountry || '',
        language: a.language || '',
        publishedAt: formatGdeltDate(a.seendate),
        tone: Number.isFinite(Number(a.tone)) ? Number(a.tone) : 0,
        category: 'world',
        provider: 'gdelt'
      }));
    cache.set(cacheKey, articles, 90);
    return articles;
  } catch (error) {
    console.error('[GDELT]', error.message);
    return [];
  }
}

module.exports = { fetchNews, formatGdeltDate };
