const CATEGORIES = [
  'world', 'nation', 'business', 'technology', 'entertainment', 'sports', 'science', 'health',
  'society', 'politics_government', 'lifestyle_leisure', 'human_interest',
  'crime_law_justice', 'education', 'environment', 'labour', 'automotive', 'real_estate'
];

const CATEGORY_LABELS = {
  world: 'Internacional',
  nation: 'Países e governos',
  business: 'Economia e negócios',
  technology: 'Tecnologia',
  entertainment: 'Cultura e entretenimento',
  sports: 'Desporto',
  science: 'Ciência',
  health: 'Saúde',
  society: 'Sociedade',
  politics_government: 'Política e governo',
  lifestyle_leisure: 'Estilo de vida e lazer',
  human_interest: 'Interesse humano',
  crime_law_justice: 'Crime, direito e justiça',
  education: 'Educação',
  environment: 'Ambiente',
  labour: 'Trabalho e emprego',
  automotive: 'Automóvel',
  real_estate: 'Imobiliário'
};

// These are search terms, not fabricated provider categories. The public category
// list above contains only categories supported by GNews and/or Currents V2.
const CATEGORY_QUERIES = {
  world: 'international world global affairs geopolitics diplomacy conflicts summits',
  nation: 'national government parliament election public policy country affairs',
  business: 'economy business finance markets companies banking inflation trade investment',
  technology: 'technology software hardware cybersecurity artificial intelligence innovation',
  entertainment: 'entertainment arts culture film television music streaming festivals',
  sports: 'sports football soccer tennis basketball athletics olympics racing',
  science: 'science research discovery space physics biology chemistry scientific study',
  health: 'health medicine hospitals public health disease medical research',
  society: 'society social issues communities demographics social policy',
  politics_government: 'politics government parliament president elections legislation diplomacy',
  lifestyle_leisure: 'lifestyle leisure travel food fashion hobbies',
  human_interest: 'human interest people communities personal stories',
  crime_law_justice: 'law justice courts crime investigations legal regulation judiciary',
  education: 'education schools universities students teachers learning',
  environment: 'environment climate biodiversity conservation pollution sustainability',
  labour: 'labour employment jobs workplace workers hiring careers',
  automotive: 'automotive cars vehicles electric vehicles auto industry transport',
  real_estate: 'real estate property housing mortgages construction commercial property'
};

// Legacy IDs remain accepted by /news so older versions of the frontend keep working.
// /news/categories exposes only the canonical categories above.
const LEGACY_CATEGORY_ALIASES = {
  international: 'world', internacional: 'world', general: 'world',
  economy: 'business', economia: 'business', finances: 'business', finance: 'business',
  financas: 'business', stocks: 'business', shares: 'business', acoes: 'business',
  markets: 'business', mercados: 'business', crypto: 'business', cripto: 'business',
  negocios: 'business', startups: 'business', energy: 'business', energia: 'business',
  agriculture: 'business', agricultura: 'business',
  politics: 'politics_government', politica: 'politics_government', government: 'politics_government',
  desporto: 'sports', sport: 'sports', football: 'sports', soccer: 'sports', futebol: 'sports',
  tecnologia: 'technology', ai: 'technology', inteligencia_artificial: 'technology',
  biotecnologia: 'science', ciencia: 'science',
  saude: 'health', educacao: 'education', ambiente: 'environment',
  carreira: 'labour', emprego: 'labour', empregos: 'labour',
  automovel: 'automotive', imobiliario: 'real_estate',
  viagens: 'lifestyle_leisure', gastronomia: 'lifestyle_leisure', moda: 'lifestyle_leisure',
  seguranca: 'crime_law_justice', direito: 'crime_law_justice',
  cultura: 'entertainment', cinema: 'entertainment', musica: 'entertainment', gaming: 'entertainment'
};

// Do not use BBC feeds or surface its domains. Brazil is excluded from the international feed.
const EXCLUDED_COUNTRIES = new Set(['BR', 'BRA', 'BRAZIL', 'BRASIL']);
const EXCLUDED_DOMAIN_SUFFIXES = ['.com.br', '.br'];
const EXCLUDED_DOMAINS = new Set([
  'bbc.com', 'bbc.co.uk', 'bbci.co.uk', 'bbc.in', 'bbc.co', 'bbc.net',
  'globo.com', 'g1.globo.com', 'uol.com.br', 'folha.uol.com.br', 'estadao.com.br',
  'valor.globo.com', 'terra.com.br', 'metropoles.com', 'cnnbrasil.com.br',
  'band.uol.com.br', 'r7.com', 'gazetadopovo.com.br', 'exame.com', 'abril.com.br',
  'tecmundo.com.br', 'tecnoblog.net'
]);

// Direct RSS publishers intentionally exclude BBC. Categories use canonical feed IDs.
const INTERNATIONAL_RSS_SOURCES = [
  { name: 'DW World', url: 'https://rss.dw.com/rdf/rss-en-world', category: 'world', country: 'DE', lang: 'en' },
  { name: 'DW Business', url: 'https://rss.dw.com/rdf/rss-en-bus', category: 'business', country: 'DE', lang: 'en' },
  { name: 'DW Science', url: 'https://rss.dw.com/rdf/rss-en-science', category: 'science', country: 'DE', lang: 'en' },
  { name: 'DW Environment', url: 'https://rss.dw.com/rdf/rss-en-environment', category: 'environment', country: 'DE', lang: 'en' },
  { name: 'France 24', url: 'https://www.france24.com/en/rss', category: 'world', country: 'FR', lang: 'en' },
  { name: 'Al Jazeera', url: 'https://www.aljazeera.com/xml/rss/all.xml', category: 'world', country: 'QA', lang: 'en' },
  { name: 'UN News', url: 'https://news.un.org/feed/subscribe/en/news/all/rss.xml', category: 'world', country: 'UN', lang: 'en' },
  { name: 'NASA Breaking News', url: 'https://www.nasa.gov/rss/dyn/breaking_news.rss', category: 'science', country: 'US', lang: 'en' },
  { name: 'TechCrunch', url: 'https://techcrunch.com/feed/', category: 'technology', country: 'US', lang: 'en' },
  { name: 'MIT Technology Review', url: 'https://www.technologyreview.com/feed/', category: 'technology', country: 'US', lang: 'en' },
  { name: 'Ars Technica', url: 'https://feeds.arstechnica.com/arstechnica/index', category: 'technology', country: 'US', lang: 'en' },
  { name: 'CoinDesk', url: 'https://www.coindesk.com/arc/outboundfeeds/rss/', category: 'business', country: 'US', lang: 'en' },
  { name: 'The Verge', url: 'https://www.theverge.com/rss/index.xml', category: 'technology', country: 'US', lang: 'en' },
  { name: 'ESPN', url: 'https://www.espn.com/espn/rss/news', category: 'sports', country: 'US', lang: 'en' },
  { name: 'AP News', url: 'https://feeds.apnews.com/rss/apf-topnews', category: 'world', country: 'US', lang: 'en' }
];

// Scores are for ranking only. This list is deliberately not a hard publisher whitelist.
const QUALITY_SOURCES = [
  'reuters.com', 'apnews.com', 'dw.com', 'france24.com', 'aljazeera.com', 'un.org',
  'nasa.gov', 'nature.com', 'science.org', 'who.int', 'ft.com', 'wsj.com',
  'bloomberg.com', 'theguardian.com', 'nytimes.com', 'euronews.com', 'npr.org',
  'cnbc.com', 'espn.com', 'techcrunch.com', 'technologyreview.com', 'arstechnica.com',
  'theverge.com', 'coindesk.com', 'politico.com', 'economist.com'
];

module.exports = {
  CATEGORIES,
  CATEGORY_LABELS,
  CATEGORY_QUERIES,
  LEGACY_CATEGORY_ALIASES,
  EXCLUDED_COUNTRIES,
  EXCLUDED_DOMAIN_SUFFIXES,
  EXCLUDED_DOMAINS,
  INTERNATIONAL_RSS_SOURCES,
  QUALITY_SOURCES
};
