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
            .edit().putString(KEY, value).commit()
    }

    fun apply(ctx: Context) {
        val mode = when (current(ctx)) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
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
        if (current(ctx) == value) return
        save(ctx, value)
        apply(ctx)
    }
}
