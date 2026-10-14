'use strict';

const { CATEGORIES } = require('../categories');

const API_KEY = process.env.GUARDIAN_API_KEY || '04b58c68-379c-4a85-be55-2f275e039936';
const API_URL = 'https://content.guardianapis.com/search';
const FETCH_TIMEOUT_MS = 15000;
const PAGE_SIZE = 50;

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
  const title = String(raw.webTitle || '').trim();
  const link = String(raw.webUrl || '').trim();
  if (!title || !/^https?:\/\//i.test(link)) return null;
  const ts = raw.webPublicationDate ? Math.floor(Date.parse(raw.webPublicationDate) / 1000) : null;
  const image =
    raw.fields && raw.fields.thumbnail && /^https?:\/\//i.test(raw.fields.thumbnail) ?
    raw.fields.thumbnail :
    '';
  return {
    title,
    link,
    summary: raw.fields && raw.fields.trailText ? String(raw.fields.trailText).replace(/<[^>]+>/g, '').trim().slice(0, 280) : '',
    image,
    ts: Number.isNaN(ts) ? null : ts,
    source: 'The Guardian',
    provider: 'guardian',
    category: catLabel,
    categoryId: catId,
    lang: 'eng',
  };
}

async function fetchCategory(catId, maxItems) {
  const cat = CATEGORIES[catId] || CATEGORIES.geral;
  const params = new URLSearchParams({
    'api-key': API_KEY,
    'page-size': String(Math.min(PAGE_SIZE, maxItems)),
    'order-by': 'newest',
    'show-fields': 'thumbnail,trailText',
  });
  if (cat.guardian && cat.guardian.trim()) params.set('section', cat.guardian);
  
  try {
    const data = await fetchWithTimeout(`${API_URL}?${params.toString()}`);
    const results = data && data.response && Array.isArray(data.response.results) ? data.response.results : [];
    return results.map((raw) => toArticle(raw, catId, cat.label)).filter(Boolean).slice(0, maxItems);
  } catch (err) {
    console.warn(`guardian ${catId} falhou: ${err.message}`);
    return [];
  }
}

module.exports = { fetchCategory };