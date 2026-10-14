'use strict';

const { CATEGORIES } = require('../categories');

const API_KEY = process.env.GNEWS_API_KEY || '5a3e9cdd12d67717cfb6643d25ebaeb5';
const API_URL = 'https://gnews.io/api/v4/top-headlines';
const FETCH_TIMEOUT_MS = 15000;
const PAGE_SIZE = 25; // limite do plano free da GNews

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
  const link = String(raw.url || '').trim();
  if (!title || !/^https?:\/\//i.test(link)) return null;
  const ts = raw.publishedAt ? Math.floor(Date.parse(raw.publishedAt) / 1000) : null;
  return {
    title,
    link,
    summary: String(raw.description || '').trim().slice(0, 280),
    image: /^https?:\/\//i.test(raw.image || '') ? raw.image : '',
    ts: Number.isNaN(ts) ? null : ts,
    source: raw.source && raw.source.name ? String(raw.source.name).trim() : 'GNews',
    provider: 'gnews',
    category: catLabel,
    categoryId: catId,
    lang: '',
  };
}

async function fetchCategory(catId, maxItems) {
  const cat = CATEGORIES[catId] || CATEGORIES.geral;
  const params = new URLSearchParams({
    token: API_KEY,
    max: String(Math.min(PAGE_SIZE, maxItems)),
  });
  if (cat.gnews && cat.gnews.trim()) params.set('category', cat.gnews);
  
  try {
    const data = await fetchWithTimeout(`${API_URL}?${params.toString()}`);
    const results = data && Array.isArray(data.articles) ? data.articles : [];
    return results.map((raw) => toArticle(raw, catId, cat.label)).filter(Boolean).slice(0, maxItems);
  } catch (err) {
    console.warn(`gnews ${catId} falhou: ${err.message}`);
    return [];
  }
}

module.exports = { fetchCategory };