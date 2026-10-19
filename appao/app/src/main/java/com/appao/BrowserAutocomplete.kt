package com.appao

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray


/** Live search suggestions from Google's public, undocumented autocomplete endpoint. */
object BrowserAutocomplete {
    fun googleSuggestions(query: String, limit: Int = 8): List<String> {
        val cleaned = query.trim()
        if (cleaned.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(cleaned, "UTF-8")
        val endpoint = "https://suggestqueries.google.com/complete/search?client=firefox&hl=pt-PT&gl=AO&q=$encoded"
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1800
                readTimeout = 1800
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "AppAoBrowser/1.0 Android")
            }
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONArray(body)
            val results = root.optJSONArray(1) ?: return emptyList()
            buildList {
                for (index in 0 until minOf(results.length(), limit.coerceAtLeast(1))) {
                    val value = results.optString(index).trim()
                    if (value.isNotBlank() && !value.equals(cleaned, ignoreCase = true)) add(value)
                }
            }.distinct().take(limit.coerceAtLeast(1))
        } catch (_: Throwable) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }
}
