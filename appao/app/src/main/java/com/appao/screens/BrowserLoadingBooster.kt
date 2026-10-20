package com.appao.screens

import android.net.Uri
import android.os.SystemClock
import android.webkit.WebSettings
import android.webkit.WebView
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Lightweight browser warm-up: resolves hosts before navigation and keeps WebView's HTTP cache enabled. */
object BrowserLoadingBooster {
    private const val HOST_CACHE_MS = 120_000L
    private val lastResolve = ConcurrentHashMap<String, Long>()
    private val dnsExecutor = Executors.newFixedThreadPool(2) { task ->
        Thread(task, "AppAo-DNS-prefetch").apply { isDaemon = true }
    }

    fun preResolveAll(urls: Iterable<String>) {
        urls.forEach(::preResolve)
    }

    fun preResolve(url: String) {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return
        if (parsed.scheme !in setOf("http", "https")) return
        val host = parsed.host?.lowercase().orEmpty()
        if (host.isBlank() || host == "localhost") return
        val now = SystemClock.elapsedRealtime()
        val last = lastResolve[host] ?: 0L
        if (now - last < HOST_CACHE_MS) return
        lastResolve[host] = now
        runCatching {
            dnsExecutor.execute {
                runCatching { InetAddress.getAllByName(host) }
                    .onFailure { lastResolve.remove(host, now) }
            }
        }.onFailure { lastResolve.remove(host, now) }
    }

    fun configure(webView: WebView) {
        runCatching {
            webView.settings.apply {
                cacheMode = WebSettings.LOAD_DEFAULT
                loadsImagesAutomatically = true
                blockNetworkImage = false
                domStorageEnabled = true
                databaseEnabled = true
            }
            webView.overScrollMode = android.view.View.OVER_SCROLL_NEVER
            // WebView is hardware-accelerated by Android by default; do not force a second
            // hardware layer because that increases GPU memory use across live tabs.
        }
    }
}
