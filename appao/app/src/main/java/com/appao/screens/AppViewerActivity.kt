package com.appao.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.appao.AccentColorManager
import com.appao.AppAoToast
import com.appao.BrowserPreferences
import com.appao.BrowsingHistoryStore
import com.appao.ConversationHistoryStore
import com.appao.ImageViewerActivity
import com.appao.MainActivity
import com.appao.NativeHtmlDialog
import com.appao.NativeM3Dialog
import com.appao.NativeSheetDialog
import com.appao.R
import com.appao.SystemBarHelper
import com.appao.ui.BrowserTransitions
import com.appao.ui.IconLoader
import com.appao.ui.NativePopupMenu
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/** Full in-app WebView browser with persistent tabs, address bar, native browser menu and downloads. */
class AppViewerActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var progress: View
    private lateinit var address: EditText
    private lateinit var favicon: ImageView
    private var rendererRecoveryAttempted = false
    private var tabId: String = ""
    private var privateMode = false
    private var addressSuggestionGeneration = 0L
    private var reusedWebView = false
    private lateinit var findBar: View
    private lateinit var findInput: EditText
    private var findMode = false
    private var browserBarHidden = false
    private var zoomBar: View? = null
    private var addressSuggestionsPopup: PopupWindow? = null
    private var elementActionsPopup: PopupWindow? = null
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private val REQUEST_BROWSER_SEARCH = 7415
    private val ACTION_USE_SELECTED_TEXT = 0xA001
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
        BrowserTransitions.configure(this)
        setContentView(R.layout.activity_app_viewer)
        SystemBarHelper.sync(this)

        val root = findViewById<android.widget.FrameLayout>(R.id.appViewerRoot)
        val placeholder = findViewById<WebView>(R.id.webView)
        progress = findViewById(R.id.appProgress)
        address = findViewById(R.id.browserAddress)
        favicon = findViewById(R.id.browserFavicon)
        findBar = findViewById(R.id.browserFindBar)
        findInput = findViewById(R.id.browserFindInput)

        privateMode = intent.getBooleanExtra("browser_private_mode", false)
        tabId = intent.getStringExtra("browser_tab_id").orEmpty()
        val incomingUrl = intent.getStringExtra("url")?.trim().orEmpty()
        if (tabId.isBlank()) tabId = createTab(normalizeTarget(incomingUrl.ifBlank { "https://duckduckgo.com/" })).id
        val storedTab = tabList().firstOrNull { it.id == tabId }
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

        IconLoader.applyIcon(findViewById(R.id.appViewerMoreIcon), "more_vert", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.browserFindCloseIcon), "close", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.browserAddressClearIcon), "close", R.color.iconTint)
        findViewById<View>(R.id.browserAddressClear).visibility = View.GONE
        web.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                lastTouchRawX = event.rawX
                lastTouchRawY = event.rawY
            }
            false
        }
        web.setOnLongClickListener {
            val result = web.hitTestResult
            val target = result?.extra?.trim().orEmpty()
            if (target.isBlank() || !target.startsWith("http://", true) && !target.startsWith("https://", true)) return@setOnLongClickListener false
            val isVideoUrl = isLikelyVideoUrl(target)
            when (result.type) {
                WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    showWebElementActions(target, "image", lastTouchRawX, lastTouchRawY); true
                }
                WebView.HitTestResult.ANCHOR_TYPE, WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    showWebElementActions(target, if (isVideoUrl) "video" else "link", lastTouchRawX, lastTouchRawY); true
                }
                else -> if (isVideoUrl) {
                    showWebElementActions(target, "video", lastTouchRawX, lastTouchRawY); true
                } else false
            }
        }

        if (!reusedWebView) {
            updateTabPage(tabId, initialUrl, storedTab?.title ?: if (privateMode) "Separador privado" else "Nova aba")
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

        findViewById<View>(R.id.browserAddressPill).setOnClickListener { openBrowserSearch() }
        address.isFocusable = false
        address.isFocusableInTouchMode = false
        address.isCursorVisible = false
        address.showSoftInputOnFocus = false
        address.setOnClickListener { openBrowserSearch() }
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
            updateTabPage(tabId, activeUrl, web.title?.take(250).orEmpty().ifBlank { storedTab?.title ?: if (privateMode) "Separador privado" else "Nova aba" })
        } else {
            web.loadUrl(initialUrl)
        }
    }

    override fun onActionModeStarted(mode: ActionMode) {
        super.onActionModeStarted(mode)
        if (!::web.isInitialized) return
        val item = mode.menu.findItem(ACTION_USE_SELECTED_TEXT) ?: mode.menu.add(
            Menu.NONE,
            ACTION_USE_SELECTED_TEXT,
            Menu.NONE,
            "Usar texto na barra de endereço"
        ).apply { setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER) }
        item.setOnMenuItemClickListener {
            pullSelectedTextIntoSearch()
            mode.finish()
            true
        }
    }

    private fun pullSelectedTextIntoSearch() {
        if (!::web.isInitialized) return
        web.evaluateJavascript("(function(){return String(window.getSelection ? window.getSelection().toString() : '').trim()})()") { encoded ->
            val selected = runCatching { org.json.JSONTokener(encoded ?: "\"\"").nextValue() as? String }.getOrNull().orEmpty().trim()
            if (selected.isBlank() || isFinishing || isDestroyed) return@evaluateJavascript
            address.setText(selected)
            address.setSelection(address.text?.length ?: 0)
            openBrowserSearch(selected)
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
                if (!privateMode) BrowsingHistoryStore.record(this@AppViewerActivity, url, title)
                lifecycleScope.launch(Dispatchers.IO) { updateTabPage(tabId, url, title) }
                loadFavicon(url)
                updateTabCounters()
                refreshAdaptiveBarColor()
                view.postDelayed({ savePageSnapshot() }, 320L)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (BrowserPreferences.adBlockEnabled(this@AppViewerActivity) && isKnownAdRequest(request.url)) {
                    BrowserPreferences.recordBlockedAd(this@AppViewerActivity)
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                if (BrowserPreferences.trackingProtectionEnabled(this@AppViewerActivity) && isKnownTrackerRequest(request.url)) {
                    BrowserPreferences.recordBlockedTracker(this@AppViewerActivity)
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
                if (!title.isNullOrBlank()) updateTabPage(tabId, web.url.orEmpty(), title.take(250))
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
        // The address pill is a launcher, not an inline editable field. Search suggestions live
        // in BrowserSearchActivity so system back always returns directly to the current page.
        address.isFocusable = false
        address.isFocusableInTouchMode = false
        address.isCursorVisible = false
        address.showSoftInputOnFocus = false
        address.setOnClickListener { openBrowserSearch() }
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
            val params = (view.layoutParams as? android.widget.FrameLayout.LayoutParams)
                ?: android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58))
            params.gravity = if (bottom) Gravity.BOTTOM else Gravity.TOP
            params.topMargin = 0
            params.bottomMargin = 0
            view.layoutParams = params
        }
        place(topBar)
        place(findBar)

        // The WebView always keeps the same bounds. Moving its margins during a collapse caused
        // re-layout/scroll feedback, which looked like jitter and occasionally jumped the page.
        val webParams = web.layoutParams as? android.widget.FrameLayout.LayoutParams
        webParams?.let {
            it.topMargin = 0
            it.bottomMargin = 0
            web.layoutParams = it
        }
        val progressParams = progress.layoutParams as? android.widget.FrameLayout.LayoutParams
        progressParams?.let {
            it.gravity = if (bottom) Gravity.BOTTOM or Gravity.START else Gravity.TOP or Gravity.START
            it.topMargin = if (!bottom && !browserBarHidden) dp(58) else 0
            it.bottomMargin = if (bottom && !browserBarHidden) dp(58) else 0
            progress.layoutParams = it
        }
    }

    private fun setBrowserBarHidden(hidden: Boolean) {
        if (address.hasFocus() || findMode || isFinishing || isDestroyed) return
        val topBar = findViewById<View>(R.id.browserTopBar)
        if (browserBarHidden == hidden) return
        browserBarHidden = hidden
        val bottom = BrowserPreferences.addressBarPosition(this).equals("bottom", true)
        val direction = if (bottom) 1f else -1f
        topBar.animate().cancel()
        if (hidden) {
            topBar.visibility = View.VISIBLE
            topBar.animate()
                .translationY(direction * dp(60))
                .alpha(0f)
                .setDuration(170L)
                .setInterpolator(Curves.IOS)
                .withEndAction { if (browserBarHidden) topBar.visibility = View.INVISIBLE }
                .start()
        } else {
            topBar.visibility = View.VISIBLE
            topBar.alpha = 0f
            topBar.translationY = direction * dp(60)
            topBar.animate().alpha(1f).translationY(0f)
                .setDuration(170L)
                .setInterpolator(Curves.IOS)
                .withEndAction { topBar.alpha = 1f; topBar.translationY = 0f }
                .start()
        }
        applyAddressBarPosition()
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
        removeTab(tabId)
        BrowserWebViewPool.removeAndDestroy(tabId)
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("appao_main_tab", "home")
        startActivity(intent)
        finish()
        overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit)
    }

    private fun openBrowserSearch(initialQuery: String = web.url.orEmpty()) {
        if (isFinishing || isDestroyed) return
        val launch = Intent(this, BrowserSearchActivity::class.java).apply {
            putExtra(BrowserSearchActivity.EXTRA_INITIAL_QUERY, initialQuery)
            putExtra("browser_private_mode", privateMode)
        }
        startActivityForResult(launch, REQUEST_BROWSER_SEARCH)
        overridePendingTransition(R.anim.browser_fade_in, R.anim.browser_fade_out)
    }

    @Deprecated("Deprecated by Android, retained for compatibility with this project's existing Activity setup")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_BROWSER_SEARCH || resultCode != Activity.RESULT_OK) return
        val target = data?.getStringExtra(BrowserSearchActivity.EXTRA_RESULT_VALUE)?.trim().orEmpty()
        if (target.isBlank()) return
        address.setText(target)
        loadAddressValue(target)
    }

    private fun loadAddressFromInput() = loadAddressValue(address.text?.toString().orEmpty())

    private fun loadAddressValue(rawValue: String) {
        val raw = rawValue.trim()
        if (raw.isBlank()) return
        val target = normalizeTarget(raw)
        address.setText(target)
        address.clearFocus()
        hideAddressSuggestions()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(address.windowToken, 0)
        web.loadUrl(target)
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
            .putExtra("browser_private_mode", privateMode)
        runCatching {
            val options = ActivityOptions.makeSceneTransitionAnimation(this, web, "browser_tab_preview")
            startActivity(launch, options.toBundle())
        }.onFailure {
            startActivity(launch)
            overridePendingTransition(R.anim.browser_fade_in, R.anim.browser_fade_out)
        }
    }

    private fun openNewBrowserTab(url: String) {
        hideAddressSuggestions()
        savePageSnapshot()
        val target = normalizeTarget(url)
        val tab = createTab(target)
        startActivity(Intent(this, AppViewerActivity::class.java).apply {
            putExtra("browser_mode", true)
            putExtra("browser_private_mode", privateMode)
            putExtra("browser_tab_id", tab.id)
            putExtra("url", target)
        })
        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
    }

    private fun openNewTab(forcePrivate: Boolean = privateMode) {
        savePageSnapshot()
        val tab = if (forcePrivate) PrivateBrowserTabStore.create(this, "https://duckduckgo.com/") else BrowserTabStore.create(this, "https://duckduckgo.com/")
        startActivity(Intent(this, AppViewerActivity::class.java).apply {
            putExtra("browser_mode", true); putExtra("browser_private_mode", forcePrivate); putExtra("browser_tab_id", tab.id); putExtra("url", tab.url)
        })
        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
    }

    private fun tabList(): List<BrowserTab> = if (privateMode) PrivateBrowserTabStore.tabs() else BrowserTabStore.tabs(this)
    private fun createTab(url: String): BrowserTab = if (privateMode) PrivateBrowserTabStore.create(this, url) else BrowserTabStore.create(this, url)
    private fun updateTabPage(id: String, url: String, title: String, snapshotPath: String? = null) {
        if (privateMode) PrivateBrowserTabStore.updatePage(this, id, url, title, snapshotPath)
        else BrowserTabStore.updatePage(this, id, url, title, snapshotPath)
    }
    private fun removeTab(id: String) { if (privateMode) PrivateBrowserTabStore.remove(this, id) else BrowserTabStore.remove(this, id) }
    private fun saveTabSnapshot(id: String, bitmap: Bitmap): String? = if (privateMode) PrivateBrowserTabStore.saveSnapshot(this, id, bitmap) else BrowserTabStore.saveSnapshot(this, id, bitmap)
    private fun tabCount(): Int = if (privateMode) PrivateBrowserTabStore.count() else BrowserTabStore.count(this)


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
                PrivateBrowserTabStore.clear(this)
                BrowsingHistoryStore.clear(this)
                ConversationHistoryStore.clear(this)
                runCatching { android.webkit.WebStorage.getInstance().deleteAllData() }
                BrowserWebViewPool.clearExcept(previousId)
                val fresh = if (privateMode) PrivateBrowserTabStore.create(this, "https://www.google.com/") else BrowserTabStore.create(this, "https://www.google.com/")
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
        val desktopEnabled = BrowserPreferences.desktopSiteEnabled(this)
        val trackingEnabled = BrowserPreferences.trackingProtectionEnabled(this)
        val addressAtBottom = BrowserPreferences.addressBarPosition(this).equals("bottom", true)

        fun truncateLabel(text: String, max: Int = 28): String =
            if (text.length > max) text.take(max).trimEnd() + "…" else text

        NativePopupMenu.show(anchor, listOf(
            NativePopupMenu.Item(1, "Recuar", iconName = "arrow_go_back", checked = false),
            NativePopupMenu.Item(2, "Avançar", iconName = "arrow_go_forward", checked = false),
            NativePopupMenu.Item(3, "Recarregar", iconName = "refresh"),
            NativePopupMenu.Item(4, "Novo separador", iconName = "plus"),
            NativePopupMenu.Item(5, "Todos os separadores", iconName = "apps"),
            NativePopupMenu.Item(6, "Zoom da página", iconName = "zoom_in"),
            NativePopupMenu.Item(7, truncateLabel(if (desktopEnabled) "Modo desktop: ligado" else "Modo desktop: desligado"), iconName = "desktop", checked = desktopEnabled),
            NativePopupMenu.Item(8, truncateLabel(if (addressAtBottom) "Barra de endereço: em baixo" else "Barra de endereço: no topo"), iconName = "options", checked = addressAtBottom),
            NativePopupMenu.Item(9, truncateLabel(if (adsEnabled) "Bloqueador de anúncios: ligado" else "Bloqueador de anúncios: desligado"), iconName = "shield_check", checked = adsEnabled),
            NativePopupMenu.Item(10, truncateLabel(if (forceDarkEnabled) "Forçar tema escuro: ligado" else "Forçar tema escuro: desligado"), iconName = "moon", checked = forceDarkEnabled),
            NativePopupMenu.Item(11, "Downloads", iconName = "download"),
            NativePopupMenu.Item(12, "Copiar ligação", iconName = "link"),
            NativePopupMenu.Item(13, "Partilhar página…", iconName = "share"),
            NativePopupMenu.Item(14, "Limpar dados de navegação", iconName = "flame"),
            NativePopupMenu.Item(15, "Pesquisar nesta página", iconName = "search"),
            NativePopupMenu.Item(16, "Novo separador privado", iconName = "eye_off"),
            NativePopupMenu.Item(17, "Permissões deste site", iconName = "shield_check"),
            NativePopupMenu.Item(18, "Início", iconName = "home"),
            NativePopupMenu.Item(19, truncateLabel(if (trackingEnabled) "Bloquear rastreadores: ligado" else "Bloquear rastreadores: desligado"), iconName = "shield_check", checked = trackingEnabled)
        ), alignLeft = false, placeAbove = true) { item ->
            val currentUrl = web.url?.takeIf { it.isNotBlank() } ?: "https://duckduckgo.com/"
            when (item.id) {
                1 -> if (web.canGoBack()) web.goBack() else AppAoToast.show(this, "Não há página anterior")
                2 -> if (web.canGoForward()) web.goForward() else AppAoToast.show(this, "Não há página seguinte")
                3 -> web.reload()
                4 -> openNewTab(privateMode)
                5 -> openTabs()
                6 -> showZoomBar()
                7 -> {
                    BrowserPreferences.setDesktopSiteEnabled(this, !desktopEnabled)
                    applyBrowserPreferences()
                    AppAoToast.show(this, if (!desktopEnabled) "Modo desktop ativado" else "Modo desktop desativado")
                    web.reload()
                }
                8 -> {
                    BrowserPreferences.setAddressBarPosition(this, if (addressAtBottom) "top" else "bottom")
                    browserBarHidden = false
                    findViewById<View>(R.id.browserTopBar).apply { animate().cancel(); translationY = 0f; alpha = 1f; visibility = View.VISIBLE }
                    applyAddressBarPosition()
                }
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
                13 -> showBrowserShareSheet()
                14 -> clearBrowserData()
                15 -> openFindInPage()
                16 -> openNewTab(true)
                17 -> showSitePermissions()
                18 -> goToHomeKeepingTab()
                19 -> {
                    BrowserPreferences.setTrackingProtectionEnabled(this, !trackingEnabled)
                    AppAoToast.show(this, if (!trackingEnabled) "Bloqueio de rastreamento ativado" else "Bloqueio de rastreamento desativado")
                    web.reload()
                }
            }
        }
    }

    private fun isLikelyVideoUrl(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase(java.util.Locale.ROOT)
        return listOf(".mp4", ".webm", ".m4v", ".mov", ".3gp", ".mkv", ".m3u8", ".mpd")
            .any { path.endsWith(it) }
    }

    private data class ElementAction(val title: String, val icon: String, val callback: () -> Unit)

    private fun showWebElementActions(url: String, kind: String, rawX: Float, rawY: Float) {
        elementActionsPopup?.dismiss()
        val pageTitle = when (kind) { "image" -> "Imagem"; "video" -> "Vídeo"; else -> "Ligação" }
        val actions = mutableListOf<ElementAction>()
        if (kind == "image") actions += ElementAction("Pré-visualizar imagem", "image") {
            startActivity(Intent(this, ImageViewerActivity::class.java).putExtra("image", url))
            overridePendingTransition(R.anim.browser_fade_in, R.anim.browser_fade_out)
        }
        if (kind == "image" || kind == "video") actions += ElementAction(if (kind == "image") "Transferir imagem" else "Transferir vídeo", "download") { enqueueWebDownload(url) }
        if (kind == "link" || kind == "video") actions += ElementAction("Abrir num separador", "open-outline") { openNewBrowserTab(url) }
        actions += ElementAction("Copiar ligação", "copy") {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Ligação", url))
            AppAoToast.show(this, "Ligação copiada")
        }
        actions += ElementAction("Partilhar", "share") {
            runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }, "Partilhar ligação")) }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dialogSurface))
                setStroke(dp(1), ContextCompat.getColor(this@AppViewerActivity, R.color.border))
            }
            elevation = dp(12).toFloat()
            clipToOutline = true
        }
        root.addView(TextView(this).apply {
            text = pageTitle
            textSize = 11.5f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dim))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(8), 0)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)))
        lateinit var popup: PopupWindow
        actions.forEach { action ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(40)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                isClickable = true
                isFocusable = true
                foreground = ContextCompat.getDrawable(this@AppViewerActivity, R.drawable.bg_transparent_pressed)
            }
            row.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                IconLoader.applyIcon(this, action.icon, R.color.iconTint)
            })
            row.addView(TextView(this).apply {
                text = action.title
                textSize = 13.5f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.text))
                setPadding(dp(11), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener { popup.dismiss(); action.callback() }
            root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
        }
        val width = minOf(dp(246), (resources.displayMetrics.widthPixels - dp(20)).coerceAtLeast(dp(200)))
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val height = root.measuredHeight.coerceAtMost(resources.displayMetrics.heightPixels - dp(120))
        popup = PopupWindow(root, width, height, true).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            elevation = dp(12).toFloat()
        }
        val container = findViewById<android.widget.FrameLayout>(R.id.appViewerRoot)
        val rootLocation = IntArray(2)
        container.getLocationOnScreen(rootLocation)
        val localX = (rawX - rootLocation[0]).toInt()
        val localY = (rawY - rootLocation[1]).toInt()
        val x = (localX + dp(10)).coerceIn(dp(8), (container.width - width - dp(8)).coerceAtLeast(dp(8)))
        val y = if (localY + height + dp(18) > container.height) {
            (localY - height - dp(8)).coerceAtLeast(dp(8))
        } else {
            (localY + dp(12)).coerceAtMost((container.height - height - dp(8)).coerceAtLeast(dp(8)))
        }
        elementActionsPopup = popup
        popup.showAtLocation(container, Gravity.TOP or Gravity.START, x, y)
        popup.setOnDismissListener { if (elementActionsPopup === popup) elementActionsPopup = null }
    }

    private fun enqueueWebDownload(url: String) {
        runCatching {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(URLUtil.guessFileName(url, null, null))
                setDescription("A transferir com o AppAo")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true); setAllowedOverRoaming(false)
                CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { addRequestHeader("Cookie", it) }
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, null, null))
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            AppAoToast.show(this, "Transferência iniciada")
        }.onFailure { AppAoToast.show(this, "Não foi possível iniciar a transferência") }
    }

    /** Compact bottom control: the page zoom changes continuously while the thumb moves. */
    private fun showZoomBar() {
        val root = findViewById<android.widget.FrameLayout>(R.id.appViewerRoot)
        zoomBar?.let { old -> (old.parent as? ViewGroup)?.removeView(old) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(12), dp(8))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(ContextCompat.getColor(this@AppViewerActivity, R.color.card2))
                setStroke(dp(1), ContextCompat.getColor(this@AppViewerActivity, R.color.divider))
            }
            elevation = dp(8).toFloat()
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "Zoom da página"; textSize = 13f
            setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.text))
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val value = TextView(this).apply {
            text = "${currentZoomPercent}%"; textSize = 12f
            setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.text))
            setPadding(dp(8), 0, dp(10), 0)
        }
        header.addView(value)
        val close = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(19), dp(19))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Fechar zoom"
            IconLoader.applyIcon(this, "close", R.color.iconTint)
            isClickable = true; isFocusable = true
            setOnClickListener {
                bar.animate().cancel()
                bar.animate().alpha(0f).translationY(dp(12).toFloat()).setDuration(170L).setInterpolator(Curves.IOS)
                    .withEndAction { (bar.parent as? ViewGroup)?.removeView(bar); if (zoomBar === bar) zoomBar = null }
                    .start()
            }
        }
        header.addView(close)
        bar.addView(header)
        val seek = SeekBar(this).apply {
            max = 150
            progress = (currentZoomPercent - 50).coerceIn(0, 150)
            progressTintList = ColorStateList.valueOf(AccentColorManager.resolvedOr(this@AppViewerActivity, R.color.pri))
            thumbTintList = ColorStateList.valueOf(AccentColorManager.resolvedOr(this@AppViewerActivity, R.color.pri))
            contentDescription = "Zoom de 50 a 200 por cento"
        }
        bar.addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)))
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val target = progress + 50
                value.text = "$target%"
                if (!fromUser) return
                val old = currentZoomPercent.coerceAtLeast(1)
                runCatching { web.zoomBy(target.toFloat() / old.toFloat()) }
                currentZoomPercent = target
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        val labelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        labelRow.addView(TextView(this).apply { text = "50%"; textSize = 10.5f; setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dim)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        labelRow.addView(TextView(this).apply { text = "200%"; textSize = 10.5f; gravity = Gravity.END; setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.dim)) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(labelRow)
        val params = android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        params.marginStart = dp(12); params.marginEnd = dp(12)
        params.bottomMargin = dp(if (BrowserPreferences.addressBarPosition(this).equals("bottom", true)) 76 else 12)
        params.topMargin = dp(12)
        root.addView(bar, params)
        zoomBar = bar
        bar.alpha = 0f
        bar.translationY = dp(12).toFloat()
        bar.animate().alpha(1f).translationY(0f).setDuration(250L).setInterpolator(Curves.IOS).start()
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
            val icon = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(19), dp(19)); scaleType = ImageView.ScaleType.CENTER_INSIDE; IconLoader.applyIcon(this, option.icon, R.color.iconTint) }
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
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("appao_main_tab", "home")
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

    private fun isKnownTrackerRequest(uri: Uri): Boolean {
        val host = uri.host?.lowercase(java.util.Locale.ROOT)?.removePrefix("www.") ?: return false
        val trackers = listOf(
            "google-analytics.com", "googletagmanager.com", "segment.io", "segment.com",
            "mixpanel.com", "hotjar.com", "fullstory.com", "newrelic.com", "nr-data.net",
            "clarity.ms", "bat.bing.com", "stats.g.doubleclick.net", "amplitude.com",
            "quantserve.com", "scorecardresearch.com", "adobedtm.com", "krxd.net"
        )
        return trackers.any { host == it || host.endsWith(".$it") }
    }

    private fun refreshAdaptiveBarColor() {
        if (!::web.isInitialized || isFinishing || isDestroyed) return
        val adaptive = BrowserPreferences.adaptiveBrowserBarColor(this)
        if (!adaptive) {
            applyAdaptiveChrome(ContextCompat.getColor(this, R.color.bg), false)
            return
        }
        val pageUrl = web.url.orEmpty()
        applyAdaptiveChrome(sampleVisiblePageColor() ?: ContextCompat.getColor(this, R.color.bg), true)
        val script = """
            (function(){
              function norm(c){
                if(!c)return '';
                c=String(c).trim();
                var h=c.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i);
                if(h){var v=h[1]; if(v.length===3)v=v[0]+v[0]+v[1]+v[1]+v[2]+v[2]; return '#'+v;}
                var m=c.match(/^rgba?\(([^)]+)\)$/i);
                if(m){var p=m[1].split(/[,\s\/]+/).filter(Boolean); if(p.length>=3 && (p.length<4 || parseFloat(p[3])>0.01)){
                  var out='#'; for(var i=0;i<3;i++){var n=Math.max(0,Math.min(255,Math.round(parseFloat(p[i])))); out+=('0'+n.toString(16)).slice(-2);} return out;
                }}
                return '';
              }
              var meta=document.querySelector('meta[name=\"theme-color\" i]');
              var color=norm(meta && meta.content); if(color)return color;
              var nodes=[document.body,document.documentElement,document.querySelector('header'),document.querySelector('nav')];
              for(var j=0;j<nodes.length;j++){if(!nodes[j])continue; var c=norm(getComputedStyle(nodes[j]).backgroundColor); if(c)return c;}
              return '';
            })()
        """.trimIndent()
        web.evaluateJavascript(script) { raw ->
            if (isFinishing || isDestroyed || web.url.orEmpty() != pageUrl || !BrowserPreferences.adaptiveBrowserBarColor(this)) return@evaluateJavascript
            val value = runCatching { org.json.JSONTokener(raw ?: "\"\"").nextValue() as? String }.getOrNull().orEmpty()
            val color = parseAdaptiveColor(value) ?: sampleVisiblePageColor() ?: ContextCompat.getColor(this, R.color.bg)
            applyAdaptiveChrome(color, true)
        }
    }

    private fun parseAdaptiveColor(value: String): Int? = runCatching {
        val normalized = value.trim()
        if (!normalized.matches(Regex("#[0-9a-fA-F]{6}"))) null else android.graphics.Color.parseColor(normalized)
    }.getOrNull()

    private fun sampleVisiblePageColor(): Int? {
        if (!::web.isInitialized || web.width <= 0 || web.height <= 0) return null
        val sampleWidth = 36
        val sampleHeight = 28
        val bitmap = runCatching { Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        return try {
            val canvas = Canvas(bitmap)
            canvas.scale(sampleWidth.toFloat() / web.width, sampleHeight.toFloat() / web.height)
            web.draw(canvas)
            val counts = HashMap<Int, Int>()
            for (y in 0 until sampleHeight) for (x in 0 until sampleWidth) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) < 200) continue
                val r = (android.graphics.Color.red(pixel) shr 4) * 16 + 8
                val g = (android.graphics.Color.green(pixel) shr 4) * 16 + 8
                val b = (android.graphics.Color.blue(pixel) shr 4) * 16 + 8
                val bucket = android.graphics.Color.rgb(r.coerceAtMost(255), g.coerceAtMost(255), b.coerceAtMost(255))
                counts[bucket] = (counts[bucket] ?: 0) + 1
            }
            counts.maxByOrNull { it.value }?.key
        } catch (_: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun applyAdaptiveChrome(color: Int, adaptive: Boolean) {
        val bar = findViewById<View>(R.id.browserTopBar)
        val pill = findViewById<View>(R.id.browserAddressPill)
        bar.setBackgroundColor(color)
        window.statusBarColor = color

        val luminance = (0.2126 * android.graphics.Color.red(color) + 0.7152 * android.graphics.Color.green(color) + 0.0722 * android.graphics.Color.blue(color)) / 255.0
        val lightBackground = luminance >= 0.56
        val contrast = if (lightBackground) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = lightBackground

        if (adaptive) {
            val inputColor = blendColors(color, if (lightBackground) android.graphics.Color.BLACK else android.graphics.Color.WHITE, 0.12f)
            pill.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(26).toFloat()
                setColor(inputColor)
                setStroke(dp(1), blendColors(color, contrast, 0.17f))
            }
            address.setTextColor(contrast)
            address.setHintTextColor(blendColors(contrast, color, 0.48f))
            findViewById<TextView>(R.id.browserTabsCountTop).apply {
                setTextColor(contrast)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(7).toFloat()
                    setColor(inputColor)
                    setStroke(dp(1), blendColors(color, contrast, 0.78f))
                }
            }
            findViewById<ImageView>(R.id.appViewerMoreIcon).setColorFilter(contrast)
            findViewById<ImageView>(R.id.browserAddressClearIcon).setColorFilter(contrast)
            findViewById<ImageView>(R.id.browserFindCloseIcon).setColorFilter(contrast)
        } else {
            pill.setBackgroundResource(R.drawable.bg_toolbar_actions)
            address.setTextColor(ContextCompat.getColor(this, R.color.text))
            address.setHintTextColor(ContextCompat.getColor(this, R.color.dim))
            findViewById<TextView>(R.id.browserTabsCountTop).apply {
                setTextColor(ContextCompat.getColor(this@AppViewerActivity, R.color.text))
                setBackgroundResource(R.drawable.bg_browser_tab_counter)
            }
            findViewById<ImageView>(R.id.appViewerMoreIcon).clearColorFilter()
            findViewById<ImageView>(R.id.browserAddressClearIcon).clearColorFilter()
            findViewById<ImageView>(R.id.browserFindCloseIcon).clearColorFilter()
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                ContextCompat.getColor(this, R.color.bg).let { defaultColor ->
                    val y = (0.2126 * android.graphics.Color.red(defaultColor) + 0.7152 * android.graphics.Color.green(defaultColor) + 0.0722 * android.graphics.Color.blue(defaultColor)) / 255.0
                    y >= 0.56
                }
        }
    }

    private fun blendColors(base: Int, overlay: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        fun c(first: Int, second: Int) = (first * (1f - a) + second * a).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(
            c(android.graphics.Color.red(base), android.graphics.Color.red(overlay)),
            c(android.graphics.Color.green(base), android.graphics.Color.green(overlay)),
            c(android.graphics.Color.blue(base), android.graphics.Color.blue(overlay))
        )
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
        val count = tabCount().coerceAtMost(99).toString()
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
            val previousPath = tabList().firstOrNull { it.id == tabId }?.snapshotPath.orEmpty()
            val path = saveTabSnapshot(tabId, bitmap)
            if (!path.isNullOrBlank()) {
                updateTabPage(tabId, currentUrl, title, path)
                if (previousPath.isNotBlank() && previousPath != path) runCatching { java.io.File(previousPath).delete() }
            }
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
            refreshAdaptiveBarColor()
        }
    }

    override fun onPause() {
        savePageSnapshot()
        super.onPause()
    }

    override fun onDestroy() {
        hideAddressSuggestions()
        elementActionsPopup?.dismiss()
        elementActionsPopup = null
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
