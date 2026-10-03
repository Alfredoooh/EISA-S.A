package com.appao

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowInsetsControllerCompat

object ThemeManager {

    private const val PREFS = "appao"
    private const val KEY = "theme"

    fun current(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "system") ?: "system"

    fun save(ctx: Context, value: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, value).apply()
    }

    fun apply(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(modeFor(current(ctx)))
        if (ctx is Activity) syncSystemBars(ctx)
    }

    fun applyInstant(activity: Activity, value: String) {
        save(activity, value)
        // configChanges=uiMode keeps the existing Activity alive. AppCompat then
        // updates the active resources without the destructive full Activity restart.
        AppCompatDelegate.setDefaultNightMode(modeFor(value))
        syncSystemBars(activity, value)
        activity.window.decorView.post { syncSystemBars(activity, value) }
    }

    fun syncSystemBars(activity: Activity, preferred: String? = null) {
        val mode = preferred ?: current(activity)
        val dark = isDark(activity, mode)
        val color = android.graphics.Color.parseColor(if (dark) "#0D0F12" else "#FFFFFF")
        activity.window.statusBarColor = color
        activity.window.navigationBarColor = color
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    fun label(ctx: Context): String = when (current(ctx)) {
        "dark" -> "Escuro"
        "light" -> "Claro"
        else -> "Sistema"
    }

    fun applyFromLabel(ctx: Context, label: String) {
        val value = when (label) {
            "Escuro" -> "dark"
            "Claro" -> "light"
            else -> "system"
        }
        if (ctx is Activity) applyInstant(ctx, value) else {
            save(ctx, value)
            apply(ctx)
        }
    }

    private fun modeFor(value: String): Int = when (value) {
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    private fun isDark(ctx: Context, mode: String): Boolean = when (mode) {
        "dark" -> true
        "light" -> false
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
