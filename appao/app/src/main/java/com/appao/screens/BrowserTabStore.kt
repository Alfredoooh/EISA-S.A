package com.appao

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Small local tab store: URLs, titles, favicon URLs and WebView snapshots persist between launches. */
data class BrowserTab(
    val id: String,
    val url: String,
    val title: String,
    val favicon: String,
    val snapshotPath: String,
    val updatedAt: Long
)

object BrowserTabStore {
    private const val PREFS = "appao_browser"
    private const val KEY_TABS = "tabs_json"
    private const val MAX_TABS = 40

    fun tabs(context: Context): List<BrowserTab> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TABS, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                add(BrowserTab(
                    id = id,
                    url = o.optString("url").ifBlank { "https://duckduckgo.com/" },
                    title = o.optString("title").ifBlank { "Nova aba" },
                    favicon = o.optString("favicon"),
                    snapshotPath = o.optString("snapshotPath"),
                    updatedAt = o.optLong("updatedAt", 0L)
                ))
            }
        }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    fun create(context: Context, url: String = "https://duckduckgo.com/"): BrowserTab {
        val tab = BrowserTab(UUID.randomUUID().toString().replace("-", "").take(12), url, "Nova aba", faviconFor(url), "", System.currentTimeMillis())
        upsert(context, tab)
        return tab
    }

    fun upsert(context: Context, tab: BrowserTab) {
        val updated = (tabs(context).filterNot { it.id == tab.id } + tab.copy(updatedAt = System.currentTimeMillis()))
            .sortedByDescending { it.updatedAt }
            .take(MAX_TABS)
        val array = JSONArray()
        updated.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id)
            put("url", item.url)
            put("title", item.title)
            put("favicon", item.favicon)
            put("snapshotPath", item.snapshotPath)
            put("updatedAt", item.updatedAt)
        }) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TABS, array.toString()).apply()
    }

    fun updatePage(context: Context, id: String, url: String, title: String, snapshotPath: String? = null) {
        val existing = tabs(context).firstOrNull { it.id == id }
            ?: BrowserTab(id, url, title, faviconFor(url), "", System.currentTimeMillis())
        upsert(context, existing.copy(
            url = url.ifBlank { existing.url },
            title = title.ifBlank { hostFor(url).ifBlank { "Nova aba" } },
            favicon = faviconFor(url),
            snapshotPath = snapshotPath ?: existing.snapshotPath,
            updatedAt = System.currentTimeMillis()
        ))
    }

    fun saveSnapshot(context: Context, id: String, bitmap: Bitmap): String? = runCatching {
        val dir = File(context.filesDir, "browser_tabs").apply { mkdirs() }
        val target = File(dir, "$id.jpg")
        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 76, it) }
        target.absolutePath
    }.getOrNull()

    fun remove(context: Context, id: String) {
        val existing = tabs(context).firstOrNull { it.id == id }
        existing?.snapshotPath?.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
        persist(context, tabs(context).filterNot { it.id == id })
    }

    fun clear(context: Context) {
        tabs(context).forEach { it.snapshotPath.takeIf(String::isNotBlank)?.let { path -> runCatching { File(path).delete() } } }
        persist(context, emptyList())
    }

    fun count(context: Context): Int = tabs(context).size

    private fun persist(context: Context, list: List<BrowserTab>) {
        val array = JSONArray()
        list.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("url", item.url); put("title", item.title)
            put("favicon", item.favicon); put("snapshotPath", item.snapshotPath); put("updatedAt", item.updatedAt)
        }) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TABS, array.toString()).apply()
    }

    fun faviconFor(url: String): String = hostFor(url).let { host -> if (host.isBlank()) "" else "https://www.google.com/s2/favicons?domain=$host&sz=96" }

    private fun hostFor(url: String): String = runCatching { android.net.Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
}
