package com.appao.screens

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.webkit.WebViewAssetLoader
import com.appao.AccentColorManager
import com.appao.R
import com.appao.SystemBarHelper
import com.appao.ThemeManager

/** Standalone mini-app screen. It intentionally has no browser chrome or app bar. */
class MiniAppActivity : AppCompatActivity() {
    private lateinit var miniWebView: WebView
    private var pageLoaded = false
    private var miniAppId: String = "docs"

    private data class MiniAppSpec(val asset: String, val title: String)

    private val specs = mapOf(
        "docs" to MiniAppSpec("docs.html", "Documentos"),
        "sheets" to MiniAppSpec("sheets.html", "Folha de cálculo"),
        "slides" to MiniAppSpec("slides.html", "Apresentações")
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        WindowCompat.setDecorFitsSystemWindows(window, true)
        miniAppId = intent.getStringExtra(EXTRA_MINI_APP_ID).orEmpty().lowercase().ifBlank { "docs" }
        val spec = specs[miniAppId] ?: specs.getValue("docs")

        val backgroundColor = ContextCompat.getColor(this, R.color.bg)
        val root = FrameLayout(this).apply {
            setBackgroundColor(backgroundColor)
            clipChildren = true
            clipToPadding = true
        }
        miniWebView = WebView(this).apply {
            setBackgroundColor(backgroundColor)
            isVerticalScrollBarEnabled = false
            // Sheet editors need two-axis gestures; keep native WebView scrolling enabled.
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                allowFileAccess = false
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                mediaPlaybackRequiresUserGesture = true
            }
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        miniWebView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageLoaded = true
                applyAppThemeToHtml()
            }
        }
        miniWebView.webChromeClient = WebChromeClient()
        root.addView(miniWebView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
        SystemBarHelper.sync(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::miniWebView.isInitialized && miniWebView.canGoBack()) miniWebView.goBack()
                else finish()
            }
        })

        val state = savedInstanceState?.getBundle(STATE_WEB_VIEW)
        val restored = state != null && miniWebView.restoreState(state) != null
        if (!restored) {
            miniWebView.loadUrl("https://appassets.androidplatform.net/assets/mini_apps/${spec.asset}")
        }
        title = spec.title
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::miniWebView.isInitialized) {
            val state = Bundle()
            miniWebView.saveState(state)
            outState.putBundle(STATE_WEB_VIEW, state)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::miniWebView.isInitialized) miniWebView.onResume()
        if (pageLoaded) applyAppThemeToHtml()
    }

    override fun onPause() {
        if (::miniWebView.isInitialized) miniWebView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::miniWebView.isInitialized) {
            miniWebView.stopLoading()
            miniWebView.webChromeClient = null
            miniWebView.webViewClient = WebViewClient()
            (miniWebView.parent as? ViewGroup)?.removeView(miniWebView)
            miniWebView.removeAllViews()
            miniWebView.destroy()
        }
        super.onDestroy()
    }

    private fun applyAppThemeToHtml() {
        if (!::miniWebView.isInitialized) return
        val isDark = ThemeManager.resolvedDark(this)
        val darkLiteral = if (isDark) "true" else "false"
        val mode = if (isDark) "dark" else "light"
        val primaryColor = AccentColorManager.resolvedOr(this, R.color.pri)
        val primary = colorToHex(primaryColor)
        val onPrimary = colorToHex(ContextCompat.getColor(this, R.color.onpri))
        val background = colorToHex(ContextCompat.getColor(this, R.color.bg))
        val surface = colorToHex(ContextCompat.getColor(this, R.color.bgElevated))
        val card = colorToHex(ContextCompat.getColor(this, R.color.card))
        val card2 = colorToHex(ContextCompat.getColor(this, R.color.card2))
        val border = colorToHex(ContextCompat.getColor(this, R.color.border))
        val line = colorToHex(ContextCompat.getColor(this, R.color.line))
        val text = colorToHex(ContextCompat.getColor(this, R.color.text))
        val dim = colorToHex(ContextCompat.getColor(this, R.color.dim))
        val pageShadow = if (isDark) "rgba(0,0,0,0.45)" else "rgba(0,0,0,0.16)"
        val slideShadow = if (isDark) "rgba(0,0,0,0.48)" else "rgba(0,0,0,0.20)"
        val stripShadow = if (isDark) "rgba(0,0,0,0.48)" else "rgba(0,0,0,0.09)"
        val primaryRgb = String.format(
            "rgba(%d,%d,%d,0.12)",
            android.graphics.Color.red(primaryColor),
            android.graphics.Color.green(primaryColor),
            android.graphics.Color.blue(primaryColor)
        )
        val stripe = if (isDark) card else card2
        val script = """
            (function() {
              var dark = $darkLiteral;
              var mode = '$mode';
              var api = window.editorApi || {};
              if (typeof api.setThemeMode === 'function') api.setThemeMode(mode);
              if (typeof api.setPaperDarkMode === 'function') api.setPaperDarkMode(dark);
              if (typeof api.setSheetDarkMode === 'function') api.setSheetDarkMode(dark);
              if (typeof api.setPrimaryColor === 'function') api.setPrimaryColor('$primary');
              var root = document.documentElement;
              var body = document.body;
              var vars = {
                '--appao-bg': '$background', '--appao-text': '$text', '--appao-primary': '$primary',
                '--doc-bg': '$background', '--page-bg': '$surface', '--page-text': '$text',
                '--page-border': '$border', '--page-shadow': '$pageShadow', '--selection': '$primary',
                '--page-number': '$dim', '--placeholder-bg': '$card', '--placeholder-border': '$border',
                '--table-border': '$border', '--table-header-bg': '$primaryRgb', '--table-stripe-bg': '$stripe',
                '--header-footer-text': '$dim', '--header-footer-border': '$line',
                '--sheet-bg': '$background', '--sheet-header-bg': '$card', '--sheet-header-text': '$dim',
                '--sheet-border': '$border', '--sheet-cell-border': '$line', '--sheet-cell-bg': '$surface',
                '--sheet-cell-text': '$text', '--sheet-selection': '$primary', '--sheet-hover': '$card2',
                '--slide-bg': '$surface', '--slide-text': '$text', '--slide-shadow': '$slideShadow',
                '--slide-border': '$border', '--strip-bg': '$background', '--strip-border': '$line',
                '--strip-shadow': '$stripShadow', '--thumb-border': '$border', '--thumb-add-border': '$border',
                '--thumb-add-color': '$dim', '--thumb-surface-bg': '$surface', '--thumb-surface-text': '$text',
                '--control-bg': '$card', '--onSurface': '$text', '--onSurfaceVariant': '$dim',
                '--craft-primary': '$primary', '--craft-on-primary': '$onPrimary'
              };
              Object.keys(vars).forEach(function(key) { body.style.setProperty(key, vars[key]); });
              root.style.setProperty('--appao-bg', '$background');
              root.style.setProperty('--appao-text', '$text');
              root.style.setProperty('--appao-primary', '$primary');
            })();
        """.trimIndent()
        miniWebView.evaluateJavascript(script, null)
    }

    private fun colorToHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    companion object {
        const val EXTRA_MINI_APP_ID = "mini_app_id"
        private const val STATE_WEB_VIEW = "mini_app_web_view_state"
    }
}
