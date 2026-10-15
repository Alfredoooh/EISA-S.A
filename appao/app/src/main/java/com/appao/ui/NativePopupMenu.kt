package com.appao

import android.view.View
import android.app.Dialog

/** Common floating menu entry point. Popups are anchored above the triggering control. */
object NativePopupMenu {
    data class Item(
        val id: Int,
        val title: String,
        val iconName: String? = null,
        val iconColor: Int? = null,
        val checked: Boolean = false,
        val highlightColor: Int? = null
    )

    fun show(
        anchor: View,
        items: List<Item>,
        alignLeft: Boolean = false,
        placeAbove: Boolean = true,
        onClick: (Item) -> Unit
    ): Dialog {
        val popupItems = items.map { item ->
            HtmlStylePopup.Item(
                label = item.title,
                iconName = item.iconName,
                checked = item.checked,
                iconColor = item.iconColor,
                highlightColor = item.highlightColor,
                onClick = { onClick(item) }
            )
        }
        return HtmlStylePopup.show(
            anchor = anchor,
            items = popupItems,
            placeAbove = placeAbove,
            alignLeft = alignLeft
        )
    }
}
