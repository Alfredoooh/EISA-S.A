package com.appao

import android.view.View
import android.widget.PopupWindow

/** Native anchored quick-add menu opened from the fixed AI composer. */
object AppsPopup {
    data class Option(val id: Int, val label: String, val svg: String, val actionKey: String)

    private val OPTIONS = listOf(
        Option(1, "Carregar ficheiro", "attach_file", "file"),
        Option(2, "Abrir câmera", "attach_camera", "camera"),
        Option(3, "Carregar imagem", "attach_image", "image"),
        Option(4, "Pensar mais", "action_deep_think", "think")
    )

    fun show(anchor: View, onPick: (String) -> Unit): PopupWindow {
        return NativePopupMenu.show(
            anchor,
            OPTIONS.map { NativePopupMenu.Item(it.id, it.label, svg = it.svg) }
        ) { item ->
            OPTIONS.firstOrNull { it.id == item.id }?.let { onPick(it.actionKey) }
        }
    }
}
