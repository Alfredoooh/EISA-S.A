package com.appao

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object NewsRepository {

    private const val BASE = "https://eisa-sa-servers.onrender.com"
    private const val KEY = "sk_live_168c3937133560729e5c18a7f1a5183fd3a8a3b351a833b692224a00ef5b45a3"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val categories = listOf(
        "general", "business", "sports", "technology",
        "entertainment", "health", "politics"
    )

    suspend fun fetchGeneral(page: Int = 1): List<NewsItem> = withContext(Dispatchers.IO) {
        val groups = coroutineScope {
            categories.map { c -> async(Dispatchers.IO) { fetchByCategory(c, page) } }
                .map { it.await() }
        }
        val max = groups.maxOfOrNull { it.size } ?: 0
        val out = ArrayList<NewsItem>()
        val seen = HashSet<String>()
        for (i in 0 until max) {
            for (g in groups) {
                if (i < g.size) {
                    val it = g[i]
                    if (seen.add(it.id)) out.add(it)
                }
            }
        }
        out
    }

    suspend fun fetchByCategory(category: String, page: Int = 1): List<NewsItem> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE/news?lang=pt&country=AO&category=$category&page=$page"
            val req = Request.Builder().url(url).header("x-api-key", KEY).build()
            val body = client.newCall(req).execute().use { it.body?.string() ?: return@withContext emptyList() }
            val arr = JSONObject(body).optJSONArray("articles") ?: return@withContext emptyList()
            val out = ArrayList<NewsItem>()
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                val u = a.optString("url")
                val t = a.optString("title")
                if (u.isEmpty() || t.isEmpty()) continue
                val domain = try { java.net.URL(u).host.replace("^www\\.".toRegex(), "") } catch (e: Exception) { "" }
                out.add(NewsItem(
                    id = "u_" + u.hashCode().toString(36),
                    title = t,
                    summary = a.optString("description"),
                    link = u,
                    source = a.optString("source", domain),
                    date = a.optString("publishedAt"),
                    image = a.optString("image"),
                    logo = if (domain.isNotEmpty()) "https://www.google.com/s2/favicons?domain=$domain&sz=128" else ""
                ))
            }
            out
        } catch (e: Exception) { emptyList() }
    }

    suspend fun fetchArticleFull(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val u = "$BASE/article?url=${URLEncoder.encode(url, "UTF-8")}"
            val req = Request.Builder().url(u).header("x-api-key", KEY).build()
            val body = client.newCall(req).execute().use { it.body?.string() ?: return@withContext null }
            JSONObject(body).optJSONObject("article")?.optString("body")
        } catch (e: Exception) { null }
    }
}
