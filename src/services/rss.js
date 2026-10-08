const Parser = require('rss-parser');
const cache = require('../utils/cache');
const { INTERNATIONAL_RSS_SOURCES } = require('../config');

const parser = new Parser({
  timeout: 12000,
  headers: {
    'User-Agent': 'Mozilla/5.0 NewsAPI/2.0'
  },
  customFields: {
    item: [
      ['media:content', 'mediaContent'],
      ['media:thumbnail', 'mediaThumbnail'],
      ['enclosure', 'enclosure'],
      ['dc:creator', 'creator'],
      ['content:encoded', 'contentEncoded']
    ]
  }
});

function extractImage(item) {
  const direct = item.enclosure?.url || item.mediaContent?.['$']?.url || item.mediaThumbnail?.['$']?.url;
  if (direct) return direct;
  return item['media:content']?.['$']?.url || item['media:thumbnail']?.['$']?.url || item.itunes?.image || '';
}

function stripHtml(text) {
  return String(text || '')
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/\s+/g, ' ')
    .trim();
}

function getDescription(item) {
  return stripHtml(item.contentSnippet || item.summary || item.description || item.content || '');
}

async function fetchFeed(source, { maxItems = 35 } = {}) {
  const cacheKey = `rss_v2_${source.url}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  try {
    const feed = await parser.parseURL(source.url);
    const articles = (feed.items || [])
      .filter(item => item.title && (item.link || item.guid))
      .slice(0, maxItems)
      .map(item => ({
        id: Buffer.from(item.link || item.guid || '').toString('base64').slice(0, 22),
        title: item.title?.trim() || '',
        description: getDescription(item),
        url: item.link || item.guid || '',
        image: extractImage(item),
        source: source.name,
        sourceCountry: source.country,
        country: source.country,
        language: source.lang,
        publishedAt: item.isoDate || item.pubDate || null,
        author: item.creator || '',
        category: source.category,
        provider: 'rss'
      }));

    cache.set(cacheKey, articles, 180);
    return articles;
  } catch (err) {
    console.error(`[RSS] ${source.name}: ${err.message}`);
    return [];
  }
}

function sourcesForCategory(category) {
  if (!category || category === 'internacional') {
    return INTERNATIONAL_RSS_SOURCES.filter(s => s.category === 'internacional' || s.category === 'ciencia' || s.category === 'economia' || s.category === 'politica');
  }
  const exact = INTERNATIONAL_RSS_SOURCES.filter(s => s.category === category);
  if (exact.length) return exact;
  return INTERNATIONAL_RSS_SOURCES.filter(s => s.category === 'internacional');
}

async function fetchByCategory(category) {
  const sources = sourcesForCategory(category);
  const results = await Promise.allSettled(sources.map(s => fetchFeed(s)));
  return results.filter(r => r.status === 'fulfilled').flatMap(r => r.value);
}

async function fetchAll() {
  const results = await Promise.allSettled(INTERNATIONAL_RSS_SOURCES.map(s => fetchFeed(s)));
  return results.filter(r => r.status === 'fulfilled').flatMap(r => r.value);
}

async function fetchByCountry(countryCode) {
  const sources = INTERNATIONAL_RSS_SOURCES.filter(s => String(s.country).toUpperCase() === String(countryCode).toUpperCase());
  const results = await Promise.allSettled(sources.map(s => fetchFeed(s)));
  return results.filter(r => r.status === 'fulfilled').flatMap(r => r.value);
}

module.exports = { fetchByCategory, fetchByCountry, fetchAll, fetchFeed, INTERNATIONAL_RSS_SOURCES };
