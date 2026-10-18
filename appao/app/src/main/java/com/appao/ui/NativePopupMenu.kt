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
        val usesSelectionIndicator = items.any { it.checked }
        val primaryColor = ContextCompat.getColor(context, R.color.pri).let {
            runCatching { AccentColorManager.resolvedOr(context, R.color.pri) }.getOrDefault(it)
        }
        items.forEachIndexed { index, item ->
            // All items share group 0 so Android does not draw a divider between
            // every single row (which made each item look like an isolated block).
            val menuItem = popup.menu.add(0, item.id, index, item.title)
            val baseIcon = item.iconResId?.let { ContextCompat.getDrawable(context, it)?.mutate() }
                ?: item.iconName?.let { IconLoader.menuDrawable(context, it) }
            val icon = if (usesSelectionIndicator && item.checked) {
                IconLoader.menuDrawable(context, "check_circle")?.mutate()?.apply { setTint(primaryColor) }
                    ?: baseIcon
            } else baseIcon
            if (icon != null) {
                val size = (19f * context.resources.displayMetrics.density + 0.5f).toInt().coerceAtLeast(1)
                icon.setBounds(0, 0, size, size)
                if (!(usesSelectionIndicator && item.checked) && item.iconResId == null && item.iconColor != null) icon.setTint(item.iconColor)
                menuItem.icon = icon
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) popup.menu.setGroupDividerEnabled(false)

        // Android hides menu icons in PopupMenu by default. Ask the platform
        // helper to reveal the supplied 19dp icons when that implementation exists.
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