package com.appao

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu

/** Native anchored AppCompat popup menu with rounded app surface and asset icons. */
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
    ): PopupMenu {
        val themed = ContextThemeWrapper(anchor.context, R.style.Theme_AppAo)
        val popup = PopupMenu(themed, anchor, android.view.Gravity.NO_GRAVITY)

        items.forEach { item ->
            val menuItem = popup.menu.add(0, item.id, item.id, item.title)
            val icon = item.svg?.let { loadSvg(themed, it) }
                ?: item.png?.let { loadPng(themed, it) }
            if (icon != null) menuItem.icon = icon
        }

        forceShowIcons(popup)
        popup.setOnMenuItemClickListener { menuItem: MenuItem ->
            items.firstOrNull { it.id == menuItem.itemId }?.let {
                onClick(it)
                true
            } ?: false
        }
        popup.setOnDismissListener { }
        popup.show()
        return popup
    }

    private fun loadSvg(context: Context, name: String): Drawable? =
        IconLoader.drawable(
            context,
            name,
            (24f * context.resources.displayMetrics.density + 0.5f).toInt(),
            androidx.core.content.ContextCompat.getColor(context, R.color.iconTint)
        )

    private fun loadPng(context: Context, name: String): Drawable? =
        IconLoader.loadPngDrawableAt(
            context,
            name,
            (24f * context.resources.displayMetrics.density + 0.5f).toInt()
        )

    private fun forceShowIcons(popup: PopupMenu) {
        try {
            val field = PopupMenu::class.java.getDeclaredField("mPopup")
            field.isAccessible = true
            val helper = field.get(popup)
            val method = helper.javaClass.getDeclaredMethod(
                "setForceShowIcon",
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(helper, true)
        } catch (_: Throwable) {
        }
    }
}
