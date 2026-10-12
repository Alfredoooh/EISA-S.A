// src/utils/news.js
const crypto = require('crypto');
const { EXCLUDED_COUNTRIES, EXCLUDED_DOMAIN_SUFFIXES, EXCLUDED_DOMAINS, QUALITY_SOURCES } = require('../config');

function extractDomain(url) {
  try { return new URL(url).hostname.replace(/^www\./, '').toLowerCase(); }
  catch { return ''; }
}

function normalizeUrl(url) {
  try {
    const u = new URL(url);
    u.hash = '';
    ['utm_source','utm_medium','utm_campaign','utm_term','utm_content','fbclid','gclid'].forEach(p => u.searchParams.delete(p));
    return u.toString().replace(/\/$/, '');
  } catch { return String(url || '').trim(); }
}

function canonicalKey(article) { return normalizeUrl(article.url || ''); }

function normalizeTitle(title) {
  return String(title || '').toLowerCase().normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9\s]/g, ' ').replace(/\s+/g, ' ').trim();
}

function titleSimilarity(a, b) {
  const aa = new Set(normalizeTitle(a).split(' ').filter(t => t.length > 2));
  const bb = new Set(normalizeTitle(b).split(' ').filter(t => t.length > 2));
  if (!aa.size || !bb.size) return 0;
  let common = 0;
  for (const t of aa) if (bb.has(t)) common++;
  return common / Math.max(aa.size, bb.size);
}

function excludedDomain(url) {
  const domain = extractDomain(url);
  if (!domain) return false;
  for (const base of EXCLUDED_DOMAINS) {
    if (domain === base || domain.endsWith('.' + base)) return true;
  }
  return EXCLUDED_DOMAIN_SUFFIXES.some(s => domain.endsWith(s));
}

function isInternational(article) {
  const country = String(article.sourceCountry || article.country || '').trim().toUpperCase();
  if (EXCLUDED_COUNTRIES.has(country)) return false;
  return !excludedDomain(article.url || article.sourceUrl || '');
}

function parseDate(value) {
  if (!value) return null;
  const d = new Date(value);
  return isNaN(d.getTime()) ? null : d;
}

function hoursOld(date, now = Date.now()) {
  const d = parseDate(date);
  if (!d) return Infinity;
  return Math.max(0, (now - d.getTime()) / 3600000);
}

// Returns 0–1. Uses the QUALITY_SOURCES weight map; unknown sources get provider defaults.
function sourceInfluence(article) {
  const domain = extractDomain(article.url || article.sourceUrl || '');
  if (QUALITY_SOURCES[domain])                       return QUALITY_SOURCES[domain] / 100;
  const sub = Object.entries(QUALITY_SOURCES).find(([base]) => domain.endsWith('.' + base));
  if (sub)                                           return sub[1] / 100;
  const providerDefaults = { guardian: 0.90, nytimes: 0.90, gnews: 0.72, newsdata: 0.70 };
  return providerDefaults[article.provider] || 0.55;
}

// Freshness decays linearly: 1.0 at 0h → 0.0 at 48h.
function freshnessScore(article, now = Date.now()) {
  const age = hoursOld(article.publishedAt, now);
  if (!isFinite(age)) return 0;
  return Math.max(0, 1 - Math.min(age, 48) / 48);
}

// Final score: 60% freshness + 40% source influence.
// This keeps articles sequentially ordered by time while promoting influential sources
// when two articles were published within the same time window.
function computeScore(article, now = Date.now()) {
  return freshnessScore(article, now) * 0.60 + sourceInfluence(article) * 0.40;
}

// Sort: primary = publishedAt descending (newest first), secondary = score descending.
// This guarantees sequential chronological order; influence only breaks ties within the same minute.
function rankArticles(articles, { now = Date.now() } = {}) {
  return articles
    .map(a => ({
      ...a,
      _ts:    parseDate(a.publishedAt)?.getTime() || 0,
      _score: computeScore(a, now),
    }))
    .sort((a, b) => {
      const timeDiff = b._ts - a._ts;
      if (Math.abs(timeDiff) > 60000) return timeDiff;   // >1 min apart → sort by time
      return b._score - a._score;                         // same minute → sort by influence
    })
    .map(({ _ts, _score, ...rest }) => ({
      ...rest,
      influenceScore: Number(sourceInfluence(rest).toFixed(3)),
      freshnessScore: Number(freshnessScore(rest, now).toFixed(3)),
    }));
}

function dedupeArticles(articles) {
  const seenUrls = new Set();
  const kept = [];
  for (const article of articles) {
    const key = canonicalKey(article);
    if (!key || seenUrls.has(key)) continue;
    const dup = kept.some(existing =>
      existing.source === article.source && titleSimilarity(existing.title, article.title) >= 0.86
    );
    if (dup) continue;
    seenUrls.add(key);
    kept.push({ ...article, url: key });
  }
  return kept;
}

function filterFresh(articles, maxAgeHours = 24, now = Date.now()) {
  return articles.filter(a => {
    const d = parseDate(a.publishedAt);
    if (!d) return false;
    return hoursOld(d, now) <= maxAgeHours && d.getTime() <= now + 600000;
  });
}

function stableId(article) {
  return crypto.createHash('sha1').update(canonicalKey(article)).digest('hex').slice(0, 16);
}

function finalizeArticles(articles, { maxAgeHours = 24, minimumFresh = 8, allowFallback = true, now = Date.now() } = {}) {
  let out = (articles || [])
    .filter(a => a && a.url && a.title)
    .map(a => ({ ...a, id: a.id || stableId(a) }))
    .filter(isInternational);
  const fresh = filterFresh(out, maxAgeHours, now);
  out = fresh.length < Math.min(12, minimumFresh) && allowFallback
    ? filterFresh(out, Math.min(72, Math.max(maxAgeHours, 48)), now)
    : fresh;
  return rankArticles(dedupeArticles(out), { now });
}

module.exports = {
  extractDomain, normalizeUrl, normalizeTitle, titleSimilarity,
  excludedDomain, isInternational, parseDate, hoursOld,
  dedupeArticles, filterFresh, rankArticles, finalizeArticles, sourceInfluence,
};