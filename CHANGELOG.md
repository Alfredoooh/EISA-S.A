# Changelog

## 3.0.0 - Internacional + Quality Gate

- Mudança para modo editorial internacional-only.
- BBC bloqueada globalmente.
- Removidas as fontes RSS brasileiras, portuguesas e angolanas do feed principal.
- Implementada whitelist de publishers internacionais de elevada qualidade.
- Implementado score de qualidade e filtro mínimo.
- GNews atualizado para a autenticação/parâmetros atuais (`apikey`).
- Currents atualizado para V2 e taxonomia canónica.
- Adicionado endpoint `/news/latest`.
- Adicionado endpoint `/news/search`.
- Adicionado `/ready` para health checks no Render.
- Adicionado suporte aos IDs reais de categoria GNews/Currents.
- Feed com prioridade por atualidade + qualidade e limite por publisher.
- RSS reduzido a publishers internacionais aprovados.
- Endpoint `/article` passou a bloquear fontes fora da whitelist e BBC.
- Segredos removidos do `render.yaml` e do pacote de distribuição; os mesmos nomes de variáveis do `.env` continuam compatíveis.
