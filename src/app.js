require('dotenv').config();
const express = require('express');
const cors = require('cors');
const newsRoutes = require('./routes/news');
const articleRoutes = require('./routes/article');
const localRoutes = require('./routes/local');

const app = express();
const PORT = Number(process.env.PORT || 3000);
const startedAt = Date.now();

app.disable('x-powered-by');
app.set('trust proxy', 1);
app.use(cors({
  origin: process.env.CORS_ORIGIN || '*',
  methods: ['GET', 'POST', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'x-api-key']
}));
app.use(express.json({ limit: '256kb' }));

// Pequeno limitador em memória para proteger o servidor de chamadas abusivas.
const rateMap = new Map();
app.use((req, res, next) => {
  const windowMs = Number(process.env.RATE_LIMIT_WINDOW_MS || 60000);
  const max = Number(process.env.RATE_LIMIT_MAX || 90);
  const key = req.ip || 'unknown';
  const now = Date.now();
  const entry = rateMap.get(key);
  if (!entry || now - entry.start >= windowMs) {
    rateMap.set(key, { start: now, count: 1 });
    return next();
  }
  entry.count += 1;
  if (entry.count > max) {
    return res.status(429).json({ status: 'error', error: 'Muitas requisições. Tente novamente em breve.' });
  }
  next();
});

app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    version: '2.0.0',
    uptimeSeconds: Math.floor((Date.now() - startedAt) / 1000),
    timestamp: new Date().toISOString(),
    providers: {
      gdelt: true,
      gnews: Boolean(process.env.GNEWS_API_KEY),
      currents: Boolean(process.env.CURRENTS_API_KEY),
      rss: true,
      searxng: true
    }
  });
});

app.get('/ready', (req, res) => {
  res.json({ ready: true, timestamp: new Date().toISOString() });
});

app.get('/', (req, res) => {
  res.json({
    name: 'International News API',
    version: '2.0.0',
    description: 'Agregador internacional com foco em notícias recentes, deduplicação, ranking por frescor e extração limpa de artigos.',
    defaults: {
      language: process.env.DEFAULT_LANGUAGE || 'en',
      maxAgeHours: 24,
      internationalOnly: true
    },
    endpoints: {
      latest: 'GET /news/latest?lang=en&maxAgeHours=12',
      feed: 'GET /news?category=tecnologia&lang=en&page=1&pageSize=20&maxAgeHours=24',
      search: 'GET /news/search?q=artificial%20intelligence&lang=en&maxAgeHours=24',
      article: 'GET /article?url=https://exemplo.com/noticia',
      local: 'GET /local?lat=-8.8368&lon=13.2343',
      categories: 'GET /news/categories',
      sources: 'GET /news/sources',
      health: 'GET /health'
    }
  });
});

function apiKeyMiddleware(req, res, next) {
  const expected = process.env.API_KEY;
  if (!expected) {
    if (process.env.REQUIRE_API_KEY !== 'false') {
      return res.status(503).json({ status: 'error', error: 'API_KEY não configurada' });
    }
    return next();
  }
  const key = req.headers['x-api-key'];
  if (!key || key !== expected) {
    return res.status(401).json({ status: 'error', error: 'Unauthorized', message: 'Header x-api-key inválido ou em falta' });
  }
  next();
}

app.use('/news', apiKeyMiddleware, newsRoutes);
app.use('/article', apiKeyMiddleware, articleRoutes);
app.use('/local', apiKeyMiddleware, localRoutes);

app.use((req, res) => {
  res.status(404).json({ status: 'error', error: 'Endpoint não encontrado' });
});

app.use((err, req, res, next) => {
  console.error('[API]', err);
  if (res.headersSent) return next(err);
  res.status(500).json({ status: 'error', error: 'Erro interno do servidor' });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`International News API v2.0.0 a correr na porta ${PORT}`);
});
