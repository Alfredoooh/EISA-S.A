const axios = require('axios');
const cache = require('../utils/cache');

const BASE_URL = 'https://gnews.io/api/v4';

const CATEGORY_MAP = {
  general: 'general',
  technology: 'technology',
  business: 'business',
  sports: 'sports',
  health: 'health',
  science: 'science',
  entertainment: 'entertainment',
  politics: 'politics'
};

async function fetchNews({ category = 'general', lang = 'pt', country = 'pt', max = 10 }) {
  const cacheKey = `gnews_${category}_${lang}_${country}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  try {
    const response = await axios.get(`${BASE_URL}/top-headlines`, {
      params: {
        token: process.env.GNEWS_API_KEY,
        topic: CATEGORY_MAP[category] || 'general',
        lang,
        country,
        max
      },
      timeout: 10000
    });

    const articles = (response.data.articles || [])
      .filter(a => a.image && a.title)
      .map(a => ({
        id: Buffer.from(a.url || '').toString('base64').slice(0, 16),
        title: a.title || '',
        description: a.description || '',
        url: a.url || '',
        image: a.image || '',
        source: a.source?.name || extractDomain(a.url),
        sourceUrl: a.source?.url || '',
        language: lang,
        publishedAt: a.publishedAt || null,
        provider: 'gnews'
      }));

    cache.set(cacheKey, articles);
    return articles;

  } catch (err) {
    console.error('[GNews]', err.message);
    return [];
  }
}

async function searchNews({ query, lang = 'pt', max = 10 }) {
  const cacheKey = `gnews_search_${query}_${lang}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  try {
    const response = await axios.get(`${BASE_URL}/search`, {
      params: {
        token: process.env.GNEWS_API_KEY,
        q: query,
        lang,
        max
      },
      timeout: 10000
    });

    const articles = (response.data.articles || [])
      .filter(a => a.image && a.title)
      .map(a => ({
        id: Buffer.from(a.url || '').toString('base64').slice(0, 16),
        title: a.title || '',
        description: a.description || '',
        url: a.url || '',
        image: a.image || '',
        source: a.source?.name || extractDomain(a.url),
        language: lang,
        publishedAt: a.publishedAt || null,
        provider: 'gnews'
      }));

    cache.set(cacheKey, articles);
    return articles;

  } catch (err) {
    console.error('[GNews Search]', err.message);
    return [];
  }
}

function extractDomain(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return '';
  }
}

module.exports = { fetchNews, searchNews };
