package com.appao.ui

import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.Menu
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.appao.R

/**
 * Shared native Android popup menus. Geometry, elevation, scrolling and dismissal are left
 * to AppCompat/Android rather than drawing a custom popup surface.
 */
object NativePopupMenu {
    data class Item(
        val id: Int,
        val title: String,
        val iconName: String? = null,
        val iconColor: Int? = null,
        val checked: Boolean = false,
        val iconResId: Int? = null,
        val enabled: Boolean = true,
        val checkable: Boolean = false
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
        val popupContext = ContextThemeWrapper(context, R.style.ThemeOverlay_AppAo_PopupMenu)
        val gravity = if (alignLeft) Gravity.START else Gravity.END
        val popup = PopupMenu(popupContext, anchor, gravity, androidx.appcompat.R.attr.popupMenuStyle, R.style.AppAo_PopupMenu)

        items.forEach { item ->
            val menuItem = popup.menu.add(Menu.NONE, item.id, Menu.NONE, item.title)
            menuItem.isEnabled = item.enabled
            if (item.checked || item.checkable) {
                menuItem.isCheckable = true
                menuItem.isChecked = item.checked
            }

            // Full-colour brand assets are passed in iconResId and intentionally never tinted.
            val icon = if (item.iconResId != null) {
                ContextCompat.getDrawable(context, item.iconResId)?.mutate()
            } else {
                item.iconName?.let { IconLoader.menuDrawable(context, it)?.mutate() }
                    ?.also { drawable -> item.iconColor?.let(drawable::setTint) }
            }
            if (icon != null) menuItem.icon = icon
        }

        // AppCompat hides icons in popup menus by default. Force the platform-supported icon
        // column while preserving native rows, fonts, touch targets, popup animation and sizing.
        runCatching { popup.setForceShowIcon(true) }.recoverCatching {
            val field = popup.javaClass.getDeclaredField("mPopup")
            field.isAccessible = true
            val helper = field.get(popup)
            helper.javaClass.getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType!!)
                .invoke(helper, true)
        }

        popup.setOnMenuItemClickListener { selected ->
            val item = items.firstOrNull { it.id == selected.itemId } ?: return@setOnMenuItemClickListener false
            onClick(item)
            true
        }
        popup.show()
        return popup
    }
}