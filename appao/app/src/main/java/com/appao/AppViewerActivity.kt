package com.appao

import android.annotation.SuppressLint
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

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        setContentView(
            R.layout.activity_app_viewer
        )

        SystemBarHelper.sync(this)

        web = findViewById(R.id.webView)
        progress = findViewById(R.id.appProgress)

        ViewCompat.setOnApplyWindowInsetsListener(
            web
        ) { view, insets ->

            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                0,
                bars.top,
                0,
                bars.bottom
            )

            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(
            progress
        ) { view, insets ->

            val top =
                insets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                ).top

            val lp =
                view.layoutParams as android.widget.FrameLayout.LayoutParams

            lp.topMargin = top

            view.layoutParams = lp

            insets
        }

        val url =
            intent
                .getStringExtra("url")
                ?.trim()
                .orEmpty()

        if (url.isBlank()) {
            finish()
            return
        }

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
            if (
                ThemeManager.current(this) == "dark"
            ) {
                Color.rgb(13, 15, 18)
            } else {
                Color.WHITE
            }
        )

        web.isVerticalScrollBarEnabled = false

        web.isHorizontalScrollBarEnabled = false

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {
                    injectThemeObserver(view)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {

                    if (
                        request.isForMainFrame
                    ) {

                        Toast.makeText(
                            this@AppViewerActivity,
                            "Não foi possível carregar este app",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail
                ): Boolean {

                    if (isFinishing) {
                        return true
                    }

                    try {
                        view.stopLoading()
                    } catch (_: Throwable) {
                    }

                    try {
                        view.destroy()
                    } catch (_: Throwable) {
                    }

                    if (
                        !rendererRecoveryAttempted
                    ) {

                        rendererRecoveryAttempted = true

                        recreate()

                    } else {

                        finish()
                    }

                    return true
                }
            }

        web.addJavascriptInterface(
            HtmlThemeBridge(),
            "AppAoTheme"
        )

        web.webChromeClient =
            object : WebChromeClient() {

                override fun onProgressChanged(
                    view: WebView?,
                    newProgress: Int
                ) {

                    val width =
                        resources
                            .displayMetrics
                            .widthPixels

                    val target =
                        (
                            width *
                                newProgress /
                                100f
                        )
                            .toInt()
                            .coerceIn(
                                0,
                                width
                            )

                    val lp =
                        progress.layoutParams

                    lp.width = target

                    progress.layoutParams = lp

                    progress.alpha =
                        if (
                            newProgress >= 100
                        ) {
                            0f
                        } else {
                            1f
                        }

                    if (
                        newProgress >= 100
                    ) {

                        progress.postDelayed(
                            {
                                if (
                                    !isFinishing &&
                                    !isDestroyed
                                ) {
                                    progress.alpha = 0f
                                }
                            },
                            120L
                        )
                    }
                }

                override fun onJsAlert(
                    view: WebView,
                    url: String,
                    message: String,
                    result: JsResult
                ): Boolean {

                    NativeHtmlDialog.alert(
                        this@AppViewerActivity,
                        "",
                        message
                    ) {
                        result.confirm()
                    }

                    return true
                }

                override fun onJsConfirm(
                    view: WebView,
                    url: String,
                    message: String,
                    result: JsResult
                ): Boolean {

                    NativeHtmlDialog.confirm(
                        this@AppViewerActivity,
                        "",
                        message
                    ) { accepted ->

                        if (accepted) {
                            result.confirm()
                        } else {
                            result.cancel()
                        }
                    }

                    return true
                }

                override fun onJsPrompt(
                    view: WebView,
                    url: String,
                    message: String,
                    defaultValue: String,
                    result: JsPromptResult
                ): Boolean {

                    NativeHtmlDialog.prompt(
                        this@AppViewerActivity,
                        "",
                        message,
                        defaultValue
                    ) { value ->

                        if (value == null) {
                            result.cancel()
                        } else {
                            result.confirm(value)
                        }
                    }

                    return true
                }
            }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    if (
                        web.canGoBack()
                    ) {
                        web.goBack()
                    } else {
                        finish()
                    }
                }
            }
        )

        progress.post {

            if (
                !isFinishing &&
                !isDestroyed
            ) {

                val width =
                    resources
                        .displayMetrics
                        .widthPixels

                val lp =
                    progress.layoutParams

                lp.width =
                    (
                        width * 0.06f
                    ).toInt()

                progress.layoutParams = lp

                progress.alpha = 1f
            }
        }

        web.loadUrl(url)
    }

    @Suppress("SetJavaScriptEnabled")
    private fun injectThemeObserver(
        view: WebView
    ) {

        view.evaluateJavascript(
            """
            (function(){
              try{
                if(window.__appAoThemeObserverInstalled){
                  return;
                }

                window.__appAoThemeObserverInstalled = true;

                function send(){

                  try{

                    var root =
                      document.documentElement;

                    var body =
                      document.body;

                    var cs =
                      getComputedStyle(root);

                    var bg =
                      (cs.getPropertyValue('--bg') || '').trim();

                    if(!bg && body){
                      bg =
                        getComputedStyle(body)
                          .backgroundColor;
                    }

                    var meta =
                      document.querySelector(
                        'meta[name="theme-color"]'
                      );

                    if(!bg && meta){
                      bg =
                        meta.content || '';
                    }

                    var dark =
                      root.getAttribute(
                        'data-theme'
                      ) === 'dark';

                    if(
                      window.AppAoTheme &&
                      bg
                    ){
                      window.AppAoTheme.setTheme(
                        bg,
                        dark
                      );
                    }

                  }catch(e){}
                }

                send();

                new MutationObserver(
                  send
                ).observe(
                  document.documentElement,
                  {
                    attributes:true,
                    attributeFilter:[
                      'data-theme',
                      'class',
                      'style'
                    ]
                  }
                );

                setInterval(
                  send,
                  1200
                );

              }catch(e){}
            })();
            """.trimIndent(),
            null
        )
    }

    private inner class HtmlThemeBridge {

        @JavascriptInterface
        fun setTheme(
            color: String,
            dark: Boolean
        ) {

            runOnUiThread {

                try {

                    val normalized =
                        color.trim()

                    if (
                        normalized.startsWith("#")
                    ) {

                        val parsed =
                            Color.parseColor(
                                normalized
                            )

                        SystemBarHelper.syncColor(
                            this@AppViewerActivity,
                            parsed
                        )

                    } else {

                        SystemBarHelper.sync(
                            this@AppViewerActivity,
                            dark
                        )
                    }

                } catch (_: Throwable) {

                    SystemBarHelper.sync(
                        this@AppViewerActivity,
                        dark
                    )
                }
            }
        }
    }

    override fun onResume() {

        super.onResume()

        SystemBarHelper.sync(this)

        if (::web.isInitialized) {
            web.post {
                if (
                    !isFinishing &&
                    !isDestroyed
                ) {
                    injectThemeObserver(web)
                }
            }
        }
    }

    override fun onConfigurationChanged(
        newConfig: android.content.res.Configuration
    ) {

        super.onConfigurationChanged(newConfig)

        SystemBarHelper.sync(this)
    }

    override fun onDestroy() {

        if (::web.isInitialized) {

            try {
                web.stopLoading()
            } catch (_: Throwable) {
            }

            try {
                web.webChromeClient = null
            } catch (_: Throwable) {
            }

            try {
                web.destroy()
            } catch (_: Throwable) {
            }
        }

        super.onDestroy()
    }
}