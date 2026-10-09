package com.appao

import android.view.View
import android.widget.PopupWindow

/** Common popup entry point. Uses a measured anchored popup with safe screen-edge insets. */
object NativePopupMenu {
    data class Item(
        val id: Int,
        val title: String,
        val svg: String? = null,
        val png: String? = null
    )

    fun show(
        anchor: View,
        items: List<Item>,
        onClick: (Item) -> Unit
    ): PopupWindow {
        val popupItems = items.map { item ->
            HtmlStylePopup.Item(
                label = item.title,
                iconName = item.svg ?: item.png,
                useSvg = item.svg != null,
                onClick = { onClick(item) }
            )
        }
        return HtmlStylePopup.show(anchor, popupItems)
    }
}
