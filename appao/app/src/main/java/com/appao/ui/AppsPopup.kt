package com.appao

import android.graphics.Color
import android.view.View
import android.widget.PopupWindow

/** Native anchored quick-add menu opened from the fixed AI composer. */
object AppsPopup {
    data class Option(val id: Int, val label: String, val svg: String, val actionKey: String)

    private val OPTIONS = listOf(
        Option(1, "Carregar ficheiro", "paperclip", "file"),
        Option(2, "Abrir câmera", "camera", "camera"),
        Option(3, "Carregar imagem", "image-plus", "image"),
        Option(4, "Pensar mais", "brain", "think")
    )

    fun show(anchor: View, onPick: (String) -> Unit): PopupWindow {
        val thinkActive = anchor.context.getSharedPreferences("appao", android.content.Context.MODE_PRIVATE)
            .getBoolean("think_mode", false)
        return NativePopupMenu.show(
            anchor,
            OPTIONS.map { option ->
                val isThink = option.actionKey == "think"
                NativePopupMenu.Item(
                    option.id,
                    option.label,
                    svg = option.svg,
                    checked = isThink && thinkActive,
                    highlightColor = if (isThink && thinkActive) Color.parseColor("#0F6CBD") else null
                )
            }
        ) { item ->
            OPTIONS.firstOrNull { it.id == item.id }?.let { onPick(it.actionKey) }
        }
    }
}
