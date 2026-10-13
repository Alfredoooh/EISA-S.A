package com.appao

import android.graphics.Color
import android.view.View
import android.app.Dialog

/** Native anchored quick-add menu opened from the fixed AI composer. */
object AppsPopup {
    data class Option(val id: Int, val label: String, val svg: String?, val actionKey: String)

    private val OPTIONS = listOf(
        Option(1, "Carregar ficheiro", "attachment", "file"),
        Option(2, "Abrir câmera", "camera", "camera"),
        Option(3, "Carregar imagem", "image", "image"),
        Option(4, "Pensar mais", "speedometer", "think")
    )

    private const val THINK_INIT_KEY = "think_mode_toggle_initialized_v1"
    private val ACTIVE_THINK_FALLBACK = Color.parseColor("#0F6CBD")

    fun isThinkEnabled(context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences("appao", android.content.Context.MODE_PRIVATE)
        // Clear the old behavior which permanently enabled Think by launching it as an app.
        if (!prefs.getBoolean(THINK_INIT_KEY, false)) {
            prefs.edit().putBoolean("think_mode", false).putBoolean(THINK_INIT_KEY, true).apply()
        }
        return prefs.getBoolean("think_mode", false)
    }

    fun toggleThink(context: android.content.Context): Boolean {
        val prefs = context.getSharedPreferences("appao", android.content.Context.MODE_PRIVATE)
        val active = !isThinkEnabled(context)
        prefs.edit().putBoolean("think_mode", active).apply()
        return active
    }

    private fun activeThinkColor(context: android.content.Context): Int {
        val selected = AccentColorManager.resolvedColor(context) ?: ACTIVE_THINK_FALLBACK
        return if (selected == Color.BLACK || selected == Color.WHITE) ACTIVE_THINK_FALLBACK else selected
    }

    fun show(anchor: View, onPick: (String) -> Unit): Dialog {
        val thinkActive = isThinkEnabled(anchor.context)
        val thinkColor = activeThinkColor(anchor.context)
        return NativePopupMenu.show(
            anchor,
            OPTIONS.map { option ->
                val isThink = option.actionKey == "think"
                NativePopupMenu.Item(
                    option.id,
                    option.label,
                    svg = option.svg,
                    checked = isThink && thinkActive,
                    highlightColor = if (isThink && thinkActive) thinkColor else null
                )
            }
        ) { item ->
            OPTIONS.firstOrNull { it.id == item.id }?.let { onPick(it.actionKey) }
        }
    }
}
