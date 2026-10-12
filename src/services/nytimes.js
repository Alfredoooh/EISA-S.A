// src/services/nytimes.js
const axios = require('axios');
const cache = require('../utils/cache');

const BASE_SEARCH = 'https://api.nytimes.com/svc/search/v2/articlesearch.json';
const BASE_WIRE = 'https://api.nytimes.com/svc/news/v3/content/nyt';
const BASE_TOP = 'https://api.nytimes.com/svc/topstories/v2';

// NYT section names for Top Stories and Times Newswire.
const SECTION_MAP = {
  world: 'world',
  business: 'business',
  technology: 'technology',
  science: 'science',
  health: 'health',
  sports: 'sports',
  entertainment: 'arts',
  politics_government: 'politics',
  environment: 'climate',
  education: 'education',
  travel: 'travel',
};

function normalizeArticle(item, category) {
  const url = item.web_url || item.url || '';
  const title = item.headline?.main || item.title || '';
  return {
    id: Buffer.from(url).toString('base64').slice(0, 22),
    title,
    description: item.abstract || item.abstract || '',
    content: item.lead_paragraph || '',
    url,
    image: item.multimedia?.find(m => m.subtype === 'xlarge')?.url ?
      `https://www.nytimes.com/${item.multimedia.find(m => m.subtype === 'xlarge').url}` :
      (item.thumbnail_standard || ''),
    source: 'The New York Times',
    sourceUrl: 'https://nytimes.com',
    sourceCountry: 'US',
    language: 'en',
    category,
    publishedAt: item.pub_date || item.published_date || null,
    provider: 'nytimes',
  };
}

async function fetchTopStories({ category = 'world', max = 30 } = {}) {
  if (!process.env.NYT_API_KEY) return [];
  const section = SECTION_MAP[category] || 'world';
  const key = `nyt_top_${section}_${max}`;
  const cached = cache.get(key);
  if (cached) return cached;
  try {
    const { data } = await axios.get(`${BASE_TOP}/${section}.json`, {
      params: { 'api-key': process.env.NYT_API_KEY },
      timeout: 12000,
    });
    const articles = (data?.results || [])
      .filter(item => item.url && item.title)
      .slice(0, max)
      .map(item => normalizeArticle(item, category));
    cache.set(key, articles, 90);
    return articles;
  } catch (err) {
    console.error('[NYT TopStories]', err.message);
    return [];
  }
}

async function fetchNewswire({ category = 'world', max = 30 } = {}) {
  if (!process.env.NYT_API_KEY) return [];
  const section = SECTION_MAP[category] || 'all';
  const key = `nyt_wire_${section}_${max}`;
  const cached = cache.get(key);
  if (cached) return cached;
  try {
    const { data } = await axios.get(`${BASE_WIRE}/${section}.json`, {
      params: { 'api-key': process.env.NYT_API_KEY, limit: Math.min(50, max) },
      timeout: 12000,
    });
    const articles = (data?.results || [])
      .filter(item => item.url && item.title)
      .slice(0, max)
      .map(item => normalizeArticle(item, category));
    cache.set(key, articles, 90);
    return articles;
  } catch (err) {
    console.error('[NYT Newswire]', err.message);
    return [];
  }
}

async function searchNews({ query, category, max = 30 } = {}) {
  if (!process.env.NYT_API_KEY || !query) return [];
  const key = `nyt_search_${query}_${category || ''}_${max}`;
  const cached = cache.get(key);
  if (cached) return cached;
  try {
    const params = {
      'api-key': process.env.NYT_API_KEY,
      q: query,
      sort: 'newest',
      fl: 'headline,abstract,web_url,pub_date,multimedia,lead_paragraph',
    };
    if (category && SECTION_MAP[category]) params.fq = `section_name:("${SECTION_MAP[category]}")`;
    const { data } = await axios.get(BASE_SEARCH, { params, timeout: 12000 });
    const articles = (data?.response?.docs || [])
      .filter(item => item.web_url && item.headline?.main)
      .slice(0, max)
      .map(item => normalizeArticle(item, category || 'search'));
    cache.set(key, articles, 90);
    return articles;
  } catch (err) {
    console.error('[NYT Search]', err.message);
    return [];
  }
}

module.exports = { fetchTopStories, fetchNewswire, searchNews, SECTION_MAP };