const axios = require('axios');
const cache = require('../utils/cache');

const BASE_URL = 'https://api.currentsapi.services/v1';
const CATEGORY_MAP = {
  internacional: 'general', economia: 'business', politica: 'politics', desporto: 'sports',
  futebol: 'sports', tecnologia: 'technology', ciencia: 'science', saude: 'health',
  entretenimento: 'entertainment'
};

function extractDomain(url) {
  try { return new URL(url).hostname.replace(/^www\./, ''); } catch { return ''; }
}

function normalize(a, category, lang) {
  return {
    id: Buffer.from(a.url || '').toString('base64').slice(0, 22),
    title: a.title || '',
    description: a.description || '',
    url: a.url || '',
    image: a.image || '',
    source: a.author || extractDomain(a.url),
    sourceCountry: a.country || '',
    country: a.country || '',
    language: a.language || lang || '',
    publishedAt: a.published || a.publishedAt || null,
    category: a.category?.[0] || category,
    provider: 'currents'
  };
}

async function fetchNews({ category = 'internacional', lang = 'en', country = '', max = 30 } = {}) {
  if (!process.env.CURRENTS_API_KEY) return [];
  const cacheKey = `currents_v2_${category}_${lang}_${country}_${max}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;
  try {
    const params = {
      apiKey: process.env.CURRENTS_API_KEY,
      language: lang || 'en',
      page_number: 1
    };
    const mapped = CATEGORY_MAP[category];
    if (mapped && mapped !== 'general') params.category = mapped;
    if (country) params.country = String(country).toUpperCase();
    const response = await axios.get(`${BASE_URL}/search`, { params, timeout: 12000 });
    const articles = (response.data?.news || []).filter(a => a?.title && a?.url).slice(0, max).map(a => normalize(a, category, lang));
    cache.set(cacheKey, articles, 90);
    return articles;
  } catch (err) {
    console.error('[Currents]', err.message);
    return [];
  }
}

async function fetchLatest({ lang = 'en', max = 30 } = {}) {
  if (!process.env.CURRENTS_API_KEY) return [];
  const cacheKey = `currents_v2_latest_${lang}_${max}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;
  try {
    const response = await axios.get(`${BASE_URL}/latest-news`, {
      params: { apiKey: process.env.CURRENTS_API_KEY, language: lang || 'en' },
      timeout: 12000
    });
    const articles = (response.data?.news || []).filter(a => a?.title && a?.url).slice(0, max).map(a => normalize(a, 'internacional', lang));
    cache.set(cacheKey, articles, 90);
    return articles;
  } catch (err) {
    console.error('[Currents Latest]', err.message);
    return [];
  }
}

const CURRENTS_SUPPORTED = new Set(Object.keys(CATEGORY_MAP));

module.exports = { fetchNews, fetchLatest, CURRENTS_SUPPORTED };
