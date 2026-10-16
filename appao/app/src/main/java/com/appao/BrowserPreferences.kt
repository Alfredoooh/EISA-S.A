package com.appao

import android.content.Context

/** Persisted browser-specific options, kept separate from the app's global theme settings. */
object BrowserPreferences {
    private const val PREFS = "appao_browser"
    private const val KEY_AD_BLOCK = "ad_block_enabled"
    private const val KEY_FORCE_DARK = "force_dark_websites"
    private const val KEY_SAVE_HISTORY = "save_page_history"
    private const val KEY_SAVE_CONVERSATIONS = "save_conversation_history"

    fun adBlockEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AD_BLOCK, true)
    fun setAdBlockEnabled(context: Context, enabled: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AD_BLOCK, enabled).apply() }
    fun forceDarkEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FORCE_DARK, false)
    fun setForceDarkEnabled(context: Context, enabled: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_FORCE_DARK, enabled).apply() }
    fun savePageHistory(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SAVE_HISTORY, true)
    fun setSavePageHistory(context: Context, enabled: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SAVE_HISTORY, enabled).apply() }
    fun saveConversationHistory(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SAVE_CONVERSATIONS, true)
    fun setSaveConversationHistory(context: Context, enabled: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SAVE_CONVERSATIONS, enabled).apply() }
}
