package com.appao

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AppViewerActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var progress: View

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_viewer)

        web = findViewById(R.id.webView)
        progress = findViewById(R.id.appProgress)

        val url = intent.getStringExtra("url") ?: run { finish(); return }
        val title = intent.getStringExtra("title") ?: "App"

        findViewById<TextView>(R.id.appTitle).text = title
        IconLoader.applySvg(findViewById<ImageView>(R.id.appBackIcon), "back", R.color.text)
        findViewById<View>(R.id.appBack).setOnClickListener {
            if (web.canGoBack()) web.goBack() else finish()
        }

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
                if (newProgress >= 100) {
                    progress.animate().alpha(0f).setDuration(400)
                        .setInterpolator(Curves.SMOOTH).start()
                }
            }
        }

        progress.layoutParams.width = (resources.displayMetrics.widthPixels * 0.88f).toInt()
        progress.requestLayout()
        progress.alpha = 1f

        web.loadUrl(url)
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }
}
