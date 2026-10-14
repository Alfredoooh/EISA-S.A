'use strict';

const { CATEGORIES, REGIONS } = require('./categories');

const API_URL = 'https://api.gdeltproject.org/api/v2/doc/doc';
const MAX_RECORDS = 75;
const WINDOW_HOURS = [6, 24, 72, 168, 336, 720]; // 6h, 1d, 3d, 7d, 14d, 30d
const REQUEST_GAP_MS = 1100;
const FETCH_TIMEOUT_MS = 15000;
const USER_AGENT = 'NexaNews/1.0 (+https://render.com)';

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function pad(n) {
  return String(n).padStart(2, '0');
}

function windowBounds(hours, now) {
  const start = new Date(now.getTime() - hours * 3600 * 1000);
  const fmt = (d) =>
    `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}` +
    `${pad(d.getUTCHours())}${pad(d.getUTCMinutes())}${pad(d.getUTCSeconds())}`;
  return [fmt(start), fmt(now)];
}

function buildQuery(catQuery, country) {
  const parts = [];
  parts.push(catQuery || 'sourcelang:por');
  if (country) parts.push(`sourcecountry:${country}`);
  return parts.join(' ');
}

async function fetchWithTimeout(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(url, {
      headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' },
      signal: controller.signal,
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const text = await res.text();
    if (!text || !text.trim()) return [];
    let data;
    try {
      data = JSON.parse(text);
    } catch {
      return []; // a GDELT às vezes devolve HTML de erro em vez de JSON; trata-se como "sem resultados"
    }
    return Array.isArray(data.articles) ? data.articles : [];
  } finally {
    clearTimeout(timer);
  }
}

function parseSeenDate(seendate) {
  if (!seendate || seendate.length < 14) return null;
  const y = seendate.slice(0, 4);
  const mo = seendate.slice(4, 6);
  const d = seendate.slice(6, 8);
  const h = seendate.slice(8, 10);
  const mi = seendate.slice(10, 12);
  const s = seendate.slice(12, 14);
  const iso = `${y}-${mo}-${d}T${h}:${mi}:${s}Z`;
  const ts = Date.parse(iso);
  return Number.isNaN(ts) ? null : Math.floor(ts / 1000);
}

function cleanTitle(title) {
  return String(title || '')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 300);
}

function toArticle(raw, regionCode, catId, catLabel, lang) {
  const title = cleanTitle(raw.title);
  const link = String(raw.url || '').trim();
  if (!title || !/^https?:\/\//i.test(link)) return null;
  const ts = parseSeenDate(raw.seendate);
  const domain = String(raw.domain || '').trim();
  return {
    id: null, // preenchido pelo chamador com um hash estável do link
    title,
    link,
    summary: '',
    image: /^https?:\/\//i.test(raw.socialimage || '') ? raw.socialimage : '',
    ts,
    source: domain || 'GDELT',
    sourceId: `gdelt-${regionCode.toLowerCase()}-${catId}`,
    region: regionCode,
    category: catLabel,
    gdeltCategory: catId,
    lang: lang || (raw.language || '').slice(0, 3) || 'pt',
    dated: ts !== null,
  };
}

/**
 * Varre as janelas de tempo (mais recente -> mais antiga) para uma combinação
 * região+categoria, até reunir maxItems artigos sem links repetidos.
 */
async function fetchRegionCategory(regionCode, catId, maxItems) {
  const region = REGIONS.find((r) => r.code === regionCode);
  const cat = CATEGORIES[catId] || CATEGORIES.geral;
  if (!region) return [];
  
  const query = buildQuery(cat.query, region.country);
  const now = new Date();
  const seenLinks = new Set();
  const out = [];
  
  for (let i = 0; i < WINDOW_HOURS.length; i++) {
    if (out.length >= maxItems) break;
    if (i > 0) await sleep(REQUEST_GAP_MS);
    
    const [start, end] = windowBounds(WINDOW_HOURS[i], now);
    const params = new URLSearchParams({
      query,
      mode: 'ArtList',
      format: 'json',
      maxrecords: String(MAX_RECORDS),
      sort: 'DateDesc',
      startdatetime: start,
      enddatetime: end,
    });
    
    let rawItems = [];
    try {
      rawItems = await fetchWithTimeout(`${API_URL}?${params.toString()}`);
    } catch (err) {
      console.warn(`gdelt ${regionCode}/${catId} janela ${WINDOW_HOURS[i]}h falhou: ${err.message}`);
      continue;
    }
    
    for (const raw of rawItems) {
      const article = toArticle(raw, regionCode, catId, cat.label, region.lang);
      if (!article) continue;
      const key = article.link.replace(/\/$/, '').toLowerCase();
      if (seenLinks.has(key)) continue;
      seenLinks.add(key);
      out.push(article);
      if (out.length >= maxItems) break;
    }
  }
  
  return out;
}

module.exports = { fetchRegionCategory, CATEGORIES, REGIONS };