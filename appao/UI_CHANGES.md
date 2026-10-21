# Alterações de interface do AppAo

## Ícones
- Os Material Symbols Rounded usam um viewport intrínseco uniforme de 24 dp, com `CENTER_INSIDE`, sem os limites manuais antigos que encolhiam o desenho. O fallback Canvas também declara 24 dp de largura e altura intrínsecas.
- Os recursos existentes, os aliases de compatibilidade e o pipeline que prepara os ícones continuam ativos. `app/src/main/assets/site_icons/site_icon_catalog.json` não foi alterado.
- O favicon do navegador é recortado em círculo dentro de um contentor de 28 dp. O `PullRefreshIndicatorView` existente conserva o seu anel gradiente e tem as mesmas dimensões do contentor do favicon.

## Gestos e carregamento
- A barra de endereço do navegador acompanha progressivamente as pequenas variações de scroll. A margem/viewport da WebView é ajustada pelo mesmo deslocamento para não deixar uma faixa vazia quando a barra desaparece.
- `ExpressiveSwipeRefreshLayout` mantém o nested scrolling de `SwipeRefreshLayout` e reconhece a direção do gesto antes de interceptar; arrastos predominantemente horizontais não devem disparar o pull-to-refresh.
- Início/Descobrir e o navegador usam `ExpressiveRefreshIndicatorView`, uma animação Canvas leve que faz morph entre silhuetas arredondadas, inspirada na linguagem visual do Material 3 Expressive. O loader do favicon é separado e não foi alterado.
- A preparação de carregamento do navegador (`BrowserLoadingBooster`) e a resolução DNS antecipada continuam preservadas.

## Pesquisa, histórico e menus
- A barra de endereço da página inicial e a do navegador usam uma transição de contentor Material para abrir a atividade de pesquisa; esta não apresenta botão de voltar, mas mantém a navegação Voltar do Android.
- Sugestões de histórico mostram o título (ou o host como fallback), não o URL. Só entradas de sites visitados podem mostrar favicon; sugestões de motores de pesquisa continuam a mostrar os ícones do motor.
- O fundo dos popup menus não tem stroke/borda sólida.

## Início, Perfil e separadores
- A página Início mantém o título alinhado à esquerda e os controlos abaixo dele.
- Perfil mantém o cabeçalho centralizado sem cartões, e as opções são separadas por divisórias finas.
- A barra inferior continua a usar o desenho personalizado e a cor de superfície do tema, incluindo o contador numérico dentro do ícone de separadores.
- O catálogo de oito sites rápidos, Downloads e Mais sites continua com os recursos existentes. A configuração de recursos mantém a grelha principal de cinco colunas e a grelha Descobrir de seis colunas.

## Recursos e validação
- Não foram adicionados ficheiros `drawable-nodpi`, nem modificados os ícones do lançador nesta passagem.
- A validação XML e as verificações estáticas específicas dos recursos alterados foram executadas. O verificador geral ainda assinala recursos gráficos gerados ausentes no snapshot, que são preparados pelo pipeline do projeto.
- A compilação integral `assembleDebug` não foi confirmada neste ambiente: o wrapper necessita de descarregar Gradle 8.2.1, mas a resolução DNS para `services.gradle.org` falhou. O projeto continua a usar Material Components 1.12.0 e `compileSdk 34`; por isso, o indicador Expressive é uma implementação Canvas leve, não o componente oficial `LoadingIndicator` que faz parte das versões 1.14+ e exige uma cadeia de ferramentas Android mais recente.
