const axios = require('axios');
const cache = require('../utils/cache');
const { filterInternationalQuality } = require('../config/newsPolicy');
const MIN_SOURCE_QUALITY = Number(process.env.MIN_SOURCE_QUALITY) || 80;

const BASE_URL = 'https://gnews.io/api/v4';

async function fetchNews({ category = 'world', lang = 'en', country = '', page = 1, max = 25 }) {
  const cacheKey = `gnews_${category}_${lang}_${country}_${page}_${max}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  try {
    const params = {
      apikey: process.env.GNEWS_API_KEY,
      category,
      lang,
      max,
      page
    };
    // Deliberately do not set country for the international feed.
    if (country) params.country = country;

    const response = await axios.get(`${BASE_URL}/top-headlines`, {
      params,
      timeout: 12000,
      validateStatus: s => s >= 200 && s < 500
    });

    if (response.status >= 400) {
      throw new Error(`GNews HTTP ${response.status}: ${response.data?.errors?.join?.(', ') || 'request failed'}`);
    }

    const articles = (response.data.articles || [])
      .filter(a => a.image && a.title && a.url)
      .map(a => ({
        id: a.url,
        title: a.title || '',
        description: a.description || '',
        url: a.url || '',
        image: a.image || '',
        source: a.source?.name || extractDomain(a.url),
        sourceUrl: a.source?.url || '',
        language: a.language || lang,
        publishedAt: a.publishedAt || null,
        provider: 'gnews'
      }));

    const quality = filterInternationalQuality(articles, MIN_SOURCE_QUALITY);
    cache.set(cacheKey, quality);
    return quality;
  } catch (err) {
    console.error('[GNews]', err.message);
    return [];
  }
}

async function searchNews({ query, lang = 'en', max = 25, page = 1 }) {
  const cacheKey = `gnews_search_${query}_${lang}_${page}_${max}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  try {
    const from = new Date(Date.now() - 72 * 60 * 60 * 1000).toISOString();
    const response = await axios.get(`${BASE_URL}/search`, {
      params: {
        apikey: process.env.GNEWS_API_KEY,
        q: query,
        lang,
        max,
        page,
        from,
        sortby: 'publishedAt'
      },
      timeout: 12000,
      validateStatus: s => s >= 200 && s < 500
    });

    if (response.status >= 400) {
      throw new Error(`GNews HTTP ${response.status}`);
    }

    const articles = (response.data.articles || [])
      .filter(a => a.image && a.title && a.url)
      .map(a => ({
        id: a.url,
        title: a.title || '',
        description: a.description || '',
        url: a.url || '',
        image: a.image || '',
        source: a.source?.name || extractDomain(a.url),
        sourceUrl: a.source?.url || '',
        language: a.language || lang,
        publishedAt: a.publishedAt || null,
        provider: 'gnews'
      }));

    const quality = filterInternationalQuality(articles, MIN_SOURCE_QUALITY);
    cache.set(cacheKey, quality);
    return quality;
  } catch (err) {
    console.error('[GNews Search]', err.message);
    return [];
  }
}

function extractDomain(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, '').toLowerCase();
  } catch {
    return '';
  }
}

module.exports = { fetchNews, searchNews };
