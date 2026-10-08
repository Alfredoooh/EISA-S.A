# International News API v2

API Node.js/Express para notícias internacionais, pensada para deploy direto no Render.

## O que foi corrigido

- A rota principal `/news` é internacional por definição e rejeita fontes brasileiras (`BR`, `.br`, `.com.br` e uma lista de domínios conhecidos).
- A categoria deixou de ser limitada a 8 tópicos. Existem **34 categorias** exatamente com a taxonomia solicitada.
- O conteúdo é ordenado por uma combinação de frescor, qualidade da fonte e relevância.
- Por padrão, a API procura notícias com até 24 horas de idade. `/news/latest` usa 12 horas.
- Quando existem poucos resultados realmente recentes, a API só amplia de forma controlada para no máximo 72 horas; não substitui resultados frescos por notícias antigas indefinidamente.
- GDELT, RSS internacional, GNews, Currents e SearXNG são combinados e deduplicados.
- SearXNG usa a lista pública dinâmica de instâncias do `searx.space`, com fallback local e rotação sequencial quando uma instância falha.
- O scraper foi reconstruído em camadas: `Schema.org articleBody` → Mozilla Readability → DOM semântico. O objetivo é devolver o corpo da própria notícia, excluindo menus, anúncios, relacionados, comentários, navegação e elementos de interface.
- `/article` inclui proteção básica contra SSRF e limita tamanho/tempo de download.
- Foi adicionado rate limit em memória e respostas de health/readiness para Render.
- As chaves de API deixaram de ser gravadas no projeto. O Render passa a pedir `sync: false` para os segredos.

## Endpoints

### Notícias atuais

`GET /news/latest?lang=en&maxAgeHours=12&page=1&pageSize=20`

### Notícias por tópico

`GET /news?category=tecnologia&lang=en&maxAgeHours=24&page=1&pageSize=20`

Categorias:

`Internacional`, `Economia`, `Política`, `Desporto`, `Futebol`, `Tecnologia`, `Ciência`, `Inteligência Artificial`, `Biotecnologia`, `Carreira`, `Empregos`, `Finanças`, `Ações`, `Mercados`, `Cripto`, `Negócios`, `Startups`, `Imobiliário`, `Energia`, `Agricultura`, `Ambiente`, `Saúde`, `Educação`, `Cultura`, `Entretenimento`, `Cinema`, `Música`, `Gaming`, `Automóvel`, `Viagens`, `Gastronomia`, `Moda`, `Segurança`, `Direito`.

Os IDs das categorias são os mesmos nomes em minúsculas e sem acentos, por exemplo `inteligencia-artificial`, `financas`, `acoes` e `saude`.

### Pesquisa internacional

`GET /news/search?q=artificial%20intelligence&lang=en&maxAgeHours=24`

A pesquisa usa GDELT + GNews + SearXNG e aplica o mesmo filtro internacional.

### Extração de artigo

`GET /article?url=https://exemplo.com/noticia`

A resposta contém `title`, `description`, `content`, `body`, `author`, `publishedAt`, `image`, `source` e `extractionMethod`.

### Fontes

`GET /news/sources`

### Categorias

`GET /news/categories`

### Saúde do serviço

`GET /health`

`GET /ready`

### Conteúdo local separado

`GET /local?lat=-8.8368&lon=13.2343`

A rota `/local` é a única que não é internacional-only; ela foi mantida separada para não contaminar a home com notícias locais.

## Render

Configure no painel do Render:

- `API_KEY`
- `GNEWS_API_KEY`
- `CURRENTS_API_KEY`
- `REQUIRE_API_KEY=true`

O arquivo `render.yaml` marca essas variáveis como segredos (`sync: false`).

## Segurança das chaves

As chaves que estavam no arquivo recebido foram removidas do projeto final. Como já apareceram em um arquivo de configuração, trate-as como comprometidas e gere/rotacione novas chaves antes de produção.

## Exemplo de chamada autenticada

`curl -H "x-api-key: A_SUA_CHAVE" https://SEU-SERVICO.onrender.com/news/latest?lang=en&maxAgeHours=12`
