const axios = require('axios');
const cache = require('../utils/cache');
const { CATEGORY_QUERIES } = require('../config');

const BASE_URL = 'https://gnews.io/api/v4';
const TOPIC_MAP = {
  internacional: 'world', economia: 'business', politica: 'nation', desporto: 'sports',
  futebol: 'sports', tecnologia: 'technology', ciencia: 'science', saude: 'health',
  entretenimento: 'entertainment'
};

function extractDomain(url) {
  try { return new URL(url).hostname.replace(/^www\./, ''); } catch { return ''; }
}

function toArticle(a, category, lang) {
  return {
    id: Buffer.from(a.url || '').toString('base64').slice(0, 22),
    title: a.title || '',
    description: a.description || '',
    content: a.content || '',
    url: a.url || '',
    image: a.image || '',
    source: a.source?.name || extractDomain(a.url),
    sourceUrl: a.source?.url || '',
    sourceCountry: a.source?.country || '',
    language: a.language || lang || '',
    publishedAt: a.publishedAt || null,
    category,
    provider: 'gnews'
  };
}

async function get(endpoint, params, cacheKey) {
  if (!process.env.GNEWS_API_KEY) return [];
  const cached = cache.get(cacheKey);
  if (cached) return cached;
  try {
    const response = await axios.get(`${BASE_URL}/${endpoint}`, {
      params: { ...params, apikey: process.env.GNEWS_API_KEY },
      timeout: 12000,
      headers: { 'User-Agent': 'NewsAPI/2.0' }
    });
    const articles = response.data?.articles || [];
    cache.set(cacheKey, articles, 90);
    return articles;
  } catch (err) {
    console.error(`[GNews ${endpoint}]`, err.message);
    return [];
  }
}

async function fetchNews({ category = 'internacional', lang = 'en', country = '', max = 25 } = {}) {
  const topic = TOPIC_MAP[category];
  let raw = [];
  if (topic) {
    raw = await get('top-headlines', {
      category: topic,
      lang: lang || 'en',
      max: Math.min(Number(max) || 25, 100),
      ...(country ? { country: String(country).toLowerCase() } : {})
    }, `gnews_v2_top_${category}_${lang}_${country}_${max}`);
  } else {
    return searchNews({ query: CATEGORY_QUERIES[category] || category, lang, max });
  }
  return raw.filter(a => a?.title && a?.url).map(a => toArticle(a, category, lang));
}

async function searchNews({ query, lang = 'en', max = 25, from, to } = {}) {
  const q = String(query || '').trim();
  if (!q) return [];
  const raw = await get('search', {
    q: q.slice(0, 200), lang: lang || 'en', max: Math.min(Number(max) || 25, 100),
    from, to, sortby: 'publishedAt'
  }, `gnews_v2_search_${q}_${lang}_${from || ''}_${to || ''}`);
  return raw.filter(a => a?.title && a?.url).map(a => toArticle(a, 'search', lang));
}

module.exports = { fetchNews, searchNews };
