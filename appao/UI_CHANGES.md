# Alterações de interface do AppAo

- Barra inferior principal substituída por `BottomNavigationView` nativa do Material 3, com indicador ativo nativo em forma de pill e cor primária da aplicação.
- Lista principal de sites definida como Facebook, CrazyGames, Adobe, HubSpot, CNN, Yahoo, X e LinkedIn; ícones carregados através dos favicons dos sites e colocados em contentores arredondados no tom do campo de endereço.
- Loader do favicon redesenhado como um anel gradiente animado por `Canvas`/`SweepGradient`.
- Removida a barra inferior interna de Descobrir que continha a opção Guardados.
- Perfil sem o cabeçalho/avatar circular e saudação; os grupos de cartões seguem o espaçamento e os fundos usados nas definições e deslocam-se com o conteúdo.
- Barra de endereço do navegador sem elevação/sombra. A mudança entre visível e oculta deixa de animar simultaneamente a margem da WebView e o posicionamento, evitando saltos e reaparições inesperadas.
- Recuperados recursos gráficos em falta no arquivo de origem para que as referências de recursos do projeto passem a validação estática.

## Validação

`python3 verify_project.py` — PASS (XML e referências estáticas de recursos).

Não foi possível executar `assembleDebug` neste ambiente: o wrapper tentou descarregar Gradle 8.2.1 e a rede deste ambiente não consegue resolver `services.gradle.org`. Portanto, o APK não foi compilado/testado aqui.

## Follow-up fixes (2026-10-20)
- Replaced the launcher icon in the existing density-specific `mipmap-*` assets using the user-provided AppAo logo; no `nodpi` assets were added.
- Rebuilt the adaptive launcher foreground with transparent background and centered, size-limited artwork so it sits within the Android adaptive-icon safe area.
- Removed the obsolete `drawable-nodpi` monochrome icon and its adaptive-icon references.
- Removed the unsupported `app:itemActiveIndicatorEnabled` XML attribute that caused Android resource linking to fail. The Material `BottomNavigationView` retains its component default active indicator behavior.

## UI and browser stability pass (2026-10-20)
- Kept the launcher mipmap assets as provided in the current project; did not add `drawable-nodpi` resources.
- Fixed normalized icon aliases in `IconLoader` so Settings entries resolve to the existing native drawable resources rather than tiny fallback glyphs. Fallback line spacing now scales with display density.
- Kept the Material 3 `BottomNavigationView` in a compact 58dp content area (plus system inset), tightened icon/label spacing, and retained a low-opacity version of the primary color for the icon-only active indicator. Surface follows `@color/bg` (white in light theme, dark body surface in night theme).
- Restored the profile header as a centered rounded card with avatar and greeting, inside the same ScrollView as its rows.
- Kept all eight quick sites plus Downloads and “Mais sites” inside one rounded parent container; individual site favicons remain circular. Added a circular surface behind the browser's address-bar favicon.
- Removed shared-element transition setup from the tab overview, disabled change animations during full adapter refreshes, lowered tab preview height to reduce thumbnail memory pressure, and retained fade transitions.
- Added a lightweight browser warm-up using asynchronous DNS pre-resolution and standard WebView cache settings. This reduces avoidable DNS delays where possible but cannot guarantee faster remote servers or network connections.



## Homepage and popup-menu correction (2026-10-21)
- Returned the home quick-sites panel to a five-column grid: eight sites plus Downloads and Mais sites now display as five entries in each of two rows. The app-discovery grid remains six columns.
- Restored 44dp circular quick-site icon containers and original utility glyph assets; remote favicons use circle cropping so images without square content do not look undersized. No `drawable-nodpi` resources were added or regenerated.
- Added a compact 24dp-wide, 3dp-high primary-colour indicator above the active bottom-navigation icon. It is overlaid on each tab so the existing icon/label stack does not move down. The tabs item continues to use its numeric tab-count symbol rather than a copy glyph.
- Browser actions continue to use an anchor-attached AppCompat `PopupMenu`, explicitly themed with the application's Material 3 popup style, a rounded M3 surface, and the existing icon column; this remains a popup menu, not a Dialog.

Validation for this update: all XML files parse successfully and the modified home grid, four navigation indicators, and popup theme references passed targeted static checks. Full Android compilation was not run because this archive does not contain the generated `drawable-nodpi` icon assets or the Gradle Wrapper JAR; those are normally injected by the project's generation/build workflow.

## Barra inferior e vista de separadores (2026-10-21)
- Removidos os traços indicadores acima dos ícones da barra inferior, sem aumentar a altura da barra.
- Ícones e labels selecionados seguem a cor primária configurada no tema claro; no tema escuro, ícones selecionados ficam branco puro e inativos cinza-claro. O item de separadores continua representado pelo contador numérico, com contorno e número coloridos conforme o estado, sem ícone de copiar.
- Aumentados os ícones de navegação das linhas do perfil para 30dp e as setas finais para 27dp; a linha de definições agora usa 30dp e `fitCenter` para evitar reduzir visualmente o desenho.
- Segmento “Separadores / Privados” redesenhado como pílula com cantos totalmente arredondados, fundo de superfície e cápsula selecionada com contraste adaptado aos temas claro/escuro.
- A vista de separadores agora inclui sempre um cartão de novo separador no final da grelha, com fundo de card e botão circular de mais centralizado; continua disponível mesmo quando não existem separadores privados.
- O catálogo `assets/site_icons/site_icon_catalog.json` foi preservado sem alteração e não foram criados recursos `drawable-nodpi`.
