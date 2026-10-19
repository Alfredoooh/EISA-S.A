package com.appao

import android.content.Context
import android.webkit.WebSettings
import java.net.URLEncoder
import java.util.Locale


/** Persisted browser-specific options, kept separate from the app's global theme settings. */
object BrowserPreferences {
    private const val PREFS = "appao_browser"
    private const val KEY_AD_BLOCK = "ad_block_enabled"
    private const val KEY_FORCE_DARK = "force_dark_websites"
    private const val KEY_SAVE_HISTORY = "save_page_history"
    private const val KEY_SAVE_CONVERSATIONS = "save_conversation_history"
    private const val KEY_SEARCH_ENGINE = "search_engine"
    private const val KEY_ADDRESS_POSITION = "address_bar_position"
    private const val KEY_DESKTOP_SITE = "desktop_site_enabled"
    private const val KEY_POPUP_POSITION = "popup_position"
    private const val KEY_TRACKING_PROTECTION = "tracking_protection_enabled"
    private const val KEY_AD_BLOCKED_COUNT = "blocked_ads_count"
    private const val KEY_TRACKER_BLOCKED_COUNT = "blocked_trackers_count"
    private const val KEY_ADAPTIVE_BAR_COLOR = "adaptive_browser_bar_color"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun adBlockEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AD_BLOCK, true)
    fun setAdBlockEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_AD_BLOCK, enabled).apply() }
    fun trackingProtectionEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_TRACKING_PROTECTION, true)
    fun setTrackingProtectionEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_TRACKING_PROTECTION, enabled).apply() }
    fun adaptiveBrowserBarColor(context: Context): Boolean = prefs(context).getBoolean(KEY_ADAPTIVE_BAR_COLOR, false)
    fun setAdaptiveBrowserBarColor(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_ADAPTIVE_BAR_COLOR, enabled).apply() }
    fun blockedAdCount(context: Context): Long = prefs(context).getLong(KEY_AD_BLOCKED_COUNT, 0L).coerceAtLeast(0L)
    fun blockedTrackerCount(context: Context): Long = prefs(context).getLong(KEY_TRACKER_BLOCKED_COUNT, 0L).coerceAtLeast(0L)
    fun blockedRequestCount(context: Context): Long = blockedAdCount(context) + blockedTrackerCount(context)
    fun recordBlockedAd(context: Context) { synchronized(this) { prefs(context).edit().putLong(KEY_AD_BLOCKED_COUNT, blockedAdCount(context) + 1L).apply() } }
    fun recordBlockedTracker(context: Context) { synchronized(this) { prefs(context).edit().putLong(KEY_TRACKER_BLOCKED_COUNT, blockedTrackerCount(context) + 1L).apply() } }
    fun forceDarkEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_FORCE_DARK, false)
    fun setForceDarkEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_FORCE_DARK, enabled).apply() }
    fun savePageHistory(context: Context): Boolean = prefs(context).getBoolean(KEY_SAVE_HISTORY, true)
    fun setSavePageHistory(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_SAVE_HISTORY, enabled).apply() }
    fun saveConversationHistory(context: Context): Boolean = prefs(context).getBoolean(KEY_SAVE_CONVERSATIONS, true)
    fun setSaveConversationHistory(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_SAVE_CONVERSATIONS, enabled).apply() }

    /** Google, Bing, DuckDuckGo and Brave are selectable providers; no provider is contacted until search. */
    fun searchEngine(context: Context): String = prefs(context).getString(KEY_SEARCH_ENGINE, "Google") ?: "Google"
    fun setSearchEngine(context: Context, engine: String) {
        val safe = listOf("Google", "Bing", "DuckDuckGo", "Brave").firstOrNull { it.equals(engine, true) } ?: "Google"
        prefs(context).edit().putString(KEY_SEARCH_ENGINE, safe).apply()
    }

    fun searchUrl(context: Context, query: String): String {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        return when (searchEngine(context).lowercase(Locale.ROOT)) {
            "bing" -> "https://www.bing.com/search?q=$encoded"
            "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
            "brave" -> "https://search.brave.com/search?q=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
    }

    fun addressBarPosition(context: Context): String = prefs(context).getString(KEY_ADDRESS_POSITION, "top") ?: "top"
    fun setAddressBarPosition(context: Context, position: String) {
        prefs(context).edit().putString(KEY_ADDRESS_POSITION, if (position.equals("bottom", true)) "bottom" else "top").apply()
    }

    fun desktopSiteEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_DESKTOP_SITE, false)
    fun setDesktopSiteEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean(KEY_DESKTOP_SITE, enabled).apply() }

    fun popupPosition(context: Context): String = prefs(context).getString(KEY_POPUP_POSITION, "follow_address") ?: "follow_address"
    fun setPopupPosition(context: Context, position: String) {
        val safe = if (position == "top" || position == "bottom") position else "follow_address"
        prefs(context).edit().putString(KEY_POPUP_POSITION, safe).apply()
    }

    private fun permissionKey(host: String, permission: String) = "site_permission_${host.lowercase(Locale.ROOT)}_${permission.lowercase(Locale.ROOT)}"
    fun sitePermissionAllowed(context: Context, host: String, permission: String): Boolean = prefs(context).getBoolean(permissionKey(host, permission), false)
    fun setSitePermission(context: Context, host: String, permission: String, allowed: Boolean) {
        if (host.isBlank()) return
        prefs(context).edit().putBoolean(permissionKey(host, permission), allowed).apply()
    }

    fun desktopUserAgent(context: Context): String {
        val mobile = WebSettings.getDefaultUserAgent(context)
        // Keep the installed Chromium version but advertise a conventional desktop platform.
        return mobile
            .replace(Regex("\\(Linux; Android [^)]*\\)"), "(Windows NT 10.0; Win64; x64)")
            .replace("; wv", "")
            .replace(" Version/4.0", "")
            .replace(" Mobile Safari", " Safari")
    }
}