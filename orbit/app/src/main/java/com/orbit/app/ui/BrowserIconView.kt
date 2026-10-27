package com.orbit.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Picture
import android.view.View
import com.caverock.androidsvg.SVG
import com.orbit.app.icons.`interface`.ICON_ADD_SVG
import com.orbit.app.icons.`interface`.ICON_BACK_SVG
import com.orbit.app.icons.`interface`.ICON_CLOSE_SVG
import com.orbit.app.icons.`interface`.ICON_COPY_SVG
import com.orbit.app.icons.`interface`.ICON_DESKTOP_SVG
import com.orbit.app.icons.`interface`.ICON_FORWARD_SVG
import com.orbit.app.icons.`interface`.ICON_GLOBE_SVG
import com.orbit.app.icons.`interface`.ICON_HISTORY_SVG
import com.orbit.app.icons.`interface`.ICON_HOME_SVG
import com.orbit.app.icons.`interface`.ICON_LOCK_SVG
import com.orbit.app.icons.`interface`.ICON_MENU_SVG
import com.orbit.app.icons.`interface`.ICON_RELOAD_SVG
import com.orbit.app.icons.`interface`.ICON_SEARCH_SVG
import com.orbit.app.icons.`interface`.ICON_SHARE_SVG
import com.orbit.app.icons.`interface`.ICON_TABS_SVG

/**
 * Renders real <svg> markup (parsed via AndroidSVG) instead of hand-drawn
 * Canvas paths. Each icon's source lives in icons/interface/<name>.kt.
 * Public API is unchanged: BrowserIconView(context, "globe").
 */
class BrowserIconView(context: Context, private val iconName: String) : View(context) {
    var tint: Int = 0xFF3C4043.toInt()
        set(value) {
            field = value
            picture = buildPicture()
            invalidate()
        }

    private var picture: Picture? = null

    init {
        picture = buildPicture()
    }

    private fun rawSvgFor(name: String): String? = when (name) {
        "back" -> ICON_BACK_SVG
        "forward" -> ICON_FORWARD_SVG
        "menu" -> ICON_MENU_SVG
        "lock" -> ICON_LOCK_SVG
        "search" -> ICON_SEARCH_SVG
        "close" -> ICON_CLOSE_SVG
        "add" -> ICON_ADD_SVG
        "reload" -> ICON_RELOAD_SVG
        "home" -> ICON_HOME_SVG
        "share" -> ICON_SHARE_SVG
        "copy" -> ICON_COPY_SVG
        "desktop" -> ICON_DESKTOP_SVG
        "desktopMode" -> ICON_DESKTOP_SVG
        "globe" -> ICON_GLOBE_SVG
        "history" -> ICON_HISTORY_SVG
        "tabs" -> ICON_TABS_SVG
        else -> null
    }

    private fun buildPicture(): Picture? {
        val raw = rawSvgFor(iconName) ?: return null

        val tinted = raw.replace(
            "stroke=\"#000000\"",
            "stroke=\"#%06X\"".format(0xFFFFFF and tint),
        )

        val svg = SVG.getFromString(tinted)
        svg.setDocumentWidth("24")
        svg.setDocumentHeight("24")

        return svg.renderToPicture(24, 24)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val pic = picture ?: return
        val side = minOf(width, height)
        if (side <= 0) return

        val save = canvas.save()
        canvas.translate((width - side) / 2f, (height - side) / 2f)
        canvas.scale(side / 24f, side / 24f)
        canvas.drawPicture(pic)
        canvas.restoreToCount(save)
    }
}