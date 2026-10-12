package com.appao

import android.app.Activity
import android.app.Application
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/** Lightweight local usage history and daily news visits for the Usage dashboard. */
object UsageTracker : Application.ActivityLifecycleCallbacks {

    data class Visit(
        val timestamp: Long,
        val source: String,
        val title: String,
        val link: String,
        val logo: String
    )

    private const val PREFS = "appao"
    private const val KEY_DAILY = "usage_daily"
    private const val KEY_SOURCES = "usage_sources"
    private const val KEY_VISITS = "usage_visits_v2"
    private const val MAX_DAYS = 7
    private const val MAX_VISITS = 300

    private var foregroundActivities = 0
    private var sessionStartedAt = 0L
    private var installed = false

    fun install(app: Application) {
        if (installed) return
        installed = true
        app.registerActivityLifecycleCallbacks(this)
    }

    private fun prefs(activity: Activity) =
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)

    private fun dayKey(time: Long = System.currentTimeMillis()): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(java.util.Date(time))
    }

    private fun addMinutes(activity: Activity, millis: Long) {
        if (millis <= 0L) return
        val key = dayKey()
        val p = prefs(activity)
        val raw = p.getString(KEY_DAILY, "{}") ?: "{}"
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        obj.put(key, obj.optLong(key, 0L) + millis)

        val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -MAX_DAYS) }
        val cutKey = dayKey(cutoff.timeInMillis)
        val keys = obj.keys()
        val remove = ArrayList<String>()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k < cutKey) remove += k
        }
        remove.forEach { obj.remove(it) }
        p.edit().putString(KEY_DAILY, obj.toString()).apply()
    }

    /** Legacy aggregated source count, retained so older saved stats are not lost. */
    fun recordNewsSource(activity: Activity, source: String) {
        val normalized = source.trim().ifBlank { "Fonte desconhecida" }
        val p = prefs(activity)
        val raw = p.getString(KEY_SOURCES, "{}") ?: "{}"
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        obj.put(normalized, obj.optInt(normalized, 0) + 1)
        p.edit().putString(KEY_SOURCES, obj.toString()).apply()
    }

    fun recordNewsVisit(
        activity: Activity,
        source: String,
        link: String,
        title: String,
        logo: String
    ) {
        val normalizedSource = source.trim().ifBlank { "Fonte desconhecida" }
        recordNewsSource(activity, normalizedSource)
        val p = prefs(activity)
        val raw = p.getString(KEY_VISITS, "[]") ?: "[]"
        val previous = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val now = System.currentTimeMillis()
        val updated = JSONArray()
        val first = (previous.length() - (MAX_VISITS - 1)).coerceAtLeast(0)
        for (i in first until previous.length()) {
            previous.optJSONObject(i)?.let(updated::put)
        }
        updated.put(JSONObject().apply {
            put("timestamp", now)
            put("day", dayKey(now))
            put("source", normalizedSource)
            put("title", title.trim())
            put("link", link.trim())
            put("logo", logo.trim())
        })
        p.edit().putString(KEY_VISITS, updated.toString()).apply()
    }

    fun todaysVisits(activity: Activity): List<Visit> {
        val raw = prefs(activity).getString(KEY_VISITS, "[]") ?: "[]"
        val today = dayKey()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in array.length() - 1 downTo 0) {
                    val obj = array.optJSONObject(i) ?: continue
                    val timestamp = obj.optLong("timestamp", 0L)
                    val storedDay = obj.optString("day", if (timestamp > 0) dayKey(timestamp) else "")
                    if (storedDay != today) continue
                    add(Visit(
                        timestamp = timestamp,
                        source = obj.optString("source", "Fonte desconhecida"),
                        title = obj.optString("title"),
                        link = obj.optString("link"),
                        logo = obj.optString("logo")
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun dailyMillis(activity: Activity): LongArray {
        val p = prefs(activity)
        val raw = p.getString(KEY_DAILY, "{}") ?: "{}"
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val out = LongArray(MAX_DAYS)
        for (index in out.indices) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -index) }
            out[out.lastIndex - index] = obj.optLong(dayKey(cal.timeInMillis), 0L)
        }
        return out
    }

    fun sourceCounts(activity: Activity): List<Pair<String, Int>> {
        val p = prefs(activity)
        val raw = p.getString(KEY_SOURCES, "{}") ?: "{}"
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val result = ArrayList<Pair<String, Int>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result += key to obj.optInt(key, 0)
        }
        return result.sortedByDescending { it.second }.take(8)
    }

    fun totalMillis(activity: Activity): Long = dailyMillis(activity).sum()

    override fun onActivityStarted(activity: Activity) {
        foregroundActivities++
        if (foregroundActivities == 1) sessionStartedAt = System.currentTimeMillis()
    }

    override fun onActivityStopped(activity: Activity) {
        foregroundActivities = (foregroundActivities - 1).coerceAtLeast(0)
        if (foregroundActivities == 0 && sessionStartedAt > 0L) {
            addMinutes(activity, System.currentTimeMillis() - sessionStartedAt)
            sessionStartedAt = 0L
        }
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
