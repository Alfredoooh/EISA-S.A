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
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

object NewsRepository {

    private const val TAG = "AppAo.News"
    private const val BASE = "https://eisa-sa-servers.onrender.com"
    private const val KEY = "sk_live_168c3937133560729e5c18a7f1a5183fd3a8a3b351a833b692224a00ef5b45a3"
    private const val CACHE_PREFS = "appao"
    private const val CACHE_KEY = "news_cache"
    private const val CACHE_TIME_KEY = "news_cache_time"
    private const val MAX_ARTICLES_PER_CATEGORY = 12
    private const val MAX_CACHE_ARTICLES = 80
    private const val MAX_BODY_BYTES = 1_500_000L
    private const val MAX_ARTICLE_BYTES = 4_000_000L

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
        "general",
        "business",
        "sports",
        "technology",
        "entertainment",
        "health",
        "politics"
    )

    private val dedupLock = Any()
    private val seenKeys = LinkedHashSet<String>()

    fun resetDedup() {
        synchronized(dedupLock) {
            seenKeys.clear()
        }
    }

    suspend fun fetchGeneral(page: Int = 1): List<NewsItem> = supervisorScope {
        val groups = ArrayList<List<NewsItem>>(categories.size)

        for (chunk in categories.chunked(2)) {
            val deferred = chunk.map { category ->
                async(Dispatchers.IO) {
                    fetchByCategory(category, page)
                }
            }

            deferred.forEach { task ->
                groups += try {
                    task.await()
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    Log.w(TAG, "Category request failed", t)
                    emptyList()
                }
            }
        }

        interleaveAndDeduplicate(groups.flatten())
    }

    suspend fun fetchByCategory(
        category: String,
        page: Int = 1,
        country: String = "AO"
    ): List<NewsItem> = withContext(Dispatchers.IO) {
        try {
            val countryQuery = if (country.isBlank()) "" else "&country=$country"
            val url = "$BASE/news?lang=pt$countryQuery&category=$category&page=$page"

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("x-api-key", KEY)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body ?: return@withContext emptyList()
                val length = body.contentLength()

                if (length > MAX_BODY_BYTES) {
                    Log.w(TAG, "Ignoring oversized news response: $length bytes")
                    return@withContext emptyList()
                }

                if (!response.isSuccessful) {
                    Log.w(TAG, "News HTTP ${response.code} for $category page=$page")
                    return@withContext emptyList()
                }

                val raw = body.string()
                if (raw.length > MAX_BODY_BYTES.toInt()) return@withContext emptyList()

                val array = JSONObject(raw)
                    .optJSONArray("articles")
                    ?: return@withContext emptyList()

                val result = ArrayList<NewsItem>(
                    minOf(
                        array.length(),
                        MAX_ARTICLES_PER_CATEGORY
                    )
                )

                for (i in 0 until minOf(array.length(), MAX_ARTICLES_PER_CATEGORY)) {
                    val article = array.optJSONObject(i) ?: continue
                    val link = article.optString("url").trim()
                    val title = article.optString("title").trim()

                    if (
                        link.length < 8 ||
                        title.isEmpty() ||
                        !link.startsWith("http", true)
                    ) continue

                    val domain = try {
                        URL(link).host.removePrefix("www.")
                    } catch (_: Throwable) {
                        ""
                    }

                    result += NewsItem(
                        id = stableId(link, title),
                        title = title.take(500),
                        summary = article.optString("description").take(1600),
                        link = link,
                        source = article.optString("source")
                            .ifBlank { domain }
                            .take(120),
                        date = article.optString("publishedAt"),
                        image = article.optString("image")
                            .trim()
                            .take(2000),
                        logo = if (domain.isNotEmpty()) {
                            "https://www.google.com/s2/favicons?domain=$domain&sz=128"
                        } else {
                            ""
                        }
                    )
                }

                deduplicate(result)
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Log.e(TAG, "fetchByCategory failed", t)
            emptyList()
        }
    }

    suspend fun fetchArticleFull(url: String): String? = withContext(Dispatchers.IO) {
        try {
            if (!url.startsWith("http", true)) return@withContext null

            val requestUrl = "$BASE/article?url=${URLEncoder.encode(url, "UTF-8")}"
            val request = Request.Builder()
                .url(requestUrl)
                .header("Accept", "application/json")
                .header("x-api-key", KEY)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body ?: return@withContext null
                val length = body.contentLength()
                if (length > MAX_ARTICLE_BYTES || !response.isSuccessful) {
                    return@withContext null
                }

                JSONObject(body.string())
                    .optJSONObject("article")
                    ?.optString("body")
                    ?.take(200_000)
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Log.e(TAG, "fetchArticleFull failed", t)
            null
        }
    }

    fun readCache(ctx: Context): List<NewsItem> = try {
        val prefs = ctx.getSharedPreferences(
            CACHE_PREFS,
            Context.MODE_PRIVATE
        )
        val raw = prefs.getString(CACHE_KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        val items = ArrayList<NewsItem>(
            minOf(array.length(), MAX_CACHE_ARTICLES)
        )

        for (i in 0 until minOf(array.length(), MAX_CACHE_ARTICLES)) {
            val o = array.optJSONObject(i) ?: continue
            val link = o.optString("link")
            val title = o.optString("title")
            if (link.isBlank() || title.isBlank()) continue

            items += NewsItem(
                id = o.optString("id")
                    .ifBlank { stableId(link, title) },
                title = title,
                summary = o.optString("summary"),
                link = link,
                source = o.optString("source"),
                date = o.optString("date"),
                image = o.optString("image"),
                logo = o.optString("logo")
            )
        }

        deduplicate(items)
    } catch (t: Throwable) {
        Log.w(TAG, "readCache failed", t)
        emptyList()
    }

    fun writeCache(
        ctx: Context,
        items: List<NewsItem>
    ) {
        try {
            val clean = deduplicate(
                items.take(MAX_CACHE_ARTICLES)
            )

            val array = JSONArray()
            clean.take(MAX_CACHE_ARTICLES).forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("title", item.title.take(500))
                        put("summary", item.summary.take(1600))
                        put("link", item.link.take(4000))
                        put("source", item.source.take(120))
                        put("date", item.date.take(80))
                        put("image", item.image.take(2000))
                        put("logo", item.logo.take(1000))
                    }
                )
            }

            ctx.getSharedPreferences(
                CACHE_PREFS,
                Context.MODE_PRIVATE
            ).edit()
                .putString(CACHE_KEY, array.toString())
                .putLong(
                    CACHE_TIME_KEY,
                    System.currentTimeMillis()
                )
                .apply()
        } catch (t: Throwable) {
            Log.w(TAG, "writeCache failed", t)
        }
    }

    private fun interleaveAndDeduplicate(
        items: List<NewsItem>
    ): List<NewsItem> {
        val byIndex = ArrayList<NewsItem>()
        val groups = items.groupBy { it.id }
        groups.values.forEach { values ->
            byIndex += values.first()
        }
        return deduplicate(byIndex)
    }

    private fun deduplicate(
        source: List<NewsItem>
    ): List<NewsItem> {
        val out = ArrayList<NewsItem>(source.size)
        val urls = HashSet<String>()
        val titles = HashSet<String>()

        source.forEach { item ->
            val urlKey = normalizeUrl(item.link)
            val titleKey = normalizeTitle(item.title)
            if (urlKey.isBlank() || titleKey.isBlank()) return@forEach
            if (!urls.add(urlKey)) return@forEach
            if (!titles.add(titleKey)) return@forEach
            out += item
        }

        synchronized(dedupLock) {
            out.forEach { item ->
                seenKeys += normalizeUrl(item.link)
                seenKeys += normalizeTitle(item.title)
            }
            while (seenKeys.size > 2000) {
                val first = seenKeys.firstOrNull() ?: break
                seenKeys.remove(first)
            }
        }

        return out
    }

    private fun stableId(
        url: String,
        title: String
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(
                (normalizeUrl(url) + "|" + normalizeTitle(title))
                    .toByteArray(Charsets.UTF_8)
            )

        return buildString {
            digest.take(10).forEach { byte ->
                append("%02x".format(byte))
            }
        }
    }

    private fun normalizeUrl(value: String): String = value.trim()
        .lowercase(Locale.ROOT)
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('#')
        .trimEnd('/')

    private fun normalizeTitle(value: String): String = value.trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\p{Punct}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
