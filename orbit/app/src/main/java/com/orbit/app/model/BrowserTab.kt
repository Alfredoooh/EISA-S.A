package com.orbit.app.model

import android.graphics.Bitmap
import android.webkit.WebView

/** State retained for one native browser tab. The WebView owns its own back/forward history. */
class BrowserTab(val id: Long) {
    lateinit var webView: WebView
    var url: String? = null
    var title: String = "Nova aba"
    var favicon: Bitmap? = null
    var thumbnail: Bitmap? = null
    var isLoading: Boolean = false
    var progress: Int = 0
    var desktopMode: Boolean = false
    var faviconRequestedFor: String? = null
}
