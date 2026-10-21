# Mini apps and launcher icon update

- Added `assets/mini_apps/docs.html`, `sheets.html`, and `slides.html` based on the provided HTML files.
- Added `MiniAppActivity`, a dedicated full-screen WebView activity with system bars only, themed from AppAo light/dark colors.
- Added the Sites / Mini apps segmented switch to `MoreSitesActivity`.
- Added native vector logos for Documents, Sheets, and Slides.
- Updated launcher raster density assets and adaptive foreground using the supplied artwork.
- No `drawable-nodpi` resources were created. The existing `assets/site_icons/site_icon_catalog.json` was not changed.
- Fixed the invalid `com.appao.core.AccentColorManager` import in `ExpressiveRefreshIndicatorView.kt`; the class belongs to `com.appao`.

- `MiniAppActivity` injects AppAo colors into the editor CSS custom properties on page load so canvases, grids, selection accents, and editor chrome follow the active theme.
