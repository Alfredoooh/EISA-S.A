const axios = require('axios');
const cache = require('../utils/cache');
const { LEGACY_CATEGORY_ALIASES } = require('../config');

const BASE_URL = 'https://api.currentsapi.services/v2';
// All values below are canonical Currents V2 categories from its official taxonomy.
const CATEGORY_MAP = {
  world: 'general',
  business: 'economy_business_finance',
  technology: 'science_technology',
  entertainment: 'arts_culture_entertainment',
  sports: 'sport',
  science: 'science_technology',
  health: 'health',
  society: 'society',
  politics_government: 'politics_government',
  lifestyle_leisure: 'lifestyle_leisure',
  human_interest: 'human_interest',
  crime_law_justice: 'crime_law_justice',
  education: 'education',
  environment: 'environment',
  labour: 'labour',
  automotive: 'automotive',
  real_estate: 'real_estate'
};

function extractDomain(url) {
  try { return new URL(url).hostname.replace(/^www\./, '').toLowerCase(); } catch { return ''; }
}
function normalize(article, category, lang) {
  return {
    id: article.id || Buffer.from(article.url || '').toString('base64').slice(0, 22),
    title: article.title || '', description: article.description || '',
    url: article.url || '', image: article.image || '',
    source: article.author || extractDomain(article.url), sourceUrl: article.url || '',
    sourceCountry: article.country || '', country: article.country || '',
    language: article.language || lang || 'en',
    category: Array.isArray(article.category) ? (article.category[0] || category) : (article.category || category),
    publishedAt: article.published || article.publishedAt || null,
    provider: 'currents'
  };
}
async function request(path, params = {}) {
  if (!process.env.CURRENTS_API_KEY) return null;
  return axios.get(`${BASE_URL}${path}`, {
    params,
    headers: { Authorization: `Bearer ${process.env.CURRENTS_API_KEY}`, Accept: 'application/json' },
    timeout: 12000
  });
}
async function fetchNews({ category = 'world', lang = 'en', country = '', max = 30, page = 1 } = {}) {
  category = LEGACY_CATEGORY_ALIASES[String(category || '').toLowerCase().replace(/[\s-]+/g, '_')] || category;
  const mapped = CATEGORY_MAP[category];
  if (!mapped) return [];
  const safeMax = Math.min(100, Math.max(1, Number(max) || 30));
  const key = `currents_v2_${category}_${lang}_${country}_${page}_${safeMax}`;
  const cached = cache.get(key);
  if (cached) return cached;
  try {
    const params = {
      language: lang || 'en', category: mapped, type: 1,
      page_number: Math.max(1, Number(page) || 1), page_size: safeMax
    };
    if (country) params.country = String(country).toUpperCase();
    const response = await request('/latest-news', params);
    const articles = (response?.data?.news || []).filter(a => a?.title && a?.url)
      .map(a => normalize(a, category, lang));
    cache.set(key, articles, 90);
    return articles;
  } catch (error) {
    console.error('[Currents V2]', error.message);
    return [];
  }
}
async function fetchLatest({ lang = 'en', max = 40, page = 1 } = {}) {
  const safeMax = Math.min(100, Math.max(1, Number(max) || 40));
  const key = `currents_v2_latest_${lang}_${page}_${safeMax}`;
  const cached = cache.get(key);
  if (cached) return cached;
  try {
    const response = await request('/latest-news', {
      language: lang || 'en', type: 1,
      page_number: Math.max(1, Number(page) || 1), page_size: safeMax
    });
    const articles = (response?.data?.news || []).filter(a => a?.title && a?.url)
      .map(a => normalize(a, 'world', lang));
    cache.set(key, articles, 90);
    return articles;
  } catch (error) {
    console.error('[Currents V2 latest]', error.message);
    return [];
  }
}
async function searchNews({ keywords, category, lang = 'en', page = 1, max = 40, from, to } = {}) {
  const query = String(keywords || '').trim();
  if (!query) return [];
  const safeMax = Math.min(100, Math.max(1, Number(max) || 40));
  const key = `currents_v2_search_${query}_${category || ''}_${lang}_${page}_${safeMax}_${from || ''}_${to || ''}`;
  const cached = cache.get(key);
  if (cached) return cached;
  try {
    const params = {
      keywords: query, language: lang || 'en', page_number: Math.max(1, Number(page) || 1),
      page_size: safeMax, type: 1,
      start_date: from || new Date(Date.now() - 72 * 3600000).toISOString(),
      end_date: to || new Date().toISOString()
    };
    if (category && CATEGORY_MAP[category]) params.category = CATEGORY_MAP[category];
    const response = await request('/search', params);
    const articles = (response?.data?.news || []).filter(a => a?.title && a?.url)
      .map(a => normalize(a, category || 'search', lang));
    cache.set(key, articles, 90);
    return articles;
  } catch (error) {
    console.error('[Currents V2 search]', error.message);
    return [];
  }
}

const CURRENTS_SUPPORTED = new Set(Object.keys(CATEGORY_MAP));
module.exports = { fetchNews, fetchLatest, searchNews, CURRENTS_SUPPORTED, CATEGORY_MAP };
