const Parser = require('rss-parser');
const cache = require('../utils/cache');

const parser = new Parser({
  timeout: 10000,
  headers: {
    'User-Agent': 'Mozilla/5.0 NewsAPI/1.0'
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

// Fontes RSS por país e categoria
const RSS_SOURCES = {
  AO: [
    { name: 'Jornal de Angola', url: 'https://jornaldeangola.sapo.ao/feed/', category: 'general', country: 'AO', lang: 'pt' },
    { name: 'Expansão', url: 'https://expansao.co.ao/feed/', category: 'business', country: 'AO', lang: 'pt' },
    { name: 'Novo Jornal', url: 'https://novojornal.co.ao/feed/', category: 'general', country: 'AO', lang: 'pt' },
    { name: 'VOA Português', url: 'https://www.voaportugues.com/api/zmgqeiqpiq', category: 'general', country: 'AO', lang: 'pt' },
    { name: 'Deutsche Welle AO', url: 'https://rss.dw.com/rdf/rss-por-all', category: 'general', country: 'AO', lang: 'pt' },
  ],
  PT: [
    { name: 'Público', url: 'https://feeds.feedburner.com/PublicoRSS', category: 'general', country: 'PT', lang: 'pt' },
    { name: 'RTP Notícias', url: 'https://www.rtp.pt/noticias/rss/ultimas', category: 'general', country: 'PT', lang: 'pt' },
    { name: 'Observador', url: 'https://observador.pt/feed/', category: 'general', country: 'PT', lang: 'pt' },
    { name: 'Jornal de Negócios', url: 'https://www.jornaldenegocios.pt/rss/rss.aspx', category: 'business', country: 'PT', lang: 'pt' },
    { name: 'Sapo Desporto', url: 'https://desporto.sapo.pt/rss', category: 'sports', country: 'PT', lang: 'pt' },
    { name: 'Notícias ao Minuto', url: 'https://www.noticiasaominuto.com/rss/ultima-hora', category: 'general', country: 'PT', lang: 'pt' },
  ],
  BR: [
    { name: 'G1', url: 'https://g1.globo.com/rss/g1/', category: 'general', country: 'BR', lang: 'pt' },
    { name: 'UOL', url: 'https://rss.uol.com.br/feed/noticias.xml', category: 'general', country: 'BR', lang: 'pt' },
    { name: 'Folha de S.Paulo', url: 'https://feeds.folha.uol.com.br/emcimadahora/rss091.xml', category: 'general', country: 'BR', lang: 'pt' },
    { name: 'Tecnoblog', url: 'https://tecnoblog.net/feed/', category: 'technology', country: 'BR', lang: 'pt' },
  ],
  MZ: [
    { name: 'O País', url: 'https://opais.co.mz/feed/', category: 'general', country: 'MZ', lang: 'pt' },
    { name: 'Club of Mozambique', url: 'https://clubofmozambique.com/feed/', category: 'general', country: 'MZ', lang: 'en' },
  ],
  GLOBAL: [
    { name: 'BBC Português', url: 'https://feeds.bbci.co.uk/portuguese/rss.xml', category: 'general', country: 'GB', lang: 'pt' },
    { name: 'DW Português', url: 'https://rss.dw.com/rdf/rss-por-all', category: 'general', country: 'DE', lang: 'pt' },
    { name: 'Reuters', url: 'https://feeds.reuters.com/reuters/topNews', category: 'general', country: 'US', lang: 'en' },
    { name: 'Al Jazeera PT', url: 'https://www.aljazeera.com/xml/rss/all.xml', category: 'general', country: 'QA', lang: 'en' },
  ]
};

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
      .filter(item => item.title)
      .slice(0, 20)
      .map(item => ({
        id: Buffer.from(item.link || item.guid || '').toString('base64').slice(0, 16),
        title: item.title?.trim() || '',
        description: item.contentSnippet?.trim() || item.summary?.trim() || '',
        url: item.link || item.guid || '',
        image: extractImage(item),
        source: source.name,
        category: source.category,
        country: source.country,
        language: source.lang,
        publishedAt: item.isoDate || item.pubDate || null,
        provider: 'rss'
      }));

    cache.set(cacheKey, articles, 180); // cache 3 min para RSS
    return articles;

  } catch (err) {
    console.error(`[RSS] ${source.name}: ${err.message}`);
    return [];
  }
}

async function fetchByCountry(countryCode) {
  const sources = RSS_SOURCES[countryCode] || [];
  const globalSources = RSS_SOURCES.GLOBAL || [];
  const allSources = [...sources, ...globalSources];

  const results = await Promise.allSettled(allSources.map(s => fetchFeed(s)));
  return results
    .filter(r => r.status === 'fulfilled')
    .flatMap(r => r.value);
}

async function fetchByCategory(category) {
  const allSources = Object.values(RSS_SOURCES).flat();
  const filtered = allSources.filter(s =>
    s.category === category || category === 'general'
  );

  const results = await Promise.allSettled(filtered.map(s => fetchFeed(s)));
  return results
    .filter(r => r.status === 'fulfilled')
    .flatMap(r => r.value);
}

async function fetchAll() {
  const allSources = Object.values(RSS_SOURCES).flat();
  const results = await Promise.allSettled(allSources.map(s => fetchFeed(s)));
  return results
    .filter(r => r.status === 'fulfilled')
    .flatMap(r => r.value);
}

module.exports = { fetchByCountry, fetchByCategory, fetchAll, RSS_SOURCES };
