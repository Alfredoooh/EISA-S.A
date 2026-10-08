const Parser = require('rss-parser');
const cache = require('../utils/cache');
const { RSS_SOURCES, filterInternationalQuality, normalizeDomain } = require('../config/newsPolicy');

const parser = new Parser({
  timeout: 12000,
  headers: {
    'User-Agent': 'InternationalNewsAPI/3.0 (+https://eisa-sa-servers.onrender.com)'
  },
  customFields: {
    item: [
      ['media:content', 'mediaContent'],
      ['media:thumbnail', 'mediaThumbnail'],
      ['enclosure', 'enclosure'],
      ['dc:creator', 'creator']
    ]
  }
});

function extractImage(item) {
  return (
    item.mediaContent?.['$']?.url ||
    item.mediaThumbnail?.['$']?.url ||
    item.enclosure?.url ||
    item['media:content']?.['$']?.url ||
    item['media:thumbnail']?.['$']?.url ||
    item.itunes?.image ||
    ''
  );
}

async function fetchFeed(source) {
  const cacheKey = `rss_${source.url}`;
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  try {
    const feed = await parser.parseURL(source.url);
    const articles = (feed.items || [])
      .filter(item => item.title && (item.link || item.guid))
      .slice(0, 30)
      .map(item => ({
        id: item.link || item.guid,
        title: item.title.trim(),
        description: item.contentSnippet?.trim() || item.summary?.trim() || '',
        url: item.link || item.guid || '',
        image: extractImage(item),
        source: source.name,
        sourceUrl: `https://${source.domain}`,
        domain: normalizeDomain(source.domain),
        category: source.category,
        language: source.lang,
        publishedAt: item.isoDate || item.pubDate || null,
        provider: 'rss'
      }));

    const quality = filterInternationalQuality(articles, MIN_SOURCE_QUALITY);
    cache.set(cacheKey, quality, 180);
    return quality;
  } catch (err) {
    console.error(`[RSS] ${source.name}: ${err.message}`);
    return [];
  }
}

async function fetchByCategory(category) {
  const sources = RSS_SOURCES.filter(s => s.category === category);
  const results = await Promise.allSettled(sources.map(fetchFeed));
  return results.filter(r => r.status === 'fulfilled').flatMap(r => r.value);
}

async function fetchAll() {
  const results = await Promise.allSettled(RSS_SOURCES.map(fetchFeed));
  return results.filter(r => r.status === 'fulfilled').flatMap(r => r.value);
}

module.exports = { fetchByCategory, fetchAll, RSS_SOURCES };
