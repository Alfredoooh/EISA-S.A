const axios = require('axios');
const cache = require('../utils/cache');
const { filterInternationalQuality } = require('../config/newsPolicy');
const MIN_SOURCE_QUALITY = Number(process.env.MIN_SOURCE_QUALITY) || 80;

const BASE_URL = 'https://api.currentsapi.services/v2';

async function request(path, params = {}) {
  return axios.get(`${BASE_URL}${path}`, {
    params,
    headers: {
      Authorization: `Bearer ${process.env.CURRENTS_API_KEY}`,
      Accept: 'application/json'
    },
    timeout: 12000,
    validateStatus: s => s >= 200 && s < 500
  });
}

function mapArticle(a) {
  return {
    id: a.url || a.id,
    title: a.title || '',
    description: a.description || '',
    url: a.url || '',
    image: a.image || '',
    source: extractDomain(a.url),
    sourceUrl: a.url || '',
    language: a.language || 'en',
    category: Array.isArray(a.category) ? a.category[0] : a.category || '',
    publishedAt: a.published || null,
    provider: 'currents'
  };
}

async function fetchNews({ category = 'general', lang = 'en', page = 1, pageSize = 40 }) {
  const cacheKey = `currents_${category}_${lang}_${page}_${pageSize}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  try {
    const response = await request('/latest-news', {
      language: lang,
      category,
      type: 1,
      page_number: page,
      page_size: pageSize
    });

    if (response.status >= 400) throw new Error(`Currents HTTP ${response.status}`);
    const articles = (response.data.news || [])
      .map(mapArticle)
      .filter(a => a.image && a.title && a.url);
    const quality = filterInternationalQuality(articles, MIN_SOURCE_QUALITY);
    cache.set(cacheKey, quality);
    return quality;
  } catch (err) {
    console.error('[Currents]', err.message);
    return [];
  }
}

async function fetchLatest({ lang = 'en', page = 1, pageSize = 50 }) {
  const cacheKey = `currents_latest_${lang}_${page}_${pageSize}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  try {
    const response = await request('/latest-news', {
      language: lang,
      type: 1,
      page_number: page,
      page_size: pageSize
    });

    if (response.status >= 400) throw new Error(`Currents HTTP ${response.status}`);
    const articles = (response.data.news || [])
      .map(mapArticle)
      .filter(a => a.image && a.title && a.url);
    const quality = filterInternationalQuality(articles, MIN_SOURCE_QUALITY);
    cache.set(cacheKey, quality);
    return quality;
  } catch (err) {
    console.error('[Currents Latest]', err.message);
    return [];
  }
}

async function searchNews({ keywords, category, lang = 'en', page = 1, pageSize = 40 }) {
  const cacheKey = `currents_search_${keywords}_${category || ''}_${lang}_${page}_${pageSize}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  try {
    const params = {
      keywords,
      language: lang,
      page_number: page,
      page_size: pageSize,
      type: 1,
      start_date: new Date(Date.now() - 72 * 60 * 60 * 1000).toISOString(),
      end_date: new Date().toISOString()
    };
    if (category) params.category = category;

    const response = await request('/search', params);
    if (response.status >= 400) throw new Error(`Currents search HTTP ${response.status}`);

    const articles = (response.data.news || [])
      .map(mapArticle)
      .filter(a => a.image && a.title && a.url);
    const quality = filterInternationalQuality(articles, MIN_SOURCE_QUALITY);
    cache.set(cacheKey, quality);
    return quality;
  } catch (err) {
    console.error('[Currents Search]', err.message);
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

module.exports = { fetchNews, fetchLatest, searchNews };
