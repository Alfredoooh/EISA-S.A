# Restauração do feed de notícias — branch `servers`

Este pacote é uma sobreposição de restauração do núcleo de notícias para o projeto existente. Não contém `.env` nem chaves; mantém o ficheiro `.env` já configurado no Render fora do pacote.

## Por que restaurar este núcleo

A versão anterior usava GDELT + GNews + Currents + RSS + SearXNG. O fluxo estrito posterior passou a depender de uma whitelist de publishers, eliminando muitos resultados. Estes ficheiros recuperam agregação multi-fonte sem whitelist rígida e reintroduzem GDELT na home e em todas as categorias.

## Categorias públicas

`GET /news/categories` passa a expor somente IDs reais suportados pelos fornecedores:

- GNews: `world`, `nation`, `business`, `technology`, `entertainment`, `sports`, `science`, `health`.
- Currents V2: `general`, `society`, `science_technology`, `politics_government`, `economy_business_finance`, `arts_culture_entertainment`, `lifestyle_leisure`, `human_interest`, `sport`, `crime_law_justice`, `education`, `environment`, `labour`, `health`, `automotive`, `real_estate`.

Para evitar categorias duplicadas de sentido parecido, a lista pública combina as categorias equivalentes (por exemplo, GNews `business` com Currents `economy_business_finance`). Os IDs antigos como `internacional`, `economia`, `tecnologia`, `futebol`, `financas`, `cripto`, `empregos`, `automovel` e `direito` continuam a ser aceites como aliases, mas deixam de aparecer como categorias separadas.

## Cache

- Cache curto de resultados dos fornecedores para atualizar a cada minuto.
- Arquivo acumulativo por categoria e por pesquisa, com retenção padrão de 168 horas (7 dias).
- Notícias anteriores ainda dentro do período de retenção permanecem no feed quando entram novas notícias.
- Uma falha temporária de um fornecedor não apaga artigos guardados no arquivo.
- O cache é em memória; reiniciar ou fazer redeploy do serviço limpa o arquivo. Para persistência entre reinícios, seria necessário Redis ou outro armazenamento persistente.

## Aplicar no repositório

Copiar/substituir os ficheiros seguindo os caminhos do ZIP na branch `servers`, sem substituir nem publicar `.env`. Os módulos incluídos são ficheiros completos, não trechos. Depois, fazer commit e deixar o Render fazer novo deploy.

## Variáveis

Os nomes existentes `API_KEY`, `GNEWS_API_KEY` e `CURRENTS_API_KEY` continuam iguais. `NEWS_RETENTION_HOURS=168` é opcional e tem esse valor por defeito.

## Endpoints de verificação

- `GET /health`
- `GET /news/categories`
- `GET /news/sources`
- `GET /news/latest?lang=en&maxAgeHours=24&page=1&pageSize=20`
- `GET /news?category=world&lang=en&maxAgeHours=24&page=1&pageSize=20`
- `GET /news/search?q=artificial%20intelligence&lang=en`

As rotas protegidas continuam a exigir o header `x-api-key` conforme a configuração existente do servidor.
