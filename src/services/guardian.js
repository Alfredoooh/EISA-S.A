// src/config.js
const CATEGORIES = [
  'world',               // Guardian ✅ NYT ✅ GNews ✅ NewsData ✅
  'business',            // Guardian ✅ NYT ✅ GNews ✅ NewsData ✅
  'technology',          // Guardian ✅ NYT ✅ GNews ✅ NewsData ✅
  'science',             // Guardian ✅ NYT ✅ GNews ✅ NewsData ✅
  'health',              // Guardian ✅ NYT ✅ GNews ✅ NewsData ✅
  'sports',              // Guardian ✅ NYT ✅ GNews ✅ NewsData ✅
  'entertainment',       // Guardian ✅ NYT ✅ GNews ✅ NewsData ✅
  'politics_government', // Guardian ✅ NYT ✅ NewsData ✅
  'environment',         // Guardian ✅ NYT ✅ NewsData ✅
  'education',           // Guardian ✅ NYT ✅ NewsData ✅
  'crime_law_justice',   // Guardian ✅ NewsData ✅
  'lifestyle_leisure',   // Guardian ✅ NewsData ✅
  'food',                // Guardian ✅ NewsData ✅ (único que tem food nativo)
  'travel',              // Guardian ✅ NYT ✅ NewsData (tourism)
];

const CATEGORY_LABELS = {
  world:               'World',
  business:            'Business & Economy',
  technology:          'Technology',
  science:             'Science',
  health:              'Health',
  sports:              'Sports',
  entertainment:       'Entertainment & Culture',
  politics_government: 'Politics & Government',
  environment:         'Environment',
  education:           'Education',
  crime_law_justice:   'Crime, Law & Justice',
  lifestyle_leisure:   'Lifestyle & Leisure',
  food:                'Food',
  travel:              'Travel',
};

// Keywords used for NYT Article Search API and NewsData.io when no native category exists.
const CATEGORY_QUERIES = {
  world:               'international world global affairs geopolitics diplomacy',
  business:            'economy business finance markets companies banking trade',
  technology:          'technology software hardware cybersecurity artificial intelligence',
  science:             'science research discovery space physics biology chemistry',
  health:              'health medicine public health disease medical research',
  sports:              'sports football soccer tennis basketball athletics olympics',
  entertainment:       'entertainment film television music arts culture festivals',
  politics_government: 'politics government parliament president elections legislation',
  environment:         'environment climate biodiversity conservation pollution sustainability',
  education:           'education schools universities students teachers learning',
  crime_law_justice:   'crime law justice courts investigation legal judiciary',
  lifestyle_leisure:   'lifestyle leisure hobbies wellness mental health fitness',
  food:                'food cuisine restaurants cooking recipes gastronomy',
  travel:              'travel tourism destinations trips holidays places visit',
};

const LEGACY_CATEGORY_ALIASES = {
  internacional: 'world',     general: 'world',       nation: 'world',
  economy:       'business',  economia: 'business',   finance: 'business',
  financas:      'business',  stocks: 'business',     markets: 'business',
  mercados:      'business',  crypto: 'business',     negocios: 'business',
  energy:        'business',  startups: 'business',
  politics:      'politics_government', politica: 'politics_government', government: 'politics_government',
  desporto:      'sports',    sport: 'sports',        football: 'sports',
  soccer:        'sports',    futebol: 'sports',
  tecnologia:    'technology', ai: 'technology',
  ciencia:       'science',   saude: 'health',
  educacao:      'education', ambiente: 'environment',
  carreira:      'lifestyle_leisure', viagens: 'travel',
  gastronomia:   'food',      culinaria: 'food',
  seguranca:     'crime_law_justice', direito: 'crime_law_justice',
  cultura:       'entertainment', cinema: 'entertainment',
  musica:        'entertainment', gaming: 'entertainment',
  moda:          'lifestyle_leisure',
};

// Language is always English — translation is handled downstream by the app.
const LANG = 'en';

const EXCLUDED_DOMAINS = new Set([
  'bbc.com', 'bbc.co.uk', 'bbci.co.uk', 'bbc.in', 'bbc.co', 'bbc.net',
  'globo.com', 'g1.globo.com', 'uol.com.br', 'folha.uol.com.br', 'estadao.com.br',
  'valor.globo.com', 'terra.com.br', 'metropoles.com', 'cnnbrasil.com.br',
  'band.uol.com.br', 'r7.com', 'gazetadopovo.com.br', 'exame.com', 'abril.com.br',
  'tecmundo.com.br', 'tecnoblog.net',
]);
const EXCLUDED_DOMAIN_SUFFIXES = ['.com.br', '.br'];
const EXCLUDED_COUNTRIES = new Set(['BR', 'BRA', 'BRAZIL', 'BRASIL']);

// Source influence weights used in ranking. Higher = more influential.
const QUALITY_SOURCES = {
  'reuters.com':           100,
  'apnews.com':            100,
  'nytimes.com':            95,
  'theguardian.com':        90,
  'ft.com':                 98,
  'bloomberg.com':          97,
  'wsj.com':                96,
  'economist.com':          94,
  'nature.com':             98,
  'science.org':            98,
  'who.int':                98,
  'nasa.gov':               97,
  'noaa.gov':               95,
  'nih.gov':                97,
  'cdc.gov':                97,
  'techcrunch.com':         88,
  'theverge.com':           88,
  'arstechnica.com':        90,
  'wired.com':              89,
  'technologyreview.com':   93,
  'scientificamerican.com': 90,
  'espn.com':               87,
  'theathletic.com':        89,
  'formula1.com':           93,
  'variety.com':            88,
  'politico.com':           90,
  'cnbc.com':               89,
  'washingtonpost.com':     89,
  'nationalgeographic.com': 93,
};

module.exports = {
  CATEGORIES,
  CATEGORY_LABELS,
  CATEGORY_QUERIES,
  LEGACY_CATEGORY_ALIASES,
  LANG,
  EXCLUDED_DOMAINS,
  EXCLUDED_DOMAIN_SUFFIXES,
  EXCLUDED_COUNTRIES,
  QUALITY_SOURCES,
};