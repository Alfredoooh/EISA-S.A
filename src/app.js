require('dotenv').config();
const express = require('express');
const cors = require('cors');
const newsRoutes = require('./routes/news');
const articleRoutes = require('./routes/article');
const localRoutes = require('./routes/local');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// Middleware de autenticação
app.use((req, res, next) => {
  const key = req.headers['x-api-key'];
  if (!key || key !== process.env.API_KEY) {
    return res.status(401).json({ error: 'Unauthorized', message: 'x-api-key inválida ou em falta' });
  }
  next();
});

// Rotas
app.use('/news', newsRoutes);
app.use('/article', articleRoutes);
app.use('/local', localRoutes);

// Health check (sem auth)
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Info (sem auth)
app.get('/', (req, res) => {
  res.json({
    name: 'News API',
    version: '1.0.0',
    endpoints: {
      news: '/news?category=general&lang=pt&page=1',
      article: '/article?url=https://...',
      local: '/local?lat=-8.8368&lon=13.2343',
      categories: '/news/categories',
      sources: '/news/sources',
      health: '/health'
    }
  });
});

app.listen(PORT, () => {
  console.log(`News API running on port ${PORT}`);
});
