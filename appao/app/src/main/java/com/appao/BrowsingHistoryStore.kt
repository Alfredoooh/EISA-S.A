package com.appao

import android.content.Context
import android.net.Uri
import com.appao.screens.BrowserTabStore
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject


/** Compact local history for Browser pages and AI/conversation queries. */
object BrowsingHistoryStore {
    private const val PREFS = "appao_browser"
    private const val KEY = "page_history_json"
    private const val MAX_ITEMS = 500

    data class Entry(val url: String, val title: String, val favicon: String, val visitedAt: Long)

    fun all(context: Context): List<Entry> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val json = JSONArray(raw)
        buildList {
            for (i in 0 until json.length()) {
                val o = json.optJSONObject(i) ?: continue
                val url = o.optString("url").trim()
                if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) continue
                add(Entry(url, o.optString("title").ifBlank { host(url) }, o.optString("favicon").ifBlank { BrowserTabStore.faviconFor(url) }, o.optLong("visitedAt")))
            }
        }.sortedByDescending { it.visitedAt }
    }.getOrDefault(emptyList())

    fun record(context: Context, url: String, title: String) {
        if (!BrowserPreferences.savePageHistory(context)) return
        val safeUrl = url.trim()
        if (!safeUrl.startsWith("http://", true) && !safeUrl.startsWith("https://", true)) return
        if (safeUrl.startsWith("https://duckduckgo.com/?q=", true) && title.isBlank()) return
        val newEntry = Entry(safeUrl, title.trim().take(240).ifBlank { host(safeUrl) }, BrowserTabStore.faviconFor(safeUrl), System.currentTimeMillis())
        val key = normalize(safeUrl)
        val updated = (listOf(newEntry) + all(context).filter { normalize(it.url) != key }).take(MAX_ITEMS)
        persist(context, updated)
    }

    fun clear(context: Context) = persist(context, emptyList())

    private fun persist(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        entries.take(MAX_ITEMS).forEach { e -> array.put(JSONObject().apply {
            put("url", e.url); put("title", e.title); put("favicon", e.favicon); put("visitedAt", e.visitedAt)
        }) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    private fun host(url: String): String = runCatching { Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
    private fun normalize(url: String) = url.trim().lowercase(Locale.ROOT).removeSuffix("/").substringBefore('#')
}

object ConversationHistoryStore {
    private const val PREFS = "appao_browser"
    private const val KEY = "conversation_history_json"
    private const val MAX_ITEMS = 300
    data class Entry(val title: String, val query: String, val createdAt: Long)

    fun all(context: Context): List<Entry> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]")
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val title = o.optString("title").trim()
                if (title.isNotBlank()) add(Entry(title, o.optString("query"), o.optLong("createdAt")))
            }
        }.sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())

    fun record(context: Context, query: String) {
        val value = query.trim().take(500)
        if (value.isBlank() || !BrowserPreferences.saveConversationHistory(context)) return
        val existing = all(context)
        if (existing.firstOrNull()?.title.equals(value, true) && System.currentTimeMillis() - (existing.firstOrNull()?.createdAt ?: 0L) < 60_000) return
        val updated = (listOf(Entry(value.lines().firstOrNull().orEmpty().take(100).ifBlank { "Nova conversa" }, value, System.currentTimeMillis())) + existing).take(MAX_ITEMS)
        val array = JSONArray()
        updated.forEach { e -> array.put(JSONObject().apply { put("title", e.title); put("query", e.query); put("createdAt", e.createdAt) }) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    fun clear(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, "[]").apply() }
}
