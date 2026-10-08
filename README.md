# International News API v3

API Node/Express para um feed de notícias **internacional-only**, com categorias baseadas em taxonomias reais dos provedores, filtro editorial por publishers aprovados e foco em notícias recentes.

## O que mudou

- BBC bloqueada por domínio em RSS, GNews, Currents, GDELT e scraping.
- Publishers desconhecidos são rejeitados pelo feed; o modo padrão exige `qualityScore >= 80`.
- O backend não envia `country=AO`, `country=BR`, `country=PT` ou equivalente no feed internacional.
- O idioma padrão do feed é `en` para reduzir o viés lusófono.
- O feed utiliza apenas categorias que existem oficialmente nos provedores usados:
  - GNews: `world`, `business`, `technology`, `entertainment`, `sports`, `science`, `health`.
  - Currents V2: `society`, `science_technology`, `politics_government`, `economy_business_finance`, `arts_culture_entertainment`, `lifestyle_leisure`, `human_interest`, `sport`, `crime_law_justice`, `education`, `environment`, `labour`, `health`, `automotive`, `real_estate`, além de `general` quando necessário.
- A API expõe a lista real em `GET /news/categories` com os IDs e o provedor responsável por cada categoria.
- O feed é limitado por frescura: 48 horas por padrão, configurável por `FRESHNESS_HOURS`.
- URLs de imagem são obrigatórias para artigos do feed.
- Deduplicação por URL canónica e título.
- Limite de 5 artigos por publisher no conjunto inicial para melhorar diversidade.

## Variáveis de ambiente

Use o mesmo `.env` do servidor anterior:

```env
PORT=3000
CURRENTS_API_KEY=...
GNEWS_API_KEY=...
API_KEY=...
CACHE_TTL=300
MIN_SOURCE_QUALITY=80
FRESHNESS_HOURS=48
```

O projeto não inclui segredos reais. No Render, configure as três chaves como Secret Environment Variables.

## Endpoints

- `GET /health`
- `GET /ready`
- `GET /news/categories`
- `GET /news/sources`
- `GET /news/latest?page=1`
- `GET /news?category=world&page=1`
- `GET /news/search?q=climate&page=1`
- `GET /article?url=https://...`

Todos os endpoints de dados continuam protegidos por `x-api-key`.
