package com.appao

import android.app.Activity
import android.app.Application
import android.os.Bundle
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/** Lightweight local usage history used by the Usage screen. */
object UsageTracker : Application.ActivityLifecycleCallbacks {

    private const val PREFS = "appao"
    private const val KEY_DAILY = "usage_daily"
    private const val KEY_SOURCES = "usage_sources"
    private const val MAX_DAYS = 7

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
        val old = obj.optLong(key, 0L)
        obj.put(key, old + millis)

        // Drop entries older than the visible weekly window.
        val cutoff = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -MAX_DAYS)
        }
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

    fun recordNewsSource(activity: Activity, source: String) {
        val normalized = source.trim().ifBlank { "Fonte desconhecida" }
        val p = prefs(activity)
        val raw = p.getString(KEY_SOURCES, "{}") ?: "{}"
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        obj.put(normalized, obj.optInt(normalized, 0) + 1)
        p.edit().putString(KEY_SOURCES, obj.toString()).apply()
    }

    fun dailyMillis(activity: Activity): LongArray {
        val p = prefs(activity)
        val raw = p.getString(KEY_DAILY, "{}") ?: "{}"
        val obj = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        val out = LongArray(MAX_DAYS)
        for (index in out.indices) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -index)
            }
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

    fun totalMillis(activity: Activity): Long =
        dailyMillis(activity).sum()

    override fun onActivityStarted(activity: Activity) {
        foregroundActivities++
        if (foregroundActivities == 1) {
            sessionStartedAt = System.currentTimeMillis()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        foregroundActivities = (foregroundActivities - 1).coerceAtLeast(0)
        if (foregroundActivities == 0 && sessionStartedAt > 0L) {
            val elapsed = System.currentTimeMillis() - sessionStartedAt
            addMinutes(activity, elapsed)
            sessionStartedAt = 0L
        }
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
