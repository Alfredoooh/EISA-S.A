package com.appao

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebResourceResponse
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
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
import java.io.ByteArrayInputStream
import java.net.URLEncoder

/** Full in-app WebView browser with persistent tabs, address bar, native browser menu and downloads. */
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

        IconLoader.applyIcon(findViewById(R.id.browserAddressAiIcon), "sparkles", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.browserFireIcon), "flame", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.appViewerMoreIcon), "menu", R.color.iconTint)

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
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
        }
        applyBrowserPreferences()
        web.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false

        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, faviconBitmap: Bitmap?) {
                if (!address.hasFocus()) address.setText(url)
                progress.alpha = 1f
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!address.hasFocus()) address.setText(url)
                val title = view.title?.take(250).orEmpty().ifBlank { hostFor(url).ifBlank { "Nova aba" } }
                BrowsingHistoryStore.record(this@AppViewerActivity, url, title)
                lifecycleScope.launch(Dispatchers.IO) {
                    BrowserTabStore.updatePage(this@AppViewerActivity, tabId, url, title)
                }
                loadFavicon(url)
                updateTabCounters()
                view.postDelayed({ savePageSnapshot() }, 320L)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (BrowserPreferences.adBlockEnabled(this@AppViewerActivity) && isKnownAdRequest(request.url)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
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

        web.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val uri = runCatching { Uri.parse(url) }.getOrNull()
            if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
                AppAoToast.show(this, "Ligação de download inválida")
                return@DownloadListener
            }
            runCatching {
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(uri).apply {
                    setTitle(fileName)
                    setDescription(hostFor(url).ifBlank { "Download do browser" })
                    setMimeType(mimeType)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
                    CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { addRequestHeader("Cookie", it) }
                }
                val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.enqueue(request)
                AppAoToast.show(this, "Download iniciado")
            }.onFailure { AppAoToast.show(this, "Não foi possível iniciar o download") }
        })

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
        findViewById<View>(R.id.browserTabsTop).setOnClickListener { openTabs() }
        findViewById<View>(R.id.appViewerMore).setOnClickListener { showPageMenu(it) }

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
                BrowsingHistoryStore.clear(this)
                ConversationHistoryStore.clear(this)
                runCatching { android.webkit.WebStorage.getInstance().deleteAllData() }
                val fresh = BrowserTabStore.create(this, "https://duckduckgo.com/")
                tabId = fresh.id
                updateTabCounters()
                web.loadUrl(fresh.url)
            }.show()
    }

    private fun showPageMenu(anchor: View) {
        val adsEnabled = BrowserPreferences.adBlockEnabled(this)
        val forceDarkEnabled = BrowserPreferences.forceDarkEnabled(this)
        NativePopupMenu.show(anchor, listOf(
            NativePopupMenu.Item(1, "Recuar", iconName = "arrow_go_back", checked = false),
            NativePopupMenu.Item(2, "Avançar", iconName = "arrow_go_forward", checked = false),
            NativePopupMenu.Item(3, "Recarregar", iconName = "refresh"),
            NativePopupMenu.Item(4, "Novo separador", iconName = "plus"),
            NativePopupMenu.Item(5, "Todos os separadores", iconName = "apps"),
            NativePopupMenu.Item(6, "Diminuir zoom", iconName = "zoom_out"),
            NativePopupMenu.Item(7, "Aumentar zoom", iconName = "zoom_in"),
            NativePopupMenu.Item(8, "Repor zoom", iconName = "contrast"),
            NativePopupMenu.Item(9, if (adsEnabled) "Bloqueador de anúncios: ligado" else "Bloqueador de anúncios: desligado", iconName = "shield_check", checked = adsEnabled),
            NativePopupMenu.Item(10, if (forceDarkEnabled) "Forçar tema escuro: ligado" else "Forçar tema escuro: desligado", iconName = "moon", checked = forceDarkEnabled),
            NativePopupMenu.Item(11, "Downloads", iconName = "download"),
            NativePopupMenu.Item(12, "Copiar ligação", iconName = "link"),
            NativePopupMenu.Item(13, "Partilhar ligação", iconName = "share"),
            NativePopupMenu.Item(14, "Limpar dados de navegação", iconName = "delete_bin"),
            NativePopupMenu.Item(15, "Pesquisar nesta página", iconName = "search")
        ), alignLeft = false, placeAbove = true) { item ->
            val currentUrl = web.url?.takeIf { it.isNotBlank() } ?: "https://duckduckgo.com/"
            when (item.id) {
                1 -> if (web.canGoBack()) web.goBack() else AppAoToast.show(this, "Não há página anterior")
                2 -> if (web.canGoForward()) web.goForward() else AppAoToast.show(this, "Não há página seguinte")
                3 -> web.reload()
                4 -> openNewTab()
                5 -> openTabs()
                6 -> {
                    if (!web.zoomOut()) web.settings.textZoom = (web.settings.textZoom - 10).coerceAtLeast(50)
                }
                7 -> {
                    if (!web.zoomIn()) web.settings.textZoom = (web.settings.textZoom + 10).coerceAtMost(200)
                }
                8 -> { web.settings.textZoom = 100; web.setInitialScale(0); web.reload() }
                9 -> {
                    BrowserPreferences.setAdBlockEnabled(this, !adsEnabled)
                    AppAoToast.show(this, if (!adsEnabled) "Bloqueador de anúncios ativado" else "Bloqueador de anúncios desativado")
                    web.reload()
                }
                10 -> {
                    BrowserPreferences.setForceDarkEnabled(this, !forceDarkEnabled)
                    applyBrowserPreferences()
                    AppAoToast.show(this, if (!forceDarkEnabled) "Tema escuro de sites ativado" else "Tema escuro de sites desativado")
                    web.reload()
                }
                11 -> startActivity(Intent(this, BrowserDownloadsActivity::class.java)).also { overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit) }
                12 -> {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Ligação", currentUrl))
                    AppAoToast.show(this, "Ligação copiada")
                }
                13 -> runCatching {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, currentUrl) }, "Partilhar ligação"))
                }.onFailure { AppAoToast.show(this, "Não foi possível partilhar") }
                14 -> clearBrowserData()
                15 -> findTextInPage()
            }
        }
    }

    private fun findTextInPage() {
        val queryInput = EditText(this).apply {
            hint = "Texto a encontrar"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.text))
            setHintTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dim))
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Pesquisar nesta página")
            .setView(queryInput)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Pesquisar") { _, _ ->
                val query = queryInput.text?.toString().orEmpty().trim()
                if (query.isNotBlank()) {
                    web.clearMatches()
                    web.findAllAsync(query)
                    AppAoToast.show(this, "A procurar na página")
                }
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun isKnownAdRequest(uri: Uri): Boolean {
        val host = uri.host?.lowercase(java.util.Locale.ROOT)?.removePrefix("www.") ?: return false
        val blockedHosts = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
            "adnxs.com", "adform.net", "taboola.com", "outbrain.com", "criteo.com", "teads.tv",
            "scorecardresearch.com", "adsrvr.org", "amazon-adsystem.com", "adroll.com", "pubmatic.com",
            "openx.net", "rubiconproject.com", "quantserve.com", "moatads.com"
        )
        return blockedHosts.any { host == it || host.endsWith(".$it") }
    }

    @Suppress("DEPRECATION")
    private fun applyBrowserPreferences() {
        if (!::web.isInitialized) return
        val forceDark = BrowserPreferences.forceDarkEnabled(this)
        val settings = web.settings
        runCatching {
            // Prefer WebView's algorithmic darkening API when the installed provider supports it.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, forceDark)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                settings.forceDark = if (forceDark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
            }
        }
    }

    private fun loadFavicon(url: String) {
        val iconUrl = BrowserTabStore.faviconFor(url)
        if (iconUrl.isBlank()) {
            IconLoader.applyIcon(favicon, "search", R.color.iconTint)
        } else Glide.with(this).load(iconUrl).circleCrop().placeholder(R.drawable.logo).into(favicon)
    }

    private fun updateTabCounters() {
        val count = BrowserTabStore.count(this).coerceAtMost(99).toString()
        findViewById<TextView>(R.id.browserTabsCountTop).text = count
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

    private fun applyProgressAccent() {
        if (::progress.isInitialized) progress.background = AccentColorManager.primaryShape(this, android.graphics.drawable.GradientDrawable.RECTANGLE, 2f)
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
        applyProgressAccent()
        if (::web.isInitialized) {
            applyBrowserPreferences()
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
