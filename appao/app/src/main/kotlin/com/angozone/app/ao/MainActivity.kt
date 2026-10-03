package com.angozone.app.ao

import android.Manifest
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single-Activity native app ao WebView host.
 *
 * The application intentionally contains one Activity and one WebView.
 * There is no Compose, Material Components, Fragment, Navigation Component,
 * XML layout or external UI toolkit.
 */
class MainActivity : ComponentActivity() {

    private lateinit var rootContainer: FrameLayout
    private lateinit var progressBar: View
    private lateinit var webView: WebView
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraOutputUri: Uri? = null
    private var cameraOutputFile: File? = null

    private var lastLoadHadError = false
    private var currentPageUrl: String = Config.TARGET_URL
    private var renderRecoveryCount = 0
    private var lastRenderRecoveryAt = 0L

    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingPermissionFlow = PermissionFlow.NONE

    private val preferences by lazy {
        getSharedPreferences(
            Config.PREFS,
            Context.MODE_PRIVATE
        )
    }

    private enum class PermissionFlow {
        NONE,
        WEB_RESOURCE,
        GEOLOCATION,
        NATIVE_REQUEST
    }

    private val connectivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(
            context: Context?,
            intent: Intent?
        ) {
            if (
                intent?.action == ConnectivityManager.CONNECTIVITY_ACTION &&
                intent.getBooleanExtra(
                    ConnectivityManager.EXTRA_NO_CONNECTIVITY,
                    false
                ).not() &&
                lastLoadHadError &&
                ::webView.isInitialized
            ) {
                lastLoadHadError = false
                webView.post {
                    if (!isFinishing && !isDestroyed) {
                        webView.reload()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the WebView/content below the system status bar. No native toolbar is added.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        applyStoredThemeBeforeWebView()
        registerFileChooser()
        registerPermissionLauncher()
        registerBackCallback()
        configureWebView()
        createContentRoot()
        registerConnectivityReceiver()

        webView.loadUrl(Config.TARGET_URL)
    }

    private fun createContentRoot() {
        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }

        rootContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        progressBar = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#1565C0"),
                    Color.parseColor("#00E5FF")
                )
            ).also { it.cornerRadius = dp(2f).toFloat() }
            alpha = 0f
            scaleX = 0f
            pivotX = 0f
            pivotY = 0.5f
            elevation = dp(6f).toFloat()
        }

        rootContainer.addView(
            progressBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(3f),
                Gravity.TOP or Gravity.START
            )
        )

        setContentView(rootContainer)
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    /**
     * Applies persisted colors before WebView creation so a returning user sees
     * the same native surface while the website starts rendering.
     */
    private fun applyStoredThemeBeforeWebView() {
        val statusColor = readColor(
            Config.PREF_STATUS_BAR,
            Config.DEFAULT_STATUS_BAR_COLOR
        )

        val navigationColor = readColor(
            Config.PREF_NAVIGATION_BAR,
            Config.DEFAULT_NAVIGATION_BAR_COLOR
        )

        val splashColor = readColor(
            Config.PREF_SPLASH,
            defaultSplashForSystemTheme()
        )

        window.statusBarColor = statusColor
        window.navigationBarColor = navigationColor
        window.setBackgroundDrawable(
            ColorDrawable(splashColor)
        )

        updateStatusBarLight(
            preferences.getBoolean(
                Config.PREF_STATUS_LIGHT,
                false
            )
        )

        updateNavigationBarLight(
            preferences.getBoolean(
                Config.PREF_NAVIGATION_LIGHT,
                false
            )
        )
    }

    private fun defaultSplashForSystemTheme(): String {
        val isNight =
            (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        return if (isNight) {
            "#0D0F12"
        } else {
            "#FFFFFF"
        }
    }

    private fun registerFileChooser() {
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            deliverFileChooserResult(result)
        }
    }

    private fun registerPermissionLauncher() {
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            handlePermissionResult(result)
        }
    }

    private fun registerBackCallback() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (::webView.isInitialized && webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        finish()
                    }
                }
            }
        )
    }

    private fun configureWebView() {
        webView = WebView(this)

        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.allowContentAccess = true
        settings.allowFileAccess = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setGeolocationEnabled(true)

        CookieManager.getInstance().setAcceptCookie(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance()
                .setAcceptThirdPartyCookies(webView, true)
        }

        webView.addJavascriptInterface(
            WebAppInterface(this),
            "Android"
        )

        webView.webViewClient = createWebViewClient()
        webView.webChromeClient = createWebChromeClient()

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimeType
            )
        }
    }

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                return routeUrl(uri)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!url.isNullOrBlank()) currentPageUrl = url
                lastLoadHadError = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!url.isNullOrBlank()) currentPageUrl = url
                syncWebThemeAndUi()
                finishProgress()
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: android.webkit.RenderProcessGoneDetail?
            ): Boolean {
                recoverFromRenderProcessCrash()
                return true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    lastLoadHadError = true
                }
            }
        }
    }

    private fun createWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                updateProgress(newProgress)
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                if (isFinishing || isDestroyed) { result?.cancel(); return true }
                showNativeAlert(message.orEmpty(), result)
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                if (isFinishing || isDestroyed) { result?.cancel(); return true }
                showNativeConfirm(message.orEmpty(), result)
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                if (isFinishing || isDestroyed) { result?.cancel(); return true }
                showNativePrompt(message.orEmpty(), defaultValue.orEmpty(), result)
                return true
            }

            override fun onShowFileChooser(
                view: WebView?,
                filePath: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePath == null || fileChooserParams == null) {
                    return false
                }

                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePath

                return try {
                    fileChooserLauncher.launch(
                        createFileChooserIntent(fileChooserParams)
                    )
                    true
                } catch (_: Exception) {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = null
                    false
                }
            }

            override fun onPermissionRequest(
                request: PermissionRequest?
            ) {
                if (request == null) return

                runOnUiThread {
                    handleWebPermissionRequest(request)
                }
            }

            override fun onPermissionRequestCanceled(
                request: PermissionRequest?
            ) {
                if (request != null && pendingWebPermissionRequest === request) {
                    pendingWebPermissionRequest = null
                    pendingPermissionFlow = PermissionFlow.NONE
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin == null || callback == null) return

                runOnUiThread {
                    handleGeolocationRequest(
                        origin,
                        callback
                    )
                }
            }
        }
    }

    private fun updateProgress(progress: Int) {
        if (!::progressBar.isInitialized) return
        progressBar.visibility = View.VISIBLE
        progressBar.alpha = 1f
        val fraction = (progress.coerceIn(0, 100) / 100f).coerceAtLeast(0.04f)
        progressBar.animate().cancel()
        progressBar.animate()
            .scaleX(fraction)
            .setDuration(120L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
        if (progress >= 100) finishProgress()
    }

    private fun finishProgress() {
        if (!::progressBar.isInitialized) return
        progressBar.animate().cancel()
        progressBar.animate()
            .alpha(0f)
            .setDuration(220L)
            .withEndAction {
                if (::progressBar.isInitialized) {
                    progressBar.scaleX = 0f
                    progressBar.visibility = View.GONE
                }
            }
            .start()
    }

    private fun recoverFromRenderProcessCrash() {
        if (!::rootContainer.isInitialized || isFinishing || isDestroyed) return

        val now = System.currentTimeMillis()
        if (now - lastRenderRecoveryAt > 10_000L) renderRecoveryCount = 0
        lastRenderRecoveryAt = now
        renderRecoveryCount++

        val url = currentPageUrl.ifBlank { Config.TARGET_URL }
        rootContainer.removeView(webView)
        runCatching {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.removeJavascriptInterface("Android")
            webView.destroy()
        }

        configureWebView()
        rootContainer.addView(
            webView,
            0,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        webView.post {
            if (!isFinishing && !isDestroyed) {
                webView.loadUrl(url)
            }
        }
    }

    private fun showNativeAlert(message: String, result: JsResult?) {
        val dialog = AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> result?.confirm() }
            .create()
        dialog.setOnCancelListener { result?.cancel() }
        dialog.setOnShowListener { styleNativeDialog(dialog) }
        runCatching { dialog.show() }.onFailure { result?.cancel() }
    }

    private fun showNativeConfirm(message: String, result: JsResult?) {
        val dialog = AlertDialog.Builder(this)
            .setMessage(message)
            .setNegativeButton("Cancelar") { _, _ -> result?.cancel() }
            .setPositiveButton("OK") { _, _ -> result?.confirm() }
            .create()
        dialog.setOnCancelListener { result?.cancel() }
        dialog.setOnShowListener { styleNativeDialog(dialog) }
        runCatching { dialog.show() }.onFailure { result?.cancel() }
    }

    private fun showNativePrompt(message: String, defaultValue: String, result: JsPromptResult?) {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(defaultValue)
            setSelectAllOnFocus(true)
            setTextColor(popupTextColor())
            setHintTextColor(dimColor(popupTextColor(), 0.42f))
            backgroundTintList = android.content.res.ColorStateList.valueOf(popupAccentColor())
        }

        val wrap = FrameLayout(this).apply {
            setPadding(dp(24f), 0, dp(24f), 0)
            addView(input, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(52f)
            ))
        }

        val dialog = AlertDialog.Builder(this)
            .setMessage(message)
            .setView(wrap)
            .setNegativeButton("Cancelar") { _, _ -> result?.cancel() }
            .setPositiveButton("OK") { _, _ -> result?.confirm(input.text.toString()) }
            .create()
        dialog.setOnCancelListener { result?.cancel() }
        dialog.setOnShowListener {
            styleNativeDialog(dialog)
            input.requestFocus()
        }
        runCatching { dialog.show() }.onFailure { result?.cancel() }
    }

    private fun styleNativeDialog(dialog: AlertDialog) {
        val bg = popupBackgroundColor()
        val text = popupTextColor()
        val accent = popupAccentColor()

        dialog.window?.apply {
            setBackgroundDrawable(
                GradientDrawable().apply {
                    setColor(bg)
                    cornerRadius = dp(26f).toFloat()
                }
            )
            setDimAmount(0.32f)
        }
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(text)
        val titleId = resources.getIdentifier("alertTitle", "id", "android")
        if (titleId != 0) dialog.findViewById<TextView>(titleId)?.setTextColor(text)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(accent)
    }

    private fun popupBackgroundColor(): Int = preferences.getInt(
        Config.PREF_POPUP_BG,
        preferences.getInt(Config.PREF_STATUS_BAR, Color.parseColor(Config.DEFAULT_STATUS_BAR_COLOR))
    )

    private fun popupTextColor(): Int = preferences.getInt(
        Config.PREF_POPUP_TEXT,
        if (isLightColor(popupBackgroundColor())) Color.BLACK else Color.WHITE
    )

    private fun popupAccentColor(): Int = preferences.getInt(
        Config.PREF_POPUP_ACCENT,
        if (isLightColor(popupBackgroundColor())) Color.rgb(0, 102, 204) else Color.rgb(0, 188, 212)
    )

    private fun isLightColor(color: Int): Boolean {
        val lum = (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0
        return lum > 0.58
    }

    fun updatePopupColors(background: Int?, text: Int?, accent: Int?) {
        val editor = preferences.edit()
        if (background != null) editor.putInt(Config.PREF_POPUP_BG, background)
        if (text != null) editor.putInt(Config.PREF_POPUP_TEXT, text)
        if (accent != null) editor.putInt(Config.PREF_POPUP_ACCENT, accent)
        editor.apply()
    }

    private fun syncWebThemeAndUi() {
        if (!::webView.isInitialized || isFinishing || isDestroyed) return
        webView.evaluateJavascript(UI_POLISH_SCRIPT, null)
    }

    private val UI_POLISH_SCRIPT = """
        (function () {
          if (window.__APP_AO_NATIVE_UI_V2__) return;
          window.__APP_AO_NATIVE_UI_V2__ = true;

          const root = document.documentElement;
          const q = (s) => Array.from(document.querySelectorAll(s));
          const clamp = (n, a, b) => Math.max(a, Math.min(b, n));

          const parseRgb = (value) => {
            if (!value) return null;
            const v = value.trim();
            let m = v.match(/^#([0-9a-f]{3,8})$/i);
            if (m) {
              let h = m[1];
              if (h.length === 3) h = h.split('').map(x => x + x).join('');
              if (h.length === 4) h = h.slice(0, 3).split('').map(x => x + x).join('');
              if (h.length >= 6) return [parseInt(h.slice(0,2),16), parseInt(h.slice(2,4),16), parseInt(h.slice(4,6),16)];
            }
            m = v.match(/^rgba?\(\s*([\d.]+)[, ]+\s*([\d.]+)[, ]+\s*([\d.]+)(?:[, /]+\s*[\d.]+)?\s*\)$/i);
            if (m) return [clamp(Math.round(+m[1]),0,255), clamp(Math.round(+m[2]),0,255), clamp(Math.round(+m[3]),0,255)];
            m = v.match(/^hsla?\(\s*([\d.]+)(?:deg)?[, ]+\s*([\d.]+)%[, ]+\s*([\d.]+)%(?:[, /]+\s*[\d.]+)?\s*\)$/i);
            if (m) {
              let h=((+m[1]%360)+360)%360/360, sat=clamp(+m[2]/100,0,1), l=clamp(+m[3]/100,0,1);
              const f=(n)=>{ const k=(n+h*12)%12; return l-sat*Math.min(l,1-l)*Math.max(-1,Math.min(k-3,9-k,1)); };
              return [Math.round(255*f(0)),Math.round(255*f(8)),Math.round(255*f(4))];
            }
            return null;
          };

          const toHex = (rgb) => '#' + rgb.map(v => v.toString(16).padStart(2,'0')).join('').toUpperCase();
          const luminance = (rgb) => (0.2126*rgb[0] + 0.7152*rgb[1] + 0.0722*rgb[2]) / 255;

          const cssVar = (...names) => {
            const styles = getComputedStyle(root);
            for (const n of names) {
              const v = styles.getPropertyValue(n).trim();
              if (v) {
                const parsed = parseRgb(v);
                if (parsed) return parsed;
              }
            }
            return null;
          };

          const bestBackground = () => {
            const metas = q('meta[name="theme-color"]');
            for (const meta of metas) {
              const media = meta.getAttribute('media');
              if (media && window.matchMedia && !window.matchMedia(media).matches) continue;
              const parsed = parseRgb(meta.content || '');
              if (parsed) return parsed;
            }
            const vars = cssVar('--background','--bg','--surface','--color-background','--color-surface');
            if (vars) return vars;
            const els = [
              document.body, root,
              document.querySelector('main'),
              document.querySelector('[role="main"]'),
              document.querySelector('#app'),
              document.querySelector('#root'),
              document.querySelector('[data-theme-root]')
            ].filter(Boolean);
            let best = null;
            let bestArea = 0;
            for (const el of els) {
              const c = parseRgb(getComputedStyle(el).backgroundColor);
              if (!c) continue;
              const r = el.getBoundingClientRect ? el.getBoundingClientRect() : {width:0,height:0};
              const area = Math.max(0, r.width * r.height);
              if (!best || area >= bestArea) { best = c; bestArea = area; }
            }
            return best || [18,18,18];
          };

          const bestText = () => cssVar('--foreground','--text','--color-text','--on-background','--color-on-background') || parseRgb(getComputedStyle(document.body).color) || [255,255,255];
          const bestAccent = () => cssVar('--primary','--primary-color','--accent','--accent-color','--color-primary','--color-accent') || [0,188,212];

          function syncTheme() {
            try {
              const bg = bestBackground();
              const text = bestText();
              const accent = bestAccent();
              const bgHex = toHex(bg);
              const light = luminance(bg) > 0.58;
              if (window.Android) {
                if (window.Android.setStatusBarColor) window.Android.setStatusBarColor(bgHex);
                if (window.Android.setStatusBarLight) window.Android.setStatusBarLight(light);
                if (window.Android.setPopupColors) window.Android.setPopupColors(bgHex, toHex(text), toHex(accent));
              }
            } catch (_) {}
          }

          const style = document.createElement('style');
          style.id = 'app-ao-native-polish';
          style.textContent = `
            @keyframes appAoShimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
            [class*="skeleton" i], [id*="skeleton" i], [class*="shimmer" i], [id*="shimmer" i], [data-skeleton] {
              background-image: linear-gradient(90deg, rgba(127,127,135,.13) 0%, rgba(255,255,255,.30) 45%, rgba(127,127,135,.13) 80%) !important;
              background-size: 220% 100% !important;
              animation: appAoShimmer 1.25s linear infinite !important;
            }
            [data-image-card], .image-card, .imageCard, [class*="image-card" i], [class*="imageCard" i], article img, [class*="feed" i] img {
              border-radius: 16px !important;
              overflow: hidden !important;
            }
            .appao-native-appbar, .appao-native-inputbar {
              will-change: translate;
              transition: translate 220ms cubic-bezier(.22,1,.36,1) !important;
            }
          `;
          (document.head || root).appendChild(style);

          let appBars = [];
          let inputBars = [];
          const previous = new WeakMap();

          const visible = (el) => { const r = el.getBoundingClientRect(); const s = getComputedStyle(el); return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none'; };
          const looksInputBar = (el) => {
            const classText = typeof el.className === 'string' ? el.className : '';
            const name = ((el.id || '') + ' ' + classText).toLowerCase();
            return /input.?bar|composer|chat.?input|message.?input|bottom.?input/.test(name) || !!el.querySelector('textarea,input,[contenteditable="true"]');
          };

          function applyTranslation(el, value) {
            if ('translate' in el.style) {
              el.style.translate = value;
            } else {
              el.style.transform = value;
            }
          }

          function scanBars() {
            const all = q('header, nav, [role="banner"], [data-appbar], [data-app-bar], [class*="appbar" i], [class*="app-bar" i], [class*="toolbar" i], [class*="inputbar" i], [class*="input-bar" i], [class*="composer" i], [class*="chat-input" i], [class*="message-input" i]');
            appBars = all.filter(el => visible(el) && (getComputedStyle(el).position === 'fixed' || getComputedStyle(el).position === 'sticky') && el.getBoundingClientRect().top <= 28).slice(0, 2);
            inputBars = all.filter(el => visible(el) && looksInputBar(el) && (getComputedStyle(el).position === 'fixed' || getComputedStyle(el).position === 'sticky') && window.innerHeight - el.getBoundingClientRect().bottom <= 32).slice(-2);
            [...appBars, ...inputBars].forEach(el => {
              el.classList.add(inputBars.includes(el) ? 'appao-native-inputbar' : 'appao-native-appbar');
              if (!previous.has(el)) previous.set(el, 0);
            });
          }

          let hidden = false;
          function showBars() {
            hidden = false;
            appBars.forEach(el => applyTranslation(el, '0 0'));
            inputBars.forEach(el => applyTranslation(el, '0 0'));
          }
          function hideBars() {
            hidden = true;
            appBars.forEach(el => applyTranslation(el, '0 -110%'));
            inputBars.forEach(el => applyTranslation(el, '0 115%'));
          }

          const last = new WeakMap();
          const onScroll = (event) => {
            const target = event.target === document ? (document.scrollingElement || document.documentElement) : event.target;
            const now = target && typeof target.scrollTop === 'number' ? target.scrollTop : window.scrollY || 0;
            const before = last.get(target);
            last.set(target, now);
            if (before == null) return;
            const delta = now - before;
            if (now <= 4 || delta < -7) showBars();
            else if (delta > 7) hideBars();
          };

          document.addEventListener('scroll', onScroll, {passive:true, capture:true});
          window.addEventListener('resize', () => { scanBars(); });
          scanBars();
          showBars();

          let scanTimer = null;
          const observer = new MutationObserver(() => {
            clearTimeout(scanTimer);
            scanTimer = setTimeout(() => { scanBars(); syncTheme(); }, 80);
          });
          observer.observe(root, {subtree:true, childList:true, attributes:true, attributeFilter:['class','style','id','data-theme','content']});

          if (window.matchMedia) {
            const mq = window.matchMedia('(prefers-color-scheme: dark)');
            mq.addEventListener && mq.addEventListener('change', syncTheme);
          }
          syncTheme();
        })();
    """.trimIndent()

    private fun routeUrl(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.US)

        if (scheme == "http" || scheme == "https") {
            val targetHost = Uri.parse(Config.TARGET_URL).host
            val requestedHost = uri.host

            val sameHost =
                targetHost != null &&
                    requestedHost != null &&
                    targetHost.equals(requestedHost, true)

            if (sameHost) {
                return false
            }
        }

        if (scheme == "about" || scheme == "file") {
            return false
        }

        return try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                )
            )
            true
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Nenhuma aplicação disponível para abrir este link.",
                Toast.LENGTH_SHORT
            ).show()
            true
        }
    }

    private fun createFileChooserIntent(
        params: WebChromeClient.FileChooserParams
    ): Intent {
        val acceptTypes = params.acceptTypes
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val mimeTypes = if (acceptTypes.isEmpty()) {
            arrayOf("*/*")
        } else {
            acceptTypes.toTypedArray()
        }

        if (params.isCaptureEnabled && mimeTypes.size == 1) {
            when (val mime = mimeTypes[0].lowercase(Locale.US)) {
                "image/*",
                "image/jpeg",
                "image/png",
                "image/webp" -> {
                    return createCaptureIntent(
                        MediaStore.ACTION_IMAGE_CAPTURE,
                        "image",
                        ".jpg"
                    )
                }

                "video/*",
                "video/mp4",
                "video/webm" -> {
                    return createCaptureIntent(
                        MediaStore.ACTION_VIDEO_CAPTURE,
                        "video",
                        ".mp4"
                    )
                }

                "audio/*",
                "audio/mp4",
                "audio/mpeg",
                "audio/webm" -> {
                    return Intent(
                        MediaStore.Audio.Media.RECORD_SOUND_ACTION
                    )
                }
            }
        }

        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)

            type = if (mimeTypes.size == 1) {
                mimeTypes[0]
            } else {
                "*/*"
            }

            if (mimeTypes.size > 1) {
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    mimeTypes
                )
            }

            putExtra(
                Intent.EXTRA_ALLOW_MULTIPLE,
                params.mode ==
                    WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
            )

            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
    }

    private fun createCaptureIntent(
        action: String,
        prefix: String,
        extension: String
    ): Intent {
        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss_SSS",
            Locale.US
        ).format(Date())

        val file = File(
            cacheDir,
            "${prefix}_${timestamp}${extension}"
        )

        val uri = FileProvider.getUriForFile(
            this,
            "com.angozone.app.ao.fileprovider",
            file
        )

        cameraOutputFile = file
        cameraOutputUri = uri

        return Intent(action).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            clipData = ClipData.newRawUri(
                "output",
                uri
            )
        }
    }

    private fun deliverFileChooserResult(
        result: ActivityResult
    ) {
        val callback = filePathCallback ?: return
        filePathCallback = null

        if (result.resultCode != RESULT_OK) {
            cleanupCaptureFile()
            callback.onReceiveValue(null)
            return
        }

        val captureUri = cameraOutputUri

        if (captureUri != null) {
            cameraOutputUri = null
            cameraOutputFile = null
            callback.onReceiveValue(arrayOf(captureUri))
            return
        }

        callback.onReceiveValue(
            extractUris(result.data)
        )
    }

    private fun extractUris(data: Intent?): Array<Uri>? {
        if (data == null) {
            return null
        }

        val clipData = data.clipData

        if (clipData != null && clipData.itemCount > 0) {
            return Array(clipData.itemCount) { index ->
                clipData.getItemAt(index).uri
            }
        }

        return data.data?.let { arrayOf(it) }
    }

    private fun handleWebPermissionRequest(
        request: PermissionRequest
    ) {
        if (isFinishing || isDestroyed) {
            request.deny()
            return
        }

        val targetHost = Uri.parse(Config.TARGET_URL).host
        val requestedHost = request.origin?.host

        if (
            targetHost == null ||
            requestedHost == null ||
            !targetHost.equals(requestedHost, true)
        ) {
            request.deny()
            return
        }

        val requested = request.resources.toSet()
        val permissions = mutableListOf<String>()

        if (
            PermissionRequest.RESOURCE_VIDEO_CAPTURE in requested &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.CAMERA
        }

        if (
            PermissionRequest.RESOURCE_AUDIO_CAPTURE in requested &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.RECORD_AUDIO
        }

        if (permissions.isEmpty()) {
            grantWebRequest(request)
            return
        }

        pendingWebPermissionRequest = request
        pendingPermissionFlow = PermissionFlow.WEB_RESOURCE

        permissionLauncher.launch(
            permissions.distinct().toTypedArray()
        )
    }

    private fun grantWebRequest(
        request: PermissionRequest
    ) {
        val grantedResources = request.resources.filter { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

                PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

                else -> false
            }
        }.toTypedArray()

        if (grantedResources.isNotEmpty()) {
            request.grant(grantedResources)
        } else {
            request.deny()
        }
    }

    private fun handleGeolocationRequest(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        if (isFinishing || isDestroyed) {
            callback.invoke(origin, false, false)
            return
        }

        val targetHost = Uri.parse(Config.TARGET_URL).host
        val requestedHost = Uri.parse(origin).host

        if (
            targetHost == null ||
            requestedHost == null ||
            !targetHost.equals(requestedHost, true)
        ) {
            callback.invoke(origin, false, false)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            callback.invoke(origin, true, false)
            return
        }

        if (hasLocationPermission()) {
            callback.invoke(origin, true, false)
            return
        }

        pendingGeoOrigin = origin
        pendingGeoCallback = callback
        pendingPermissionFlow = PermissionFlow.GEOLOCATION

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun handlePermissionResult(
        result: Map<String, Boolean>
    ) {
        when (pendingPermissionFlow) {
            PermissionFlow.WEB_RESOURCE -> {
                pendingWebPermissionRequest?.let { request ->
                    if (!isFinishing && !isDestroyed) {
                        grantWebRequest(request)
                    } else {
                        request.deny()
                    }
                }

                pendingWebPermissionRequest = null
            }

            PermissionFlow.GEOLOCATION -> {
                val origin = pendingGeoOrigin
                val callback = pendingGeoCallback

                pendingGeoOrigin = null
                pendingGeoCallback = null

                if (origin != null && callback != null) {
                    val granted =
                        result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                            hasLocationPermission()

                    callback.invoke(
                        origin,
                        granted,
                        false
                    )
                }
            }

            PermissionFlow.NATIVE_REQUEST,
            PermissionFlow.NONE -> Unit
        }

        pendingPermissionFlow = PermissionFlow.NONE
    }

    private fun hasLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        return checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestNativePermissions(
        kind: String
    ) {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            isFinishing ||
            isDestroyed
        ) {
            return
        }

        val requested = when (kind.lowercase(Locale.US)) {
            "camera" -> arrayOf(
                Manifest.permission.CAMERA
            )

            "microphone",
            "mic",
            "audio" -> arrayOf(
                Manifest.permission.RECORD_AUDIO
            )

            "location",
            "gps" -> arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

            "all",
            "media" -> arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

            else -> emptyArray()
        }

        val missing = requested.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            pendingPermissionFlow = PermissionFlow.NATIVE_REQUEST
            permissionLauncher.launch(
                missing.toTypedArray()
            )
        }
    }

    private fun enqueueDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        try {
            val fileName = android.webkit.URLUtil.guessFileName(
                url,
                contentDisposition,
                mimeType
            )

            val request = DownloadManager.Request(
                Uri.parse(url)
            )
                .setNotificationVisibility(
                    DownloadManager.Request
                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                .setMimeType(
                    mimeType ?: "application/octet-stream"
                )
                .setTitle(fileName)
                .setDescription("Download app ao")

            if (!userAgent.isNullOrBlank()) {
                request.addRequestHeader(
                    "User-Agent",
                    userAgent
                )
            }

            CookieManager.getInstance()
                .getCookie(url)
                ?.let { cookie ->
                    request.addRequestHeader(
                        "Cookie",
                        cookie
                    )
                }

            getSystemService(
                DownloadManager::class.java
            ).enqueue(request)

        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Não foi possível iniciar o download.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun applyStatusBarLight(
        isLight: Boolean
    ) {
        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).isAppearanceLightStatusBars = isLight
    }

    private fun applyNavigationBarLight(
        isLight: Boolean
    ) {
        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).isAppearanceLightNavigationBars = isLight
    }

    fun updateStatusBarColor(
        color: Int
    ) {
        preferences.edit()
            .putInt(
                Config.PREF_STATUS_BAR,
                color
            )
            .apply()

        window.statusBarColor = color
    }

    fun updateNavigationBarColor(
        color: Int
    ) {
        preferences.edit()
            .putInt(
                Config.PREF_NAVIGATION_BAR,
                color
            )
            .apply()

        window.navigationBarColor = color
    }

    fun updateSplashColor(
        color: Int
    ) {
        preferences.edit()
            .putInt(
                Config.PREF_SPLASH,
                color
            )
            .apply()

        window.setBackgroundDrawable(
            ColorDrawable(color)
        )
    }

    fun updateStatusBarDimmed(
        enabled: Boolean,
        amount: Float
    ) {
        val base = preferences.getInt(
            Config.PREF_STATUS_BAR,
            Color.parseColor(
                Config.DEFAULT_STATUS_BAR_COLOR
            )
        )

        window.statusBarColor = if (enabled) {
            dimColor(base, amount)
        } else {
            base
        }
    }

    fun updateNavigationBarDimmed(
        enabled: Boolean,
        amount: Float
    ) {
        val base = preferences.getInt(
            Config.PREF_NAVIGATION_BAR,
            Color.parseColor(
                Config.DEFAULT_NAVIGATION_BAR_COLOR
            )
        )

        window.navigationBarColor = if (enabled) {
            dimColor(base, amount)
        } else {
            base
        }
    }

    fun updateThemeMode(
        mode: String
    ) {
        val normalized = mode.lowercase(Locale.US)

        preferences.edit()
            .putString(
                Config.PREF_THEME_MODE,
                normalized
            )
            .apply()

        when (normalized) {
            "light" -> applyThemePreset(true)
            "dark" -> applyThemePreset(false)

            "system" -> {
                val isNight =
                    (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

                applyThemePreset(!isNight)
            }
        }
    }

    private fun applyThemePreset(
        light: Boolean
    ) {
        if (light) {
            updateSplashColor(Color.WHITE)
            updateStatusBarColor(
                Color.rgb(242, 242, 247)
            )
            updateNavigationBarColor(Color.WHITE)
            updateStatusBarLight(true)
            updateNavigationBarLight(true)
        } else {
            updateSplashColor(
                Color.rgb(13, 15, 18)
            )
            updateStatusBarColor(
                Color.rgb(18, 18, 18)
            )
            updateNavigationBarColor(
                Color.rgb(18, 18, 18)
            )
            updateStatusBarLight(false)
            updateNavigationBarLight(false)
        }
    }

    fun updateStatusBarLight(
        isLight: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                Config.PREF_STATUS_LIGHT,
                isLight
            )
            .apply()

        applyStatusBarLight(isLight)
    }

    fun updateNavigationBarLight(
        isLight: Boolean
    ) {
        preferences.edit()
            .putBoolean(
                Config.PREF_NAVIGATION_LIGHT,
                isLight
            )
            .apply()

        applyNavigationBarLight(isLight)
    }

    fun requestKeepScreenOn(
        keepOn: Boolean
    ) {
        if (::webView.isInitialized) {
            webView.keepScreenOn = keepOn
        }
    }

    fun copyToClipboard(
        text: String
    ) {
        val clipboard = getSystemService(
            ClipboardManager::class.java
        )

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "app ao",
                text
            )
        )
    }

    fun shareText(
        text: String,
        title: String
    ) {
        try {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            text
                        )
                    },
                    title.ifBlank {
                        "Partilhar"
                    }
                )
            )
        } catch (_: Exception) {
            // No share handler is available.
        }
    }

    fun vibrate(
        milliseconds: Long
    ) {
        val safeDuration = milliseconds.coerceIn(
            1L,
            1000L
        )

        val vibrator = getSystemService(
            Vibrator::class.java
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(
                    safeDuration,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(safeDuration)
        }
    }

    fun openExternalUrl(
        url: String
    ) {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )
            )
        } catch (_: Exception) {
            // Ignore invalid/no-handler URLs.
        }
    }

    fun appVersion(): String = "1.0.0"

    private fun dimColor(
        color: Int,
        amount: Float
    ): Int {
        val factor = 1f - amount.coerceIn(
            0f,
            0.65f
        )

        return Color.rgb(
            (Color.red(color) * factor).toInt(),
            (Color.green(color) * factor).toInt(),
            (Color.blue(color) * factor).toInt()
        )
    }

    private fun readColor(
        key: String,
        fallback: String
    ): Int {
        return if (
            preferences.contains(key)
        ) {
            preferences.getInt(
                key,
                Color.parseColor(fallback)
            )
        } else {
            Color.parseColor(fallback)
        }
    }

    private fun cleanupCaptureFile() {
        cameraOutputUri = null
        cameraOutputFile?.delete()
        cameraOutputFile = null
    }

    private fun unregisterConnectivityReceiverSafely() {
        try {
            unregisterReceiver(
                connectivityReceiver
            )
        } catch (_: IllegalArgumentException) {
            // Already unregistered.
        }
    }

    private fun registerConnectivityReceiver() {
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(connectivityReceiver, filter)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.post {
                if (!isFinishing && !isDestroyed) syncWebThemeAndUi()
            }
        }
    }

    override fun onDestroy() {
        if (::progressBar.isInitialized) progressBar.animate().cancel()
        unregisterConnectivityReceiverSafely()

        pendingWebPermissionRequest?.deny()
        pendingWebPermissionRequest = null

        pendingGeoCallback = null
        pendingGeoOrigin = null
        pendingPermissionFlow = PermissionFlow.NONE

        filePathCallback?.onReceiveValue(null)
        filePathCallback = null

        cleanupCaptureFile()

        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.removeAllViews()
            webView.destroy()
        }

        super.onDestroy()
    }
}