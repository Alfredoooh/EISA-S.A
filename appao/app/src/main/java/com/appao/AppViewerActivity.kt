package com.appao

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class AppViewerActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var progress: View

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
        }

        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val target = (resources.displayMetrics.widthPixels * newProgress / 100f).toInt()
                val lp = progress.layoutParams
                lp.width = target
                progress.layoutParams = lp
                progress.alpha = 1f
                if (newProgress >= 100) progress.animate().alpha(0f).setDuration(400).start()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        progress.layoutParams.width = (resources.displayMetrics.widthPixels * 0.88f).toInt()
        progress.requestLayout()
        progress.alpha = 1f

        web.loadUrl(url)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // tratado pelo dispatcher
    }
}
