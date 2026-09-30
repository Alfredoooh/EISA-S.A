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

// Rotas públicas (SEM auth) — têm de vir ANTES do middleware
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

app.get('/', (req, res) => {
  res.json({
    name: 'News API',
    version: '1.0.0',
    endpoints: {
      news:       'GET /news?category=general&lang=pt&page=1',
      search:     'GET /news?q=angola&lang=pt',
      article:    'GET /article?url=https://...',
      local:      'GET /local?lat=-8.8368&lon=13.2343',
      categories: 'GET /news/categories',
      sources:    'GET /news/sources',
      health:     'GET /health'
    }
  });
});

// Middleware de autenticação — aplica-se só às rotas abaixo
app.use((req, res, next) => {
  const key = req.headers['x-api-key'];
  if (!key || key !== process.env.API_KEY) {
    return res.status(401).json({
      error: 'Unauthorized',
      message: 'Header x-api-key inválido ou em falta'
    });
  }
  next();
});

// Rotas protegidas
app.use('/news', newsRoutes);
app.use('/article', articleRoutes);
app.use('/local', localRoutes);

app.listen(PORT, () => {
  console.log(`News API a correr na porta ${PORT}`);
});