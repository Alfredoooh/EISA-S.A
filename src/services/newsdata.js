// src/services/newsdata.js
const axios = require('axios');
const cache = require('../utils/cache');
const { CATEGORY_QUERIES } = require('../config');

const BASE_URL = 'https://newsdata.io/api/1';

// NewsData.io native category IDs.
const CATEGORY_MAP = {
  world: 'world',
  business: 'business',
  technology: 'technology',
  science: 'science',
  health: 'health',
  sports: 'sports',
  entertainment: 'entertainment',
  politics_government: 'politics',
  environment: 'environment',
  education: 'education',
  crime_law_justice: 'crime',
  lifestyle_leisure: 'lifestyle',
  food: 'food',
  travel: 'tourism',
};

function normalize(item, category) {
  return {
    id: item.article_id || Buffer.from(item.link || '').toString('base64').slice(0, 22),
    title: item.title || '',
    description: item.description || '',
    content: item.content || '',
    url: item.link || '',
    image: item.image_url || '',
    source: item.source_name || '',
    sourceUrl: item.source_url || '',
    sourceCountry: item.country?.[0] || '',
    language: 'en',
    category,
    publishedAt: item.pubDate || null,
    provider: 'newsdata',
  };
}

async function fetchNews({ category = 'world', max = 30 } = {}) {
  if (!process.env.NEWSDATA_API_KEY) return [];
  const mapped = CATEGORY_MAP[category];
  if (!mapped) return [];
  const safeMax = Math.min(50, Math.max(10, Number(max) || 30));
  const key = `newsdata_v1_${category}_${safeMax}`;
  const cached = cache.get(key);
  if (cached) return cached;
  try {
    const { data } = await axios.get(`${BASE_URL}/latest`, {
      params: {
        apikey: process.env.NEWSDATA_API_KEY,
        category: mapped,
        language: 'en',
        size: safeMax,
      },
      timeout: 12000,
    });
    const articles = (data?.results || [])
      .filter(item => item.link && item.title)
      .map(item => normalize(item, category));
    cache.set(key, articles, 90);
    return articles;
  } catch (err) {
    console.error('[NewsData]', err.message);
    return [];
  }
}

async function searchNews({ query, category, max = 30 } = {}) {
  if (!process.env.NEWSDATA_API_KEY || !query) return [];
  const safeMax = Math.min(50, Math.max(10, Number(max) || 30));
  const key = `newsdata_v1_search_${query}_${category || ''}_${safeMax}`;
  const cached = cache.get(key);
  if (cached) return cached;
  try {
    const params = {
      apikey: process.env.NEWSDATA_API_KEY,
      q: String(query).slice(0, 200),
      language: 'en',
      size: safeMax,
    };
    if (category && CATEGORY_MAP[category]) params.category = CATEGORY_MAP[category];
    const { data } = await axios.get(`${BASE_URL}/latest`, { params, timeout: 12000 });
    const articles = (data?.results || [])
      .filter(item => item.link && item.title)
      .map(item => normalize(item, category || 'search'));
    cache.set(key, articles, 90);
    return articles;
  } catch (err) {
    console.error('[NewsData search]', err.message);
    return [];
  }
}

module.exports = { fetchNews, searchNews, CATEGORY_MAP };