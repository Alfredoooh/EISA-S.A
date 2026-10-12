package com.appao

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class AppViewerActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var progress: View
    private var rendererRecoveryAttempted = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_app_viewer)
        SystemBarHelper.sync(this)

        web = findViewById(R.id.webView)
        progress = findViewById(R.id.appProgress)
        applyProgressAccent()
        IconLoader.applySvg(findViewById(R.id.appViewerCloseIcon), "close", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.appViewerMoreIcon), "more_horiz", R.color.iconTint)
        findViewById<View>(R.id.appViewerClose).setOnClickListener { finish() }
        findViewById<View>(R.id.appViewerMore).setOnClickListener { showPageMenu(it) }

        val url = intent.getStringExtra("url")?.trim().orEmpty()
        if (url.isBlank()) { finish(); return }

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
        }
        web.setBackgroundColor(
            ContextCompat.getColor(
                this,
                R.color.bg
            )
        )
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                injectThemeObserver(view)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) Toast.makeText(this@AppViewerActivity, "Não foi possível carregar este app", Toast.LENGTH_SHORT).show()
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (isFinishing) return true
                try { view.destroy() } catch (_: Throwable) {}
                if (!rendererRecoveryAttempted) {
                    rendererRecoveryAttempted = true
                    recreate()
                } else {
                    finish()
                }
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

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                NativeHtmlDialog.alert(this@AppViewerActivity, "", message) { result.confirm() }
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                NativeHtmlDialog.confirm(this@AppViewerActivity, "", message) { ok -> if (ok) result.confirm() else result.cancel() }
                return true
            }

            override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean {
                NativeHtmlDialog.prompt(this@AppViewerActivity, "", message, defaultValue) { value ->
                    if (value == null) result.cancel() else result.confirm(value)
                }
                return true
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else finish() }
        })

        progress.post {
            progress.layoutParams.width = (resources.displayMetrics.widthPixels * 0.06f).toInt()
            progress.alpha = 1f
        }
        web.loadUrl(url)
    }

    private fun showPageMenu(anchor: View) {
        NativePopupMenu.show(
            anchor,
            listOf(
                NativePopupMenu.Item(1, "Abrir no navegador", svg = "external_link"),
                NativePopupMenu.Item(2, "Copiar ligação", svg = "copy"),
                NativePopupMenu.Item(3, "Compartilhar ligação", svg = "share"),
                NativePopupMenu.Item(4, "Recarregar", svg = "refresh")
            ),
            alignLeft = false,
            placeAbove = false
        ) { item ->
            val currentUrl = web.url?.takeIf { it.isNotBlank() } ?: intent.getStringExtra("url").orEmpty()
            when (item.id) {
                1 -> runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                }.onFailure { Toast.makeText(this, "Não foi possível abrir o navegador", Toast.LENGTH_SHORT).show() }
                2 -> {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Ligação", currentUrl))
                    AppAoToast.show(this, "Ligação copiada")
                }
                3 -> runCatching {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, currentUrl)
                    }, "Compartilhar ligação"))
                }.onFailure { Toast.makeText(this, "Não foi possível compartilhar", Toast.LENGTH_SHORT).show() }
                4 -> web.reload()
            }
        }
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
              setInterval(send,1200);
            })();
        """.trimIndent(), null)
    }

    private inner class HtmlThemeBridge {
        @JavascriptInterface
        fun setTheme(color: String, dark: Boolean) {
            runOnUiThread {
                try {
                    val normalized = color.trim()
                    val parsed: Int? = if (normalized.startsWith("#")) Color.parseColor(normalized) else null
                    if (parsed != null) SystemBarHelper.syncColor(this@AppViewerActivity, parsed)
                    else SystemBarHelper.sync(this@AppViewerActivity, dark)
                } catch (_: Throwable) {
                    SystemBarHelper.sync(this@AppViewerActivity, dark)
                }
            }
        }
    }

    private fun applyProgressAccent() {
        if (::progress.isInitialized) {
            progress.background = AccentColorManager.primaryShape(
                this,
                android.graphics.drawable.GradientDrawable.RECTANGLE,
                2f
            )
        }
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
        applyProgressAccent()
        web.post { injectThemeObserver(web) }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
    }

    override fun onDestroy() {
        try {
            web.stopLoading()
            web.webChromeClient = null
            web.destroy()
        } catch (_: Throwable) {}
        super.onDestroy()
    }
}
