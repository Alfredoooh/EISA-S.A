require('dotenv').config();
const express       = require('express');
const cors          = require('cors');
const newsRoutes    = require('./routes/news');
const articleRoutes = require('./routes/article');

const app       = express();
const PORT      = Number(process.env.PORT || 3000);
const startedAt = Date.now();

app.disable('x-powered-by');
app.set('trust proxy', 1);

const corsOptions = {
  origin:         process.env.CORS_ORIGIN || '*',
  methods:        ['GET', 'POST', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'x-api-key'],
};

app.use(cors(corsOptions));
app.options('/(.*)', cors(corsOptions));
app.use(express.json({ limit: '256kb' }));

const rateMap = new Map();
app.use((req, res, next) => {
  const windowMs = Number(process.env.RATE_LIMIT_WINDOW_MS || 60000);
  const max      = Number(process.env.RATE_LIMIT_MAX || 120);
  const key      = req.ip || 'unknown';
  const now      = Date.now();
  const entry    = rateMap.get(key);
  if (!entry || now - entry.start >= windowMs) {
    rateMap.set(key, { start: now, count: 1 });
    return next();
  }
  entry.count++;
  if (entry.count > max) return res.status(429).json({ status: 'error', error: 'Too many requests' });
  next();
});

app.get('/health', (_req, res) => res.json({
  status:        'ok',
  version:       '3.0.0',
  language:      'en',
  uptimeSeconds: Math.floor((Date.now() - startedAt) / 1000),
  timestamp:     new Date().toISOString(),
  providers: {
    guardian: Boolean(process.env.GUARDIAN_API_KEY),
    nytimes:  Boolean(process.env.NYT_API_KEY),
    gnews:    Boolean(process.env.GNEWS_API_KEY),
    newsdata: Boolean(process.env.NEWSDATA_API_KEY),
  },
}));

app.get('/ready', (_req, res) => res.json({ ready: true, timestamp: new Date().toISOString() }));

app.get('/', (_req, res) => res.json({
  name:     'News Aggregator API',
  version:  '3.0.0',
  language: 'en',
  sources:  ['The Guardian', 'The New York Times', 'GNews', 'NewsData.io'],
  endpoints: {
    latest:     'GET /news/latest?maxAgeHours=12&page=1&pageSize=20',
    feed:       'GET /news?category=world&page=1&pageSize=20&maxAgeHours=24',
    search:     'GET /news/search?q=artificial+intelligence',
    article:    'GET /article?url=https://...',
    categories: 'GET /news/categories',
    sources:    'GET /news/sources',
    health:     'GET /health',
  },
}));

function apiKeyMiddleware(req, res, next) {
  const expected = process.env.API_KEY;
  if (!expected) {
    if (process.env.REQUIRE_API_KEY !== 'false')
      return res.status(503).json({ status: 'error', error: 'API_KEY not configured' });
    return next();
  }
  if (req.headers['x-api-key'] !== expected)
    return res.status(401).json({ status: 'error', error: 'Unauthorized' });
  next();
}

app.use('/news',    apiKeyMiddleware, newsRoutes);
app.use('/article', apiKeyMiddleware, articleRoutes);

app.use((_req, res) => res.status(404).json({ status: 'error', error: 'Endpoint not found' }));
app.use((err, _req, res, _next) => {
  console.error('[API]', err);
  if (!res.headersSent) res.status(500).json({ status: 'error', error: 'Internal server error' });
});

app.listen(PORT, '0.0.0.0', () =>
  console.log(`News Aggregator API v3.0.0 running on port ${PORT} — language: en`)
);