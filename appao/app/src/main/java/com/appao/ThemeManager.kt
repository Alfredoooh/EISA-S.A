package com.appao

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

/** Single persisted source of truth for the app theme. */
object ThemeManager {

    private const val PREFS = "appao"
    private const val KEY = "theme"

    fun current(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "system")
            ?: "system"

    fun save(ctx: Context, value: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, value)
            .apply()
    }

    fun mode(ctx: Context): Int = when (current(ctx)) {
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun resolvedDark(ctx: Context): Boolean = when (current(ctx)) {
        "dark" -> true
        "light" -> false
        else -> (
            ctx.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            ) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun apply(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(
            mode(ctx)
        )
    }

    fun applyAndRefresh(
        activity: AppCompatActivity,
        value: String
    ) {
        save(activity, value)

        AppCompatDelegate.setDefaultNightMode(
            mode(activity)
        )

        try {
            activity.delegate.applyDayNight()
        } catch (_: Throwable) {
        }

        activity.window.decorView.post {
            if (!activity.isFinishing && !activity.isDestroyed) {
                SystemBarHelper.sync(activity)
            }
        }
    }

    fun label(ctx: Context): String = when (current(ctx)) {
        "dark" -> "Escuro"
        "light" -> "Claro"
        else -> "Sistema"
    }

    fun applyFromLabel(
        ctx: Context,
        label: String
    ) {
        val value = when (label) {
            "Escuro" -> "dark"
            "Claro" -> "light"
            else -> "system"
        }

        save(ctx, value)

        AppCompatDelegate.setDefaultNightMode(
            mode(ctx)
        )
    }
}
