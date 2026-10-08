require('dotenv').config();
const express = require('express');
const cors = require('cors');
const newsRoutes = require('./routes/news');
const articleRoutes = require('./routes/article');
const localRoutes = require('./routes/local');

const app = express();
const PORT = process.env.PORT || 3000;

app.disable('x-powered-by');
app.use(cors());
app.use(express.json({ limit: '100kb' }));

app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString(), mode: 'international_only' });
});

app.get('/ready', (req, res) => {
  const ready = Boolean(process.env.API_KEY && process.env.GNEWS_API_KEY && process.env.CURRENTS_API_KEY);
  res.status(ready ? 200 : 503).json({ status: ready ? 'ready' : 'not_ready' });
});

app.get('/', (req, res) => {
  res.json({
    name: 'International News API',
    version: '3.0.0',
    mode: 'international_only',
    policies: {
      blockedSources: ['BBC'],
      minimumQualityScore: 80,
      defaultFreshnessHours: 48,
      language: 'en',
      countryFilter: 'disabled'
    },
    endpoints: {
      news: 'GET /news?category=world&page=1',
      latest: 'GET /news/latest?page=1',
      search: 'GET /news/search?q=climate',
      article: 'GET /article?url=https://...',
      categories: 'GET /news/categories',
      sources: 'GET /news/sources',
      health: 'GET /health',
      ready: 'GET /ready'
    }
  });
});

app.use((req, res, next) => {
  const key = req.headers['x-api-key'];
  if (!key || key !== process.env.API_KEY) {
    return res.status(401).json({ error: 'Unauthorized', message: 'Header x-api-key inválido ou em falta' });
  }
  next();
});

app.use('/news', newsRoutes);
app.use('/article', articleRoutes);
app.use('/local', localRoutes);

app.use((req, res) => res.status(404).json({ error: 'Not found' }));

app.listen(PORT, () => console.log(`International News API 3.0.0 a correr na porta ${PORT}`));
