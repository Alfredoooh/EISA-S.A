const Parser = require('rss-parser');
const cache = require('../utils/cache');
const { INTERNATIONAL_RSS_SOURCES, LEGACY_CATEGORY_ALIASES } = require('../config');
const { normalizeUrl, excludedDomain } = require('../utils/news');

const parser = new Parser({
  timeout: 12000,
  headers: { 'User-Agent': 'Mozilla/5.0 InternationalNewsAPI/2.1' },
  customFields: { item: [
    ['media:content', 'mediaContent'], ['media:thumbnail', 'mediaThumbnail'],
    ['enclosure', 'enclosure'], ['dc:creator', 'creator'], ['content:encoded', 'contentEncoded']
  ] }
});
function extractImage(item) {
  return item.enclosure?.url || item.mediaContent?.['$']?.url || item.mediaThumbnail?.['$']?.url ||
    item['media:content']?.['$']?.url || item['media:thumbnail']?.['$']?.url || item.itunes?.image || '';
}
function stripHtml(value) {
  return String(value || '').replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ').replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ').replace(/&amp;/gi, '&').replace(/\s+/g, ' ').trim();
}
function description(item) { return stripHtml(item.contentSnippet || item.summary || item.description || item.content || ''); }
async function fetchFeed(source, { maxItems = 35 } = {}) {
  const key = `rss_v2_${source.url}`;
  const cached = cache.get(key);
  if (cached) return cached;
  try {
    const feed = await parser.parseURL(source.url);
    const articles = (feed.items || []).filter(item => item.title && (item.link || item.guid)).slice(0, maxItems)
      .map(item => ({
        id: Buffer.from(item.link || item.guid || '').toString('base64').slice(0, 22),
        title: String(item.title || '').trim(), description: description(item),
        url: normalizeUrl(item.link || item.guid || ''), image: extractImage(item),
        source: source.name, sourceUrl: `https://${source.domain || new URL(source.url).hostname}`,
        sourceCountry: source.country, country: source.country, language: source.lang,
        publishedAt: item.isoDate || item.pubDate || null, author: item.creator || '',
        category: source.category, provider: 'rss'
      })).filter(article => article.url && !excludedDomain(article.url));
    cache.set(key, articles, 180);
    return articles;
  } catch (error) {
    console.error(`[RSS] ${source.name}: ${error.message}`);
    return [];
  }
}
function sourcesForCategory(category) {
  category = LEGACY_CATEGORY_ALIASES[String(category || '').toLowerCase().replace(/[\s-]+/g, '_')] || category;
  if (!category || category === 'world') return INTERNATIONAL_RSS_SOURCES.filter(s => s.category === 'world' || s.category === 'science' || s.category === 'business');
  const exact = INTERNATIONAL_RSS_SOURCES.filter(s => s.category === category);
  if (exact.length) return exact;
  return INTERNATIONAL_RSS_SOURCES.filter(s => s.category === 'world');
}
async function fetchByCategory(category) {
  const sources = sourcesForCategory(category);
  const results = await Promise.allSettled(sources.map(source => fetchFeed(source)));
  return results.filter(result => result.status === 'fulfilled').flatMap(result => result.value);
}
async function fetchAll() {
  const results = await Promise.allSettled(INTERNATIONAL_RSS_SOURCES.map(source => fetchFeed(source)));
  return results.filter(result => result.status === 'fulfilled').flatMap(result => result.value);
}
async function fetchByCountry(countryCode) {
  const sources = INTERNATIONAL_RSS_SOURCES.filter(source => String(source.country).toUpperCase() === String(countryCode).toUpperCase());
  const results = await Promise.allSettled(sources.map(source => fetchFeed(source)));
  return results.filter(result => result.status === 'fulfilled').flatMap(result => result.value);
}

module.exports = { fetchByCategory, fetchByCountry, fetchAll, fetchFeed, INTERNATIONAL_RSS_SOURCES };
