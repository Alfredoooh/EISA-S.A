const axios = require('axios');
const cache = require('../utils/cache');

const INSTANCE_REGISTRY = 'https://searx.space/data/instances.json';

const DEFAULT_INSTANCES = [
  'https://search.jeremyh.xyz',
  'https://searx.tsmdt.de',
  'https://search.serpensin.com',
  'https://search.anoni.net',
  'https://searx.ononoki.org'
];

async function getInstances() {
  const configured = String(process.env.SEARXNG_INSTANCES || '')
    .split(',').map(x => x.trim()).filter(Boolean);
  if (configured.length) return configured;

  const cached = cache.get('searxng_instances_registry');
  if (cached?.length) return cached;
  try {
    const response = await axios.get(INSTANCE_REGISTRY, { timeout: 5000, headers: { 'User-Agent': 'NewsAPI/2.0' } });
    const data = response.data;
    const candidates = data?.instances && typeof data.instances === 'object' ? data.instances : data;
    const instances = [];
    for (const [key, value] of Object.entries(candidates || {})) {
      const url = typeof value === 'string' ? value : (value?.url || key);
      if (!/^https?:\/\//i.test(url)) continue;
      const details = typeof value === 'object' && value ? value : {};
      if (details.network?.ipv4 === false && details.network?.ipv6 === false) continue;
      instances.push(url.replace(/\/$/, ''));
    }
    const unique = [...new Set(instances)].slice(0, 20);
    if (unique.length) {
      cache.set('searxng_instances_registry', unique, 1800);
      return unique;
    }
  } catch (err) {
    console.warn('[SearXNG Registry]', err.message);
  }
  return DEFAULT_INSTANCES;
}

function normalizeResult(item, category = 'search') {
  return {
    id: Buffer.from(item.url || '').toString('base64').slice(0, 22),
    title: item.title || '',
    description: item.content || '',
    url: item.url || '',
    image: item.img_src || item.thumbnail || '',
    source: item.source || '',
    publishedAt: item.publishedDate || item.published_at || null,
    category,
    provider: 'searxng'
  };
}

async function queryInstance(baseUrl, query, options = {}) {
  const url = `${baseUrl.replace(/\/$/, '')}/search`;
  const params = {
    q: query,
    format: 'json',
    categories: 'news',
    language: options.language || 'en',
    safesearch: 1,
    time_range: options.timeRange || 'day',
    pageno: 1
  };
  const response = await axios.get(url, { params, timeout: 7000, headers: { 'User-Agent': 'NewsAPI/2.0' } });
  return (response.data?.results || []).map(r => normalizeResult(r, options.category));
}

async function searchNews({ query, language = 'en', category = 'internacional', timeRange = 'day', max = 40 } = {}) {
  const q = String(query || '').trim();
  if (!q) return [];
  const cacheKey = `searx_v2_${q}_${language}_${category}_${timeRange}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  for (const instance of await getInstances()) {
    try {
      const results = await queryInstance(instance, q, { language, category, timeRange });
      if (results.length) {
        const limited = results.slice(0, max);
        cache.set(cacheKey, limited, 120);
        return limited;
      }
    } catch (err) {
      console.warn(`[SearXNG] ${instance}: ${err.message}`);
    }
  }
  return [];
}

module.exports = { searchNews, getInstances, INSTANCE_REGISTRY };
