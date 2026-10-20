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
