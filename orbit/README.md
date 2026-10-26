# Orbit — Browser Android (AppCompat)

O Orbit foi adaptado para um navegador Android com interface construída em Views nativas. O `WebView` é usado para apresentar o conteúdo real dos sites; a barra de endereço, os botões, o menu, a pesquisa, os diálogos e a grelha de separadores são componentes Android nativos.

## Implementação

- AppCompat como única dependência de interface declarada; não há Jetpack Compose nem a biblioteca Material Components.
- Navegação por separadores com uma instância `WebView` por separador, histórico nativo do WebView e limpeza ao fechar separadores.
- Barra de endereço com favicon e apenas o título do site, botão circular de segurança e informação de HTTPS/TLS.
- Pesquisa e sugestões com cancelamento de pedidos anteriores e validação de respostas obsoletas.
- Vista de separadores com miniaturas capturadas da página atual e animação nativa de expansão/retorno com `ValueAnimator` e `PathInterpolator`.
- Menus e diálogos personalizados com Views nativas, sem `AlertDialog` padrão.
- Modos Claro, Escuro e Sistema, aplicados à interface e às barras do sistema sem reiniciar a Activity.
- Progressbar abaixo da barra de estado nativa, respeito pelos insets, suporte a HTTP/HTTPS, bloqueio de erros de certificado TLS e recuperação do renderer do WebView.
- Recursos do launcher originais mantidos nas respetivas pastas `mipmap` e `drawable`.

## Estrutura importante

- `app/src/main/java/com/orbit/app/MainActivity.kt` — orquestra a interface, navegação, carregamento de páginas e ciclo de vida.
- `app/src/main/java/com/orbit/app/browser/BrowserTabManager.kt` — cria, seleciona, fecha e descarta os separadores.
- `app/src/main/java/com/orbit/app/model/BrowserTab.kt` — estado associado a cada separador.
- `app/src/main/java/com/orbit/app/ui/` — ícones desenhados com Canvas, paletas e construtores de Views.
- `app/src/main/res/values/themes.xml` e `values-night/themes.xml` — temas AppCompat e valores iniciais das barras do sistema.
- `design/browser.html` — referência HTML de design mais recente; não é carregada como interface da aplicação nem empacotada no APK.
- `design/browser-original.html` — cópia do HTML que estava no ZIP recebido, preservada como referência original e fora do APK.

## Compilação

Abra a pasta do projeto no Android Studio ou execute num ambiente com JDK 17, Android SDK compatível com API 37 e acesso aos repositórios Maven/Google:

```bash
./gradlew clean assembleDebug
```

O APK de depuração deverá ser produzido em `app/build/outputs/apk/debug/app-debug.apk`.

### Estado da validação neste ambiente

Foram verificados estaticamente os ficheiros XML e as referências de recursos, e foi feita uma pesquisa por dependências/ referências de Compose e Material Components. **Não foi possível executar a build Android neste ambiente**: o Gradle Wrapper precisou de descarregar o Gradle 9.6.0 de `services.gradle.org`, mas a resolução DNS/rede desta sessão falhou, e não existe um Android SDK local instalado. Assim, não é correto afirmar que `assembleDebug` passou; a compilação real deve ser confirmada no Android Studio ou no Codemagic.

## Segurança e limites do navegador

- Erros de certificado TLS são cancelados; a aplicação não oferece opção de os ignorar.
- Conteúdo misto é bloqueado e o Safe Browsing é ativado quando suportado pelo Android.
- Ligações `mailto:` e `tel:` são encaminhadas para aplicações compatíveis; esquemas não reconhecidos não são executados dentro do WebView.
- HTTPS cifra o transporte, mas não é uma garantia de legitimidade do site.
- As miniaturas são capturas nativas do desenho visível do WebView. Alguns conteúdos protegidos por DRM, vídeo protegido ou superfícies externas podem não aparecer na captura devido às restrições da plataforma.
