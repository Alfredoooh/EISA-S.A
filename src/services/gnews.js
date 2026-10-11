const axios = require('axios');
const cache = require('../utils/cache');
const { CATEGORY_QUERIES, LEGACY_CATEGORY_ALIASES } = require('../config');

const BASE_URL = 'https://gnews.io/api/v4';
const TOPIC_MAP = {
  world: 'world', nation: 'nation', business: 'business', technology: 'technology',
  entertainment: 'entertainment', sports: 'sports', science: 'science', health: 'health'
};

function extractDomain(url) {
  try { return new URL(url).hostname.replace(/^www\./, '').toLowerCase(); } catch { return ''; }
}
function toArticle(a, category, lang) {
  return {
    id: Buffer.from(a.url || '').toString('base64').slice(0, 22),
    title: a.title || '', description: a.description || '', content: a.content || '',
    url: a.url || '', image: a.image || '', source: a.source?.name || extractDomain(a.url),
    sourceUrl: a.source?.url || '', sourceCountry: a.source?.country || '',
    language: a.language || lang || '', publishedAt: a.publishedAt || null,
    category, provider: 'gnews'
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
      headers: { 'User-Agent': 'InternationalNewsAPI/2.1' }
    });
    const articles = response.data?.articles || [];
    cache.set(cacheKey, articles, 90);
    return articles;
  } catch (error) {
    console.error(`[GNews ${endpoint}]`, error.message);
    return [];
  }
}
async function fetchNews({ category = 'world', lang = 'en', country = '', max = 25 } = {}) {
  category = LEGACY_CATEGORY_ALIASES[String(category || '').toLowerCase().replace(/[\s-]+/g, '_')] || category;
  const topic = TOPIC_MAP[category];
  const safeMax = Math.min(Number(max) || 25, 100);
  if (!topic) {
    return searchNews({ query: CATEGORY_QUERIES[category] || CATEGORY_QUERIES.world, lang, max: safeMax });
  }
  const raw = await get('top-headlines', {
    category: topic, lang: lang || 'en', max: safeMax,
    // Do not set country on the international feed.
    ...(country ? { country: String(country).toLowerCase() } : {})
  }, `gnews_v2_top_${category}_${lang}_${country}_${safeMax}`);
  return raw.filter(a => a?.title && a?.url).map(a => toArticle(a, category, lang));
}
async function searchNews({ query, lang = 'en', max = 25, from, to } = {}) {
  const q = String(query || '').trim();
  if (!q) return [];
  const safeMax = Math.min(Number(max) || 25, 100);
  const raw = await get('search', {
    q: q.slice(0, 200), lang: lang || 'en', max: safeMax,
    from, to, sortby: 'publishedAt'
  }, `gnews_v2_search_${q}_${lang}_${from || ''}_${to || ''}_${safeMax}`);
  return raw.filter(a => a?.title && a?.url).map(a => toArticle(a, 'search', lang));
}

module.exports = { fetchNews, searchNews, TOPIC_MAP };
