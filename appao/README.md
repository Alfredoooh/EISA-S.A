# app ao — Android Native Kotlin

Projeto Android nativo atualizado para o app ao.

## Incluído
- Kotlin nativo (`com.appao`)
- MainActivity, ArticleActivity e AppViewerActivity
- Feed de notícias, categorias, clima, drawer, artigo, comentários e reações
- WebView para os subapps HTML em `assets/apps/`
- Curves.kt com os cubic-bezier equivalentes ao HTML
- Gradle/Gradle Wrapper
- Codemagic CI (`codemagic.yaml`)
- ProGuard
- gradle.properties
- scripts em `tools/`
- ícones base em `master/`
- keystore de desenvolvimento fornecida no projeto original

## Assets
Coloque os assets existentes em:
`app/src/main/assets/icons/svg/`
`app/src/main/assets/icons/png/`
`app/src/main/assets/illustrations/png/`
`app/src/main/assets/apps/`

## Build
```bash
./gradlew assembleDebug
```

O `codemagic.yaml` está preparado para o mesmo projeto, com `PACKAGE_NAME=com.appao`.
