'use strict';

const { CATEGORIES } = require('../categories');

const API_KEY = process.env.NEWSDATA_API_KEY || 'pub_7d7d1ac2f86b4bc6b4662fd5d6dad47c';
const API_URL = 'https://newsdata.io/api/1/latest';
const FETCH_TIMEOUT_MS = 15000;

async function fetchWithTimeout(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(url, { signal: controller.signal, headers: { Accept: 'application/json' } });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return res.json();
  } finally {
    clearTimeout(timer);
  }
}

function toArticle(raw, catId, catLabel) {
  const title = String(raw.title || '').trim();
  const link = String(raw.link || '').trim();
  if (!title || !/^https?:\/\//i.test(link)) return null;
  const ts = raw.pubDate ? Math.floor(Date.parse(raw.pubDate.replace(' ', 'T') + 'Z') / 1000) : null;
  return {
    title,
    link,
    summary: String(raw.description || '').trim().slice(0, 280),
    image: /^https?:\/\//i.test(raw.image_url || '') ? raw.image_url : '',
    ts: Number.isNaN(ts) ? null : ts,
    source: String(raw.source_name || raw.source_id || 'NewsData').trim(),
    provider: 'newsdata',
    category: catLabel,
    categoryId: catId,
    lang: (raw.language || '').slice(0, 3) || '',
  };
}

async function fetchCategory(catId, maxItems) {
  const cat = CATEGORIES[catId] || CATEGORIES.geral;
  const params = new URLSearchParams({ apikey: API_KEY });
  if (cat.newsdata && cat.newsdata.trim()) params.set('category', cat.newsdata);
  
  try {
    const data = await fetchWithTimeout(`${API_URL}?${params.toString()}`);
    const results = data && Array.isArray(data.results) ? data.results : [];
    return results.map((raw) => toArticle(raw, catId, cat.label)).filter(Boolean).slice(0, maxItems);
  } catch (err) {
    console.warn(`newsdata ${catId} falhou: ${err.message}`);
    return [];
  }
}

module.exports = { fetchCategory };