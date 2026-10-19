package com.appao

import android.view.View
import android.widget.PopupWindow
import com.appao.ui.NativePopupMenu

/** Native anchored quick-add menu opened from the fixed AI composer. */
object AppsPopup {
    data class Option(val id: Int, val label: String, val iconName: String?, val actionKey: String)

    private val OPTIONS = listOf(
        Option(1, "Carregar ficheiro", "attachment", "file"),
        Option(2, "Abrir câmera", "camera", "camera"),
        Option(3, "Carregar imagem", "image", "image"),
        Option(4, "Pensar mais", "speedometer", "think")
    )

    private const val THINK_INIT_KEY = "think_mode_toggle_initialized_v1"

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

    fun show(anchor: View, onPick: (String) -> Unit): PopupWindow {
        val thinkActive = isThinkEnabled(anchor.context)
        return NativePopupMenu.show(
            anchor,
            OPTIONS.map { option ->
                val isThink = option.actionKey == "think"
                NativePopupMenu.Item(
                    option.id,
                    option.label,
                    iconName = option.iconName,
                    checked = isThink && thinkActive
                )
            }
        ) { item ->
            OPTIONS.firstOrNull { it.id == item.id }?.let { onPick(it.actionKey) }
        }
    }
}
