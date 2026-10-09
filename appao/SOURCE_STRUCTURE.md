# Estrutura do código Appao

O código Android foi organizado por responsabilidade em subpastas físicas, mantendo o pacote Kotlin `com.appao` para preservar os nomes de classes usados pelo `AndroidManifest.xml`, layouts e recursos existentes.

- `app/src/main/java/com/appao/screens/`: Activities e ecrãs nativos, incluindo feed, notícia, pesquisa, categorias, comentários, publicação e definições.
- `app/src/main/java/com/appao/news/`: modelo de notícia, adaptador do feed, repositório/cache de notícias e modelos/adaptadores de comentários.
- `app/src/main/java/com/appao/core/`: tema, curvas de animação, sincronização das barras do sistema, rastreio e utilitários de arranque.
- `app/src/main/java/com/appao/ui/`: ícones, menus, diálogos, snackbars, contentores e componentes visuais reutilizáveis.

Os recursos de layout permanecem em `app/src/main/res/layout/`; cores, estilos e fundos continuam nas pastas de recursos Android apropriadas. As categorias são carregadas de `/news/categories` e a lista de fallback contém apenas IDs/categorias declarados pelo servidor API v3.
