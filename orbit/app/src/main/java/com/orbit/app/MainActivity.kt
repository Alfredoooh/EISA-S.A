package com.orbit.app

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.graphics.BitmapFactory
import android.net.http.SslError
import android.os.Message
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.orbit.app.browser.BrowserTabManager
import com.orbit.app.model.BrowserTab
import com.orbit.app.ui.BrowserIconView
import com.orbit.app.ui.BrowserPalette
import com.orbit.app.ui.BrowserThemeMode
import com.orbit.app.ui.addLinearChild
import com.orbit.app.ui.dp
import com.orbit.app.ui.makeIconButton
import com.orbit.app.ui.makeText
import com.orbit.app.ui.roundedBackground
import com.orbit.app.ui.rippleBackground
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Orbit is a native AppCompat browser. Android Views render all browser chrome, tabs,
 * search UI, sheets and dialogs; WebView is used only for the actual website content.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val HOME_URL = "https://www.google.com/webhp?igu=1"
        private const val PREFS = "orbit_browser_preferences"
        private const val PREF_THEME = "theme_mode"
        private const val ANIM_DURATION = 420L
        private const val MAX_THUMB_WIDTH = 360
        private const val MAX_THUMB_HEIGHT = 780
    }

    private lateinit var root: FrameLayout
    private lateinit var browserLayer: FrameLayout
    private lateinit var webViewHost: FrameLayout
    private lateinit var toolbarLayer: LinearLayout
    private lateinit var toolbarRow: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var backButton: FrameLayout
    private lateinit var forwardButton: FrameLayout
    private lateinit var addressPill: LinearLayout
    private lateinit var faviconImage: ImageView
    private lateinit var fallbackFavicon: BrowserIconView
    private lateinit var titleText: TextView
    private lateinit var lockButton: FrameLayout
    private lateinit var lockIcon: BrowserIconView
    private lateinit var menuButton: FrameLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var emptyIcon: BrowserIconView
    private lateinit var emptyText: TextView
    private lateinit var gestureZone: View
    private lateinit var tabsGrid: LinearLayout

    private lateinit var tabs: BrowserTabManager
    private lateinit var palette: BrowserPalette

    private var themeMode = BrowserThemeMode.SYSTEM
    private var toolbarHeightPx = 0
    private var toolbarHidden = false
    private var tabsVisible = false
    private var animatingTabs = false
    private var searchVisible = false
    private var sheetVisible = false

    private var securityOverlay: FrameLayout? = null
    private var themeOverlay: FrameLayout? = null
    private var searchOverlay: FrameLayout? = null
    private var suggestionsContainer: LinearLayout? = null
    private var searchInput: EditText? = null

    private var suggestionFuture: Future<*>? = null
    private var suggestionRunnable: Runnable? = null

    private val suggestionGeneration = AtomicInteger(0)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val suggestionExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val faviconExecutor: ExecutorService =
        Executors.newFixedThreadPool(2)

    private val faviconTasks = ConcurrentHashMap<Long, Future<*>>()

    private val ease = PathInterpolator(
        0.20f,
        0.80f,
        0.20f,
        1.0f,
    )

    private var destroyed = false

    private data class CardRefs(
        val card: View,
        val preview: View,
        val image: ImageView,
    )

    private val cardViews = mutableMapOf<Long, CardRefs>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val previousCrash = OrbitApplication.readLastCrash(this)
        if (!previousCrash.isNullOrBlank()) {
            showPreviousCrash(previousCrash)
            return
        }

        initializeBrowser()
    }

    private fun initializeBrowser() {
        themeMode = runCatching {
            BrowserThemeMode.valueOf(
                getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getString(PREF_THEME, "SYSTEM") ?: "SYSTEM",
            )
        }.getOrDefault(BrowserThemeMode.SYSTEM)

        palette = BrowserPalette.from(this, themeMode)

        configureSystemBars()
        buildBrowserSurface()

        tabs = BrowserTabManager(
            webViewHost,
            ::createWebView,
            ::onActiveTabChanged,
        )

        tabs.createTab(activate = true)

        applyThemeToChrome()
        updateAddressDisplay(tabs.activeTab)
        updateNavigationButtons()

        window.decorView.post {
            if (!destroyed) {
                updateSystemBarIconContrast()
            }
        }
    }

    /** Shows the last uncaught exception so it can be copied from the phone. */
    private fun showPreviousCrash(report: String) {
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(18))
            setBackgroundColor(Color.rgb(250, 250, 250))
        }

        val heading = TextView(this).apply {
            text = "Relatório do erro do Orbit"
            textSize = 20f
            setTextColor(Color.rgb(32, 33, 36))
            typeface = android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL,
            )
        }
        screen.addView(
            heading,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val explanation = TextView(this).apply {
            text = "O erro da abertura anterior foi guardado. Copia o relatório e envia-o para identificar a linha responsável."
            textSize = 14f
            setTextColor(Color.rgb(80, 80, 80))
            setPadding(0, dp(8), 0, dp(12))
        }
        screen.addView(
            explanation,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val reportScroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val reportText = TextView(this).apply {
            text = report
            textSize = 12f
            setTextColor(Color.rgb(40, 40, 40))
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedBackground(
                Color.rgb(240, 241, 243),
                dp(8).toFloat(),
            )
        }
        reportScroll.addView(
            reportText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        screen.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        val copyButton = android.widget.Button(this).apply {
            text = "Copiar relatório"
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(
                    ClipData.newPlainText("Orbit crash report", report),
                )
                Toast.makeText(
                    this@MainActivity,
                    "Relatório copiado. Cola-o na conversa.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        screen.addView(
            copyButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) },
        )

        val retryButton = android.widget.Button(this).apply {
            text = "Tentar abrir novamente"
            setOnClickListener {
                OrbitApplication.clearLastCrash(this@MainActivity)
                recreate()
            }
        }
        screen.addView(
            retryButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        setContentView(screen)
    }

    private fun configureSystemBars() {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }

        window.statusBarColor = palette.surface
        window.navigationBarColor = palette.surface

        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        // Do not query WindowInsetsController here: on some Android versions the
        // Window decor has not been created yet. initializeBrowser() applies icon
        // contrast after buildBrowserSurface() has called setContentView().
    }

    private fun updateSystemBarIconContrast() {
        if (Build.VERSION.SDK_INT >= 30) {
            // Access through decorView so the Window creates its decor before the
            // controller is requested. window.insetsController can dereference a
            // null internal decor during the early Activity startup phase.
            val controller = window.decorView.windowInsetsController ?: return

            val mask =
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS

            val appearance = if (palette.isDark) 0 else mask

            controller.setSystemBarsAppearance(appearance, mask)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                window.decorView.systemUiVisibility and
                    (
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                        ).inv()
                ) or if (palette.isDark) {
                0
            } else {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
    }

    private fun buildBrowserSurface() {
        root = FrameLayout(this).apply {
            setBackgroundColor(palette.background)
            clipChildren = false
            clipToPadding = false

            importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_YES

            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val systemBars = insets.getInsets(
                        android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars(),
                    )

                    view.setPadding(
                        0,
                        systemBars.top,
                        0,
                        systemBars.bottom,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(
                        0,
                        insets.systemWindowInsetTop,
                        0,
                        insets.systemWindowInsetBottom,
                    )
                }

                insets
            }
        }

        setContentView(root)

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )

        root.requestApplyInsets()

        browserLayer = FrameLayout(this).apply {
            setBackgroundColor(palette.surface)
        }

        root.addView(
            browserLayer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        webViewHost = FrameLayout(this).apply {
            setBackgroundColor(palette.surface)
            clipToPadding = false
            clipChildren = false
        }

        browserLayer.addView(
            webViewHost,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        buildEmptyState()

        browserLayer.addView(
            emptyState,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        buildToolbar()

        browserLayer.addView(
            toolbarLayer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )

        gestureZone = View(this).apply {
            visibility = View.GONE
            isClickable = true
            setOnTouchListener { _, event ->
                handleRevealGesture(event)
            }
        }

        browserLayer.addView(
            gestureZone,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(32),
                Gravity.TOP,
            ),
        )

        toolbarLayer.bringToFront()

        toolbarLayer.post {
            toolbarHeightPx = toolbarLayer.height

            if (toolbarHeightPx > 0) {
                webViewHost.setPadding(0, toolbarHeightPx, 0, 0)
                setToolbarHidden(toolbarHidden, false)
            }
        }
    }

    private fun buildToolbar() {
        toolbarLayer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.surface)
            elevation = dp(1).toFloat()
            clipChildren = false
        }

        toolbarRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setBackgroundColor(palette.surface)
        }

        backButton = makeIconButton(
            this,
            "back",
            sizeDp = 38,
            tint = palette.foreground,
            description = "Voltar",
        ) {
            goBack()
        }

        forwardButton = makeIconButton(
            this,
            "forward",
            sizeDp = 38,
            tint = palette.foreground,
            description = "Avançar",
        ) {
            goForward()
        }

        addLinearChild(
            toolbarRow,
            backButton,
            dp(38),
            dp(40),
            right = dp(2),
        )

        addLinearChild(
            toolbarRow,
            forwardButton,
            dp(38),
            dp(40),
            right = dp(4),
        )

        addressPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(5), 0)
            background = roundedBackground(
                palette.pill,
                dp(28).toFloat(),
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                openSearch()
            }
        }

        val faviconSlot = FrameLayout(this).apply {
            isFocusable = false
        }

        fallbackFavicon = BrowserIconView(this, "globe").apply {
            tint = palette.secondary
        }

        faviconImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            contentDescription = "Ícone do site"
        }

        faviconSlot.addView(
            fallbackFavicon,
            FrameLayout.LayoutParams(
                dp(20),
                dp(20),
                Gravity.CENTER,
            ),
        )

        faviconSlot.addView(
            faviconImage,
            FrameLayout.LayoutParams(
                dp(20),
                dp(20),
                Gravity.CENTER,
            ),
        )

        addLinearChild(
            addressPill,
            faviconSlot,
            dp(24),
            dp(28),
            right = dp(8),
        )

        titleText = makeText(
            this,
            "Nova aba",
            14.5f,
            palette.foreground,
            android.graphics.Typeface.NORMAL,
        ).apply {
            typeface = android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL,
            )

            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 0, 0, 0)

            contentDescription =
                "Título da página; tocar para pesquisar ou escrever um URL"

            isClickable = false
        }

        addLinearChild(
            addressPill,
            titleText,
            0,
            dp(40),
            weight = 1f,
        )

        lockButton = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true

            contentDescription =
                "Informações de segurança da ligação"

            background = rippleBackground(
                this@MainActivity,
                palette.softSurface,
                50f,
                palette.pressed,
            )

            setOnClickListener {
                showSecurityInfo()
            }
        }

        lockIcon = BrowserIconView(this, "lock").apply {
            tint = palette.secondary
        }

        lockButton.addView(
            lockIcon,
            FrameLayout.LayoutParams(
                dp(17),
                dp(17),
                Gravity.CENTER,
            ),
        )

        addLinearChild(
            addressPill,
            lockButton,
            dp(29),
            dp(29),
            left = dp(4),
        )

        addLinearChild(
            toolbarRow,
            addressPill,
            0,
            dp(40),
            weight = 1f,
            right = dp(4),
            left = dp(1),
        )

        menuButton = makeIconButton(
            this,
            "menu",
            sizeDp = 38,
            tint = palette.foreground,
            description = "Menu do navegador",
        ) {
            openBottomSheet()
        }

        addLinearChild(
            toolbarRow,
            menuButton,
            dp(38),
            dp(40),
            left = dp(1),
        )

        toolbarLayer.addView(
            toolbarRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(60),
            ),
        )

        progressBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal,
        ).apply {
            max = 100
            progress = 0
            isIndeterminate = false

            progressTintList =
                android.content.res.ColorStateList.valueOf(palette.blue)

            progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)

            visibility = View.GONE
            contentDescription = "Progresso de carregamento"
        }

        toolbarLayer.addView(
            progressBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(2),
            ),
        )
    }

    private fun buildEmptyState() {
        emptyState = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(palette.surface)
            isClickable = false
        }

        emptyIcon = BrowserIconView(this, "globe").apply {
            tint = if (palette.isDark) {
                0xFF5F6368.toInt()
            } else {
                0xFFDADCE0.toInt()
            }
        }

        emptyState.addView(
            emptyIcon,
            LinearLayout.LayoutParams(dp(44), dp(44)),
        )

        emptyText = makeText(
            this,
            "Escreve um URL acima para começar a navegar",
            14f,
            palette.secondary,
        ).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, dp(12), 0, 0)
        }

        emptyState.addView(
            emptyText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tab: BrowserTab): WebView {
        val webView = WebView(this)

        webView.setBackgroundColor(palette.surface)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        webView.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            if (Build.VERSION.SDK_INT >= 26) {
                safeBrowsingEnabled = true
            }

            allowFileAccess = false
            allowContentAccess = true
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                if (!request.isForMainFrame) return false

                val uri = request.url ?: return true

                return when (uri.scheme?.lowercase(Locale.ROOT)) {
                    "http", "https" -> false

                    "mailto", "tel" -> {
                        try {
                            startActivity(
                                Intent(Intent.ACTION_VIEW, uri),
                            )
                        } catch (_: Exception) {
                            showToast(
                                "Não existe uma aplicação compatível para abrir esta ligação.",
                            )
                        }

                        true
                    }

                    else -> true
                }
            }

            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: Bitmap?,
            ) {
                if (!isTabAlive(tab) || tab.webView !== view) return

                super.onPageStarted(view, url, favicon)

                val previousHost = domainOf(tab.url)
                val nextHost = domainOf(url)

                if (previousHost != nextHost) {
                    tab.favicon = null
                    tab.faviconRequestedFor = null
                }

                tab.url = url

                if (nextHost != null) {
                    tab.title = nextHost
                } else if (url == "about:blank") {
                    tab.title = "Nova aba"
                }

                tab.isLoading = true
                tab.progress = 5

                if (tabs.activeTab === tab) {
                    showToolbar()
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = 5
                    updateAddressDisplay(tab)
                    updateEmptyState(tab)
                }
            }

            override fun onPageFinished(
                view: WebView,
                url: String,
            ) {
                if (!isTabAlive(tab) || tab.webView !== view) return

                super.onPageFinished(view, url)

                tab.url = view.url ?: url

                val actualTitle = view.title?.trim().orEmpty()

                if (actualTitle.isNotEmpty()) {
                    tab.title = actualTitle
                } else if (
                    !tab.url.isNullOrBlank() &&
                    tab.url != "about:blank"
                ) {
                    tab.title = domainOf(tab.url) ?: tab.url.orEmpty()
                } else {
                    tab.title = "Nova aba"
                }

                tab.isLoading = false
                tab.progress = 100

                if (tabs.activeTab === tab) {
                    progressBar.progress = 100

                    mainHandler.postDelayed({
                        if (
                            destroyed ||
                            !isTabAlive(tab) ||
                            tabs.activeTab !== tab
                        ) {
                            return@postDelayed
                        }

                        progressBar.visibility = View.GONE
                        updateAddressDisplay(tab)
                        updateNavigationButtons()
                        updateEmptyState(tab)
                    }, 130L)
                }

                requestFaviconIfNeeded(tab)

                mainHandler.postDelayed({
                    if (
                        destroyed ||
                        runCatching {
                            tab.webView.parent == null
                        }.getOrDefault(true)
                    ) {
                        return@postDelayed
                    }

                    tab.thumbnail = captureView(
                        tab.webView,
                        MAX_THUMB_WIDTH,
                        MAX_THUMB_HEIGHT,
                    )

                    if (tabsVisible) {
                        renderTabsGrid()
                    }
                }, 220L)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (!isTabAlive(tab) || tab.webView !== view) return

                super.onReceivedError(view, request, error)

                if (request.isForMainFrame && tabs.activeTab === tab) {
                    progressBar.visibility = View.GONE
                    tab.isLoading = false
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError,
            ) {
                // Never bypass certificate errors.
                // A browser must fail closed on TLS errors.
                handler.cancel()

                if (!isTabAlive(tab) || tab.webView !== view) return

                if (tabs.activeTab === tab) {
                    showInformationDialog(
                        title = "Ligação bloqueada",
                        message = "O Android bloqueou esta página porque o certificado de segurança não pôde ser validado. Não é seguro continuar nesta ligação.",
                        icon = "lock",
                        positiveLabel = "Entendi",
                    )
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: android.webkit.RenderProcessGoneDetail,
            ): Boolean {
                // Remove references to the dead renderer's WebView.
                if (
                    !destroyed &&
                    ::tabs.isInitialized &&
                    tab.webView === view
                ) {
                    val lastUrl = tab.url ?: view.url
                    val replacement = createWebView(tab)

                    tabs.replaceWebView(
                        tab,
                        view,
                        replacement,
                    )

                    tab.thumbnail = null
                    tab.isLoading = false

                    if (!lastUrl.isNullOrBlank() && lastUrl != "about:blank") {
                        tab.url = lastUrl
                        replacement.loadUrl(lastUrl)
                    } else {
                        tab.url = null
                        updateAddressDisplay(tab)
                        updateEmptyState(tab)
                    }

                    showToast(
                        "A página foi recuperada depois de um erro de renderização.",
                    )
                }

                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(
                view: WebView,
                newProgress: Int,
            ) {
                if (!isTabAlive(tab) || tab.webView !== view) return

                tab.progress = newProgress.coerceIn(0, 100)

                if (tab.isLoading && tabs.activeTab === tab) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = tab.progress
                }
            }

            override fun onReceivedTitle(
                view: WebView,
                title: String?,
            ) {
                if (!isTabAlive(tab) || tab.webView !== view) return

                if (!title.isNullOrBlank()) {
                    tab.title = title.trim()

                    if (tabs.activeTab === tab) {
                        updateAddressDisplay(tab)
                    }

                    if (tabsVisible) {
                        renderTabsGrid()
                    }
                }
            }

            override fun onReceivedIcon(
                view: WebView,
                icon: Bitmap?,
            ) {
                if (!isTabAlive(tab) || tab.webView !== view) return

                if (icon != null && !icon.isRecycled) {
                    tab.favicon = icon

                    if (tabs.activeTab === tab) {
                        updateAddressDisplay(tab)
                    }
                }
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                if (
                    !isUserGesture ||
                    destroyed ||
                    !::tabs.isInitialized
                ) {
                    return false
                }

                val transport =
                    resultMsg.obj as? WebView.WebViewTransport
                        ?: return false

                val newTab = tabs.createTab(activate = true)

                transport.webView = newTab.webView
                resultMsg.sendToTarget()

                return true
            }

            override fun onCloseWindow(window: WebView) {
                if (destroyed || !::tabs.isInitialized) return

                val index = tabs.tabs.indexOfFirst {
                    it.webView === window
                }

                if (index < 0 || tabs.size <= 1) return

                val closed = tabs.closeTab(index)

                closed?.let {
                    faviconTasks.remove(it.id)?.cancel(true)
                }

                updateAddressDisplay(tabs.activeTab)
                updateNavigationButtons()
                updateEmptyState(tabs.activeTab)
            }
        }

        webView.setOnScrollChangeListener {
            scrolledView,
            _,
            scrollY,
            _,
            oldScrollY,
            ->
            if (
                scrolledView !== tab.webView ||
                tabs.activeTab !== tab ||
                tabsVisible ||
                searchVisible ||
                sheetVisible
            ) {
                return@setOnScrollChangeListener
            }

            val difference = scrollY - oldScrollY

            if (scrollY <= 0) {
                showToolbar()
            } else if (difference > dp(7)) {
                hideToolbar()
            } else if (difference < -dp(7)) {
                showToolbar()
            }
        }

        return webView
    }

    private fun requestFaviconIfNeeded(tab: BrowserTab) {
        if (!isTabAlive(tab)) return

        val pageUrl = tab.webView.url ?: tab.url ?: return
        val host = runCatching {
            Uri.parse(pageUrl).host
        }.getOrNull() ?: return

        if (
            tab.favicon != null ||
            tab.faviconRequestedFor == host ||
            host.isBlank()
        ) {
            return
        }

        tab.faviconRequestedFor = host

        faviconTasks.remove(tab.id)?.cancel(true)

        faviconTasks[tab.id] = faviconExecutor.submit {
            var connection: HttpURLConnection? = null

            try {
                val base = URL(pageUrl)
                val faviconUrl = URL(
                    "${base.protocol}://${base.host}/favicon.ico",
                )

                connection = (
                    faviconUrl.openConnection() as HttpURLConnection
                    ).apply {
                    connectTimeout = 1800
                    readTimeout = 1800
                    instanceFollowRedirects = true
                    setRequestProperty(
                        "User-Agent",
                        "OrbitBrowser/1.0",
                    )
                }

                connection.connect()

                if (connection.responseCode in 200..299) {
                    val bytes = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        var total = 0

                        while (true) {
                            val count = input.read(buffer)

                            if (count < 0) break

                            total += count

                            if (total > 256 * 1024) break

                            output.write(buffer, 0, count)
                        }

                        output.toByteArray()
                    }

                    val bitmap = BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size,
                    )

                    if (
                        bitmap != null &&
                        !destroyed &&
                        tabs.tabs.any { it === tab }
                    ) {
                        mainHandler.post {
                            if (
                                destroyed ||
                                tab.favicon != null ||
                                (tab.webView.url ?: tab.url)?.let {
                                    domainOf(it)
                                } != host.removePrefix("www.")
                            ) {
                                return@post
                            }

                            tab.favicon = bitmap

                            if (tabs.activeTab === tab) {
                                updateAddressDisplay(tab)
                            }

                            if (tabsVisible) {
                                renderTabsGrid()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Some sites do not expose a favicon.
            } finally {
                connection?.disconnect()
                faviconTasks.remove(tab.id)
            }
        }
    }

    private fun isTabAlive(tab: BrowserTab): Boolean =
        !destroyed &&
            ::tabs.isInitialized &&
            tabs.tabs.any { it === tab }

    private fun onActiveTabChanged(tab: BrowserTab?) {
        if (destroyed || !::titleText.isInitialized) return

        updateAddressDisplay(tab)
        updateNavigationButtons()
        updateEmptyState(tab)

        if (tab == null || !tab.isLoading) {
            progressBar.visibility = View.GONE
        } else {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = tab.progress
        }
    }

    private fun updateAddressDisplay(tab: BrowserTab?) {
        if (!::titleText.isInitialized) return

        val url = tab?.webView?.url ?: tab?.url

        if (
            tab == null ||
            url.isNullOrBlank() ||
            url == "about:blank"
        ) {
            titleText.text = "Nova aba"
            faviconImage.setImageDrawable(null)
            faviconImage.visibility = View.GONE
            fallbackFavicon.visibility = View.VISIBLE
            lockIcon.tint = palette.secondary

            lockButton.contentDescription =
                "Sem ligação aberta. Tocar para ver informações de segurança."

            return
        }

        val title = tab.title.takeIf {
            it.isNotBlank() && it != "about:blank"
        } ?: domainOf(url) ?: "Página"

        titleText.text = title

        val bitmap = tab.favicon

        if (bitmap != null && !bitmap.isRecycled) {
            faviconImage.setImageBitmap(bitmap)
            faviconImage.visibility = View.VISIBLE
            fallbackFavicon.visibility = View.GONE
        } else {
            faviconImage.setImageDrawable(null)
            faviconImage.visibility = View.GONE
            fallbackFavicon.visibility = View.VISIBLE
        }

        val secure = url.startsWith(
            "https://",
            ignoreCase = true,
        )

        lockIcon.tint = if (secure) {
            palette.safe
        } else {
            palette.unsafe
        }

        lockButton.contentDescription = if (secure) {
            "Ligação HTTPS; tocar para obter informações de segurança"
        } else {
            "Ligação sem HTTPS; tocar para ver o aviso de segurança"
        }
    }

    private fun updateEmptyState(tab: BrowserTab?) {
        val url = tab?.webView?.url ?: tab?.url

        emptyState.visibility =
            if (tab == null || url.isNullOrBlank() || url == "about:blank") {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun updateNavigationButtons() {
        if (!::backButton.isInitialized) return

        val tab = tabs.activeTab

        val canBack = tab?.webView?.canGoBack() == true
        val canForward = tab?.webView?.canGoForward() == true

        backButton.alpha = if (canBack) 1f else 0.38f
        forwardButton.alpha = if (canForward) 1f else 0.38f

        backButton.isEnabled = canBack
        forwardButton.isEnabled = canForward
    }

    private fun goBack() {
        val tab = tabs.activeTab ?: return

        if (tab.webView.canGoBack()) {
            tab.webView.goBack()
            showToolbar()
        }
    }

    private fun goForward() {
        val tab = tabs.activeTab ?: return

        if (tab.webView.canGoForward()) {
            tab.webView.goForward()
            showToolbar()
        }
    }

    private fun navigateFromInput(rawInput: String) {
        val finalUrl = normalizeUrl(rawInput) ?: return

        closeSearch()

        val tab = tabs.activeTab ?: tabs.createTab(true)

        tab.url = finalUrl
        tab.title = domainOf(finalUrl) ?: finalUrl
        tab.favicon = null
        tab.faviconRequestedFor = null
        tab.thumbnail = null
        tab.isLoading = true
        tab.progress = 5

        updateAddressDisplay(tab)
        updateEmptyState(tab)
        updateNavigationButtons()
        showToolbar()

        tab.webView.loadUrl(finalUrl)
    }

    private fun normalizeUrl(rawInput: String): String? {
        val input = rawInput.trim()

        if (input.isEmpty()) return null

        if (
            input.startsWith("javascript:", true) ||
            input.startsWith("file:", true) ||
            input.startsWith("content:", true)
        ) {
            showToast("Este tipo de endereço não é permitido.")
            return null
        }

        if (
            input.startsWith("https://", true) ||
            input.startsWith("http://", true)
        ) {
            return runCatching {
                val uri = Uri.parse(input)

                if (uri.host.isNullOrBlank()) null else input
            }.getOrNull() ?: run {
                showToast("O endereço não é válido.")
                null
            }
        }

        val looksLikeHost = !input.contains(' ') && (
            Regex(
                "^[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+(:[0-9]{1,5})?([/?#].*)?$",
            ).matches(input) ||
                Regex(
                    "^\\d{1,3}(\\.\\d{1,3}){3}(:[0-9]{1,5})?([/?#].*)?$",
                ).matches(input) ||
                input.startsWith("localhost", true)
            )

        return if (looksLikeHost) {
            "https://$input"
        } else {
            "https://www.google.com/search?igu=1&q=" +
                URLEncoder.encode(input, "UTF-8")
        }
    }

    private fun domainOf(url: String?): String? {
        if (url.isNullOrBlank()) return null

        return runCatching {
            Uri.parse(url).host
                ?.removePrefix("www.")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun hideToolbar() = setToolbarHidden(true, true)

    private fun showToolbar() = setToolbarHidden(false, true)

    private fun setToolbarHidden(
        hidden: Boolean,
        animate: Boolean,
    ) {
        if (!::toolbarLayer.isInitialized) return

        toolbarHidden = hidden

        if (toolbarHeightPx <= 0) {
            toolbarHeightPx =
                toolbarLayer.height.takeIf { it > 0 } ?: dp(54)

            webViewHost.setPadding(0, toolbarHeightPx, 0, 0)
        }

        toolbarLayer.animate().cancel()

        if (animate) {
            toolbarLayer.animate()
                .translationY(
                    if (hidden) -toolbarHeightPx.toFloat() else 0f,
                )
                .setDuration(225L)
                .setInterpolator(ease)
                .start()
        } else {
            toolbarLayer.translationY =
                if (hidden) -toolbarHeightPx.toFloat() else 0f
        }

        gestureZone.visibility = if (hidden) {
            View.VISIBLE
        } else {
            View.GONE
        }

        if (hidden) {
            gestureZone.bringToFront()
        } else {
            toolbarLayer.bringToFront()
        }
    }

    private var gestureStartY = 0f
    private var gestureMoved = false

    private fun handleRevealGesture(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureStartY = event.rawY
                gestureMoved = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - gestureStartY

                if (dy > dp(8)) {
                    gestureMoved = true
                    showToolbar()
                }

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (!gestureMoved) {
                    showToolbar()
                }

                return true
            }
        }

        return true
    }

    private fun openSearch(initialValue: String? = null) {
        if (searchVisible || tabsVisible || destroyed) return

        closeBottomSheet()
        hideSecurityDialog()

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(palette.surface)
            alpha = 0f
            isFocusableInTouchMode = true

            importantForAccessibility =
                View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.surface)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        val inputPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBackground(
                palette.pill,
                dp(28).toFloat(),
            )
            setPadding(dp(12), 0, dp(7), 0)
        }

        val searchIcon = BrowserIconView(this, "search").apply {
            tint = palette.secondary
        }

        inputPill.addView(
            searchIcon,
            LinearLayout.LayoutParams(dp(20), dp(20)),
        )

        val input = EditText(this).apply {
            setSingleLine(true)
            textSize = 15f
            setTextColor(palette.foreground)
            setHintTextColor(palette.secondary)
            hint = "Pesquisar ou escrever um URL"
            setPadding(dp(10), 0, dp(4), 0)
            background = null

            setSelectAllOnFocus(true)

            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI

            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO

            setAutocapitalizeNone()
        }

        addLinearChild(
            inputPill,
            input,
            0,
            dp(48),
            weight = 1f,
        )

        val clearButton = makeIconButton(
            this,
            "close",
            sizeDp = 28,
            iconDp = 15,
            tint = palette.secondary,
            backgroundColor = palette.line,
            pressedColor = palette.pressed,
            description = "Limpar texto",
        ) {
            input.setText("")
            input.requestFocus()
            scheduleSuggestions("")
        }

        clearButton.visibility = View.GONE

        addLinearChild(
            inputPill,
            clearButton,
            dp(28),
            dp(28),
            left = dp(3),
        )

        addLinearChild(
            top,
            inputPill,
            0,
            dp(48),
            weight = 1f,
            right = dp(8),
        )

        val closeButton = makeIconButton(
            this,
            "close",
            40,
            22,
            palette.foreground,
            description = "Fechar pesquisa",
        ) {
            closeSearch()
        }

        addLinearChild(
            top,
            closeButton,
            dp(40),
            dp(40),
        )

        content.addView(
            top,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val suggestionsScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }

        val suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(16))
        }

        suggestionsScroll.addView(
            suggestions,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        content.addView(
            suggestionsScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        overlay.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        searchOverlay = overlay
        searchInput = input
        suggestionsContainer = suggestions
        searchVisible = true

        val start = initialValue
            ?: tabs.activeTab?.let {
                it.webView.url ?: it.url
            }.orEmpty()
                .takeUnless { it == "about:blank" }
                .orEmpty()

        input.setText(start)

        if (start.isNotEmpty()) {
            input.setSelection(0, input.length())
        } else {
            input.setSelection(0)
        }

        input.addTextChangedListener(
            object : android.text.TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    clearButton.visibility =
                        if (s.isNullOrEmpty()) {
                            View.GONE
                        } else {
                            View.VISIBLE
                        }

                    scheduleSuggestions(s?.toString().orEmpty())
                }

                override fun afterTextChanged(
                    s: android.text.Editable?,
                ) = Unit
            },
        )

        input.setOnEditorActionListener { _, actionId, event ->
            val enter =
                event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                    event.action == android.view.KeyEvent.ACTION_UP

            if (
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO ||
                enter
            ) {
                navigateFromInput(
                    input.text?.toString().orEmpty(),
                )
                true
            } else {
                false
            }
        }

        renderSuggestions(
            start,
            emptyList(),
            suggestions,
        )

        overlay.animate()
            .alpha(1f)
            .setDuration(180L)
            .setInterpolator(ease)
            .start()

        input.postDelayed({
            if (!searchVisible || destroyed) return@postDelayed

            input.requestFocus()

            (
                getSystemService(INPUT_METHOD_SERVICE)
                    as? InputMethodManager
                )?.showSoftInput(
                input,
                InputMethodManager.SHOW_IMPLICIT,
            )
        }, 75L)
    }

    private fun EditText.setAutocapitalizeNone() {
        setRawInputType(
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI,
        )

        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
    }

    private fun scheduleSuggestions(query: String) {
        if (destroyed) return

        suggestionRunnable?.let(mainHandler::removeCallbacks)
        suggestionFuture?.cancel(true)

        val generation = suggestionGeneration.incrementAndGet()

        val runnable = Runnable {
            if (!searchVisible || destroyed) return@Runnable

            val trimmed = query.trim()

            if (trimmed.isEmpty()) {
                renderSuggestions(
                    "",
                    emptyList(),
                    suggestionsContainer,
                )
                return@Runnable
            }

            suggestionFuture = suggestionExecutor.submit {
                var connection: HttpURLConnection? = null

                try {
                    val encoded = URLEncoder.encode(
                        trimmed,
                        "UTF-8",
                    )

                    connection = (
                        URL(
                            "https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded",
                        ).openConnection() as HttpURLConnection
                        ).apply {
                        connectTimeout = 1800
                        readTimeout = 1800
                        requestMethod = "GET"

                        setRequestProperty(
                            "User-Agent",
                            "OrbitBrowser/1.0",
                        )
                    }

                    val response =
                        connection.inputStream
                            .bufferedReader(Charsets.UTF_8)
                            .use {
                                it.readText().take(256 * 1024)
                            }

                    val json = JSONArray(response)
                    val resultArray = json.optJSONArray(1)
                    val results = mutableListOf<String>()

                    if (resultArray != null) {
                        for (
                            index in 0 until min(
                                resultArray.length(),
                                8,
                            )
                        ) {
                            resultArray.optString(index)
                                .takeIf { it.isNotBlank() }
                                ?.let(results::add)
                        }
                    }

                    mainHandler.post {
                        if (
                            !destroyed &&
                            searchVisible &&
                            suggestionGeneration.get() == generation
                        ) {
                            renderSuggestions(
                                trimmed,
                                results,
                                suggestionsContainer,
                            )
                        }
                    }
                } catch (_: Exception) {
                    mainHandler.post {
                        if (
                            !destroyed &&
                            searchVisible &&
                            suggestionGeneration.get() == generation
                        ) {
                            renderSuggestions(
                                trimmed,
                                emptyList(),
                                suggestionsContainer,
                            )
                        }
                    }
                } finally {
                    connection?.disconnect()
                }
            }
        }

        suggestionRunnable = runnable
        mainHandler.postDelayed(runnable, 170L)
    }

    private fun renderSuggestions(
        query: String,
        results: List<String>,
        target: LinearLayout?,
    ) {
        if (target == null || destroyed) return

        target.removeAllViews()

        if (query.isBlank()) {
            val empty = makeText(
                this,
                "Escreve algo para pesquisar",
                13f,
                palette.secondary,
            )

            empty.gravity = Gravity.CENTER

            target.addView(
                empty,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(64),
                ),
            )

            return
        }

        addSuggestionRow(
            target,
            query,
            "search",
            true,
        )

        results.asSequence()
            .filter { it != query }
            .distinct()
            .take(7)
            .forEach {
                addSuggestionRow(
                    target,
                    it,
                    "history",
                    false,
                )
            }
    }

    private fun addSuggestionRow(
        parent: LinearLayout,
        text: String,
        iconName: String,
        direct: Boolean,
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            minimumHeight = dp(48)

            background = rippleBackground(
                this@MainActivity,
                Color.TRANSPARENT,
                0f,
                palette.pressed,
            )

            isClickable = true
            isFocusable = true

            setOnClickListener {
                navigateFromInput(text)
            }
        }

        val icon = BrowserIconView(this, iconName).apply {
            tint = palette.secondary
        }

        row.addView(
            icon,
            LinearLayout.LayoutParams(dp(20), dp(20)),
        )

        val label = makeText(
            this,
            text,
            15f,
            palette.foreground,
        ).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(16), 0, dp(8), 0)
        }

        addLinearChild(
            row,
            label,
            0,
            dp(48),
            weight = 1f,
        )

        if (!direct) {
            val insert = BrowserIconView(
                this,
                "forward",
            ).apply {
                tint = palette.secondary
                rotation = -45f
                contentDescription = "Preencher pesquisa"

                setOnClickListener {
                    searchInput?.setText(text)
                    searchInput?.setSelection(text.length)
                    searchInput?.requestFocus()
                }
            }

            row.addView(
                insert,
                LinearLayout.LayoutParams(dp(20), dp(20)),
            )
        }

        parent.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48),
            ),
        )
    }

    private fun closeSearch() {
        val overlay = searchOverlay ?: return

        if (!searchVisible) return

        searchVisible = false

        suggestionGeneration.incrementAndGet()

        suggestionRunnable?.let(mainHandler::removeCallbacks)
        suggestionRunnable = null

        suggestionFuture?.cancel(true)
        suggestionFuture = null

        (
            getSystemService(INPUT_METHOD_SERVICE)
                as? InputMethodManager
            )?.hideSoftInputFromWindow(
            searchInput?.windowToken,
            0,
        )

        searchInput?.clearFocus()

        overlay.animate()
            .alpha(0f)
            .setDuration(160L)
            .setInterpolator(ease)
            .withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)

                if (searchOverlay === overlay) {
                    searchOverlay = null
                }

                searchInput = null
                suggestionsContainer = null
            }
            .start()
    }

    private fun openBottomSheet() {
        if (
            sheetVisible ||
            tabsVisible ||
            searchVisible ||
            destroyed
        ) {
            return
        }

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val scrim = View(this).apply {
            setBackgroundColor(palette.scrim)
            alpha = 0f
        }

        overlay.addView(
            scrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(10))

            background = topRoundedBackground(
                palette.surface,
                dp(20).toFloat(),
            )

            elevation = dp(12).toFloat()
        }

        val handle = View(this).apply {
            background = roundedBackground(
                palette.line,
                dp(2).toFloat(),
            )
        }

        val handleParams = LinearLayout.LayoutParams(
            dp(36),
            dp(4),
        )

        handleParams.gravity = Gravity.CENTER_HORIZONTAL
        handleParams.bottomMargin = dp(10)

        sheet.addView(handle, handleParams)

        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(14))
        }

        addSheetAction(
            quickRow,
            "tabs",
            "Abas",
            count = tabs.size,
        ) {
            closeBottomSheet()
            openTabsView()
        }

        addSheetAction(
            quickRow,
            "back",
            "Voltar",
            enabled = tabs.activeTab?.webView?.canGoBack() == true,
        ) {
            closeBottomSheet()
            goBack()
        }

        addSheetAction(
            quickRow,
            "forward",
            "Avançar",
            enabled = tabs.activeTab?.webView?.canGoForward() == true,
        ) {
            closeBottomSheet()
            goForward()
        }

        addSheetAction(
            quickRow,
            "reload",
            "Recarregar",
            enabled = !tabs.activeTab?.url.isNullOrBlank(),
        ) {
            closeBottomSheet()
            tabs.activeTab?.let {
                it.webView.reload()
                showToolbar()
            }
        }

        addSheetAction(
            quickRow,
            "home",
            "Início",
        ) {
            closeBottomSheet()
            navigateFromInput(HOME_URL)
        }

        sheet.addView(
            quickRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        sheet.addView(
            View(this).apply {
                setBackgroundColor(palette.line)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1),
            ),
        )

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(2))
        }

        addSheetListItem(list, "add", "Nova aba") {
            closeBottomSheet()
            tabs.createTab(true)
            updateNavigationButtons()
            openSearch("")
        }

        addSheetListItem(list, "share", "Partilhar") {
            closeBottomSheet()
            shareCurrentPage()
        }

        addSheetListItem(list, "copy", "Copiar link") {
            closeBottomSheet()
            copyCurrentUrl()
        }

        addSheetListItem(list, "desktop", "Versão desktop") {
            closeBottomSheet()
            toggleDesktopMode()
        }

        addSheetListItem(
            list,
            "desktopMode",
            "Tema: ${themeLabel(themeMode)}",
        ) {
            closeBottomSheet()
            showThemeSelector()
        }

        sheet.addView(
            list,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        overlay.addView(
            sheet,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )

        overlay.setOnClickListener {
            closeBottomSheet()
        }

        sheet.setOnClickListener {
            // Consume taps inside the sheet.
        }

        attachSheetDragGesture(sheet, scrim)

        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        sheetVisible = true
        overlay.tag = "orbit_bottom_sheet"

        overlay.post {
            if (!sheetVisible || destroyed) return@post

            sheet.translationY = sheet.height.toFloat()

            scrim.animate()
                .alpha(if (palette.isDark) 0.58f else 0.40f)
                .setDuration(220L)
                .setInterpolator(ease)
                .start()

            sheet.animate()
                .translationY(0f)
                .setDuration(280L)
                .setInterpolator(ease)
                .start()
        }
    }

    private var sheetDragStartY = 0f
    private var sheetDragging = false
    private var sheetVelocityTracker: VelocityTracker? = null

    @SuppressLint("ClickableViewAccessibility")
    private fun attachSheetDragGesture(sheet: LinearLayout, scrim: View) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val maxAlpha = if (palette.isDark) 0.58f else 0.40f

        sheet.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sheetDragStartY = event.rawY
                    sheetDragging = false

                    sheetVelocityTracker?.recycle()
                    sheetVelocityTracker = VelocityTracker.obtain().apply {
                        addMovement(event)
                    }

                    sheet.animate().cancel()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - sheetDragStartY

                    if (!sheetDragging && dy > touchSlop) {
                        sheetDragging = true
                    }

                    if (sheetDragging) {
                        val translation = max(0f, dy)
                        sheet.translationY = translation

                        val progress =
                            1f - min(1f, translation / sheet.height.toFloat())
                        scrim.alpha = maxAlpha * progress

                        sheetVelocityTracker?.addMovement(event)
                    }

                    sheetDragging
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (!sheetDragging) {
                        sheetVelocityTracker?.recycle()
                        sheetVelocityTracker = null
                        return@setOnTouchListener false
                    }

                    sheetVelocityTracker?.apply {
                        addMovement(event)
                        computeCurrentVelocity(1000)
                    }

                    val flingVelocity = sheetVelocityTracker?.yVelocity ?: 0f
                    val travel = sheet.translationY
                    val threshold = sheet.height.toFloat() * 0.35f

                    sheetVelocityTracker?.recycle()
                    sheetVelocityTracker = null
                    sheetDragging = false

                    if (travel > threshold || flingVelocity > dp(800)) {
                        closeBottomSheet()
                    } else {
                        sheet.animate()
                            .translationY(0f)
                            .setDuration(200L)
                            .setInterpolator(ease)
                            .start()

                        scrim.animate()
                            .alpha(maxAlpha)
                            .setDuration(200L)
                            .start()
                    }

                    true
                }

                else -> false
            }
        }
    }

    private fun addSheetAction(
        row: LinearLayout,
        icon: String,
        label: String,
        enabled: Boolean = true,
        count: Int? = null,
        onClick: () -> Unit,
    ) {
        val action = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(1), dp(4), dp(1), dp(4))
            minimumHeight = dp(62)
            alpha = if (enabled) 1f else 0.36f
            isClickable = enabled
            isFocusable = enabled

            background = rippleBackground(
                this@MainActivity,
                Color.TRANSPARENT,
                12f,
                palette.pressed,
            )

            setOnClickListener {
                if (enabled) onClick()
            }
        }

        val iconHolder = FrameLayout(this)

        if (count != null) {
            val counter = TextView(this).apply {
                text = count.toString()
                textSize = 11f

                typeface = android.graphics.Typeface.create(
                    "sans-serif",
                    android.graphics.Typeface.BOLD,
                )

                setTextColor(palette.foreground)
                gravity = Gravity.CENTER

                background = roundedBackground(
                    Color.TRANSPARENT,
                    dp(5).toFloat(),
                    palette.foreground,
                    dp(1),
                )
            }

            iconHolder.addView(
                counter,
                FrameLayout.LayoutParams(
                    dp(24),
                    dp(24),
                    Gravity.CENTER,
                ),
            )
        } else {
            val iconView = BrowserIconView(this, icon).apply {
                tint = palette.foreground
            }

            iconHolder.addView(
                iconView,
                FrameLayout.LayoutParams(
                    dp(24),
                    dp(24),
                    Gravity.CENTER,
                ),
            )
        }

        action.addView(
            iconHolder,
            LinearLayout.LayoutParams(dp(36), dp(30)),
        )

        val caption = makeText(
            this,
            label,
            10.5f,
            palette.foreground,
        ).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        action.addView(
            caption,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(20),
            ),
        )

        row.addView(
            action,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )
    }

    private fun addSheetListItem(
        parent: LinearLayout,
        iconName: String,
        label: String,
        action: () -> Unit,
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            minimumHeight = dp(48)

            background = rippleBackground(
                this@MainActivity,
                Color.TRANSPARENT,
                0f,
                palette.pressed,
            )

            isClickable = true
            isFocusable = true

            setOnClickListener {
                action()
            }
        }

        val icon = BrowserIconView(this, iconName).apply {
            tint = palette.secondary
        }

        row.addView(
            icon,
            LinearLayout.LayoutParams(dp(22), dp(22)),
        )

        val text = makeText(
            this,
            label,
            15f,
            palette.foreground,
        ).apply {
            setPadding(dp(20), 0, 0, 0)
        }

        addLinearChild(
            row,
            text,
            0,
            dp(48),
            weight = 1f,
        )

        parent.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48),
            ),
        )
    }

    private fun closeBottomSheet() {
        val overlay =
            root.findViewWithTag<FrameLayout>("orbit_bottom_sheet")
                ?: return

        if (!sheetVisible) return

        sheetVisible = false

        val sheet = overlay.getChildAt(1)
        val scrim = overlay.getChildAt(0)
        val travel = (sheet?.height ?: dp(260)).toFloat()
        val remaining = travel - (sheet?.translationY ?: 0f)
        val duration = (220L * (remaining / travel).coerceIn(0.35f, 1f)).toLong()

        sheet?.animate()
            ?.translationY(travel)
            ?.setDuration(duration)
            ?.setInterpolator(ease)
            ?.start()

        scrim?.animate()
            ?.alpha(0f)
            ?.setDuration((duration * 0.8f).toLong())
            ?.withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
            }
            ?.start()
    }

    private fun shareCurrentPage() {
        val url = tabs.activeTab?.let {
            it.webView.url ?: it.url
        } ?: return

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }

            startActivity(
                Intent.createChooser(
                    intent,
                    "Partilhar página",
                ),
            )
        } catch (_: Exception) {
            showToast("Não foi possível partilhar esta página.")
        }
    }

    private fun copyCurrentUrl() {
        val url = tabs.activeTab?.let {
            it.webView.url ?: it.url
        }

        if (url.isNullOrBlank() || url == "about:blank") {
            showToast("Não existe um link para copiar.")
            return
        }

        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText("Link", url),
        )

        showToast("Link copiado.")
    }

    private fun toggleDesktopMode() {
        val tab = tabs.activeTab ?: return

        tab.desktopMode = !tab.desktopMode

        tab.webView.settings.userAgentString =
            if (tab.desktopMode) {
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            } else {
                WebSettings.getDefaultUserAgent(this)
            }

        tab.webView.settings.useWideViewPort = tab.desktopMode
        tab.webView.settings.loadWithOverviewMode = tab.desktopMode

        tab.webView.reload()

        showToast(
            if (tab.desktopMode) {
                "Versão desktop ativada."
            } else {
                "Versão móvel ativada."
            },
        )
    }

    private fun openTabsView() {
        if (
            tabsVisible ||
            animatingTabs ||
            searchVisible ||
            destroyed
        ) {
            return
        }

        closeBottomSheet()

        val active = tabs.activeTab ?: return

        tabs.tabs.forEach { tab ->
            val captured = captureView(
                tab.webView,
                MAX_THUMB_WIDTH,
                MAX_THUMB_HEIGHT,
            )

            if (captured != null) {
                tab.thumbnail = captured
            } else if (tab === active) {
                tab.thumbnail = null
            }
        }

        val liveSnapshot = captureView(
            active.webView,
            1280,
            2400,
        ) ?: run {
            tabsVisible = true
            showTabsWithoutMorph()
            return
        }

        val sourceRect = boundsInRoot(active.webView)
        val overlay = createTabsOverlay()

        tabsVisible = true
        animatingTabs = true
        overlay.alpha = 0f

        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        renderTabsGrid()

        active.webView.visibility = View.INVISIBLE

        val morph = createMorphView(
            liveSnapshot,
            sourceRect,
        )

        root.addView(morph, morph.layoutParams)
        morph.bringToFront()

        overlay.post {
            if (destroyed || !tabsVisible) {
                removeMorph(morph)
                return@post
            }

            val targetView = cardViews[active.id]?.preview
            val targetRect = targetView?.let(::boundsInRoot)

            if (
                targetRect == null ||
                targetRect.width() <= 0 ||
                targetRect.height() <= 0
            ) {
                removeMorph(morph)
                browserLayer.visibility = View.INVISIBLE
                overlay.alpha = 1f
                animatingTabs = false
                return@post
            }

            morph.bringToFront()

            animateMorph(
                morph = morph,
                from = sourceRect,
                to = targetRect,
                opening = true,
                onFrame = { value ->
                    overlay.alpha = value
                },
                end = {
                    removeMorph(morph)
                    browserLayer.visibility = View.INVISIBLE
                    overlay.alpha = 1f
                    animatingTabs = false
                },
            )
        }
    }

    private fun showTabsWithoutMorph() {
        val overlay = createTabsOverlay()

        tabsVisible = true
        overlay.alpha = 0f

        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        renderTabsGrid()

        overlay.animate()
            .alpha(1f)
            .setDuration(260L)
            .setInterpolator(ease)
            .withEndAction {
                animatingTabs = false
            }
            .start()

        browserLayer.visibility = View.INVISIBLE
    }

    private fun createTabsOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(palette.softSurface)
            isFocusableInTouchMode = true
            contentDescription = "Separadores abertos"
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(10))
        }

        val title = makeText(
            this,
            "Separadores",
            18f,
            palette.foreground,
            android.graphics.Typeface.BOLD,
        )

        addLinearChild(
            header,
            title,
            0,
            dp(38),
            weight = 1f,
        )

        val close = makeIconButton(
            this,
            "close",
            sizeDp = 38,
            iconDp = 21,
            tint = palette.foreground,
            backgroundColor = palette.pill,
            pressedColor = palette.pressed,
            description = "Fechar vista dos separadores",
        ) {
            val active = tabs.activeTab
            val preview = active?.let {
                cardViews[it.id]?.preview
            }

            if (active != null && preview != null) {
                closeTabsView(
                    active.id,
                    boundsInRoot(preview),
                )
            } else {
                closeTabsView(
                    active?.id,
                    null,
                )
            }
        }

        addLinearChild(
            header,
            close,
            dp(38),
            dp(38),
            left = dp(8),
        )

        content.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val scroller = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        tabsGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(100))
        }

        scroller.addView(
            tabsGrid,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        content.addView(
            scroller,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        overlay.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val fab = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(11), dp(20), dp(11))

            background = rippleBackground(
                this@MainActivity,
                palette.blue,
                26f,
                if (palette.isDark) {
                    0x338AB4F8
                } else {
                    0x331A73E8
                },
            )

            elevation = dp(4).toFloat()
            isClickable = true
            isFocusable = true

            setOnClickListener {
                val active = tabs.activeTab
                val preview = active?.let {
                    cardViews[it.id]?.preview
                }

                val rect = preview?.let(::boundsInRoot)

                closeTabsView(
                    active?.id,
                    rect,
                    addNewTab = true,
                )
            }
        }

        val add = BrowserIconView(
            this,
            "add",
        ).apply {
            tint = Color.WHITE
        }

        fab.addView(
            add,
            LinearLayout.LayoutParams(dp(20), dp(20)),
        )

        val fabText = makeText(
            this,
            "Nova aba",
            14f,
            Color.WHITE,
            android.graphics.Typeface.NORMAL,
        ).apply {
            typeface = android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL,
            )

            setPadding(dp(8), 0, 0, 0)
        }

        fab.addView(
            fabText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(22),
            ),
        )

        val fabParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        )

        fabParams.bottomMargin = dp(20)

        overlay.addView(fab, fabParams)

        return overlay
    }

    private fun renderTabsGrid() {
        if (!::tabsGrid.isInitialized || !tabsVisible || destroyed) {
            return
        }

        tabsGrid.removeAllViews()
        cardViews.clear()

        val snapshot = tabs.tabs.toList()
        var index = 0

        while (index < snapshot.size) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }

            val first = snapshot[index]

            row.addView(
                createTabCard(first, index),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )

            if (index + 1 < snapshot.size) {
                val spacer = View(this)

                row.addView(
                    spacer,
                    LinearLayout.LayoutParams(dp(12), 1),
                )

                val second = snapshot[index + 1]

                row.addView(
                    createTabCard(second, index + 1),
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    ),
                )
            } else {
                row.addView(
                    View(this),
                    LinearLayout.LayoutParams(0, 1, 1f),
                )
            }

            val rowParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )

            rowParams.bottomMargin = dp(12)

            tabsGrid.addView(row, rowParams)

            index += 2
        }
    }

    private fun createTabCard(
        tab: BrowserTab,
        index: Int,
    ): View {
        val active = tabs.activeTab === tab

        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            background = roundedBackground(
                palette.surface,
                dp(14).toFloat(),
                if (active) palette.blue else Color.TRANSPARENT,
                if (active) dp(1) else 0,
            )

            clipToOutline = true
            elevation = dp(1.5f).toFloat()
            isClickable = true
            isFocusable = true

            setOnClickListener {
                val rect = cardViews[tab.id]?.preview?.let(
                    ::boundsInRoot,
                )

                closeTabsView(tab.id, rect)
            }
        }

        val preview = AspectPreviewFrame(this).apply {
            setBackgroundColor(palette.surface)
            clipChildren = true
            clipToOutline = true
        }

        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP

            val bitmap = tab.thumbnail

            if (bitmap != null && !bitmap.isRecycled) {
                setImageBitmap(bitmap)
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }

            contentDescription = "Captura da página ${tab.title}"
        }

        preview.addView(
            image,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        if (tab.thumbnail == null || tab.thumbnail?.isRecycled == true) {
            addMockPreview(preview)
        }

        val close = makeIconButton(
            this,
            "close",
            sizeDp = 26,
            iconDp = 14,
            tint = palette.foreground,
            backgroundColor = if (palette.isDark) {
                0xEE292C33.toInt()
            } else {
                0xEEFFFFFF.toInt()
            },
            pressedColor = palette.pressed,
            description = "Fechar separador ${index + 1}",
        ) {
            val closedActive = tabs.activeTab === tab
            val removedIndex = tabs.tabs.indexOf(tab)

            tabs.closeTab(removedIndex)

            faviconTasks.remove(tab.id)?.cancel(true)

            if (tabs.size == 0) {
                tabs.createTab(true)
            }

            if (closedActive) {
                updateAddressDisplay(tabs.activeTab)
                updateNavigationButtons()
                updateEmptyState(tabs.activeTab)
            }

            renderTabsGrid()
        }

        val previewLayer = FrameLayout(this)

        previewLayer.addView(
            preview,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val closeParams = FrameLayout.LayoutParams(
            dp(26),
            dp(26),
            Gravity.TOP or Gravity.RIGHT,
        )

        closeParams.setMargins(0, dp(8), dp(8), 0)

        previewLayer.addView(close, closeParams)

        shell.addView(
            previewLayer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(10))
            background = android.graphics.drawable.ColorDrawable(
                palette.surface,
            )
        }

        val title = tab.title.takeIf {
            it.isNotBlank()
        } ?: tab.url?.let(::domainOf) ?: "Nova aba"

        val subtitle = tab.url?.let(::domainOf) ?: "Página em branco"

        val titleView = makeText(
            this,
            title,
            12.5f,
            palette.foreground,
            android.graphics.Typeface.BOLD,
        ).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        footer.addView(
            titleView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(17),
            ),
        )

        val subtitleView = makeText(
            this,
            subtitle,
            10.5f,
            palette.secondary,
        ).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        }

        footer.addView(
            subtitleView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(15),
            ),
        )

        shell.addView(
            footer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        cardViews[tab.id] = CardRefs(
            shell,
            preview,
            image,
        )

        return shell
    }

    private fun addMockPreview(preview: FrameLayout) {
        val mock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(palette.surface)
        }

        val mockBar = View(this).apply {
            background = roundedBackground(
                palette.pill,
                dp(6).toFloat(),
            )
        }

        mock.addView(
            mockBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(16),
            ),
        )

        val icon = BrowserIconView(this, "globe").apply {
            tint = palette.line
        }

        val iconParams = LinearLayout.LayoutParams(
            dp(26),
            dp(26),
        )

        iconParams.topMargin = dp(12)

        mock.addView(icon, iconParams)

        listOf(
            0.62f,
            0.86f,
            0.43f,
            0.94f,
            0.83f,
            0.58f,
        ).forEachIndexed { index, fraction ->

            val line = View(this).apply {
                background = roundedBackground(
                    if (index == 3) palette.line else palette.pill,
                    dp(3).toFloat(),
                )
            }

            val params = LinearLayout.LayoutParams(
                max(
                    dp(30),
                    (
                        resources.displayMetrics.widthPixels *
                            0.38f *
                            fraction
                        ).roundToInt(),
                ),
                dp(6),
            )

            params.topMargin = dp(
                if (index == 3) 15 else 7,
            )

            mock.addView(line, params)
        }

        preview.addView(
            mock,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun closeTabsView(
        tabId: Long?,
        sourceRect: android.graphics.Rect?,
        addNewTab: Boolean = false,
    ) {
        if (!tabsVisible || animatingTabs) {
            if (addNewTab && !destroyed) {
                tabs.createTab(true)
                openSearch("")
            }

            return
        }

        val overlay = root.childrenOfType<FrameLayout>()
            .firstOrNull {
                it.contentDescription == "Separadores abertos"
            }

        if (overlay == null) {
            tabsVisible = false
            browserLayer.visibility = View.VISIBLE

            if (addNewTab) {
                tabs.createTab(true)
                openSearch("")
            }

            return
        }

        animatingTabs = true

        val selected = if (tabId != null) {
            tabs.tabs.firstOrNull { it.id == tabId }
        } else {
            tabs.activeTab
        }

        if (selected != null) {
            tabs.switchTo(tabs.tabs.indexOf(selected))
        }

        updateAddressDisplay(tabs.activeTab)
        updateNavigationButtons()
        updateEmptyState(tabs.activeTab)
        setToolbarHidden(false, false)

        val targetRect = boundsInRoot(
            tabs.activeTab?.webView ?: webViewHost,
        )

        val bitmap = tabs.activeTab?.webView?.let {
            captureView(it, 1280, 2400)
        }

        if (
            sourceRect == null ||
            bitmap == null ||
            sourceRect.width() <= 0 ||
            sourceRect.height() <= 0 ||
            targetRect.width() <= 0 ||
            targetRect.height() <= 0
        ) {
            fadeTabsOut(overlay, addNewTab)
            return
        }

        browserLayer.visibility = View.VISIBLE
        browserLayer.alpha = 0f

        val morph = createMorphView(
            bitmap,
            sourceRect,
        )

        root.addView(morph, morph.layoutParams)
        morph.bringToFront()

        animateMorph(
            morph = morph,
            from = sourceRect,
            to = targetRect,
            opening = false,
            onFrame = { value ->
                overlay.alpha = 1f - value
                browserLayer.alpha = value
            },
            end = {
                removeMorph(morph)

                (overlay.parent as? ViewGroup)?.removeView(overlay)

                browserLayer.alpha = 1f
                browserLayer.visibility = View.VISIBLE

                tabsVisible = false
                animatingTabs = false

                toolbarLayer.bringToFront()
                setToolbarHidden(false, false)

                if (addNewTab) {
                    tabs.createTab(true)
                    openSearch("")
                }
            },
        )
    }

    private fun fadeTabsOut(
        overlay: FrameLayout,
        addNewTab: Boolean,
    ) {
        overlay.animate()
            .alpha(0f)
            .setDuration(240L)
            .setInterpolator(ease)
            .withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)

                browserLayer.visibility = View.VISIBLE
                browserLayer.alpha = 1f

                tabsVisible = false
                animatingTabs = false

                setToolbarHidden(false, false)

                if (addNewTab) {
                    tabs.createTab(true)
                    openSearch("")
                }
            }
            .start()
    }

    private fun createMorphView(
        bitmap: Bitmap,
        rect: android.graphics.Rect,
    ): ImageView {
        var radiusPx = 0f

        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            clipToOutline = true
            elevation = dp(14).toFloat()

            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(
                    view: View,
                    outline: Outline,
                ) {
                    outline.setRoundRect(
                        0,
                        0,
                        max(1, view.width),
                        max(1, view.height),
                        radiusPx,
                    )
                }
            }
        }

        image.tag = MorphMeta(
            rect,
            { radiusPx = it; image.invalidateOutline() },
        )

        image.layoutParams = FrameLayout.LayoutParams(
            max(1, rect.width()),
            max(1, rect.height()),
        ).apply {
            leftMargin = rect.left
            topMargin = rect.top
        }

        image.pivotX = 0f
        image.pivotY = 0f

        return image
    }

    private data class MorphMeta(
        val startRect: android.graphics.Rect,
        val updateRadius: (Float) -> Unit,
    )

    private fun animateMorph(
        morph: ImageView,
        from: android.graphics.Rect,
        to: android.graphics.Rect,
        opening: Boolean,
        onFrame: (Float) -> Unit,
        end: () -> Unit,
    ) {
        val meta = morph.tag as? MorphMeta

        val startWidth = max(
            1,
            from.width(),
        ).toFloat()

        val startHeight = max(
            1,
            from.height(),
        ).toFloat()

        val finalScaleX = to.width().toFloat() / startWidth
        val finalScaleY = to.height().toFloat() / startHeight

        morph.translationX = 0f
        morph.translationY = 0f
        morph.scaleX = 1f
        morph.scaleY = 1f

        val animator = ValueAnimator.ofFloat(
            0f,
            1f,
        ).apply {
            duration = ANIM_DURATION
            interpolator = ease

            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float

                morph.translationX =
                    (to.left - from.left) * fraction

                morph.translationY =
                    (to.top - from.top) * fraction

                morph.scaleX =
                    1f + (finalScaleX - 1f) * fraction

                morph.scaleY =
                    1f + (finalScaleY - 1f) * fraction

                val visibleRadius = dp(14).toFloat() *
                    if (opening) fraction else (1f - fraction)

                val currentMaxScale = max(
                    0.001f,
                    max(morph.scaleX, morph.scaleY),
                )

                meta?.updateRadius(
                    visibleRadius / currentMaxScale,
                )

                onFrame(fraction)
            }

            addListener(
                object : android.animation.AnimatorListenerAdapter() {

                    override fun onAnimationEnd(
                        animation: android.animation.Animator,
                    ) {
                        end()
                    }

                    override fun onAnimationCancel(
                        animation: android.animation.Animator,
                    ) {
                        end()
                    }
                },
            )
        }

        animator.start()
    }

    private fun removeMorph(morph: View) {
        (morph.parent as? ViewGroup)?.removeView(morph)
        (morph as? ImageView)?.setImageDrawable(null)
    }

    private fun boundsInRoot(view: View): android.graphics.Rect {
        val viewLocation = IntArray(2)
        val rootLocation = IntArray(2)

        view.getLocationOnScreen(viewLocation)
        root.getLocationOnScreen(rootLocation)

        val left =
            viewLocation[0] - rootLocation[0] - root.paddingLeft

        val top =
            viewLocation[1] - rootLocation[1] - root.paddingTop

        return android.graphics.Rect(
            left,
            top,
            left + view.width,
            top + view.height,
        )
    }

    private fun captureView(
        view: View,
        maxWidth: Int,
        maxHeight: Int,
    ): Bitmap? {
        if (view.width <= 0 || view.height <= 0 || destroyed) {
            return null
        }

        return try {
            val ratio = min(
                1f,
                min(
                    maxWidth.toFloat() / view.width,
                    maxHeight.toFloat() / view.height,
                ),
            )

            val width = max(
                1,
                (view.width * ratio).roundToInt(),
            )

            val height = max(
                1,
                (view.height * ratio).roundToInt(),
            )

            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888,
            )

            val canvas = Canvas(bitmap)

            canvas.scale(ratio, ratio)
            view.draw(canvas)

            bitmap
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun showSecurityInfo() {
        val tab = tabs.activeTab
        val url = tab?.webView?.url ?: tab?.url

        val secure = !url.isNullOrBlank() &&
            url.startsWith("https://", true)

        val domain = domainOf(url)

        val title: String
        val message: String

        if (url.isNullOrBlank() || url == "about:blank") {
            title = "Nenhum site aberto"
            message = "Abre um site para consultar as informações da ligação."
        } else if (secure) {
            title = "Ligação segura"

            message =
                "${domain ?: "Este site"} usa HTTPS. Os dados enviados entre este dispositivo e o site são cifrados durante o transporte. Isto não garante, por si só, que o site seja legítimo."
        } else {
            title = "Ligação não segura"

            message =
                "${domain ?: "Este site"} não está a usar HTTPS. A ligação não é cifrada; evita introduzir palavras-passe ou dados pessoais nesta página."
        }

        showInformationDialog(
            title,
            message,
            if (secure) "lock" else "globe",
            "Entendi",
        )
    }

    private fun showInformationDialog(
        title: String,
        message: String,
        icon: String,
        positiveLabel: String,
    ) {
        hideSecurityDialog()

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(palette.scrim)
            alpha = 0f
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(20))

            background = roundedBackground(
                palette.surface,
                dp(24).toFloat(),
            )

            elevation = dp(20).toFloat()
            scaleX = 0.92f
            scaleY = 0.92f
            translationY = dp(12).toFloat()
        }

        val iconHolder = FrameLayout(this).apply {
            background = roundedBackground(
                if (icon == "lock") palette.blueSoft else palette.pill,
                dp(28).toFloat(),
            )
        }

        val iconView = BrowserIconView(this, icon).apply {
            tint = if (icon == "lock") {
                palette.safe
            } else {
                palette.secondary
            }
        }

        iconHolder.addView(
            iconView,
            FrameLayout.LayoutParams(
                dp(26),
                dp(26),
                Gravity.CENTER,
            ),
        )

        card.addView(
            iconHolder,
            LinearLayout.LayoutParams(dp(52), dp(52)),
        )

        val titleView = makeText(
            this,
            title,
            19f,
            palette.foreground,
            android.graphics.Typeface.BOLD,
        ).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, dp(16), 0, dp(10))
        }

        card.addView(
            titleView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val body = makeText(
            this,
            message,
            14f,
            palette.secondary,
        ).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
        }

        card.addView(
            body,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val button = TextView(this).apply {
            text = positiveLabel
            textSize = 14f

            typeface = android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL,
            )

            setTextColor(
                if (palette.isDark) {
                    palette.background
                } else {
                    Color.WHITE
                },
            )

            gravity = Gravity.CENTER

            background = rippleBackground(
                this@MainActivity,
                palette.blue,
                22f,
                if (palette.isDark) {
                    0x338AB4F8
                } else {
                    0x331A73E8
                },
            )

            isClickable = true
            isFocusable = true

            setOnClickListener {
                hideSecurityDialog()
            }
        }

        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(44),
        )

        buttonParams.topMargin = dp(22)

        card.addView(button, buttonParams)

        overlay.addView(
            card,
            FrameLayout.LayoutParams(
                min(dp(340), resources.displayMetrics.widthPixels - dp(40)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        overlay.setOnClickListener {
            hideSecurityDialog()
        }

        card.setOnClickListener {
            // Consume taps inside the dialog.
        }

        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        securityOverlay = overlay

        overlay.animate()
            .alpha(1f)
            .setDuration(190L)
            .setInterpolator(ease)
            .start()

        card.animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(260L)
            .setInterpolator(ease)
            .start()
    }

    private fun hideSecurityDialog() {
        val overlay = securityOverlay ?: return

        securityOverlay = null

        overlay.animate()
            .alpha(0f)
            .setDuration(150L)
            .withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
            }
            .start()
    }

    private fun showThemeSelector() {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(palette.scrim)
            alpha = 0f
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(12))

            background = roundedBackground(
                palette.surface,
                dp(22).toFloat(),
            )

            elevation = dp(18).toFloat()
            scaleX = 0.94f
            scaleY = 0.94f
            translationY = dp(12).toFloat()
        }

        val heading = makeText(
            this,
            "Tema",
            19f,
            palette.foreground,
            android.graphics.Typeface.BOLD,
        )

        card.addView(
            heading,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(34),
            ),
        )

        val description = makeText(
            this,
            "Escolhe a aparência do navegador.",
            13f,
            palette.secondary,
        )

        val descriptionParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(28),
        )

        card.addView(description, descriptionParams)

        listOf(
            BrowserThemeMode.SYSTEM to "Sistema",
            BrowserThemeMode.LIGHT to "Claro",
            BrowserThemeMode.DARK to "Escuro",
        ).forEach { (mode, label) ->

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), 0, dp(12), 0)
                minimumHeight = dp(48)

                background = rippleBackground(
                    this@MainActivity,
                    Color.TRANSPARENT,
                    12f,
                    palette.pressed,
                )

                isClickable = true
                isFocusable = true

                setOnClickListener {
                    setThemeMode(mode)
                    dismissThemeSelector()
                }
            }

            val modeIcon = BrowserIconView(
                this,
                when (mode) {
                    BrowserThemeMode.SYSTEM -> "desktopMode"
                    BrowserThemeMode.LIGHT -> "home"
                    BrowserThemeMode.DARK -> "lock"
                },
            ).apply {
                tint = palette.secondary
            }

            row.addView(
                modeIcon,
                LinearLayout.LayoutParams(dp(22), dp(22)),
            )

            val labelView = makeText(
                this,
                label,
                15f,
                palette.foreground,
            ).apply {
                setPadding(dp(14), 0, 0, 0)
            }

            addLinearChild(
                row,
                labelView,
                0,
                dp(48),
                weight = 1f,
            )

            if (themeMode == mode) {
                val check = makeText(
                    this,
                    "✓",
                    18f,
                    palette.blue,
                    android.graphics.Typeface.BOLD,
                )

                row.addView(
                    check,
                    LinearLayout.LayoutParams(dp(24), dp(48)),
                )
            }

            card.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(48),
                ),
            )
        }

        overlay.addView(
            card,
            FrameLayout.LayoutParams(
                min(dp(340), resources.displayMetrics.widthPixels - dp(40)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        overlay.setOnClickListener {
            dismissThemeSelector()
        }

        card.setOnClickListener {
            // Consume taps inside the theme selector.
        }

        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        themeOverlay = overlay

        overlay.animate()
            .alpha(1f)
            .setDuration(190L)
            .setInterpolator(ease)
            .start()

        card.animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(250L)
            .setInterpolator(ease)
            .start()
    }

    private fun dismissThemeSelector() {
        val overlay = themeOverlay ?: return

        themeOverlay = null

        overlay.animate()
            .alpha(0f)
            .setDuration(150L)
            .withEndAction {
                (overlay.parent as? ViewGroup)?.removeView(overlay)
            }
            .start()
    }

    private fun setThemeMode(mode: BrowserThemeMode) {
        themeMode = mode

        getSharedPreferences(
            PREFS,
            MODE_PRIVATE,
        ).edit()
            .putString(PREF_THEME, mode.name)
            .apply()

        palette = BrowserPalette.from(this, mode)

        window.statusBarColor = palette.surface
        window.navigationBarColor = palette.surface

        updateSystemBarIconContrast()
        applyThemeToChrome()
        updateAddressDisplay(tabs.activeTab)
        updateEmptyState(tabs.activeTab)

        tabs.tabs.forEach { tab ->
            if (
                tab.url.isNullOrBlank() ||
                tab.url == "about:blank"
            ) {
                tab.webView.setBackgroundColor(palette.surface)
            }
        }
    }

    private fun applyThemeToChrome() {
        if (!::root.isInitialized) return

        root.setBackgroundColor(palette.background)
        browserLayer.setBackgroundColor(palette.surface)
        webViewHost.setBackgroundColor(palette.surface)
        toolbarLayer.setBackgroundColor(palette.surface)
        toolbarRow.setBackgroundColor(palette.surface)

        addressPill.background = roundedBackground(
            palette.pill,
            dp(28).toFloat(),
        )

        titleText.setTextColor(palette.foreground)
        fallbackFavicon.tint = palette.secondary

        lockButton.background = rippleBackground(
            this,
            palette.softSurface,
            50f,
            palette.pressed,
        )

        listOf(
            backButton,
            forwardButton,
            menuButton,
        ).forEach { button ->
            button.background = rippleBackground(
                this,
                Color.TRANSPARENT,
                50f,
                palette.pressed,
            )

            button.findIcon()?.tint = palette.foreground
        }

        lockIcon.tint =
            if (
                (
                    tabs.activeTab?.let {
                        it.webView.url ?: it.url
                    } ?: ""
                    ).startsWith("https://", true)
            ) {
                palette.safe
            } else {
                palette.unsafe
            }

        emptyState.setBackgroundColor(palette.surface)

        emptyIcon.tint = if (palette.isDark) {
            0xFF5F6368.toInt()
        } else {
            0xFFDADCE0.toInt()
        }

        emptyText.setTextColor(palette.secondary)

        progressBar.progressTintList =
            android.content.res.ColorStateList.valueOf(palette.blue)

        if (tabsVisible) {
            renderTabsGrid()
        }
    }

    private fun themeLabel(mode: BrowserThemeMode): String =
        when (mode) {
            BrowserThemeMode.SYSTEM -> "Sistema"
            BrowserThemeMode.LIGHT -> "Claro"
            BrowserThemeMode.DARK -> "Escuro"
        }

    private fun topRoundedBackground(
        color: Int,
        radius: Float,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)

            cornerRadii = floatArrayOf(
                radius,
                radius,
                radius,
                radius,
                0f,
                0f,
                0f,
                0f,
            )
        }

    private fun View.findIcon(): BrowserIconView? {
        if (this is BrowserIconView) return this

        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                val found = getChildAt(index).findIcon()

                if (found != null) {
                    return found
                }
            }
        }

        return null
    }

    private inline fun <reified T : View> ViewGroup.childrenOfType(): List<T> =
        (0 until childCount).mapNotNull {
            getChildAt(it) as? T
        }

    private fun showToast(message: String) {
        if (!destroyed) {
            Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            themeOverlay != null -> {
                dismissThemeSelector()
            }

            securityOverlay != null -> {
                hideSecurityDialog()
            }

            searchVisible -> {
                closeSearch()
            }

            sheetVisible -> {
                closeBottomSheet()
            }

            tabsVisible -> {
                val active = tabs.activeTab

                val preview = active?.let {
                    cardViews[it.id]?.preview
                }

                closeTabsView(
                    active?.id,
                    preview?.let(::boundsInRoot),
                )
            }

            tabs.activeTab?.webView?.canGoBack() == true -> {
                goBack()
            }

            tabs.size > 1 -> {
                tabs.closeTab(tabs.activeIndex)
                updateAddressDisplay(tabs.activeTab)
                updateNavigationButtons()
            }

            else -> {
                super.onBackPressed()
            }
        }
    }

    override fun onConfigurationChanged(
        newConfig: Configuration,
    ) {
        super.onConfigurationChanged(newConfig)

        if (themeMode != BrowserThemeMode.SYSTEM) {
            root.requestApplyInsets()
            return
        }

        val updatedPalette = BrowserPalette.from(
            this,
            themeMode,
        )

        val appearanceChanged =
            updatedPalette.isDark != palette.isDark

        var restoreSearch = false
        var searchQuery = ""
        var restoreSheet = false
        var restoreSecurity = false
        var restoreThemeSelector = false

        if (appearanceChanged) {
            restoreSearch = searchVisible
            searchQuery = searchInput?.text?.toString().orEmpty()
            restoreSheet = sheetVisible
            restoreSecurity = securityOverlay != null
            restoreThemeSelector = themeOverlay != null

            suggestionGeneration.incrementAndGet()

            suggestionRunnable?.let(mainHandler::removeCallbacks)
            suggestionRunnable = null

            suggestionFuture?.cancel(true)
            suggestionFuture = null

            searchInput?.let { input ->
                (
                    getSystemService(INPUT_METHOD_SERVICE)
                        as? InputMethodManager
                    )?.hideSoftInputFromWindow(
                    input.windowToken,
                    0,
                )

                input.clearFocus()
            }

            searchOverlay?.let { overlay ->
                (overlay.parent as? ViewGroup)?.removeView(overlay)
            }

            searchOverlay = null
            searchInput = null
            suggestionsContainer = null
            searchVisible = false

            root.findViewWithTag<FrameLayout>(
                "orbit_bottom_sheet",
            )?.let { sheet ->
                (sheet.parent as? ViewGroup)?.removeView(sheet)
            }

            sheetVisible = false

            securityOverlay?.let {
                (it.parent as? ViewGroup)?.removeView(it)
            }

            securityOverlay = null

            themeOverlay?.let {
                (it.parent as? ViewGroup)?.removeView(it)
            }

            themeOverlay = null
        }

        palette = updatedPalette

        window.statusBarColor = palette.surface
        window.navigationBarColor = palette.surface

        updateSystemBarIconContrast()
        applyThemeToChrome()
        root.requestApplyInsets()

        if (appearanceChanged) {
            root.post {
                if (destroyed) return@post

                when {
                    restoreSearch -> openSearch(searchQuery)
                    restoreSecurity -> showSecurityInfo()
                    restoreThemeSelector -> showThemeSelector()
                    restoreSheet -> openBottomSheet()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        if (::tabs.isInitialized) {
            tabs.activeTab?.webView?.onPause()
        }
    }

    override fun onResume() {
        super.onResume()

        if (::tabs.isInitialized) {
            tabs.activeTab?.webView?.onResume()
        }
    }

    override fun onDestroy() {
        destroyed = true

        suggestionRunnable?.let(mainHandler::removeCallbacks)
        suggestionFuture?.cancel(true)

        faviconTasks.values.forEach {
            it.cancel(true)
        }

        faviconTasks.clear()

        mainHandler.removeCallbacksAndMessages(null)

        suggestionExecutor.shutdownNow()
        faviconExecutor.shutdownNow()

        if (::tabs.isInitialized) {
            tabs.dispose()
        }

        super.onDestroy()
    }

    private inner class AspectPreviewFrame(
        context: Context,
    ) : FrameLayout(context) {

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val width = MeasureSpec.getSize(widthMeasureSpec)

            val computedHeight = max(
                dp(160),
                (width * 13f / 9f).roundToInt(),
            )

            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(
                    computedHeight,
                    MeasureSpec.EXACTLY,
                ),
            )
        }
    }
}