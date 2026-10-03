package com.appao

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class AppViewerActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var progress: View
    private var lastHtmlBg: Int? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        ThemeManager.syncSystemBars(this)
        setContentView(R.layout.activity_app_viewer)

        web = findViewById(R.id.webView)
        progress = findViewById(R.id.appProgress)
        val url = intent.getStringExtra("url") ?: run { finish(); return }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        web.addJavascriptInterface(HtmlBridge(this), "Android")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

            override fun onPageFinished(view: WebView, pageUrl: String) {
                injectThemeObserver()
                readHtmlTheme()
                hideProgress()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) hideProgress()
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                // WebView renderer crashes must not terminate the whole Activity.
                try {
                    val parent = view.parent as? android.view.ViewGroup
                    parent?.removeView(view)
                    view.destroy()
                    recreateWebView(url)
                } catch (_: Exception) {
                    finish()
                }
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val target = (resources.displayMetrics.widthPixels * (newProgress / 100f)).toInt()
                progress.layoutParams = progress.layoutParams.apply { width = target }
                progress.requestLayout()
                progress.alpha = if (newProgress >= 100) 1f else 1f
                if (newProgress >= 100) hideProgress()
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                NativePopup.showAlert(this@AppViewerActivity, "", message) { result.confirm() }
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                NativePopup.showConfirm(this@AppViewerActivity, "", message, "Confirmar", "Cancelar") { ok ->
                    if (ok) result.confirm() else result.cancel()
                }
                return true
            }

            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean {
                NativePopup.showPrompt(this@AppViewerActivity, "", message, defaultValue, "OK", "Cancelar") { value ->
                    if (value == null) result.cancel() else result.confirm(value)
                }
                return true
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        showProgress()
        web.loadUrl(url)
    }

    private fun recreateWebView(url: String) {
        val parent = findViewById<android.view.ViewGroup>(R.id.viewerRoot)
        val newWeb = WebView(this)
        newWeb.id = R.id.webView
        parent?.addView(newWeb, 0, android.widget.FrameLayout.LayoutParams(-1, -1))
        web = newWeb
        // Re-entering onCreate would reset too much; renderer crashes are exceptional.
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(HtmlBridge(this), "Android")
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        web.loadUrl(url)
    }

    private fun showProgress() {
        progress.alpha = 1f
        progress.layoutParams = progress.layoutParams.apply { width = 0 }
        progress.requestLayout()
    }

    private fun hideProgress() {
        progress.animate().alpha(0f).setDuration(250).withEndAction {
            progress.layoutParams = progress.layoutParams.apply { width = 0 }
            progress.requestLayout()
        }.start()
    }

    private fun injectThemeObserver() {
        val js = """
            (function(){
              if(window.__appAoThemeObserver)return;
              window.__appAoThemeObserver=true;
              function send(){try{
                var root=getComputedStyle(document.documentElement);
                var bg=root.getPropertyValue('--bg').trim() || getComputedStyle(document.body).backgroundColor;
                var text=root.getPropertyValue('--text').trim() || getComputedStyle(document.body).color;
                if(window.Android && Android.onHtmlThemeChanged) Android.onHtmlThemeChanged(bg,text);
              }catch(e){}}
              window.__appAoReadTheme=send;
              send();
              new MutationObserver(send).observe(document.documentElement,{attributes:true,attributeFilter:['data-theme','style','class']});
              new MutationObserver(send).observe(document.head,{childList:true,subtree:true});
            })();
        """.trimIndent()
        web.evaluateJavascript(js, null)
    }

    private fun readHtmlTheme() {
        web.evaluateJavascript("window.__appAoReadTheme && window.__appAoReadTheme();", null)
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            web.stopLoading()
            web.removeJavascriptInterface("Android")
            web.destroy()
        }
        super.onDestroy()
    }

    class HtmlBridge(private val activity: AppViewerActivity) {
        @android.webkit.JavascriptInterface
        fun setStatusBarColor(color: String) {
            activity.runOnUiThread { activity.applyHtmlSystemColor(color, true) }
        }

        @android.webkit.JavascriptInterface
        fun setNavigationBarColor(color: String) {
            activity.runOnUiThread { activity.applyHtmlSystemColor(color, false) }
        }

        @android.webkit.JavascriptInterface
        fun setThemeMode(mode: String) {
            activity.runOnUiThread {
                if (mode == "dark" || mode == "light" || mode == "system") {
                    ThemeManager.applyInstant(activity, mode)
                    activity.readHtmlTheme()
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun onHtmlThemeChanged(background: String, text: String) {
            activity.runOnUiThread { activity.applyHtmlTheme(background) }
        }

        @android.webkit.JavascriptInterface
        fun setSplashBackgroundColor(color: String) = Unit

        @android.webkit.JavascriptInterface
        fun setStatusBarDimmed(dimmed: Boolean, amount: Double) {
            activity.runOnUiThread {
                activity.window.setDimAmountSafe(if (dimmed) amount.toFloat() else 0f)
            }
        }
    }

    private fun applyHtmlTheme(background: String) {
        parseCssColor(background)?.let {
            if (lastHtmlBg != it) {
                lastHtmlBg = it
                window.statusBarColor = it
                window.navigationBarColor = it
                val light = isLight(it)
                val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                controller.isAppearanceLightStatusBars = light
                controller.isAppearanceLightNavigationBars = light
            }
        }
    }

    private fun applyHtmlSystemColor(value: String, status: Boolean) {
        parseCssColor(value)?.let { color ->
            if (status) window.statusBarColor = color else window.navigationBarColor = color
            val light = isLight(color)
            val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            if (status) controller.isAppearanceLightStatusBars = light else controller.isAppearanceLightNavigationBars = light
        }
    }

    private fun parseCssColor(value: String): Int? {
        return try {
            val v = value.trim()
            if (v.startsWith("#")) Color.parseColor(v) else {
                val m = Regex("rgba?\\(([^)]+)\\)").find(v) ?: return null
                val p = m.groupValues[1].split(',').map { it.trim() }
                val r = p[0].toFloat().toInt(); val g = p[1].toFloat().toInt(); val b = p[2].toFloat().toInt()
                Color.rgb(r,g,b)
            }
        } catch (_: Exception) { null }
    }

    private fun isLight(color: Int): Boolean {
        val lum = 0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)
        return lum > 160
    }

    private fun android.view.Window.setDimAmountSafe(amount: Float) {
        // Window-level dim is not meaningful for a normal Activity; retained as a no-op bridge.
    }
}
