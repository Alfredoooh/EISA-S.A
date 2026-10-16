package com.appao

import android.annotation.SuppressLint
import android.app.Activity
import android.Manifest
import android.app.ActivityOptions
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.view.Gravity
import android.text.Editable
import android.text.TextWatcher
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
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
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
    private var reusedWebView = false
    private lateinit var findBar: View
    private lateinit var findInput: EditText
    private var findMode = false
    private var browserBarHidden = false
    private var addressSuggestionsPopup: PopupWindow? = null
    private val addressSuggestionHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var addressSuggestionRunnable: Runnable? = null
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingWebPermissions: Array<String> = emptyArray()
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String = ""
    private var pendingWebGrantResources: Array<String> = emptyArray()
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private var currentZoomPercent = 100
    private val REQUEST_WEB_MEDIA = 7301
    private val REQUEST_WEB_LOCATION = 7302

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback
            fileChooserCallback = null
            if (callback != null) {
                val selected = if (result.resultCode == Activity.RESULT_OK) {
                    WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
                } else null
                callback.onReceiveValue(selected)
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_app_viewer)
        SystemBarHelper.sync(this)

        val root = findViewById<android.widget.FrameLayout>(R.id.appViewerRoot)
        val placeholder = findViewById<WebView>(R.id.webView)
        progress = findViewById(R.id.appProgress)
        address = findViewById(R.id.browserAddress)
        favicon = findViewById(R.id.browserFavicon)
        findBar = findViewById(R.id.browserFindBar)
        findInput = findViewById(R.id.browserFindInput)

        tabId = intent.getStringExtra("browser_tab_id").orEmpty()
        val incomingUrl = intent.getStringExtra("url")?.trim().orEmpty()
        if (tabId.isBlank()) tabId = BrowserTabStore.create(this, normalizeTarget(incomingUrl.ifBlank { "https://duckduckgo.com/" })).id
        val storedTab = BrowserTabStore.tabs(this).firstOrNull { it.id == tabId }
        val initialUrl = normalizeTarget(incomingUrl.ifBlank { storedTab?.url ?: "https://duckduckgo.com/" })

        val pooledWebView = BrowserWebViewPool.get(tabId)
        if (pooledWebView != null && pooledWebView !== placeholder) {
            val parent = placeholder.parent as? ViewGroup
            val index = parent?.indexOfChild(placeholder) ?: -1
            val params = placeholder.layoutParams
            parent?.removeView(placeholder)
            (pooledWebView.parent as? ViewGroup)?.removeView(pooledWebView)
            pooledWebView.id = R.id.webView
            if (index >= 0) root.addView(pooledWebView, index.coerceAtMost(root.childCount), params)
            else root.addView(pooledWebView, params)
            web = pooledWebView
            reusedWebView = true
        } else {
            web = placeholder
            BrowserWebViewPool.put(tabId, web, this)
        }
        BrowserWebViewPool.setOwner(tabId, this)
        web.transitionName = intent.getStringExtra("transition_name") ?: "browser_tab_preview"
        applyProgressAccent()

        IconLoader.applyIcon(findViewById(R.id.browserFireIcon), "flame", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.appViewerMoreIcon), "menu", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.browserFindCloseIcon), "close", R.color.iconTint)

        if (!reusedWebView) {
            BrowserTabStore.updatePage(this, tabId, initialUrl, storedTab?.title ?: "Nova aba")
            web.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                // A browser must not let a normal web page read arbitrary local files.
                // Uploads still use the system document picker through onShowFileChooser.
                allowFileAccess = false
                allowContentAccess = true
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = true
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
        }
        applyBrowserPreferences()
        web.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        configureWebViewClients()
        applyAddressBarPosition()
        configureAddressInput()
        configureFindInPage()

        findViewById<View>(R.id.browserAddressPill).setOnClickListener { address.requestFocus() }
        findViewById<View>(R.id.browserFire).setOnClickListener { clearBrowserData() }
        findViewById<View>(R.id.browserTabsTop).setOnClickListener { openTabs() }
        findViewById<View>(R.id.appViewerMore).setOnClickListener { showPageMenu(it) }

        web.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (findMode || address.hasFocus() || isFinishing) return@setOnScrollChangeListener
            val delta = scrollY - oldScrollY
            if (delta > dp(4)) setBrowserBarHidden(true)
            else if (delta < -dp(4)) setBrowserBarHidden(false)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    findMode -> closeFindInPage()
                    web.canGoBack() -> web.goBack()
                    else -> closeCurrentTabAndGoHome()
                }
            }
        })

        progress.post {
            if (progress.layoutParams.width == 0) progress.layoutParams.width = (resources.displayMetrics.widthPixels * .06f).toInt()
            progress.alpha = 0f
        }
        val activeUrl = if (reusedWebView) web.url.orEmpty().ifBlank { initialUrl } else initialUrl
        address.setText(activeUrl)
        loadFavicon(activeUrl)
        updateTabCounters()
        if (reusedWebView) {
            BrowserTabStore.updatePage(this, tabId, activeUrl, web.title?.take(250).orEmpty().ifBlank { storedTab?.title ?: "Nova aba" })
        } else {
            web.loadUrl(initialUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebViewClients() {
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, faviconBitmap: Bitmap?) {
                if (browserBarHidden && !address.hasFocus() && !findMode) setBrowserBarHidden(false)
                if (!address.hasFocus()) address.setText(url)
                progress.alpha = 1f
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!address.hasFocus()) address.setText(url)
                val title = view.title?.take(250).orEmpty().ifBlank { hostFor(url).ifBlank { "Nova aba" } }
                BrowsingHistoryStore.record(this@AppViewerActivity, url, title)
                lifecycleScope.launch(Dispatchers.IO) { BrowserTabStore.updatePage(this@AppViewerActivity, tabId, url, title) }
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
                return when (request.url.scheme?.lowercase()) {
                    "http", "https", "about", "data", "javascript", "file", "blob" -> false
                    else -> { runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }; true }
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) Toast.makeText(this@AppViewerActivity, "Não foi possível carregar a página", Toast.LENGTH_SHORT).show()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (isFinishing) return true
                runCatching { BrowserWebViewPool.removeAndDestroy(tabId) }
                if (!rendererRecoveryAttempted) { rendererRecoveryAttempted = true; recreate() } else closeCurrentTabAndGoHome()
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val width = resources.displayMetrics.widthPixels
                val lp = progress.layoutParams
                lp.width = (width * newProgress / 100f).toInt().coerceIn(0, width)
                progress.layoutParams = lp
                progress.alpha = if (newProgress >= 100) 0f else 1f
                if (newProgress >= 100) progress.postDelayed({ progress.alpha = 0f }, 120)
            }
            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                return try {
                    fileChooserLauncher.launch(fileChooserParams.createIntent())
                    true
                } catch (_: Exception) {
                    fileChooserCallback = null
                    filePathCallback.onReceiveValue(null)
                    false
                }
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
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { handleWebPermissionRequest(request) }
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (pendingWebPermissionRequest === request) pendingWebPermissionRequest = null
            }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                runOnUiThread { handleGeolocationRequest(origin, callback) }
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
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                AppAoToast.show(this, "Download iniciado")
            }.onFailure { AppAoToast.show(this, "Não foi possível iniciar o download") }
        })
    }

    private fun configureAddressInput() {
        address.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) { loadAddressFromInput(); true } else false
        }
        address.setOnFocusChangeListener { _, focused ->
            setAddressSearchMode(focused)
            if (focused) {
                address.post { address.selectAll() }
                showAddressSuggestions(address.text?.toString().orEmpty())
            } else {
                addressSuggestionHandler.postDelayed({ hideAddressSuggestions() }, 120L)
            }
        }
        address.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!address.hasFocus()) return
                addressSuggestionRunnable?.let(addressSuggestionHandler::removeCallbacks)
                val query = s?.toString().orEmpty()
                addressSuggestionRunnable = Runnable { showAddressSuggestions(query) }
                addressSuggestionRunnable?.let { addressSuggestionHandler.postDelayed(it, 100L) }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun setAddressSearchMode(active: Boolean) {
        findViewById<View>(R.id.browserFire).visibility = if (active) View.GONE else View.VISIBLE
        findViewById<View>(R.id.browserTabsTop).visibility = if (active) View.GONE else View.VISIBLE
        findViewById<View>(R.id.appViewerMore).visibility = if (active) View.GONE else View.VISIBLE
        findViewById<View>(R.id.browserAddressPill).animate().cancel()
        val pill = findViewById<View>(R.id.browserAddressPill)
        pill.animate().scaleX(1f).setDuration(240L).setInterpolator(Curves.IOS).start()
        if (active) setBrowserBarHidden(false)
        if (!active) hideAddressSuggestions()
    }

    private fun showAddressSuggestions(query: String) {
        if (!address.hasFocus()) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(22).toFloat()
                setColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dialogSurface))
                setStroke(dp(1), ContextCompat.getColor(this@AppViewerActivity, R.color.border))
            }
        }
        val trimmed = query.trim()
        fun addSuggestion(title: String, detail: String, iconName: String, target: String) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(dp(10), dp(5), dp(10), dp(5))
                isClickable = true
                isFocusable = true
                setBackgroundResource(R.drawable.bg_transparent_pressed)
                setOnClickListener {
                    hideAddressSuggestions()
                    address.setText(if (target.isBlank()) trimmed else target)
                    address.setSelection(address.text.length)
                    loadAddressFromInput()
                }
            }
            val icon = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)); scaleType = ImageView.ScaleType.CENTER_INSIDE; IconLoader.applyIcon(this, iconName, R.color.iconTint) }
            row.addView(icon)
            val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
            words.addView(TextView(this).apply { text = title; maxLines = 1; textSize = 14f; setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.text)) })
            if (detail.isNotBlank()) words.addView(TextView(this).apply { text = detail; maxLines = 1; textSize = 11.5f; setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dim)) })
            row.addView(words, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            panel.addView(row)
        }
        if (trimmed.isNotBlank()) addSuggestion("Pesquisar “$trimmed”", BrowserPreferences.searchEngine(this), "search", trimmed)
        val recent = BrowsingHistoryStore.all(this).filter {
            trimmed.isBlank() || it.title.contains(trimmed, true) || it.url.contains(trimmed, true)
        }.take(if (trimmed.isBlank()) 5 else 4)
        recent.forEach { addSuggestion(it.title.ifBlank { it.url }, it.url, "history", it.url) }
        if (recent.isEmpty() && trimmed.isBlank()) addSuggestion("Nova pesquisa", "Escreve uma palavra ou endereço", "search", "")

        val width = (resources.displayMetrics.widthPixels - dp(20)).coerceAtLeast(dp(240))
        hideAddressSuggestions()
        addressSuggestionsPopup = PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            isOutsideTouchable = true
            isFocusable = false
            elevation = dp(12).toFloat()
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            showAsDropDown(findViewById(R.id.browserTopBar), -dp(2), dp(3))
        }
    }

    private fun hideAddressSuggestions() {
        addressSuggestionRunnable?.let(addressSuggestionHandler::removeCallbacks)
        addressSuggestionsPopup?.dismiss()
        addressSuggestionsPopup = null
    }

    private fun configureFindInPage() {
        findInput.setOnEditorActionListener { _, _, _ ->
            val query = findInput.text?.toString().orEmpty().trim()
            web.clearMatches()
            if (query.isNotBlank()) web.findAllAsync(query)
            true
        }
        findInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                web.clearMatches()
                val query = s?.toString().orEmpty().trim()
                if (query.isNotBlank()) web.findAllAsync(query)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        findViewById<View>(R.id.browserFindClose).setOnClickListener { closeFindInPage() }
    }

    private fun openFindInPage() {
        findMode = true
        hideAddressSuggestions()
        setBrowserBarHidden(false)
        findViewById<View>(R.id.browserTopBar).visibility = View.GONE
        findBar.visibility = View.VISIBLE
        applyAddressBarPosition()
        findInput.setText("")
        findInput.requestFocus()
        findInput.post { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun closeFindInPage() {
        findMode = false
        web.clearMatches()
        findBar.visibility = View.GONE
        findViewById<View>(R.id.browserTopBar).visibility = View.VISIBLE
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(findInput.windowToken, 0)
        findInput.clearFocus()
        applyAddressBarPosition()
    }

    private fun applyAddressBarPosition() {
        if (!::web.isInitialized) return
        val topBar = findViewById<View>(R.id.browserTopBar)
        val bottom = BrowserPreferences.addressBarPosition(this).equals("bottom", true)
        fun place(view: View) {
            val params = (view.layoutParams as? android.widget.FrameLayout.LayoutParams) ?: android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58))
            params.gravity = if (bottom) Gravity.BOTTOM else Gravity.TOP
            params.topMargin = if (bottom) 0 else 0
            params.bottomMargin = 0
            view.layoutParams = params
        }
        place(topBar)
        place(findBar)
        val webParams = web.layoutParams as? android.widget.FrameLayout.LayoutParams
        webParams?.let {
            it.topMargin = if (!bottom && !browserBarHidden) dp(58) else 0
            it.bottomMargin = if (bottom && !browserBarHidden) dp(58) else 0
            web.layoutParams = it
        }
        val progressParams = progress.layoutParams as? android.widget.FrameLayout.LayoutParams
        progressParams?.let {
            it.gravity = Gravity.TOP or Gravity.START
            it.topMargin = if (bottom) 0 else dp(58)
            progress.layoutParams = it
        }
    }

    private fun setBrowserBarHidden(hidden: Boolean) {
        if (address.hasFocus() || findMode) return
        val topBar = findViewById<View>(R.id.browserTopBar)
        if (topBar.visibility != View.VISIBLE) return
        if (browserBarHidden == hidden) return
        browserBarHidden = hidden
        val bottom = BrowserPreferences.addressBarPosition(this).equals("bottom", true)
        val direction = if (bottom) 1f else -1f
        topBar.animate().cancel()
        topBar.animate().translationY(if (hidden) direction * dp(62) else 0f)
            .setDuration(280L).setInterpolator(Curves.IOS).start()
        val webParams = web.layoutParams as? android.widget.FrameLayout.LayoutParams
        webParams?.let {
            if (bottom) it.bottomMargin = if (hidden) 0 else dp(58) else it.topMargin = if (hidden) 0 else dp(58)
            web.layoutParams = it
        }
    }

    private fun handleWebPermissionRequest(request: PermissionRequest) {
        val host = hostFor(request.origin.toString())
        if (host.isBlank()) { request.deny(); return }
        if (!BrowserPreferences.sitePermissionAllowed(this, host, "media")) {
            NativeM3Dialog.confirm(this, "Permitir acesso ao microfone/câmara?", "$host pediu acesso a uma função multimédia. Só será permitido depois da tua confirmação.", positive = "Permitir") { allow ->
                BrowserPreferences.setSitePermission(this, host, "media", allow)
                if (allow) requestMediaRuntimePermissions(request) else request.deny()
            }
        } else requestMediaRuntimePermissions(request)
    }

    private fun requestMediaRuntimePermissions(request: PermissionRequest) {
        val resources = request.resources.filter {
            it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
        }.toTypedArray()
        if (resources.isEmpty()) { request.deny(); return }
        val required = mutableListOf<String>()
        if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) required += Manifest.permission.CAMERA
        if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) required += Manifest.permission.RECORD_AUDIO
        if (required.isEmpty()) request.grant(resources) else {
            pendingWebPermissionRequest = request
            pendingWebGrantResources = resources
            pendingWebPermissions = required.toTypedArray()
            ActivityCompat.requestPermissions(this, pendingWebPermissions, REQUEST_WEB_MEDIA)
        }
    }

    private fun handleGeolocationRequest(origin: String, callback: GeolocationPermissions.Callback) {
        val host = hostFor(origin)
        if (host.isBlank()) { callback.invoke(origin, false, false); return }
        if (!BrowserPreferences.sitePermissionAllowed(this, host, "location")) {
            NativeM3Dialog.confirm(this, "Permitir localização?", "$host pediu acesso à tua localização.", positive = "Permitir") { allow ->
                BrowserPreferences.setSitePermission(this, host, "location", allow)
                if (allow) requestSystemLocation(origin, callback) else callback.invoke(origin, false, false)
            }
        } else requestSystemLocation(origin, callback)
    }

    private fun requestSystemLocation(origin: String, callback: GeolocationPermissions.Callback) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            callback.invoke(origin, true, false)
        } else {
            pendingGeoOrigin = origin
            pendingGeoCallback = callback
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_WEB_LOCATION)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_WEB_MEDIA -> {
                val request = pendingWebPermissionRequest
                pendingWebPermissionRequest = null
                if (request != null && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) request.grant(pendingWebGrantResources) else request?.deny()
                pendingWebGrantResources = emptyArray()
                pendingWebPermissions = emptyArray()
            }
            REQUEST_WEB_LOCATION -> {
                val granted = grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }
                pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
                pendingGeoCallback = null
                pendingGeoOrigin = ""
            }
        }
    }

    private fun closeCurrentTabAndGoHome() {
        hideAddressSuggestions()
        BrowserTabStore.remove(this, tabId)
        BrowserWebViewPool.removeAndDestroy(tabId)
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
        overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit)
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
        return if (isHost) "https://$input" else BrowserPreferences.searchUrl(this, input)
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
                val previousId = tabId
                BrowserTabStore.clear(this)
                BrowsingHistoryStore.clear(this)
                ConversationHistoryStore.clear(this)
                runCatching { android.webkit.WebStorage.getInstance().deleteAllData() }
                BrowserWebViewPool.clearExcept(previousId)
                val fresh = BrowserTabStore.create(this, "https://www.google.com/")
                BrowserWebViewPool.rekey(previousId, fresh.id, web, this)
                tabId = fresh.id
                updateTabCounters()
                web.loadUrl(fresh.url)
            }.show()
    }

    /** Browser controls are presented in a native, bottom-aligned sheet. The action toolbar stays fixed while the list scrolls. */
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

    private fun showZoomSheet() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(24))
            setBackgroundColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dialogSurface))
        }
        root.addView(TextView(this).apply {
            text = "Zoom da página"; textSize = 19f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.text))
            setPadding(0, 0, 0, dp(5))
        })
        val value = TextView(this).apply {
            text = "${currentZoomPercent}%"; textSize = 15f; gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.text))
        }
        root.addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)))
        val seek = SeekBar(this).apply {
            max = 150
            progress = (currentZoomPercent - 50).coerceIn(0, 150)
            progressTintList = ColorStateList.valueOf(AccentColorManager.resolvedOr(this@AppViewerActivity, R.color.pri))
            thumbTintList = ColorStateList.valueOf(AccentColorManager.resolvedOr(this@AppViewerActivity, R.color.pri))
        }
        root.addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val target = progress + 50
                value.text = "$target%"
                if (fromUser) {
                    val ratio = target.toFloat() / currentZoomPercent.coerceAtLeast(1)
                    runCatching { web.zoomBy(ratio) }
                    web.settings.textZoom = target
                    currentZoomPercent = target
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@AppViewerActivity).apply { text = "50%"; textSize = 12f; setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dim)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@AppViewerActivity).apply { text = "200%"; textSize = 12f; gravity = Gravity.END; setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dim)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        root.addView(TextView(this).apply {
            text = "Repor para 100%"; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(AccentColorManager.resolvedOr(this@AppViewerActivity, R.color.pri))
            setPadding(dp(10), dp(14), dp(10), dp(5))
            isClickable = true; isFocusable = true
            setOnClickListener { currentZoomPercent = 100; web.settings.textZoom = 100; runCatching { web.zoomBy(1f / web.scale.coerceAtLeast(0.01f)) }; seek.progress = 50; value.text = "100%" }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        NativeSheetDialog.show(this, root)
    }

    private fun showSitePermissions() {
        val host = hostFor(web.url.orEmpty())
        if (host.isBlank()) { AppAoToast.show(this, "Esta página não tem um domínio válido"); return }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(22))
            setBackgroundColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dialogSurface))
        }
        root.addView(TextView(this).apply {
            text = "Permissões do site"; textSize = 19f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.text))
        })
        root.addView(TextView(this).apply {
            text = host; textSize = 12.5f; setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dim))
            setPadding(0, dp(4), 0, dp(12))
        })
        fun addPermissionRow(label: String, permission: String) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(54) }
            row.addView(TextView(this@AppViewerActivity).apply {
                text = label; textSize = 14f; setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.text))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val toggle = MaterialSwitch(this).apply {
                isChecked = BrowserPreferences.sitePermissionAllowed(this@AppViewerActivity, host, permission)
                setOnCheckedChangeListener { _, checked -> BrowserPreferences.setSitePermission(this@AppViewerActivity, host, permission, checked) }
            }
            row.addView(toggle)
            root.addView(row)
        }
        addPermissionRow("Câmara e microfone", "media")
        addPermissionRow("Localização", "location")
        root.addView(TextView(this).apply {
            text = "O Android ainda pode pedir autorização ao sistema. A decisão acima é guardada separadamente para este domínio."; textSize = 12f
            setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dim)); setPadding(0, dp(12), 0, 0)
        })
        NativeSheetDialog.show(this, root)
    }

    private fun showBrowserShareSheet() {
        val url = web.url.orEmpty()
        val title = web.title?.take(150).orEmpty().ifBlank { hostFor(url) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(20))
            setBackgroundColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dialogSurface))
        }
        root.addView(TextView(this).apply {
            text = "Partilhar página"; textSize = 19f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.text))
            setPadding(dp(4), dp(3), dp(4), dp(5))
        })
        root.addView(TextView(this).apply {
            text = title + if (url.isNotBlank()) "\n$url" else ""; textSize = 12.5f; maxLines = 3
            setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dim))
            setPadding(dp(4), 0, dp(4), dp(12))
        })
        val grid = android.widget.GridLayout(this).apply { columnCount = 3; useDefaultMargins = false }
        data class ShareOption(val label: String, val icon: String, val packageName: String? = null, val copy: Boolean = false)
        val options = listOf(
            ShareOption("Copiar ligação", "link", copy = true),
            ShareOption("WhatsApp", "chatbubble-outline", "com.whatsapp"),
            ShareOption("Telegram", "paper-plane-outline", "org.telegram.messenger"),
            ShareOption("Gmail", "mail-outline", "com.google.android.gm"),
            ShareOption("Bluetooth", "bluetooth-outline", "com.android.bluetooth"),
            ShareOption("Mais aplicações", "share-social-outline")
        )
        lateinit var dialog: BottomSheetDialog
        options.forEachIndexed { index, option ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                minimumHeight = dp(86); setPadding(dp(4), dp(8), dp(4), dp(7))
                background = ContextCompat.getDrawable(this@AppViewerActivity, R.drawable.bg_sheet_item)
                isClickable = true; isFocusable = true
                setOnClickListener {
                    dialog.dismiss()
                    if (option.copy) {
                        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Ligação", url))
                        AppAoToast.show(this@AppViewerActivity, "Ligação copiada")
                    } else shareWith(option.packageName, title, url)
                }
            }
            val icon = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)); scaleType = ImageView.ScaleType.CENTER_INSIDE; IconLoader.applyIcon(this, option.icon, R.color.iconTint) }
            cell.addView(icon)
            cell.addView(TextView(this).apply { text = option.label; textSize = 11f; gravity = Gravity.CENTER; maxLines = 2; setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.text)); setPadding(0, dp(6), 0, 0) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            grid.addView(cell, android.widget.GridLayout.LayoutParams().apply { width = 0; height = dp(96); columnSpec = android.widget.GridLayout.spec(index % 3, 1f); rowSpec = android.widget.GridLayout.spec(index / 3); setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
        root.addView(grid)
        dialog = NativeSheetDialog.show(this, root)
    }

    private fun shareWith(packageName: String?, title: String, url: String) {
        val text = listOf(title, url).filter { it.isNotBlank() }.joinToString("\n")
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); if (!packageName.isNullOrBlank()) setPackage(packageName) }
        runCatching {
            if (packageName.isNullOrBlank() || intent.resolveActivity(packageManager) == null) startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Partilhar página"))
            else startActivity(intent)
        }.onFailure {
            runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Partilhar página")) }
                .onFailure { AppAoToast.show(this, "Nenhuma aplicação de partilha disponível") }
        }
    }

    private fun goToHomeKeepingTab() {
        hideAddressSuggestions()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
        overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit)
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
            settings.userAgentString = if (BrowserPreferences.desktopSiteEnabled(this)) BrowserPreferences.desktopUserAgent(this) else WebSettings.getDefaultUserAgent(this)
        }
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
            configureWebViewClients()
            BrowserWebViewPool.setOwner(tabId, this)
            updateTabCounters()
            applyAddressBarPosition()
        }
    }

    override fun onPause() {
        savePageSnapshot()
        super.onPause()
    }

    override fun onDestroy() {
        hideAddressSuggestions()
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        if (::web.isInitialized && BrowserWebViewPool.contains(tabId) && BrowserWebViewPool.isOwner(tabId, this)) {
            BrowserWebViewPool.releaseOwner(tabId, this)
            runCatching { web.webViewClient = WebViewClient(); web.webChromeClient = WebChromeClient(); web.setDownloadListener(null) }
            runCatching { (web.parent as? ViewGroup)?.removeView(web) }
        } else if (::web.isInitialized && !BrowserWebViewPool.contains(tabId)) {
            runCatching { web.stopLoading(); web.webViewClient = WebViewClient(); web.webChromeClient = WebChromeClient(); web.destroy() }
        }
        super.onDestroy()
    }

    private fun hostFor(url: String): String = runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
}
