package com.appao

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

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
        when (current(ctx)) {
            "dark"  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else    -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun label(ctx: Context): String = when (current(ctx)) {
        "dark" -> "Escuro"
        "light" -> "Claro"
        else -> "Sistema"
    }

    fun applyFromLabel(ctx: Context, label: String) {
        when (label) {
            "Escuro" -> save(ctx, "dark")
            "Claro" -> save(ctx, "light")
            else -> save(ctx, "system")
        }
        apply(ctx)
    }
}
