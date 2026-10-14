const axios = require('axios');
const cache = require('../utils/cache');

const INSTANCE_REGISTRY = 'https://searx.space/data/instances.json';
const DEFAULT_INSTANCES = [
  'https://search.jeremyh.xyz', 'https://searx.tsmdt.de', 'https://search.serpensin.com',
  'https://search.anoni.net', 'https://searx.ononoki.org'
];
async function getInstances() {
  const configured = String(process.env.SEARXNG_INSTANCES || '').split(',').map(v => v.trim()).filter(Boolean);
  if (configured.length) return configured;
  const cached = cache.get('searxng_instances_registry');
  if (cached?.length) return cached;
  try {
    const response = await axios.get(INSTANCE_REGISTRY, { timeout: 5000, headers: { 'User-Agent': 'InternationalNewsAPI/2.1' } });
    const source = response.data?.instances && typeof response.data.instances === 'object' ? response.data.instances : response.data;
    const instances = [];
    for (const [key, value] of Object.entries(source || {})) {
      const url = typeof value === 'string' ? value : (value?.url || key);
      if (!/^https?:\/\//i.test(url)) continue;
      const detail = typeof value === 'object' && value ? value : {};
      if (detail.network?.ipv4 === false && detail.network?.ipv6 === false) continue;
      instances.push(url.replace(/\/$/, ''));
    }
    const unique = [...new Set(instances)].slice(0, 20);
    if (unique.length) { cache.set('searxng_instances_registry', unique, 1800); return unique; }
  } catch (error) { console.warn('[SearXNG registry]', error.message); }
  return DEFAULT_INSTANCES;
}
function normalizeResult(item, category = 'world') {
  return {
    id: Buffer.from(item.url || '').toString('base64').slice(0, 22),
    title: item.title || '', description: item.content || '', url: item.url || '',
    image: item.img_src || item.thumbnail || '', source: item.source || '',
    publishedAt: item.publishedDate || item.published_at || null, category, provider: 'searxng'
  };
}
async function queryInstance(baseUrl, query, options = {}) {
  const response = await axios.get(`${baseUrl.replace(/\/$/, '')}/search`, {
    params: {
      q: query, format: 'json', categories: 'news', language: options.language || 'en',
      safesearch: 1, time_range: options.timeRange || 'day', pageno: 1
    },
    timeout: 7000, headers: { 'User-Agent': 'InternationalNewsAPI/2.1' }
  });
  return (response.data?.results || []).map(item => normalizeResult(item, options.category));
}
async function searchNews({ query, language = 'en', category = 'world', timeRange = 'day', max = 40 } = {}) {
  const q = String(query || '').trim();
  if (!q) return [];
  const key = `searx_v2_${q}_${language}_${category}_${timeRange}`;
  const cached = cache.get(key);
  if (cached) return cached;
  for (const instance of await getInstances()) {
    try {
      const articles = await queryInstance(instance, q, { language, category, timeRange });
      if (articles.length) {
        const limited = articles.slice(0, Math.max(1, Number(max) || 40));
        cache.set(key, limited, 120);
        return limited;
      }
    } catch (error) { console.warn(`[SearXNG] ${instance}: ${error.message}`); }
  }
  return [];
}

module.exports = { searchNews, getInstances, INSTANCE_REGISTRY };
