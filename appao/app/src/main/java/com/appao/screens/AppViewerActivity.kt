package com.appao

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

/** Full WebView browser with persistent tabs, address bar, page menu and bottom navigation. */
class AppViewerActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var progress: View
    private lateinit var address: EditText
    private lateinit var favicon: ImageView
    private var rendererRecoveryAttempted = false
    private var tabId: String = ""
    private var isSubmittingAddress = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_app_viewer)
        SystemBarHelper.sync(this)

        web = findViewById(R.id.webView)
        progress = findViewById(R.id.appProgress)
        address = findViewById(R.id.browserAddress)
        favicon = findViewById(R.id.browserFavicon)
        web.transitionName = intent.getStringExtra("transition_name") ?: "browser_tab_preview"
        applyProgressAccent()

        IconLoader.applySvg(findViewById(R.id.browserAddressAiIcon), "sparkles", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.browserFireIcon), "flame", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.appViewerMoreIcon), "menu", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.browserBackIcon), "arrow_left", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.browserHomeIcon), "home", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.browserBottomAiIcon), "sparkles", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.browserBottomFireIcon), "flame", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.browserNewTabIcon), "plus", R.color.iconTint)

        tabId = intent.getStringExtra("browser_tab_id").orEmpty()
        val incomingUrl = intent.getStringExtra("url")?.trim().orEmpty()
        if (tabId.isBlank()) tabId = BrowserTabStore.create(this, normalizeTarget(incomingUrl.ifBlank { "https://duckduckgo.com/" })).id
        val storedTab = BrowserTabStore.tabs(this).firstOrNull { it.id == tabId }
        val initialUrl = normalizeTarget(incomingUrl.ifBlank { storedTab?.url ?: "https://duckduckgo.com/" })
        BrowserTabStore.updatePage(this, tabId, initialUrl, storedTab?.title ?: "Nova aba")

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
        }
        web.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false

        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, faviconBitmap: Bitmap?) {
                if (!address.hasFocus()) address.setText(url)
                progress.alpha = 1f
            }

            override fun onPageFinished(view: WebView, url: String) {
                injectThemeObserver(view)
                if (!address.hasFocus()) address.setText(url)
                val title = view.title?.take(250).orEmpty().ifBlank { hostFor(url).ifBlank { "Nova aba" } }
                lifecycleScope.launch(Dispatchers.IO) {
                    BrowserTabStore.updatePage(this@AppViewerActivity, tabId, url, title)
                }
                loadFavicon(url)
                updateTabCounters()
                view.postDelayed({ savePageSnapshot() }, 320L)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url.toString()
                return when (request.url.scheme?.lowercase()) {
                    "http", "https", "about", "data", "javascript", "file", "blob" -> false
                    else -> {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        true
                    }
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) Toast.makeText(this@AppViewerActivity, "Não foi possível carregar a página", Toast.LENGTH_SHORT).show()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (isFinishing) return true
                runCatching { view.destroy() }
                if (!rendererRecoveryAttempted) {
                    rendererRecoveryAttempted = true
                    recreate()
                } else finish()
                return true
            }
        }
        web.addJavascriptInterface(HtmlThemeBridge(), "AppAoTheme")
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val target = (resources.displayMetrics.widthPixels * newProgress / 100f).toInt()
                val lp = progress.layoutParams
                lp.width = target.coerceIn(0, resources.displayMetrics.widthPixels)
                progress.layoutParams = lp
                progress.alpha = if (newProgress >= 100) 0f else 1f
                if (newProgress >= 100) progress.postDelayed({ progress.alpha = 0f }, 120)
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) BrowserTabStore.updatePage(this@AppViewerActivity, tabId, web.url.orEmpty(), title.take(250))
            }
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                NativeHtmlDialog.alert(this@AppViewerActivity, "", message) { result.confirm() }; return true
            }
            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                NativeHtmlDialog.confirm(this@AppViewerActivity, "", message) { ok -> if (ok) result.confirm() else result.cancel() }; return true
            }
            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean {
                NativeHtmlDialog.prompt(this@AppViewerActivity, "", message, defaultValue) { value -> if (value == null) result.cancel() else result.confirm(value) }; return true
            }
        }

        address.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                loadAddressFromInput(); true
            } else false
        }
        address.setOnFocusChangeListener { _, focused ->
            if (focused) address.post { address.selectAll() }
        }
        findViewById<View>(R.id.browserAddressPill).setOnClickListener { address.requestFocus() }
        findViewById<View>(R.id.browserAddressAi).setOnClickListener { askAiAboutPage() }
        findViewById<View>(R.id.browserFire).setOnClickListener { clearBrowserData() }
        findViewById<View>(R.id.browserBottomFire).setOnClickListener { clearBrowserData() }
        findViewById<View>(R.id.browserTabsTop).setOnClickListener { openTabs() }
        findViewById<View>(R.id.browserBottomTabs).setOnClickListener { openTabs() }
        findViewById<View>(R.id.appViewerMore).setOnClickListener { showPageMenu(it) }
        findViewById<View>(R.id.browserBack).setOnClickListener { if (web.canGoBack()) web.goBack() else finish() }
        findViewById<View>(R.id.browserHome).setOnClickListener { web.loadUrl("https://duckduckgo.com/") }
        findViewById<View>(R.id.browserBottomAi).setOnClickListener { askAiAboutPage() }
        findViewById<View>(R.id.browserNewTab).setOnClickListener { openNewTab() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else finish() }
        })

        progress.post {
            if (progress.layoutParams.width == 0) progress.layoutParams.width = (resources.displayMetrics.widthPixels * .06f).toInt()
            progress.alpha = 0f
        }
        address.setText(initialUrl)
        loadFavicon(initialUrl)
        updateTabCounters()
        web.loadUrl(initialUrl)
    }

    private fun loadAddressFromInput() {
        val raw = address.text.toString().trim()
        if (raw.isBlank()) return
        isSubmittingAddress = true
        val target = normalizeTarget(raw)
        address.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(address.windowToken, 0)
        web.loadUrl(target)
        isSubmittingAddress = false
    }

    private fun normalizeTarget(value: String): String {
        val input = value.trim()
        if (input.isBlank()) return "https://duckduckgo.com/"
        if (input.startsWith("http://", true) || input.startsWith("https://", true)) return input
        val isHost = !input.contains(' ') && input.contains('.') && !input.startsWith(".") && input.substringAfterLast('.', "").length in 2..20
        return if (isHost) "https://$input" else "https://duckduckgo.com/?q=" + URLEncoder.encode(input, "UTF-8")
    }

    private fun openTabs() {
        savePageSnapshot()
        val launch = Intent(this, BrowserTabsActivity::class.java)
            .putExtra("source_tab_id", tabId)
        runCatching {
            val options = ActivityOptions.makeSceneTransitionAnimation(this, web, "browser_tab_preview")
            startActivity(launch, options.toBundle())
        }.onFailure {
            startActivity(launch)
            overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
        }
    }

    private fun openNewTab() {
        savePageSnapshot()
        val tab = BrowserTabStore.create(this, "https://duckduckgo.com/")
        startActivity(Intent(this, AppViewerActivity::class.java).apply {
            putExtra("browser_mode", true); putExtra("browser_tab_id", tab.id); putExtra("url", tab.url)
        })
        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
    }

    private fun askAiAboutPage() {
        val query = web.url?.takeIf { it.isNotBlank() }?.let { "Ajuda-me a compreender esta página: $it" }.orEmpty()
        startActivity(Intent(this, SearchActivity::class.java).putExtra("query", query))
        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
    }

    private fun clearBrowserData() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Limpar navegação")
            .setMessage("Fechar os separadores e limpar o histórico de navegação deste browser?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Limpar") { _, _ ->
                web.stopLoading()
                web.clearHistory()
                web.clearCache(true)
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                BrowserTabStore.clear(this)
                val fresh = BrowserTabStore.create(this, "https://duckduckgo.com/")
                tabId = fresh.id
                updateTabCounters()
                web.loadUrl(fresh.url)
            }.show()
    }

    private fun showPageMenu(anchor: View) {
        NativePopupMenu.show(anchor, listOf(
            NativePopupMenu.Item(1, "Novo separador", svg = "plus"),
            NativePopupMenu.Item(2, "Todos os separadores", svg = "apps"),
            NativePopupMenu.Item(3, "Abrir no navegador externo", svg = "arrow_top_right"),
            NativePopupMenu.Item(4, "Copiar ligação", svg = "link"),
            NativePopupMenu.Item(5, "Partilhar ligação", svg = "share"),
            NativePopupMenu.Item(6, "Recarregar", svg = "refresh")
        ), alignLeft = false, placeAbove = false) { item ->
            val currentUrl = web.url?.takeIf { it.isNotBlank() } ?: "https://duckduckgo.com/"
            when (item.id) {
                1 -> openNewTab()
                2 -> openTabs()
                3 -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))) }
                    .onFailure { AppAoToast.show(this, "Não foi possível abrir o navegador") }
                4 -> {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Ligação", currentUrl)); AppAoToast.show(this, "Ligação copiada")
                }
                5 -> runCatching {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, currentUrl) }, "Partilhar ligação"))
                }.onFailure { AppAoToast.show(this, "Não foi possível partilhar") }
                6 -> web.reload()
            }
        }
    }

    private fun loadFavicon(url: String) {
        val iconUrl = BrowserTabStore.faviconFor(url)
        if (iconUrl.isBlank()) {
            IconLoader.applySvg(favicon, "search", R.color.iconTint)
        } else Glide.with(this).load(iconUrl).circleCrop().placeholder(R.drawable.logo).into(favicon)
    }

    private fun updateTabCounters() {
        val count = BrowserTabStore.count(this).coerceAtMost(99).toString()
        findViewById<TextView>(R.id.browserTabsCountTop).text = count
        findViewById<TextView>(R.id.browserTabsCountBottom).text = count
    }

    private fun savePageSnapshot() {
        if (!::web.isInitialized || tabId.isBlank() || web.width <= 0 || web.height <= 0) return
        val currentUrl = web.url.orEmpty()
        if (!currentUrl.startsWith("http", true)) return
        val title = web.title?.take(250).orEmpty().ifBlank { hostFor(currentUrl).ifBlank { "Nova aba" } }
        val width = minOf(web.width, 720).coerceAtLeast(1)
        val height = minOf(web.height, 1280).coerceAtLeast(1)
        val bitmap = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return
        runCatching {
            val canvas = Canvas(bitmap)
            canvas.scale(width.toFloat() / web.width, height.toFloat() / web.height)
            web.draw(canvas)
            val path = BrowserTabStore.saveSnapshot(this, tabId, bitmap)
            BrowserTabStore.updatePage(this, tabId, currentUrl, title, path)
        }
        bitmap.recycle()
    }

    @Suppress("SetJavaScriptEnabled")
    private fun injectThemeObserver(view: WebView) {
        view.evaluateJavascript("""
            (function(){
              if(window.__appAoThemeObserverInstalled){ return; }
              window.__appAoThemeObserverInstalled=true;
              function send(){
                try{
                  var root=document.documentElement, body=document.body;
                  var cs=getComputedStyle(root);
                  var bg=(cs.getPropertyValue('--bg')||'').trim();
                  if(!bg && body){ bg=getComputedStyle(body).backgroundColor; }
                  var meta=document.querySelector('meta[name=theme-color]');
                  if(!bg && meta){ bg=meta.content||''; }
                  var dark=(root.getAttribute('data-theme')==='dark');
                  if(window.AppAoTheme && bg) window.AppAoTheme.setTheme(bg,dark);
                }catch(e){}
              }
              send();
              new MutationObserver(send).observe(document.documentElement,{attributes:true,attributeFilter:['data-theme','class','style']});
            })();
        """.trimIndent(), null)
    }

    private inner class HtmlThemeBridge {
        @JavascriptInterface fun setTheme(color: String, dark: Boolean) {
            runOnUiThread {
                try {
                    val normalized = color.trim()
                    val parsed: Int? = if (normalized.startsWith("#")) Color.parseColor(normalized) else null
                    if (parsed != null) SystemBarHelper.syncColor(this@AppViewerActivity, parsed) else SystemBarHelper.sync(this@AppViewerActivity, dark)
                } catch (_: Throwable) { SystemBarHelper.sync(this@AppViewerActivity) }
            }
        }
    }

    private fun applyProgressAccent() {
        if (::progress.isInitialized) progress.background = AccentColorManager.primaryShape(this, android.graphics.drawable.GradientDrawable.RECTANGLE, 2f)
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
        applyProgressAccent()
        if (::web.isInitialized) {
            web.post { injectThemeObserver(web) }
            updateTabCounters()
        }
    }

    override fun onPause() {
        savePageSnapshot()
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { web.stopLoading(); web.webChromeClient = null; web.destroy() }
        super.onDestroy()
    }

    private fun hostFor(url: String): String = runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
}
