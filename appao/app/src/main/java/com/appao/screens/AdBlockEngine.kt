package com.appao.screens

import android.content.Context
import android.net.Uri
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loads the curated blocklist from assets into memory once, and matches requests
 * against it with both exact/suffix host matching and regex patterns for common
 * ad/CDN naming conventions (subdomains like "ads.", "-ads-", "/banner/", etc.).
 * Designed to be strict even on sites that rotate ad-serving subdomains frequently,
 * which is common on ad-heavy and adult sites.
 */
object AdBlockEngine {
    private val loaded = AtomicBoolean(false)
    private val hostSet = HashSet<String>()

    // Generic naming patterns used across many ad/tracking networks regardless of
    // which specific domain they're hosted on. Kept conservative to avoid false
    // positives on legitimate content paths.
    private val patternRegexes = listOf(
        Regex("(^|\\.)ads?\\d*\\.[a-z0-9-]+\\.[a-z]{2,}$", RegexOption.IGNORE_CASE),
        Regex("(^|\\.)adserver\\.", RegexOption.IGNORE_CASE),
        Regex("(^|\\.)adservice\\.", RegexOption.IGNORE_CASE),
        Regex("(^|\\.)banners?\\.[a-z0-9-]+\\.[a-z]{2,}$", RegexOption.IGNORE_CASE),
        Regex("(^|[./-])doubleclick", RegexOption.IGNORE_CASE),
        Regex("(^|[./-])popads?", RegexOption.IGNORE_CASE),
        Regex("(^|[./-])pop-?up-?ads?", RegexOption.IGNORE_CASE),
        Regex("/(ads|advert|banner|popunder|pop-under)/", RegexOption.IGNORE_CASE),
        Regex("[?&](ad|aff|click)id=", RegexOption.IGNORE_CASE),
        Regex("(^|\\.)track(ing)?\\.[a-z0-9-]+\\.[a-z]{2,}$", RegexOption.IGNORE_CASE),
        Regex("(^|\\.)analytics\\.[a-z0-9-]+\\.[a-z]{2,}$", RegexOption.IGNORE_CASE),
        Regex("(^|\\.)metrics\\.[a-z0-9-]+\\.[a-z]{2,}$", RegexOption.IGNORE_CASE)
    )

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded.get()) return
        runCatching {
            context.assets.open("adblock/blocklist.txt").bufferedReader().use { reader ->
                reader.forEachLine { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                    hostSet.add(line.lowercase(Locale.ROOT))
                }
            }
        }
        loaded.set(true)
    }

    fun isBlocked(context: Context, uri: Uri, strict: Boolean): Boolean {
        ensureLoaded(context)
        val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return false
        if (host.isBlank()) return false

        if (hostInSet(host)) return true

        if (strict) {
            val full = uri.toString()
            if (patternRegexes.any { it.containsMatchIn(host) || it.containsMatchIn(full) }) {
                return true
            }
        }
        return false
    }

    private fun hostInSet(host: String): Boolean {
        if (hostSet.contains(host)) return true
        var current = host
        while (true) {
            val dot = current.indexOf('.')
            if (dot < 0) break
            current = current.substring(dot + 1)
            if (current.isEmpty() || !current.contains('.')) break
            if (hostSet.contains(current)) return true
        }
        return false
    }
}