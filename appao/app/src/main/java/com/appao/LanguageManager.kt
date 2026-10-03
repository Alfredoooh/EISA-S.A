package com.appao

import android.content.Context

/** Persistência simples do idioma selecionado. A interface mantém-se estável até existirem traduções. */
object LanguageManager {
    private const val PREFS = "appao"
    private const val KEY = "language"

    fun current(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "Português") ?: "Português"

    fun save(ctx: Context, label: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, label).apply()
    }

    fun label(ctx: Context): String = current(ctx)
}
