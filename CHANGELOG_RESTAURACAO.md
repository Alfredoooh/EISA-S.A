# Restauração do feed

- Restaurado o agregador multi-fonte GDELT, GNews, Currents V2, RSS e SearXNG.
- Removida a whitelist rígida que eliminava publishers válidos.
- Mantido o bloqueio à BBC e às fontes brasileiras conhecidas/domínios `.br`.
- A lista exposta pela API usa apenas categorias oficiais dos fornecedores, com IDs antigos aceites por alias.
- Introduzido arquivo acumulativo de notícias de 7 dias, separado do cache curto usado para atualizar resultados novos.
