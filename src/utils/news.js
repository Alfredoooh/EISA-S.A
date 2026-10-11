const crypto = require('crypto');
const {
  EXCLUDED_COUNTRIES,
  EXCLUDED_DOMAIN_SUFFIXES,
  EXCLUDED_DOMAINS,
  QUALITY_SOURCES
} = require('../config');

function extractDomain(url) {
  try { return new URL(url).hostname.replace(/^www\./, '').toLowerCase(); }
  catch { return ''; }
}

function normalizeUrl(url) {
  try {
    const u = new URL(url);
    u.hash = '';
    ['utm_source','utm_medium','utm_campaign','utm_term','utm_content','fbclid','gclid','mc_cid','mc_eid'].forEach(p => u.searchParams.delete(p));
    return u.toString().replace(/\/$/, '');
  } catch { return String(url || '').trim(); }
}

function canonicalKey(article) { return normalizeUrl(article.url || ''); }
function normalizeTitle(title) {
  return String(title || '').toLowerCase().normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s]/g, ' ').replace(/\s+/g, ' ').trim();
}
function titleSimilarity(a, b) {
  const aa = new Set(normalizeTitle(a).split(' ').filter(x => x.length > 2));
  const bb = new Set(normalizeTitle(b).split(' ').filter(x => x.length > 2));
  if (!aa.size || !bb.size) return 0;
  let common = 0;
  for (const token of aa) if (bb.has(token)) common++;
  return common / Math.max(aa.size, bb.size);
}

function excludedDomain(domainValue) {
  const domain = extractDomain(domainValue);
  if (!domain) return false;
  for (const base of EXCLUDED_DOMAINS) {
    if (domain === base || domain.endsWith('.' + base)) return true;
  }
  return EXCLUDED_DOMAIN_SUFFIXES.some(suffix => domain.endsWith(suffix));
}

function isInternational(article) {
  const rawCountry = String(article.sourceCountry || article.country || '').trim().toUpperCase();
  if (EXCLUDED_COUNTRIES.has(rawCountry)) return false;
  return !excludedDomain(article.url || article.sourceUrl || article.source || '');
}

function parseDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}
function hoursOld(date, now = Date.now()) {
  const d = parseDate(date);
  if (!d) return Infinity;
  return Math.max(0, (now - d.getTime()) / 3600000);
}

function sourceQuality(article) {
  const domain = extractDomain(article.url || article.sourceUrl || article.source || '');
  if (QUALITY_SOURCES.some(q => domain === q || domain.endsWith('.' + q))) return 1;
  if (article.provider === 'rss') return 0.78;
  if (article.provider === 'gnews') return 0.72;
  if (article.provider === 'gdelt') return 0.68;
  if (article.provider === 'searxng') return 0.62;
  if (article.provider === 'currents') return 0.70;
  return 0.5;
}
function freshnessScore(article, now = Date.now()) {
  const age = hoursOld(article.publishedAt, now);
  if (!Number.isFinite(age)) return 0.15;
  return Math.max(0, 1 - Math.min(age, 168) / 168);
}
function relevanceScore(article, category, query = '') {
  const text = `${article.title || ''} ${article.description || ''}`.toLowerCase();
  let score = 0;
  if (category && article.category === category) score += 0.25;
  const tokens = normalizeTitle(query).split(' ').filter(t => t.length > 2);
  if (tokens.length) {
    const haystack = normalizeTitle(text);
    score += tokens.filter(term => haystack.includes(term)).length / tokens.length * 0.45;
  }
  return Math.min(score, 0.7);
}
function rankArticles(articles, { category, query, now = Date.now() } = {}) {
  return articles.map(article => ({
    ...article,
    qualityScore: Number(sourceQuality(article).toFixed(3)),
    freshnessScore: Number(freshnessScore(article, now).toFixed(3)),
    relevanceScore: Number(relevanceScore(article, category, query).toFixed(3))
  })).map(article => ({
    ...article,
    score: Number((article.qualityScore * 0.28 + article.freshnessScore * 0.52 + article.relevanceScore * 0.20).toFixed(4))
  })).sort((a, b) => {
    const dateDiff = (parseDate(b.publishedAt)?.getTime() || 0) - (parseDate(a.publishedAt)?.getTime() || 0);
    return (b.score - a.score) * 1000000 + dateDiff;
  });
}
function dedupeArticles(articles) {
  const seenUrls = new Set();
  const kept = [];
  for (const article of articles) {
    const urlKey = canonicalKey(article);
    if (!urlKey || seenUrls.has(urlKey)) continue;
    const duplicateTitle = kept.some(existing =>
      existing.source === article.source && titleSimilarity(existing.title, article.title) >= 0.86
    );
    if (duplicateTitle) continue;
    seenUrls.add(urlKey);
    kept.push({ ...article, url: urlKey });
  }
  return kept;
}
function filterFresh(articles, maxAgeHours = 24, now = Date.now()) {
  return articles.filter(article => {
    const date = parseDate(article.publishedAt);
    if (!date) return false;
    const age = hoursOld(date, now);
    return age <= maxAgeHours && date.getTime() <= now + 10 * 60 * 1000;
  });
}
function stableId(article) {
  return crypto.createHash('sha1').update(canonicalKey(article)).digest('hex').slice(0, 16);
}
function finalizeArticles(articles, options = {}) {
  const now = options.now || Date.now();
  const maxAgeHours = Number(options.maxAgeHours || 24);
  let out = (articles || [])
    .filter(a => a && a.url && a.title)
    .map(a => ({ ...a, id: a.id || stableId(a) }))
    .filter(isInternational);
  const fresh = filterFresh(out, maxAgeHours, now);
  // Keep the original controlled fallback so sparse topics still return articles.
  out = fresh.length < Math.min(12, options.minimumFresh || 8) && options.allowFallback !== false
    ? filterFresh(out, Math.min(72, Math.max(maxAgeHours, 48)), now)
    : fresh;
  return rankArticles(dedupeArticles(out), options);
}

module.exports = {
  extractDomain, normalizeUrl, normalizeTitle, titleSimilarity, excludedDomain,
  isInternational, parseDate, hoursOld, dedupeArticles, filterFresh,
  rankArticles, finalizeArticles, sourceQuality
};
