package com.appao.screens

import android.net.Uri
import android.os.SystemClock
import android.webkit.WebSettings
import android.webkit.WebView
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request

/** Browser warm-up: resolves DNS AND opens a real TLS connection ahead of navigation, so the
    handshake that normally happens after the user taps a link/suggestion is already done by
    the time WebView actually requests the page. DNS alone only removes the lookup step; the
    TCP+TLS handshake is usually the larger share of first-request latency on a fresh host.
    This version also pushes HTTP/2 preference and a larger connection pool so several hosts
    (main page + its CDNs) can be warmed in parallel instead of serially. */
object BrowserLoadingBooster {
    private const val HOST_CACHE_MS = 120_000L
    private val lastResolve = ConcurrentHashMap<String, Long>()
    private val warmExecutor = Executors.newFixedThreadPool(6) { task ->
        Thread(task, "AppAo-connection-warmup").apply { isDaemon = true }
    }

    private val warmupClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
        .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .build()

    fun preResolveAll(urls: Iterable<String>) {
        urls.forEach(::preResolve)
    }

    /** Warms the main host plus a handful of extra hosts (e.g. CDN/asset domains a page is
        known to call) in parallel, instead of one at a time. */
    fun preResolveWithExtras(url: String, extraHosts: Iterable<String> = emptyList()) {
        preResolve(url)
        extraHosts.forEach { host ->
            if (host.isNotBlank()) preResolve("https://$host/")
        }
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
            warmExecutor.execute {
                val resolved = runCatching { InetAddress.getAllByName(host) }.isSuccess
                if (!resolved) { lastResolve.remove(host, now); return@execute }
                if (parsed.scheme.equals("https", true)) {
                    runCatching {
                        val request = Request.Builder()
                            .url("https://$host/")
                            .head()
                            .build()
                        warmupClient.newCall(request).execute().close()
                    }
                }
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
                setGeolocationEnabled(false)
                @Suppress("DEPRECATION")
                setEnableSmoothTransition(true)
            }
            webView.overScrollMode = android.view.View.OVER_SCROLL_NEVER
            webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        }
    }
}