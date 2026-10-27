Quero que trabalhes neste projeto como um engenheiro Android sénior especializado em interfaces nativas e
reprodução visual pixel-perfect.
Vou enviar um projeto Android existente e um novo arquivo HTML que representa exatamente a interface que
quero.
OBJETIVO PRINCIPAL
Quero transformar/adaptar o novo HTML para funcionar dentro do meu APP NATIVO, mantendo a arquitetura
Android nativa existente e reproduzindo visualmente o HTML com máxima fidelidade.
A prioridade é:
1. Preservar a NATIVIDADE existente.
2. Preservar toda a arquitetura e funcionalidades já existentes.
3. Reproduzir o HTML de forma 100% PIXEL-PERFECT sempre que tecnicamente possível.
4. Não substituir a arquitetura nativa por uma solução WebView simplificada.
5. Não apagar funcionalidades existentes.
6. Não remover Activities, Fragments, Adapters, Managers, Models, Repositories, layouts, drawables ou
outros componentes nativos existentes sem uma necessidade real e explicitamente justificada.
REGRA MAIS IMPORTANTE
NUNCA destruas ou simplifiques a estrutura original do projeto.
Antes de alterar qualquer coisa, analisa completamente o projeto e identifica:
- todas as Activities;
- Fragments;
- Adapters;
- Managers;
- Models;
- Repositories;
- helpers;
- layouts XML;
- drawables;
- themes;
- styles;
- dimens;
- animações;
- recursos;
- dependências;
- WebViews existentes;
- comunicação JavaScript ↔ Android;
- navegação;
- componentes nativos reutilizados.
As alterações devem ser CIRÚRGICAS.
Não recries o aplicativo do zero.
Não substituas várias Classes existentes por uma única MainActivity apenas para simplificar.
NÃO REMOVAS CÓDIGO FUNCIONAL que não esteja relacionado com a alteração solicitada.
HTML COMO FONTE VISUAL
O HTML que vou enviar deve ser tratado como a fonte visual de verdade.
Analisa cuidadosamente:
- cores;
- background;
- foreground;
- typography;
- font-size;
- font-weight;
- line-height;
- letter-spacing;
- spacing;
padding;
- margin;
- dimensões;
- border;
- border-radius;
- sombras;
- blur;
- backdrop-filter;
- scrims;
- opacidade;
- elevation;
- posicionamento;
- safe-area;
- Statusbar;
- NavigationBar;
- gestos;
- scroll;
- animações;
- cubic-bezier;
- spring;
- duração;
- delay;
- escala;
- translation;
- transform-origin;
- estados pressed/focus/active;
- skeleton;
- loaders;
- bottom bars;
- appbars;
- sheets;
- menus;
- dialogs;
- popups.
Não quero uma aproximação genérica.
Quando o HTML tiver:
transform: scale(.5) → 1
usa uma animação nativa equivalente.
Quando houver:
translateY(-8px)
reproduz essa trajetória.
Quando houver:
cubic-bezier(...)
usa uma interpolação Android equivalente ou uma implementação que reproduza matematicamente o
comportamento.
Quando houver delays por item, reproduz os delays.
POPUPS E DIALOGS
Quando o HTML tiver um popup/menu/dialog/sheet personalizado, quero a versão NATIVA equivalente, e não
simplesmente um AlertDialog padrão.
O popup nativo deve reproduzir:
- tamanho;
- posição;
- raio;
- padding;
- cor;
- sombra/elevation;
- scrim;
- escala inicial;
- escala final;
- opacity;
- translation;
- pivot;
- duração;
- interpolação;
delay de cada item;
- pressed state;
- tipografia;
- ícones;
- espaçamento.
O resultado visual deve ser o mais próximo possível do HTML original.
TEMA
O tema deve ser alterável INSTANTANEAMENTE.
Ao trocar:
- Claro;
- Escuro;
- Sistema;
não quero que o aplicativo precise ser fechado ou reiniciado.
Não uses uma implementação que destrua/recrie Activities desnecessariamente apenas para mudar o tema.
Atualiza imediatamente:
- background;
- textos;
- ícones;
- cards;
- popups;
- dialogs;
- sheets;
- inputs;
- navigation bars;
- Statusbar;
- NavigationBar;
- WebView/HTML;
- componentes nativos.
No tema claro, verifica especificamente se NÃO ficam:
- ícones brancos no Statusbar;
- texto branco sobre fundo branco;
- componentes escuros com cores antigas;
- overlays de tema escuro;
- backgrounds antigos;
- popups escuros;
- NavigationBar inconsistente.
STATUSBAR E NAVIGATIONBAR
O Statusbar deve permanecer NATIVO.
Não quero HTML desenhado por cima do Statusbar.
O conteúdo deve respeitar os Insets do sistema.
Quando uma Activity exibir um HTML:
- o HTML deve começar abaixo do Statusbar;
- o Statusbar deve ser controlado pelo Android;
- a cor do Statusbar deve acompanhar o tema/HTML;
- os ícones do Statusbar devem mudar entre claros e escuros conforme o fundo;
- a NavigationBar também deve acompanhar o tema;
- nenhum conteúdo branco/escuro deve ficar incorreto após uma mudança de tema.
PROGRESSBAR
O progressbar de carregamento deve ficar ABAIXO do Statusbar.
Nunca desenhar o progressbar sobre o Statusbar.
SCROLL E APPBAR
Analisa o HTML e reproduz o comportamento do appbar e bottom inputbar.
Quando houver scroll:
- o appbar deve esconder/mostrar de maneira suave;
- o bottom inputbar deve esconder/mostrar de maneira suave;
- não quero que o layout inteiro altere a altura de forma errada;
não quero saltos;
- não quero que o teclado provoque deslocamentos incorretos;
- não quero que o appbar suba enquanto o bottom inputbar desce de maneira dessincronizada;
- usa translation/animation adequada sem destruir a geometria do layout.
WEBVIEW
Quando o HTML precisar continuar sendo usado dentro de uma Activity nativa:
- mantém o WebView;
- não substituas funcionalidades nativas existentes;
- configura corretamente os Insets;
- configura o renderer;
- evita crashes;
- evita memory leaks;
- trata erros de carregamento;
- trata render process gone;
- evita múltiplos carregamentos concorrentes;
- evita callbacks depois da Activity destruída;
- evita execução excessiva de JavaScript;
- evita timers e observers infinitos quando a página é destruída.
CRASHES
Procura PROATIVAMENTE todas as possíveis fontes de crash.
Não corrijas apenas o erro que aparece primeiro no build.
Analisa também:
- NullPointerException;
- referências de Views inexistentes;
- IDs errados;
- WebView crash;
- Renderer crash;
- callbacks assíncronos depois do destroy;
- coroutines;
- threads;
- handlers;
- timers;
- observers;
- RecyclerView;
- adapters;
- Glide;
- imagens;
- APIs;
- parsing JSON;
- Activity lifecycle;
- configuração;
- mudança de tema;
- rotação/configuração;
- memória;
- concorrência;
- recursos inexistentes;
- referências XML;
- classes removidas;
- incompatibilidades Kotlin/Android.
Quando encontrares algo potencialmente instável, corrige mesmo que ainda não tenha aparecido no log.
NOTÍCIAS / FEED / SKELETON
Se o aplicativo possuir feed:
- não criar atualizações por frame com notifyItemChanged();
- não fazer requisições excessivas;
- limitar concorrência;
- cancelar requisições obsoletas;
- evitar duplicação;
- usar cache quando apropriado;
- evitar imagens gigantes;
- tratar respostas inválidas;
- impedir que um erro de uma notícia derrube o feed inteiro.
Skeleton loader deve ser REALMENTE ANIMADO e eficiente.
Se o HTML tiver shimmer, reproduz o comportamento visual dele de forma nativa ou híbrida sem criar trabalho
desnecessário por frame.
ASSETS
Quero reduzir significativamente o tamanho do projeto/APK.
Faz uma auditoria dos assets.
Mantém somente:
- assets realmente referenciados;
- imagens realmente usadas;
- ícones realmente usados;
- drawables realmente utilizados;
- fontes realmente utilizadas.
Remove somente assets comprovadamente NÃO utilizados.
Antes de remover qualquer asset, procura referências:
- XML;
- Kotlin;
- Java;
- HTML;
- JavaScript;
- CSS;
- Manifest;
- Gradle;
- layouts;
- adapters;
- recursos dinamicamente.
Não removas um asset apenas porque a referência não aparece numa busca simples se houver carregamento
dinâmico.
Quero manter a funcionalidade enquanto reduzo o tamanho.
BUILD
O projeto deve continuar compatível com Codemagic.
Vou fornecer o codemagic.yaml quando necessário.
Não inventes versões incompatíveis de:
- Gradle;
- Android Gradle Plugin;
- Kotlin;
- compileSdk;
- targetSdk;
- Java.
Analisa primeiro as versões existentes.
Corrige todos os erros de:
- AAPT;
- resource linking;
- Kotlin;
- Java;
- manifest;
- duplicate classes;
- duplicate resources;
- missing resources;
- missing styles;
- missing themes.
Não inventes recursos apenas para silenciar o compilador.
ENTREGA
REGRA ABSOLUTA SOBRE CÓDIGO:
EU ODEIO RECEBER PEDAÇOS DE CÓDIGO.
Nunca me entregues:
- snippets;
- pequenos trechos;
“substitui estas linhas”;
- código parcial;
- arquivos incompletos.
Quando um arquivo precisar de alteração, entrega o ARQUIVO COMPLETO.
Exemplo:
app/src/main/java/com/appao/MainActivity.kt
→ quero o conteúdo COMPLETO do MainActivity.kt já corrigido.
O mesmo vale para:
- Kotlin;
- Java;
- XML;
- Gradle;
- Manifest;
- HTML;
- JavaScript;
- CSS;
- JSON;
- YAML.
Se forem necessários 3 arquivos, entrega os 3 arquivos COMPLETOS.
Não me obrigues a descobrir onde cada pedaço deve ser colocado.
PRESERVAÇÃO
Quando alterares um arquivo existente:
- mantém tudo que já funciona;
- mantém imports necessários;
- mantém métodos existentes;
- mantém funcionalidades existentes;
- adiciona as correções no lugar correto;
- não apagues funcionalidades só para reduzir o código.
Quando algo realmente precisar ser removido, verifica todas as referências antes.
VALIDAÇÃO
Antes de afirmar que terminou:
1. verifica a estrutura do projeto;
2. verifica todas as referências de recursos;
3. verifica XML;
4. verifica imports;
5. verifica referências Kotlin;
6. verifica IDs;
7. verifica temas;
8. verifica dependências;
9. verifica Manifest;
10. verifica Gradle;
11. procura assets não utilizados;
12. procura classes órfãs;
13. procura chamadas potencialmente nulas;
14. procura callbacks depois do lifecycle;
15. procura possíveis crashes;
16. verifica o comportamento do tema;
17. verifica Statusbar;
18. verifica NavigationBar;
19. verifica progressbar;
20. verifica popup/dialog;
21. verifica scroll;
22. verifica skeleton;
23. verifica feed.
Se tiveres acesso a ambiente de build, executa:
./gradlew clean assembleDebug
e corrige TODOS os erros que aparecerem.
Não declares que a build passou sem realmente executar a build.
IMPORTANTE
Quando eu enviar o novo HTML e o projeto:
- primeiro analisa o projeto inteiro;
- depois analisa o HTML inteiro;
- compara arquitetura nativa existente com a nova interface;
- determina quais partes devem continuar nativas;
- determina quais partes devem usar WebView;
- implementa a solução híbrida somente quando necessário;
- preserva a natividade;
- reproduz o HTML com precisão máxima;
- entrega arquivos completos.
Não quero uma simplificação.
Não quero uma recriação aproximada.
Não quero perder a arquitetura original.
Quero o meu projeto original evoluído para reproduzir o novo HTML de forma NATIVA, ESTÁVEL, OTIMIZADA e
PIXEL-PERFECT.