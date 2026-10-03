package com.appao

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object NewsRepository {

    private const val TAG = "AppAo.News"
    private const val BASE = "https://eisa-sa-servers.onrender.com"
    private const val KEY = "sk_live_168c3937133560729e5c18a7f1a5183fd3a8a3b351a833b692224a00ef5b45a3"
    private const val CACHE_PREFS = "appao"
    private const val CACHE_KEY = "news_cache"
    private const val CACHE_TIME_KEY = "news_cache_time"
    private const val MAX_ARTICLES_PER_CATEGORY = 12
    private const val MAX_CACHE_ARTICLES = 120

    private val okHttpDispatcher = Dispatcher().apply {
        maxRequests = 3
        maxRequestsPerHost = 3
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(okHttpDispatcher)
        .callTimeout(14, TimeUnit.SECONDS)
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val categories = listOf(
        "general", "business", "sports", "technology",
        "entertainment", "health", "politics"
    )

    private val seenKeys = HashSet<String>()

    private fun dedupKey(url: String, title: String): String {
        val u = url.trim().lowercase().replace(Regex("https?://(www\\.)?"), "").trimEnd('/')
        val t = titleKey(title)
        return "$u|$t"
    }

    private fun titleKey(title: String): String = title.trim().lowercase()
        .replace(Regex("[\\p{Punct}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun resetDedup() = seenKeys.clear()

    suspend fun fetchGeneral(page: Int = 1): List<NewsItem> = supervisorScope {
        val groups = ArrayList<List<NewsItem>>(categories.size)
        for (chunk in categories.chunked(2)) {
            val deferred = chunk.map { category ->
                async(Dispatchers.IO) { fetchByCategory(category, page) }
            }
            for (d in deferred) {
                groups += try {
                    d.await()
                } catch (e: Exception) {
                    Log.w(TAG, "Category request failed", e)
                    emptyList()
                }
            }
        }

        val max = groups.maxOfOrNull { it.size } ?: 0
        val out = ArrayList<NewsItem>(minOf(max * groups.size, MAX_CACHE_ARTICLES))
        val localTitleKeys = HashSet<String>()

        for (i in 0 until max) {
            if (out.size >= MAX_CACHE_ARTICLES) break
            for (group in groups) {
                if (i >= group.size || out.size >= MAX_CACHE_ARTICLES) continue
                val item = group[i]
                val primary = dedupKey(item.link, item.title)
                val secondary = titleKey(item.title)
                if (seenKeys.contains(primary) || seenKeys.contains("t:$secondary") || !localTitleKeys.add(secondary)) continue
                if (seenKeys.size > 2000) seenKeys.clear()
                seenKeys.add(primary)
                seenKeys.add("t:$secondary")
                out += item
            }
        }
        out
    }

    suspend fun fetchByCategory(category: String, page: Int = 1, country: String = "AO"): List<NewsItem> = withContext(Dispatchers.IO) {
        try {
            val countryQuery = if (country.isBlank()) "" else "&country=$country"
            val url = "$BASE/news?lang=pt$countryQuery&category=$category&page=$page"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("x-api-key", KEY)
                .build()
            client.newCall(req).execute().use { response ->
                val body = response.body ?: return@withContext emptyList()
                val length = body.contentLength()
                if (length > 1_500_000) {
                    Log.w(TAG, "Ignoring oversized news response: $length bytes")
                    return@withContext emptyList()
                }
                val raw = body.string()
                if (!response.isSuccessful || raw.length > 1_750_000) return@withContext emptyList()
                val arr = JSONObject(raw).optJSONArray("articles") ?: return@withContext emptyList()
                val out = ArrayList<NewsItem>(minOf(arr.length(), MAX_ARTICLES_PER_CATEGORY))
                for (i in 0 until minOf(arr.length(), MAX_ARTICLES_PER_CATEGORY)) {
                    val a = arr.optJSONObject(i) ?: continue
                    val u = a.optString("url").trim()
                    val t = a.optString("title").trim()
                    if (u.length < 8 || t.isEmpty() || !u.startsWith("http", true)) continue
                    val domain = try {
                        java.net.URL(u).host.removePrefix("www.")
                    } catch (_: Exception) { "" }
                    out += NewsItem(
                        id = "u_" + u.hashCode().toUInt().toString(36),
                        title = t.take(500),
                        summary = a.optString("description").take(1600),
                        link = u,
                        source = a.optString("source").ifBlank { domain }.take(120),
                        date = a.optString("publishedAt"),
                        image = a.optString("image").trim().take(2000),
                        logo = if (domain.isNotEmpty()) "https://www.google.com/s2/favicons?domain=$domain&sz=128" else ""
                    )
                }
                out
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "fetchByCategory failed", e)
            emptyList()
        }
    }

    suspend fun fetchArticleFull(url: String): String? = withContext(Dispatchers.IO) {
        try {
            if (!url.startsWith("http", true)) return@withContext null
            val u = "$BASE/article?url=${URLEncoder.encode(url, "UTF-8")}"
            val req = Request.Builder().url(u).header("Accept", "application/json").header("x-api-key", KEY).build()
            client.newCall(req).execute().use { response ->
                val body = response.body ?: return@withContext null
                val length = body.contentLength()
                if (length > 4_000_000 || !response.isSuccessful) return@withContext null
                JSONObject(body.string()).optJSONObject("article")?.optString("body")?.take(200_000)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "fetchArticleFull failed", e)
            null
        }
    }

    fun readCache(ctx: Context): List<NewsItem> = try {
        val prefs = ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(CACHE_KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = ArrayList<NewsItem>(minOf(arr.length(), MAX_CACHE_ARTICLES))
        for (i in 0 until minOf(arr.length(), MAX_CACHE_ARTICLES)) {
            val o = arr.optJSONObject(i) ?: continue
            val link = o.optString("link")
            val title = o.optString("title")
            if (link.isBlank() || title.isBlank()) continue
            out += NewsItem(
                id = o.optString("id").ifBlank { "u_" + link.hashCode().toUInt().toString(36) },
                title = title,
                summary = o.optString("summary"),
                link = link,
                source = o.optString("source"),
                date = o.optString("date"),
                image = o.optString("image"),
                logo = o.optString("logo")
            )
        }
        out
    } catch (_: Throwable) { emptyList() }

    fun writeCache(ctx: Context, items: List<NewsItem>) {
        try {
            val arr = JSONArray()
            items.take(MAX_CACHE_ARTICLES).forEach { item ->
                arr.put(JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("summary", item.summary)
                    put("link", item.link)
                    put("source", item.source)
                    put("date", item.date)
                    put("image", item.image)
                    put("logo", item.logo)
                })
            }
            ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit()
                .putString(CACHE_KEY, arr.toString())
                .putLong(CACHE_TIME_KEY, System.currentTimeMillis())
                .apply()
        } catch (t: Throwable) {
            Log.w(TAG, "writeCache failed", t)
        }
    }
}
