// src/services/gnews.js
const axios = require('axios');
const cache = require('../utils/cache');
const { CATEGORY_QUERIES } = require('../config');

const BASE_URL = 'https://gnews.io/api/v4';

// GNews native category IDs — only these 8 exist on the free/paid tier.
const TOPIC_MAP = {
  world:         'world',
  business:      'business',
  technology:    'technology',
  entertainment: 'entertainment',
  sports:        'sports',
  science:       'science',
  health:        'health',
  // Categories below have no native GNews topic; we fall back to keyword search.
};

function normalize(item, category) {
  return {
    id:            Buffer.from(item.url || '').toString('base64').slice(0, 22),
    title:         item.title       || '',
    description:   item.description || '',
    content:       item.content     || '',
    url:           item.url         || '',
    image:         item.image       || '',
    source:        item.source?.name || '',
    sourceUrl:     item.source?.url  || '',
    sourceCountry: item.source?.country || '',
    language:      'en',
    category,
    publishedAt:   item.publishedAt || null,
    provider:      'gnews',
  };
}

async function get(endpoint, params, cacheKey) {
  if (!process.env.GNEWS_API_KEY) return [];
  const cached = cache.get(cacheKey);
  if (cached) return cached;
  try {
    const { data } = await axios.get(`${BASE_URL}/${endpoint}`, {
      params: { ...params, apikey: process.env.GNEWS_API_KEY },
      timeout: 12000,
      headers: { 'User-Agent': 'NewsAggregatorAPI/3.0' },
    });
    const articles = data?.articles || [];
    cache.set(cacheKey, articles, 90);
    return articles;
  } catch (err) {
    console.error(`[GNews ${endpoint}]`, err.message);
    return [];
  }
}

async function fetchNews({ category = 'world', max = 25 } = {}) {
  const safeMax = Math.min(100, Math.max(10, Number(max) || 25));
  const topic = TOPIC_MAP[category];
  if (topic) {
    const raw = await get(
      'top-headlines',
      { topic, lang: 'en', max: safeMax },
      `gnews_top_${category}_${safeMax}`
    );
    return raw.filter(a => a?.url && a?.title).map(a => normalize(a, category));
  }
  // Fallback: keyword search for categories GNews doesn't support natively.
  return searchNews({ query: CATEGORY_QUERIES[category] || category, max: safeMax, category });
}

async function searchNews({ query, max = 25, category = 'search' } = {}) {
  const q = String(query || '').trim();
  if (!q) return [];
  const safeMax = Math.min(100, Math.max(10, Number(max) || 25));
  const raw = await get(
    'search',
    { q: q.slice(0, 200), lang: 'en', max: safeMax, sortby: 'publishedAt' },
    `gnews_search_${q}_${safeMax}`
  );
  return raw.filter(a => a?.url && a?.title).map(a => normalize(a, category));
}

module.exports = { fetchNews, searchNews, TOPIC_MAP };