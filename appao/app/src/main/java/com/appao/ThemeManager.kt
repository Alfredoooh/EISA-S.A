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
        val normalized = when (value) {
            "dark", "light", "system" -> value
            else -> "system"
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, normalized).apply()
    }

    fun saveFromLabel(ctx: Context, label: String) {
        save(ctx, when (label) {
            "Escuro" -> "dark"
            "Claro" -> "light"
            else -> "system"
        })
    }

    private fun mode(ctx: Context): Int = when (current(ctx)) {
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    fun apply(ctx: Context) {
        val wanted = mode(ctx)
        if (AppCompatDelegate.getDefaultNightMode() == wanted) return
        AppCompatDelegate.setDefaultNightMode(wanted)
    }

    fun label(ctx: Context): String = when (current(ctx)) {
        "dark" -> "Escuro"
        "light" -> "Claro"
        else -> "Sistema"
    }

    @Deprecated("Use saveFromLabel() then apply()")
    fun applyFromLabel(ctx: Context, label: String) {
        saveFromLabel(ctx, label)
        apply(ctx)
    }
}
