const axios = require('axios');
const cache = require('../utils/cache');

const BASE_URL = 'https://api.currentsapi.services/v1';

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

async function fetchNews({ category = 'general', lang = 'pt', country = '', page = 1 }) {
  const cacheKey = `currents_${category}_${lang}_${country}_${page}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  try {
    const params = {
      apiKey: process.env.CURRENTS_API_KEY,
      language: lang,
      page_number: page
    };

    if (category && category !== 'general') {
      params.category = CATEGORY_MAP[category] || category;
    }

    if (country) {
      params.country = country;
    }

    const response = await axios.get(`${BASE_URL}/search`, {
      params,
      timeout: 10000
    });

    const articles = (response.data.news || [])
      .filter(a => a.image && a.image !== 'None' && a.title)
      .map(a => ({
        id: Buffer.from(a.url || '').toString('base64').slice(0, 16),
        title: a.title || '',
        description: a.description || '',
        url: a.url || '',
        image: a.image || '',
        source: a.author || extractDomain(a.url),
        category: a.category?.[0] || category,
        language: a.language || lang,
        publishedAt: a.published || null,
        provider: 'currents'
      }));

    cache.set(cacheKey, articles);
    return articles;

  } catch (err) {
    console.error('[Currents]', err.message);
    return [];
  }
}

async function fetchLatest({ lang = 'pt' }) {
  const cacheKey = `currents_latest_${lang}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  try {
    const response = await axios.get(`${BASE_URL}/latest-news`, {
      params: {
        apiKey: process.env.CURRENTS_API_KEY,
        language: lang
      },
      timeout: 10000
    });

    const articles = (response.data.news || [])
      .filter(a => a.image && a.image !== 'None' && a.title)
      .map(a => ({
        id: Buffer.from(a.url || '').toString('base64').slice(0, 16),
        title: a.title || '',
        description: a.description || '',
        url: a.url || '',
        image: a.image || '',
        source: a.author || extractDomain(a.url),
        language: a.language || lang,
        publishedAt: a.published || null,
        provider: 'currents'
      }));

    cache.set(cacheKey, articles);
    return articles;

  } catch (err) {
    console.error('[Currents Latest]', err.message);
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

module.exports = { fetchNews, fetchLatest };
