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

data class NewsCategory(
    val id: String,
    val label: String,
    val providers: List<String> = emptyList()
)

/**
 * Bounded, cache-first news repository.
 * Every public list is deduplicated by normalized URL and normalized title.
 */
object NewsRepository {

    private const val TAG = "AppAo.News"
    private const val BASE = "https://eisa-sa-servers.onrender.com"
    private const val KEY = "sk_live_168c3937133560729e5c18a7f1a5183fd3a8a3b351a833b692224a00ef5b45a3"
    private const val CACHE_PREFS = "appao"
    private const val CACHE_KEY = "news_cache_v3_international"
    private const val CACHE_TIME_KEY = "news_cache_v3_international_time"
    private const val MAX_ARTICLES_PER_CATEGORY = 12
    private const val MAX_CACHE_ARTICLES = 80
    private const val MAX_BODY_BYTES = 1_500_000L
    private const val MAX_ARTICLE_BYTES = 4_000_000L

    private val okHttpDispatcher = Dispatcher().apply {
        maxRequests = 10
        maxRequestsPerHost = 6
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(okHttpDispatcher)
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val defaultCategories = listOf(
        NewsCategory("world", "Internacional", listOf("gnews")),
        NewsCategory("business", "Negócios", listOf("gnews", "currents")),
        NewsCategory("technology", "Tecnologia", listOf("gnews", "currents")),
        NewsCategory("entertainment", "Entretenimento", listOf("gnews", "currents")),
        NewsCategory("sports", "Desporto", listOf("gnews", "currents")),
        NewsCategory("science", "Ciência", listOf("gnews", "currents")),
        NewsCategory("health", "Saúde", listOf("gnews", "currents")),
        NewsCategory("society", "Sociedade", listOf("currents")),
        NewsCategory("politics_government", "Política e Governo", listOf("currents")),
        NewsCategory("lifestyle_leisure", "Estilo de Vida e Lazer", listOf("currents")),
        NewsCategory("human_interest", "Interesse Humano", listOf("currents")),
        NewsCategory("crime_law_justice", "Crime, Direito e Justiça", listOf("currents")),
        NewsCategory("education", "Educação", listOf("currents")),
        NewsCategory("environment", "Ambiente", listOf("currents")),
        NewsCategory("labour", "Trabalho", listOf("currents")),
        NewsCategory("automotive", "Automóvel", listOf("currents")),
        NewsCategory("real_estate", "Imobiliário", listOf("currents"))
    )
    @Volatile private var cachedCategories: List<NewsCategory>? = null
    @Volatile private var categoriesLoadedAt: Long = 0L

    /** Immediate category snapshot for UI-first rendering while a refresh runs. */
    fun categoriesSnapshot(): List<NewsCategory> = cachedCategories ?: defaultCategories

    suspend fun fetchCategories(forceRefresh: Boolean = false): List<NewsCategory> {
        val current = cachedCategories
        if (!forceRefresh && current != null && System.currentTimeMillis() - categoriesLoadedAt < 5 * 60_000L) {
            return current
        }
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE/news/categories")
                    .header("Accept", "application/json")
                    .header("x-api-key", KEY)
                    .build()
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful || raw.isBlank()) {
                        cachedCategories ?: defaultCategories
                    } else {
                        val array = JSONObject(raw).optJSONArray("categories")
                        val parsed = ArrayList<NewsCategory>()
                        if (array != null) {
                            for (i in 0 until array.length()) {
                                val item = array.optJSONObject(i) ?: continue
                                val id = item.optString("id").trim()
                                val label = item.optString("label").trim()
                                if (id.isBlank() || label.isBlank()) continue
                                val providerArray = item.optJSONArray("providers")
                                val providers = ArrayList<String>()
                                if (providerArray != null) {
                                    for (j in 0 until providerArray.length()) {
                                        providerArray.optString(j).takeIf { it.isNotBlank() }?.let(providers::add)
                                    }
                                }
                                parsed += NewsCategory(id, label, providers)
                            }
                        }
                        (parsed.takeIf { it.isNotEmpty() } ?: defaultCategories).also {
                            cachedCategories = it
                            categoriesLoadedAt = System.currentTimeMillis()
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                android.util.Log.w(TAG, "Could not load server categories; using the server's documented category IDs", t)
                cachedCategories ?: defaultCategories
            }
        }
    }

    /** Kept for compatibility with the existing MainActivity. */
    fun resetDedup() = Unit

    suspend fun fetchGeneral(page: Int = 1): List<NewsItem> =
        fetchByCategory("world", page, "")

    suspend fun fetchByCategory(
        category: String,
        page: Int = 1,
        country: String = "AO"
    ): List<NewsItem> = withContext(Dispatchers.IO) {
        try {
            // API v3 is international-only. Never append country=AO or legacy category aliases.
            val encodedCategory = URLEncoder.encode(category, "UTF-8")
            val url = "$BASE/news?category=$encodedCategory&page=$page&pageSize=20"

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

    private fun categoryCacheKey(category: String): String =
        "news_cache_category_" + category.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_]+"), "_")

    fun readCache(ctx: Context): List<NewsItem> = readCacheForKey(ctx, CACHE_KEY)

    fun readCategoryCache(ctx: Context, category: String): List<NewsItem> =
        readCacheForKey(ctx, categoryCacheKey(category))

    private fun readCacheForKey(ctx: Context, key: String): List<NewsItem> = try {
        val raw = ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            .getString(key, "[]") ?: "[]"
        val array = JSONArray(raw)
        val items = ArrayList<NewsItem>(minOf(array.length(), MAX_CACHE_ARTICLES))
        for (i in 0 until minOf(array.length(), MAX_CACHE_ARTICLES)) {
            val o = array.optJSONObject(i) ?: continue
            val link = o.optString("link").trim()
            val title = cleanText(o.optString("title"))
            if (link.isBlank() || title.isBlank()) continue
            items += NewsItem(
                id = o.optString("id").ifBlank { stableId(link, title) },
                title = title,
                summary = cleanText(o.optString("summary")),
                link = link,
                source = cleanText(o.optString("source")),
                date = o.optString("date"),
                image = o.optString("image"),
                logo = o.optString("logo")
            )
        }
        deduplicate(items)
    } catch (t: Throwable) {
        Log.w(TAG, "readCache failed for $key", t)
        emptyList()
    }

    fun writeCache(ctx: Context, items: List<NewsItem>) = writeCacheForKey(ctx, CACHE_KEY, items)

    fun writeCategoryCache(ctx: Context, category: String, items: List<NewsItem>) =
        writeCacheForKey(ctx, categoryCacheKey(category), items)

    private fun writeCacheForKey(ctx: Context, key: String, items: List<NewsItem>) {
        try {
            val array = JSONArray()
            deduplicate(items.take(MAX_CACHE_ARTICLES)).forEach { item ->
                array.put(JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title.take(500))
                    put("summary", item.summary)
                    put("link", item.link.take(4000))
                    put("source", item.source.take(120))
                    put("date", item.date.take(80))
                    put("image", item.image.take(2000))
                    put("logo", item.logo.take(1000))
                })
            }
            ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit()
                .putString(key, array.toString())
                .putLong("${key}_time", System.currentTimeMillis())
                .apply()
        } catch (t: Throwable) {
            Log.w(TAG, "writeCache failed for $key", t)
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

        val candidates = buildList {
            declared?.let { add(runCatching { String(bytes, it) }.getOrNull()) }
            add(runCatching { String(bytes, Charsets.UTF_8) }.getOrNull())
            add(runCatching { String(bytes, Charset.forName("windows-1252")) }.getOrNull())
            add(runCatching { String(bytes, Charsets.ISO_8859_1) }.getOrNull())
        }.filterNotNull().distinct()

        fun corruptionScore(value: String): Int {
            var score = value.count { it == '\uFFFD' } * 10
            value.forEachIndexed { index, c ->
                when (c) {
                    '\u00C3', '\u00C2', '\u00E2', '\u00F0', '\u00F1', '\u00BF', '\u00BD' -> score += 2
                    'Ã', 'Â', 'â', 'ð', '�' -> score += 2
                }
                if (index + 1 < value.length) {
                    val pair = value.substring(index, index + 2)
                    if (pair in setOf("Ã", "Â", "�½", "¿½", "â€")) score += 3
                }
            }
            return score
        }

        return candidates.minByOrNull(::corruptionScore) ?: String(bytes, Charsets.UTF_8)
    }

    private fun cleanText(
        value: String
    ): String {
        if (value.isBlank()) return ""

        val htmlText = try {
            Html.fromHtml(
                value,
                Html.FROM_HTML_MODE_LEGACY
            ).toString()
        } catch (_: Throwable) {
            value
        }

        return repairMojibake(htmlText)
            .replace('\u00A0', ' ')
            .replace("\uFFFD", "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Public display sanitizer used for older cached feed entries. */
    fun cleanForDisplay(value: String): String = cleanText(value)

    /** Repairs common UTF-8 text that was decoded once as Latin-1/Windows-1252. */
    private fun repairMojibake(value: String): String {
        var current = value

        fun score(text: String): Int {
            var count = text.count { it == '\uFFFD' } * 10
            text.forEach { ch ->
                when (ch) {
                    'Ã', 'Â', 'â', 'ð', 'ï', '¿', '½' -> count += 1
                }
            }
            return count
        }

        repeat(3) {
            val before = score(current)
            if (before == 0) return current

            val candidates = listOf(
                runCatching { String(current.toByteArray(Charset.forName("windows-1252")), Charsets.UTF_8) }.getOrNull(),
                runCatching { String(current.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8) }.getOrNull()
            ).filterNotNull()

            val best = candidates.minByOrNull(::score)
            if (best != null && score(best) < before) current = best else return@repeat
        }

        val replacements = linkedMapOf(
            "re�ne" to "reúne",
            "re¿ne" to "reúne",
            "n�o" to "não",
            "n¿o" to "não",
            "s�o" to "são",
            "s¿o" to "são",
            "fam�lias" to "famílias",
            "recorr�ncia" to "recorrência",
            "rela��es" to "relações",
            "solu��es" to "soluções",
            "d�vidas" to "dívidas",
            "frequ�ncia" to "frequência",
            "lan�ados" to "lançados",
            "renegocia��o" to "renegociação",
            "comunica��o" to "comunicação",
            "prote��o" to "proteção",
            "detec��o" to "detecção",
            "observat�rio" to "observatório",
            "constru�do" to "construído",
            "ter�a-feira" to "terça-feira",
            "�reas" to "áreas",
            "�nica" to "única",
            "Ci�½ncias" to "Ciências",
            "Ci¿½ncias" to "Ciências",
            "Pr�½mio" to "Prêmio",
            "Pr¿½mio" to "Prêmio",
            "Fi�½sica" to "Física",
            "Fi¿½sica" to "Física",
            "contribui��es" to "contribuições",
            "contribui¿½es" to "contribuições",
            "�reas" to "áreas",
            "mensageiros fantasmag�ricos" to "mensageiros fantasmagóricos",
            "mensageiros fantasm¿½gicos" to "mensageiros fantasmagóricos",
            "esp�ço" to "espaço",
            "esp¿½o" to "espaço",
            "alta energia" to "alta energia",
            "observat�rio de neutrinos" to "observatório de neutrinos",
            "constru�do no polo" to "construído no polo",
            "ter�½a-feira" to "terça-feira"
        )
        var repaired = current
        replacements.forEach { (broken, fixed) ->
            repaired = repaired.replace(broken, fixed)
        }

        // Remove any unrecoverable replacement glyphs left by malformed legacy feeds.
        repaired = repaired
            .replace("�", "")
            .replace("\uFFFD", "")
            .replace(Regex("\\s{2,}"), " ")

        return repaired
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
