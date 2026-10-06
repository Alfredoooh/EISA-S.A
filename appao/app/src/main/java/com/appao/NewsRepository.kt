package com.appao

import android.content.Context
import android.text.Html
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
import java.nio.charset.Charset
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Bounded, cache-first news repository.
 * Every public list is deduplicated by normalized URL and normalized title.
 */
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
        maxRequestsPerHost = 2
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

    /** Kept for compatibility with the existing MainActivity. */
    fun resetDedup() = Unit

    suspend fun fetchGeneral(
        page: Int = 1
    ): List<NewsItem> = supervisorScope {

        val results = ArrayList<List<NewsItem>>(
            categories.size
        )

        // Only two category requests are active at a time.
        for (chunk in categories.chunked(2)) {
            val jobs = chunk.map { category ->
                async(Dispatchers.IO) {
                    runCatching {
                        fetchByCategory(
                            category,
                            page,
                            "AO"
                        )
                    }.getOrElse { error ->
                        if (error is kotlinx.coroutines.CancellationException) {
                            throw error
                        }
                        Log.w(
                            TAG,
                            "Category request failed: $category",
                            error
                        )
                        emptyList()
                    }
                }
            }

            jobs.forEach { job ->
                results += job.await()
            }
        }

        // Preserve the category/interleave behavior while removing duplicates
        // across every category in this response.
        val out = ArrayList<NewsItem>()
        val iterators = results.map { it.iterator() }.toMutableList()
        val seenUrls = HashSet<String>()
        val seenTitles = HashSet<String>()

        var emitted = true
        while (emitted) {
            emitted = false
            val iteratorIt = iterators.iterator()
            while (iteratorIt.hasNext()) {
                val iterator = iteratorIt.next()
                if (!iterator.hasNext()) {
                    iteratorIt.remove()
                    continue
                }

                val item = iterator.next()
                val urlKey = normalizeUrl(item.link)
                val titleKey = normalizeTitle(item.title)

                if (
                    urlKey.isBlank() ||
                    titleKey.isBlank()
                ) {
                    continue
                }

                if (
                    seenUrls.add(urlKey) &&
                    seenTitles.add(titleKey)
                ) {
                    out += item
                }

                emitted = true
            }
        }

        out
    }

    suspend fun fetchByCategory(
        category: String,
        page: Int = 1,
        country: String = "AO"
    ): List<NewsItem> = withContext(Dispatchers.IO) {
        try {
            val countryQuery =
                if (country.isBlank()) "" else "&country=$country"

            val url =
                "$BASE/news?lang=pt$countryQuery&category=$category&page=$page"

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("Accept-Charset", "utf-8")
                .header("x-api-key", KEY)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body
                    ?: return@withContext emptyList()

                val length = body.contentLength()
                if (
                    length > MAX_BODY_BYTES
                ) {
                    Log.w(
                        TAG,
                        "Ignoring oversized news response: $length bytes"
                    )
                    return@withContext emptyList()
                }

                if (!response.isSuccessful) {
                    Log.w(
                        TAG,
                        "News HTTP ${response.code} for $category page=$page"
                    )
                    return@withContext emptyList()
                }

                val raw = decodeBody(
                    body.bytes(),
                    response.header("Content-Type")
                )

                if (
                    raw.toByteArray(Charsets.UTF_8).size >
                    MAX_BODY_BYTES
                ) {
                    return@withContext emptyList()
                }

                val array = JSONObject(raw)
                    .optJSONArray("articles")
                    ?: return@withContext emptyList()

                val result = ArrayList<NewsItem>(
                    minOf(
                        array.length(),
                        MAX_ARTICLES_PER_CATEGORY
                    )
                )

                for (
                    i in 0 until minOf(
                        array.length(),
                        MAX_ARTICLES_PER_CATEGORY
                    )
                ) {
                    val article =
                        array.optJSONObject(i)
                            ?: continue

                    val link =
                        article.optString("url")
                            .trim()

                    val title = cleanText(
                        article.optString("title")
                    )

                    if (
                        link.length < 8 ||
                        title.isBlank() ||
                        !link.startsWith(
                            "http",
                            true
                        )
                    ) {
                        continue
                    }

                    val domain = try {
                        URL(link)
                            .host
                            .removePrefix("www.")
                            .lowercase(Locale.ROOT)
                    } catch (_: Throwable) {
                        ""
                    }

                    val source = cleanText(
                        article.optString("source")
                    ).ifBlank { domain }

                    result += NewsItem(
                        id = stableId(
                            link,
                            title
                        ),
                        title = title.take(500),
                        summary = cleanText(
                            article.optString("description")
                        ),
                        link = link.take(4000),
                        source = source.take(120),
                        date = article
                            .optString("publishedAt")
                            .take(80),
                        image = article
                            .optString("image")
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
            if (
                t is kotlinx.coroutines.CancellationException
            ) {
                throw t
            }

            Log.e(
                TAG,
                "fetchByCategory failed",
                t
            )

            emptyList()
        }
    }

    suspend fun fetchArticleFull(
        url: String,
        expectedTitle: String = ""
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (!url.startsWith("http", true)) {
                return@withContext null
            }

            val requestUrl =
                "$BASE/article?url=" +
                    URLEncoder.encode(
                        url,
                        "UTF-8"
                    )

            val request = Request.Builder()
                .url(requestUrl)
                .header("Accept", "application/json")
                .header("Accept-Charset", "utf-8")
                .header("x-api-key", KEY)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body
                    ?: return@withContext null

                if (
                    body.contentLength() >
                    MAX_ARTICLE_BYTES ||
                    !response.isSuccessful
                ) {
                    return@withContext null
                }

                val raw = decodeBody(
                    body.bytes(),
                    response.header("Content-Type")
                )

                val article = JSONObject(raw).optJSONObject("article")
                    ?: return@withContext null

                val responseUrl = article.optString("url").trim()
                val responseTitle = cleanText(article.optString("title"))
                if (responseUrl.isNotBlank() && normalizeUrl(responseUrl) != normalizeUrl(url)) {
                    Log.w(TAG, "Rejected mismatched article response for $url")
                    return@withContext null
                }
                if (expectedTitle.isNotBlank() && responseTitle.isNotBlank()) {
                    val a = normalizeTitle(responseTitle)
                    val b = normalizeTitle(expectedTitle)
                    if (a != b && !a.contains(b) && !b.contains(a)) {
                        Log.w(TAG, "Rejected mismatched article title for $url")
                        return@withContext null
                    }
                }

                cleanText(article.optString("body")).take(200_000)
            }
        } catch (t: Throwable) {
            if (
                t is kotlinx.coroutines.CancellationException
            ) {
                throw t
            }

            Log.e(
                TAG,
                "fetchArticleFull failed",
                t
            )

            null
        }
    }

    fun readCache(
        ctx: Context
    ): List<NewsItem> = try {
        val prefs = ctx.getSharedPreferences(
            CACHE_PREFS,
            Context.MODE_PRIVATE
        )

        val raw = prefs.getString(
            CACHE_KEY,
            "[]"
        ) ?: "[]"

        val array = JSONArray(raw)
        val items = ArrayList<NewsItem>(
            minOf(
                array.length(),
                MAX_CACHE_ARTICLES
            )
        )

        for (
            i in 0 until minOf(
                array.length(),
                MAX_CACHE_ARTICLES
            )
        ) {
            val o = array.optJSONObject(i)
                ?: continue

            val link = o.optString("link")
                .trim()
            val title = cleanText(
                o.optString("title")
            )

            if (
                link.isBlank() ||
                title.isBlank()
            ) {
                continue
            }

            items += NewsItem(
                id = o.optString("id")
                    .ifBlank {
                        stableId(
                            link,
                            title
                        )
                    },
                title = title,
                summary = cleanText(
                    o.optString("summary")
                ),
                link = link,
                source = cleanText(
                    o.optString("source")
                ),
                date = o.optString("date"),
                image = o.optString("image"),
                logo = o.optString("logo")
            )
        }

        deduplicate(items)
    } catch (t: Throwable) {
        Log.w(
            TAG,
            "readCache failed",
            t
        )
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

            clean.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put(
                            "id",
                            item.id
                        )
                        put(
                            "title",
                            item.title.take(500)
                        )
                        put(
                            "summary",
                            item.summary
                        )
                        put(
                            "link",
                            item.link.take(4000)
                        )
                        put(
                            "source",
                            item.source.take(120)
                        )
                        put(
                            "date",
                            item.date.take(80)
                        )
                        put(
                            "image",
                            item.image.take(2000)
                        )
                        put(
                            "logo",
                            item.logo.take(1000)
                        )
                    }
                )
            }

            ctx.getSharedPreferences(
                CACHE_PREFS,
                Context.MODE_PRIVATE
            )
                .edit()
                .putString(
                    CACHE_KEY,
                    array.toString()
                )
                .putLong(
                    CACHE_TIME_KEY,
                    System.currentTimeMillis()
                )
                .apply()

        } catch (t: Throwable) {
            Log.w(
                TAG,
                "writeCache failed",
                t
            )
        }
    }

    fun cacheIsFresh(
        ctx: Context,
        maxAgeMs: Long = 5 * 60 * 1000L
    ): Boolean {
        return try {
            val timestamp = ctx
                .getSharedPreferences(
                    CACHE_PREFS,
                    Context.MODE_PRIVATE
                )
                .getLong(
                    CACHE_TIME_KEY,
                    0L
                )

            timestamp > 0L &&
                System.currentTimeMillis() -
                timestamp < maxAgeMs
        } catch (_: Throwable) {
            false
        }
    }

    private fun deduplicate(
        source: List<NewsItem>
    ): List<NewsItem> {
        val out = ArrayList<NewsItem>(
            source.size
        )

        val urls = HashSet<String>()
        val titles = HashSet<String>()

        source.forEach { item ->
            val urlKey = normalizeUrl(
                item.link
            )
            val titleKey = normalizeTitle(
                item.title
            )

            if (
                urlKey.isBlank() ||
                titleKey.isBlank()
            ) {
                return@forEach
            }

            if (!urls.add(urlKey)) {
                return@forEach
            }

            if (!titles.add(titleKey)) {
                return@forEach
            }

            out += item
        }

        return out
    }

    private fun decodeBody(bytes: ByteArray, contentType: String?): String {
        val declared = runCatching {
            contentType
                ?.substringAfter("charset=", "")
                ?.trim()
                ?.trim('"', '\'')
                ?.takeIf { it.isNotBlank() }
                ?.let { Charset.forName(it) }
        }.getOrNull()

        val utf8 = runCatching { String(bytes, declared ?: Charsets.UTF_8) }.getOrElse { String(bytes, Charsets.UTF_8) }
        if (!utf8.contains('\uFFFD')) return utf8

        val windows1252 = runCatching { Charset.forName("windows-1252") }.getOrNull()
        val latin = windows1252?.let { runCatching { String(bytes, it) }.getOrNull() }
            ?: runCatching { String(bytes, Charsets.ISO_8859_1) }.getOrNull()

        return if (latin != null && latin.count { it == '\uFFFD' } < utf8.count { it == '\uFFFD' }) {
            latin
        } else {
            utf8
        }
    }

    private fun cleanText(
        value: String
    ): String {
        if (value.isBlank()) return ""

        return try {
            Html.fromHtml(
                value,
                Html.FROM_HTML_MODE_LEGACY
            )
                .toString()
                .replace('\u00A0', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
        } catch (_: Throwable) {
            value
                .replace('\u00A0', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }

    private fun stableId(
        url: String,
        title: String
    ): String {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(
                (
                    normalizeUrl(url) +
                        "|" +
                        normalizeTitle(title)
                    )
                    .toByteArray(Charsets.UTF_8)
            )

        return buildString {
            digest.take(10).forEach { byte ->
                append(
                    "%02x".format(byte)
                )
            }
        }
    }

    private fun normalizeUrl(
        value: String
    ): String {
        val base = value
            .trim()
            .lowercase(Locale.ROOT)
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('#')

        if (base.isBlank()) return ""

        val parts = base.split("?", limit = 2)
        if (parts.size == 1) {
            return parts[0].trimEnd('/')
        }

        val query = parts[1]
            .split('&')
            .asSequence()
            .filter { it.isNotBlank() }
            .filterNot {
                val key = it
                    .substringBefore('=')
                    .lowercase(Locale.ROOT)
                key.startsWith("utm_") ||
                    key in setOf(
                        "fbclid",
                        "gclid",
                        "mc_cid",
                        "mc_eid",
                        "ref",
                        "ref_src"
                    )
            }
            .joinToString("&")

        return if (query.isBlank()) {
            parts[0].trimEnd('/')
        } else {
            parts[0].trimEnd('/') + "?" + query
        }
    }

    private fun normalizeTitle(
        value: String
    ): String = cleanText(value)
        .lowercase(Locale.ROOT)
        .replace("\uFFFD", " ")
        .replace(Regex("[\\p{Punct}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
