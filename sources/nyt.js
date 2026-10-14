'use strict';

const { CATEGORIES } = require('../categories');

const API_KEY = process.env.NYT_API_KEY || 'zcbpOW2GkW7x1qcKn90rGTzI5WwTUgUDRIGCDDRua6Lud0Xl';
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
  const link = String(raw.url || '').trim();
  if (!title || !/^https?:\/\//i.test(link)) return null;
  const ts = raw.published_date ? Math.floor(Date.parse(raw.published_date) / 1000) : null;
  let image = '';
  if (Array.isArray(raw.multimedia) && raw.multimedia.length) {
    const found = raw.multimedia.find((m) => m.url && /^https?:\/\//i.test(m.url));
    if (found) image = found.url;
  }
  return {
    title,
    link,
    summary: String(raw.abstract || '').trim().slice(0, 280),
    image,
    ts: Number.isNaN(ts) ? null : ts,
    source: 'The New York Times',
    provider: 'nyt',
    category: catLabel,
    categoryId: catId,
    lang: 'eng',
  };
}

async function fetchCategory(catId, maxItems) {
  const cat = CATEGORIES[catId] || CATEGORIES.geral;
  const section = cat.nyt && cat.nyt.trim() ? cat.nyt : 'home';
  const url = `https://api.nytimes.com/svc/topstories/v2/${encodeURIComponent(section)}.json?api-key=${encodeURIComponent(API_KEY)}`;
  
  try {
    const data = await fetchWithTimeout(url);
    const results = data && Array.isArray(data.results) ? data.results : [];
    return results.map((raw) => toArticle(raw, catId, cat.label)).filter(Boolean).slice(0, maxItems);
  } catch (err) {
    console.warn(`nyt ${catId} falhou: ${err.message}`);
    return [];
  }
}

module.exports = { fetchCategory };