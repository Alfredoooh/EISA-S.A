const BLOCKED_DOMAINS = new Set([
  'bbc.com', 'www.bbc.com', 'bbc.co.uk', 'www.bbc.co.uk', 'bbci.co.uk',
  'www.bbci.co.uk', 'bbc.in', 'bbc.co', 'bbc.net'
]);

// Publishers selected for editorial reliability and generally usable article imagery.
// The feed is intentionally whitelist-based: unknown publishers are rejected.
const QUALITY_SOURCES = {
  // Tier 1 — wire services / major international public broadcasters
  'reuters.com': 100,
  'apnews.com': 100,
  'aljazeera.com': 95,
  'dw.com': 95,
  'france24.com': 95,
  'euronews.com': 90,
  'npr.org': 90,
  'pbs.org': 90,
  'theguardian.com': 90,
  'nytimes.com': 90,
  'washingtonpost.com': 90,
  'cnn.com': 88,
  'abcnews.go.com': 88,
  'cbsnews.com': 88,
  'nbcnews.com': 88,
  'usatoday.com': 85,
  'time.com': 85,
  'newsweek.com': 82,
  // Business / finance
  'ft.com': 98,
  'bloomberg.com': 98,
  'wsj.com': 96,
  'cnbc.com': 92,
  'economist.com': 94,
  'fortune.com': 84,
  'marketwatch.com': 84,
  'forbes.com': 82,
  // Technology
  'arstechnica.com': 92,
  'theverge.com': 90,
  'techcrunch.com': 88,
  'wired.com': 90,
  'technologyreview.com': 94,
  'engadget.com': 82,
  'zdnet.com': 80,
  // Science / health
  'nature.com': 98,
  'science.org': 98,
  'scientificamerican.com': 90,
  'newscientist.com': 88,
  'statnews.com': 88,
  'nih.gov': 98,
  'who.int': 98,
  'cdc.gov': 98,
  'nasa.gov': 98,
  'noaa.gov': 96,
  // Sport
  'espn.com': 88,
  'skysports.com': 88,
  'theathletic.com': 90,
  'olympics.com': 94,
  'formula1.com': 94,
  // Automotive
  'autonews.com': 88,
  'motor1.com': 82,
  'caranddriver.com': 84,
  // Travel / culture / lifestyle
  'nationalgeographic.com': 94,
  'smithsonianmag.com': 90,
  'vogue.com': 85,
  'variety.com': 90,
  'hollywoodreporter.com': 90,
};

const GNEWS_CATEGORIES = {
  world: {
    id: 'world',
    label: 'Internacional',
    providers: ['gnews'],
    gnews: 'world'
  },
  business: {
    id: 'business',
    label: 'Negócios',
    providers: ['gnews', 'currents'],
    gnews: 'business',
    currents: 'economy_business_finance'
  },
  technology: {
    id: 'technology',
    label: 'Tecnologia',
    providers: ['gnews', 'currents'],
    gnews: 'technology',
    currents: 'science_technology'
  },
  entertainment: {
    id: 'entertainment',
    label: 'Entretenimento',
    providers: ['gnews', 'currents'],
    gnews: 'entertainment',
    currents: 'arts_culture_entertainment'
  },
  sports: {
    id: 'sports',
    label: 'Desporto',
    providers: ['gnews', 'currents'],
    gnews: 'sports',
    currents: 'sport'
  },
  science: {
    id: 'science',
    label: 'Ciência',
    providers: ['gnews', 'currents'],
    gnews: 'science',
    currents: 'science_technology'
  },
  health: {
    id: 'health',
    label: 'Saúde',
    providers: ['gnews', 'currents'],
    gnews: 'health',
    currents: 'health'
  },
  society: {
    id: 'society',
    label: 'Sociedade',
    providers: ['currents'],
    currents: 'society'
  },
  politics_government: {
    id: 'politics_government',
    label: 'Política e Governo',
    providers: ['currents'],
    currents: 'politics_government'
  },
  lifestyle_leisure: {
    id: 'lifestyle_leisure',
    label: 'Estilo de Vida e Lazer',
    providers: ['currents'],
    currents: 'lifestyle_leisure'
  },
  human_interest: {
    id: 'human_interest',
    label: 'Interesse Humano',
    providers: ['currents'],
    currents: 'human_interest'
  },
  crime_law_justice: {
    id: 'crime_law_justice',
    label: 'Crime, Direito e Justiça',
    providers: ['currents'],
    currents: 'crime_law_justice'
  },
  education: {
    id: 'education',
    label: 'Educação',
    providers: ['currents'],
    currents: 'education'
  },
  environment: {
    id: 'environment',
    label: 'Ambiente',
    providers: ['currents'],
    currents: 'environment'
  },
  labour: {
    id: 'labour',
    label: 'Trabalho',
    providers: ['currents'],
    currents: 'labour'
  },
  automotive: {
    id: 'automotive',
    label: 'Automóvel',
    providers: ['currents'],
    currents: 'automotive'
  },
  real_estate: {
    id: 'real_estate',
    label: 'Imobiliário',
    providers: ['currents'],
    currents: 'real_estate'
  }
};

const RSS_SOURCES = [
  { name: 'Al Jazeera', domain: 'aljazeera.com', url: 'https://www.aljazeera.com/xml/rss/all.xml', category: 'world', lang: 'en' },
  { name: 'Deutsche Welle', domain: 'dw.com', url: 'https://rss.dw.com/rdf/rss-en-all', category: 'world', lang: 'en' },
  { name: 'France 24', domain: 'france24.com', url: 'https://www.france24.com/en/rss', category: 'world', lang: 'en' },
  { name: 'Euronews', domain: 'euronews.com', url: 'https://www.euronews.com/rss', category: 'world', lang: 'en' },
  { name: 'The Guardian', domain: 'theguardian.com', url: 'https://www.theguardian.com/world/rss', category: 'world', lang: 'en' },
  { name: 'The New York Times', domain: 'nytimes.com', url: 'https://rss.nytimes.com/services/xml/rss/nyt/World.xml', category: 'world', lang: 'en' },
  { name: 'NPR', domain: 'npr.org', url: 'https://feeds.npr.org/1001/rss.xml', category: 'world', lang: 'en' },
  { name: 'CNBC', domain: 'cnbc.com', url: 'https://www.cnbc.com/id/100003114/device/rss/rss.html', category: 'business', lang: 'en' },
  { name: 'NASA', domain: 'nasa.gov', url: 'https://www.nasa.gov/rss/dyn/breaking_news.rss', category: 'science', lang: 'en' },
  { name: 'The Verge', domain: 'theverge.com', url: 'https://www.theverge.com/rss/index.xml', category: 'technology', lang: 'en' },
  { name: 'Ars Technica', domain: 'arstechnica.com', url: 'https://feeds.arstechnica.com/arstechnica/index', category: 'technology', lang: 'en' },
  { name: 'Nature', domain: 'nature.com', url: 'https://www.nature.com/nature.rss', category: 'science', lang: 'en' },
  { name: 'Variety', domain: 'variety.com', url: 'https://variety.com/feed/', category: 'entertainment', lang: 'en' },
  { name: 'ESPN', domain: 'espn.com', url: 'https://www.espn.com/espn/rss/news', category: 'sports', lang: 'en' },
  { name: 'Formula 1', domain: 'formula1.com', url: 'https://www.formula1.com/en/latest/all.xml', category: 'sports', lang: 'en' }
];

function normalizeDomain(value) {
  if (!value) return '';
  try {
    const raw = /^https?:\/\//i.test(value) ? value : `https://${value}`;
    return new URL(raw).hostname.toLowerCase().replace(/^www\./, '');
  } catch {
    return String(value).toLowerCase().replace(/^www\./, '').split('/')[0];
  }
}

function isBlockedDomain(domain) {
  const d = normalizeDomain(domain);
  if (!d) return false;
  for (const blocked of BLOCKED_DOMAINS) {
    if (d === blocked || d.endsWith(`.${blocked}`)) return true;
  }
  return false;
}

function qualityScore(value) {
  const d = normalizeDomain(value);
  if (!d || isBlockedDomain(d)) return 0;
  if (QUALITY_SOURCES[d]) return QUALITY_SOURCES[d];
  let best = 0;
  for (const [base, score] of Object.entries(QUALITY_SOURCES)) {
    if (d.endsWith(`.${base}`)) best = Math.max(best, score);
  }
  return best;
}

function isQualitySource(article, minScore = 80) {
  const candidates = [article.sourceUrl, article.url, article.source, article.domain];
  return candidates.some(v => qualityScore(v) >= minScore && !isBlockedDomain(v));
}

function filterInternationalQuality(articles, minScore = 80) {
  return (articles || []).filter(article => {
    if (!article || !article.url || !article.title) return false;
    const domain = normalizeDomain(article.url);
    if (isBlockedDomain(domain)) return false;
    if (!isQualitySource(article, minScore)) return false;
    if (article.image) {
      const imageDomain = normalizeDomain(article.image);
      if (isBlockedDomain(imageDomain)) return false;
    }
    return true;
  }).map(article => ({
    ...article,
    qualityScore: Math.max(
      qualityScore(article.sourceUrl),
      qualityScore(article.url),
      qualityScore(article.source)
    )
  }));
}

const CATEGORY_LIST = Object.values(GNEWS_CATEGORIES);

module.exports = {
  BLOCKED_DOMAINS,
  QUALITY_SOURCES,
  RSS_SOURCES,
  GNEWS_CATEGORIES,
  CATEGORY_LIST,
  normalizeDomain,
  qualityScore,
  isBlockedDomain,
  isQualitySource,
  filterInternationalQuality
};
