const CATEGORIES = [
  'internacional', 'economia', 'politica', 'desporto',
  'futebol', 'tecnologia', 'ciencia', 'inteligencia-artificial', 'biotecnologia',
  'carreira', 'empregos', 'financas', 'acoes', 'mercados', 'cripto',
  'negocios', 'startups', 'imobiliario', 'energia', 'agricultura', 'ambiente',
  'saude', 'educacao', 'cultura', 'entretenimento', 'cinema', 'musica',
  'gaming', 'automovel', 'viagens', 'gastronomia', 'moda', 'seguranca',
  'direito'
];

const CATEGORY_LABELS = {
  internacional: 'Internacional',
  economia: 'Economia',
  politica: 'Política',
  desporto: 'Desporto',
  futebol: 'Futebol',
  tecnologia: 'Tecnologia',
  ciencia: 'Ciência',
  'inteligencia-artificial': 'Inteligência Artificial',
  biotecnologia: 'Biotecnologia',
  carreira: 'Carreira',
  empregos: 'Empregos',
  financas: 'Finanças',
  acoes: 'Ações',
  mercados: 'Mercados',
  cripto: 'Cripto',
  negocios: 'Negócios',
  startups: 'Startups',
  imobiliario: 'Imobiliário',
  energia: 'Energia',
  agricultura: 'Agricultura',
  ambiente: 'Ambiente',
  saude: 'Saúde',
  educacao: 'Educação',
  cultura: 'Cultura',
  entretenimento: 'Entretenimento',
  cinema: 'Cinema',
  musica: 'Música',
  gaming: 'Gaming',
  automovel: 'Automóvel',
  viagens: 'Viagens',
  gastronomia: 'Gastronomia',
  moda: 'Moda',
  seguranca: 'Segurança',
  direito: 'Direito'
};

// Termos usados pelo GDELT/SearXNG para aumentar a precisão por tópico.
const CATEGORY_QUERIES = {
  internacional: 'world international geopolitics diplomacy conflict government election treaty summit',
  economia: 'economy economic growth inflation GDP recession central bank interest rates',
  politica: 'politics government parliament president election diplomacy policy legislation',
  desporto: 'sports athletics olympics tennis basketball rugby formula 1 cycling',
  futebol: 'football soccer champions league world cup premier league la liga serie a bundesliga',
  tecnologia: 'technology software hardware semiconductor cloud cybersecurity smartphone internet',
  ciencia: 'science research discovery physics chemistry astronomy climate biology space',
  'inteligencia-artificial': 'artificial intelligence AI machine learning generative AI neural networks model',
  biotecnologia: 'biotechnology genomics gene editing CRISPR biopharma biotech',
  carreira: 'career workplace professional development leadership skills remote work',
  empregos: 'jobs hiring employment recruitment labor market vacancies careers',
  financas: 'finance banking personal finance investment wealth credit debt financial markets',
  acoes: 'stocks shares equities earnings IPO dividend stock market companies',
  mercados: 'markets commodities forex bonds yields indices investors',
  cripto: 'cryptocurrency bitcoin ethereum blockchain digital assets crypto market',
  negocios: 'business companies corporate strategy management mergers acquisitions',
  startups: 'startups venture capital funding founders unicorn innovation accelerator',
  imobiliario: 'real estate property housing mortgage construction commercial real estate',
  energia: 'energy oil gas electricity renewables solar wind nuclear power',
  agricultura: 'agriculture farming crops food security fertilizer commodities agribusiness',
  ambiente: 'environment climate biodiversity conservation pollution sustainability',
  saude: 'health medicine hospitals public health disease vaccines medical research',
  educacao: 'education schools universities students teachers learning policy',
  cultura: 'culture arts heritage literature museum visual arts cultural events',
  entretenimento: 'entertainment celebrities television streaming awards shows',
  cinema: 'film movie cinema hollywood festival box office director actor',
  musica: 'music album artist singer concert festival streaming music industry',
  gaming: 'gaming video games esports console PC game studio release',
  automovel: 'automotive cars vehicles electric vehicles EV auto industry',
  viagens: 'travel tourism aviation hotels destinations tourism industry',
  gastronomia: 'food gastronomy restaurants chefs cuisine culinary dining',
  moda: 'fashion designers luxury apparel runway brands clothing',
  seguranca: 'security cybersecurity defense safety terrorism crime border',
  direito: 'law legal courts justice regulation legislation lawyers supreme court'
};

// O produto é internacional por definição. Brasil fica explicitamente excluído
// para evitar que uma preferência por idioma português volte a puxar fontes brasileiras.
const EXCLUDED_COUNTRIES = new Set([
  'BR', 'BRA', 'Brazil', 'Brasil'
]);

const EXCLUDED_DOMAIN_SUFFIXES = [
  '.com.br', '.br'
];

const EXCLUDED_DOMAINS = new Set([
  'g1.globo.com', 'globo.com', 'uol.com.br', 'folha.uol.com.br', 'estadao.com.br',
  'valor.globo.com', 'terra.com.br', 'metropoles.com', 'cnnbrasil.com.br',
  'band.uol.com.br', 'r7.com', 'gazetadopovo.com.br', 'exame.com', 'abril.com.br',
  'tecmundo.com.br', 'tecnoblog.net'
]);

const INTERNATIONAL_RSS_SOURCES = [
  { name: 'BBC World', url: 'https://feeds.bbci.co.uk/news/world/rss.xml', category: 'internacional', country: 'GB', lang: 'en' },
  { name: 'BBC Business', url: 'https://feeds.bbci.co.uk/news/business/rss.xml', category: 'economia', country: 'GB', lang: 'en' },
  { name: 'BBC Technology', url: 'https://feeds.bbci.co.uk/news/technology/rss.xml', category: 'tecnologia', country: 'GB', lang: 'en' },
  { name: 'BBC Science & Environment', url: 'https://feeds.bbci.co.uk/news/science_and_environment/rss.xml', category: 'ciencia', country: 'GB', lang: 'en' },
  { name: 'BBC Health', url: 'https://feeds.bbci.co.uk/news/health/rss.xml', category: 'saude', country: 'GB', lang: 'en' },
  { name: 'BBC Politics', url: 'https://feeds.bbci.co.uk/news/politics/rss.xml', category: 'politica', country: 'GB', lang: 'en' },
  { name: 'BBC Sport', url: 'https://feeds.bbci.co.uk/sport/rss.xml', category: 'desporto', country: 'GB', lang: 'en' },
  { name: 'BBC Football', url: 'https://feeds.bbci.co.uk/sport/football/rss.xml', category: 'futebol', country: 'GB', lang: 'en' },
  { name: 'BBC Entertainment', url: 'https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml', category: 'entretenimento', country: 'GB', lang: 'en' },
  { name: 'BBC Travel', url: 'https://www.bbc.com/travel/feed', category: 'viagens', country: 'GB', lang: 'en' },
  { name: 'DW World', url: 'https://rss.dw.com/rdf/rss-en-world', category: 'internacional', country: 'DE', lang: 'en' },
  { name: 'DW Business', url: 'https://rss.dw.com/rdf/rss-en-bus', category: 'negocios', country: 'DE', lang: 'en' },
  { name: 'DW Science', url: 'https://rss.dw.com/rdf/rss-en-science', category: 'ciencia', country: 'DE', lang: 'en' },
  { name: 'DW Environment', url: 'https://rss.dw.com/rdf/rss-en-environment', category: 'ambiente', country: 'DE', lang: 'en' },
  { name: 'France 24 World', url: 'https://www.france24.com/en/rss', category: 'internacional', country: 'FR', lang: 'en' },
  { name: 'Al Jazeera', url: 'https://www.aljazeera.com/xml/rss/all.xml', category: 'internacional', country: 'QA', lang: 'en' },
  { name: 'UN News', url: 'https://news.un.org/feed/subscribe/en/news/all/rss.xml', category: 'internacional', country: 'UN', lang: 'en' },
  { name: 'NASA Breaking News', url: 'https://www.nasa.gov/rss/dyn/breaking_news.rss', category: 'ciencia', country: 'US', lang: 'en' },
  { name: 'TechCrunch', url: 'https://techcrunch.com/feed/', category: 'tecnologia', country: 'US', lang: 'en' },
  { name: 'MIT Technology Review', url: 'https://www.technologyreview.com/feed/', category: 'tecnologia', country: 'US', lang: 'en' },
  { name: 'Ars Technica', url: 'https://feeds.arstechnica.com/arstechnica/index', category: 'tecnologia', country: 'US', lang: 'en' },
  { name: 'CoinDesk', url: 'https://www.coindesk.com/arc/outboundfeeds/rss/', category: 'cripto', country: 'US', lang: 'en' },
  { name: 'The Verge', url: 'https://www.theverge.com/rss/index.xml', category: 'tecnologia', country: 'US', lang: 'en' },
  { name: 'ESPN', url: 'https://www.espn.com/espn/rss/news', category: 'desporto', country: 'US', lang: 'en' },
  { name: 'AP News', url: 'https://feeds.apnews.com/rss/apf-topnews', category: 'internacional', country: 'US', lang: 'en' }
];

const QUALITY_SOURCES = [
  'reuters.com', 'apnews.com', 'bbc.com', 'bbc.co.uk', 'dw.com', 'france24.com',
  'aljazeera.com', 'un.org', 'nasa.gov', 'nature.com', 'science.org', 'who.int',
  'ft.com', 'wsj.com', 'bloomberg.com', 'theguardian.com', 'nytimes.com'
];

module.exports = {
  CATEGORIES,
  CATEGORY_LABELS,
  CATEGORY_QUERIES,
  EXCLUDED_COUNTRIES,
  EXCLUDED_DOMAIN_SUFFIXES,
  EXCLUDED_DOMAINS,
  INTERNATIONAL_RSS_SOURCES,
  QUALITY_SOURCES
};
