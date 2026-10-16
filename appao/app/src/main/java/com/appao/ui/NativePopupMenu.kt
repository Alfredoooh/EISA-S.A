package com.appao

import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import androidx.core.content.ContextCompat

/** One shared Android PopupMenu implementation for consistent native menus. */
object NativePopupMenu {
    data class Item(
        val id: Int,
        val title: String,
        val iconName: String? = null,
        val iconColor: Int? = null,
        val checked: Boolean = false,
        val iconResId: Int? = null
    )

    @Suppress("UNUSED_PARAMETER")
    fun show(
        anchor: View,
        items: List<Item>,
        alignLeft: Boolean = false,
        placeAbove: Boolean = true,
        onClick: (Item) -> Unit
    ): PopupMenu {
        val context = anchor.context
        val popup = PopupMenu(context, anchor, if (alignLeft) Gravity.START else Gravity.END)
        items.forEachIndexed { index, item ->
            // Unique groups allow Android's own menu implementation to draw its
            // standard thin divider between menu rows on API 28+.
            val menuItem = popup.menu.add(index + 1, item.id, index, item.title)
            if (item.checked) {
                menuItem.isCheckable = true
                menuItem.isChecked = true
            }
            val icon = item.iconResId?.let { ContextCompat.getDrawable(context, it)?.mutate() }
                ?: item.iconName?.let { IconLoader.menuDrawable(context, it) }
            if (icon != null) {
                val size = (16f * context.resources.displayMetrics.density + 0.5f).toInt().coerceAtLeast(1)
                icon.setBounds(0, 0, size, size)
                if (item.iconResId == null && item.iconColor != null) icon.setTint(item.iconColor)
                menuItem.icon = icon
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) popup.menu.setGroupDividerEnabled(true)

        // Android hides menu icons in PopupMenu by default. Ask the platform
        // helper to reveal the supplied 16dp icons when that implementation exists.
        runCatching {
            val field = PopupMenu::class.java.getDeclaredField("mPopup")
            field.isAccessible = true
            val helper = field.get(popup)
            helper.javaClass.getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                .invoke(helper, true)
        }
        popup.setOnMenuItemClickListener { selected ->
            items.firstOrNull { it.id == selected.itemId }?.let(onClick)
            true
        }
        popup.show()
        return popup
    }
}
