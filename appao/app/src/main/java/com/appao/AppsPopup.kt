package com.appao

import android.view.View
import android.widget.PopupWindow

/** Quick-add menu opened from the fixed AI composer. */
object AppsPopup {
    data class Option(val label: String, val svg: String, val actionKey: String)

    private val OPTIONS = listOf(
        Option("Carregar ficheiro", "file", "file"),
        Option("Abrir câmera", "camera", "camera"),
        Option("Carregar imagem", "image", "image"),
        Option("Pensar mais", "think", "think")
    )

    fun show(anchor: View, onPick: (String) -> Unit): PopupWindow {
        return HtmlStylePopup.show(
            anchor,
            OPTIONS.map { option ->
                HtmlStylePopup.Item(
                    label = option.label,
                    iconName = option.svg,
                    useSvg = true
                ) { onPick(option.actionKey) }
            },
            placeAbove = true
        )
    }
}
