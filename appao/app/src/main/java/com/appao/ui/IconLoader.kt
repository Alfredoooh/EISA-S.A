package com.appao

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.SVG
import java.util.Locale

object IconLoader {
    private val sourceCache = LruCache<String, String>(96)
    private val aliases: Map<String, String> = mapOf(
        "menu" to "menu-outline", "menu_line" to "menu-outline",
        "more_vert" to "ellipsis-vertical-outline", "more_horizontal" to "ellipsis-horizontal-outline",
        "arrow_left" to "arrow-back-outline", "back" to "arrow-back-outline",
        "arrow_right" to "arrow-forward-outline", "forward" to "arrow-forward-outline",
        "arrow_up" to "arrow-up-outline", "arrow_down" to "arrow-down-outline",
        "arrow_down2" to "chevron-down-outline", "arrow_top_right" to "arrow-up-outline",
        "arrow_top_right2" to "open-outline", "arrow_up_right" to "open-outline",
        "plus" to "add-outline", "add" to "add-outline", "plus_circle" to "add-circle-outline",
        "search" to "search-outline", "search_line" to "search-outline",
        "home" to "home-outline", "home_filled" to "home",
        "apps" to "apps-outline", "apps_line" to "apps-outline", "apps-outline" to "apps-outline",
        "apps_filled" to "apps", "apps_fill" to "apps",
        "compass" to "compass-outline", "compass_line" to "compass-outline",
        "compass_filled" to "compass", "compass_fill" to "compass",
        "flame" to "flame-outline", "fire" to "flame-outline", "fire_line" to "flame-outline",
        "sparkles" to "sparkles-outline", "sparkling_2_line" to "sparkles-outline",
        "write" to "create-outline", "edit" to "create-outline", "pencil" to "create-outline",
        "edit_square" to "create-outline", "settings" to "settings-outline", "settings_line" to "settings-outline",
        "contrast" to "contrast-outline", "language" to "language-outline", "global" to "globe-outline", "globe" to "globe-outline",
        "location" to "location-outline", "map_pin" to "location-outline",
        "filters" to "funnel-outline", "filter" to "funnel-outline", "filter_settings" to "options-outline",
        "camera" to "camera-outline", "folder" to "folder-outline", "archive" to "archive-outline", "archive_box" to "archive-outline",
        "bookmark" to "bookmark-outline", "bookmark_filled" to "bookmark",
        "heart" to "heart-outline", "heart_line" to "heart-outline", "heart_filled" to "heart", "heart_fill" to "heart", "heart_filled_color" to "heart",
        "chart_pie" to "pie-chart-outline", "chart_bars" to "bar-chart-outline",
        "check_mark" to "checkmark-outline", "check" to "checkmark-outline",
        "check_mark_circle" to "checkmark-circle-outline", "check_circle" to "checkmark-circle-outline",
        "close" to "close-outline", "close_circle_mini" to "close-circle-outline",
        "link" to "link-outline", "share" to "share-social-outline", "refresh" to "refresh-outline",
        "trash_bin" to "trash-outline", "delete_bin" to "trash-outline", "clear" to "trash-outline",
        "file_text" to "document-text-outline", "terms" to "document-text-outline", "file_list" to "reader-outline", "article" to "reader-outline", "newspaper" to "newspaper-outline",
        "profile" to "person-outline", "user" to "person-outline", "person-circle" to "person-circle-outline", "account_circle" to "person-circle-outline",
        "speedometer" to "speedometer-outline", "locked" to "lock-closed-outline", "no_locked" to "lock-open-outline",
        "image" to "image-outline", "attachment" to "attach-outline", "translate" to "language-outline",
        "moon" to "moon-outline", "sun" to "sunny-outline", "shield_check" to "shield-checkmark-outline",
        "download" to "download-outline", "download_2" to "download-outline", "download_folder" to "file-tray-full-outline",
        "external_link" to "open-outline", "zoom_in" to "expand-outline", "zoom_out" to "contract-outline",
        "arrow_go_back" to "arrow-undo-outline", "arrow_go_forward" to "arrow-redo-outline",
        "history" to "time-outline", "history_line" to "time-outline", "time" to "time-outline",
        "message_new" to "create-outline", "new_chat" to "create-outline", "chat" to "chatbubble-outline", "chat_history" to "chatbubbles-outline",
        "browser" to "globe-outline", "rocket" to "rocket-outline", "notification" to "notifications-outline",
        "downloads" to "download-outline", "desktop" to "desktop-outline", "key" to "key-outline",
        "eye" to "eye-outline", "eye_off" to "eye-off-outline", "settings_list" to "options-outline",
        "wifi" to "wifi-outline", "calendar" to "calendar-outline", "copy" to "copy-outline",
        "apps_filled_color" to "apps", "compass_line_color" to "compass-outline"
    )

    fun clearMemory() { sourceCache.evictAll() }

    private fun normalizedName(name: String): String = name.lowercase(Locale.ROOT).trim().replace('-', '_')

    private fun source(context: Context, name: String): String? {
        val iconFile = aliases[normalizedName(name)] ?: run {
            val direct = name.lowercase(Locale.ROOT).trim()
            if (direct.matches(Regex("[a-z0-9-]+"))) direct else return null
        }
        sourceCache.get(iconFile)?.let { return it }
        val xml = runCatching {
            context.assets.open("icons/ionicons/$iconFile.svg").bufferedReader(Charsets.UTF_8).use { it.readText() }
                .replace("currentColor", "#${Integer.toHexString(ContextCompat.getColor(context, R.color.iconTint)).takeLast(6)}")
        }.getOrNull() ?: return null
        sourceCache.put(iconFile, xml)
        return xml
    }

    private fun drawable(context: Context, name: String, tint: Int): Drawable? {
        val iconFile = aliases[normalizedName(name)] ?: run {
            val direct = name.lowercase(Locale.ROOT).trim()
            if (direct.matches(Regex("[a-z0-9-]+"))) direct else return null
        }
        val drawableName = "ic_" + iconFile.replace('-', '_')
        val drawableId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
        if (drawableId != 0) {
            val supplied = ContextCompat.getDrawable(context, drawableId)?.mutate() ?: return null
            val preserveColor = iconFile.startsWith("brand-") || iconFile.startsWith("search-") || iconFile == "logo-google" || iconFile == "heart" || iconFile == "compass"
            if (!preserveColor) supplied.setTint(tint)
            return supplied
        }
        val raw = sourceCache.get(iconFile) ?: runCatching {
            context.assets.open("icons/ionicons/$iconFile.svg").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()?.also { sourceCache.put(iconFile, it) } ?: return null
        val colorized = raw
            .replace("currentColor", String.format(Locale.US, "#%06X", 0xFFFFFF and tint))
            .replace("#000000", String.format(Locale.US, "#%06X", 0xFFFFFF and tint))
        return runCatching { IonSvgDrawable(colorized, tint) }.getOrNull()
    }

    fun menuDrawable(context: Context, name: String, tintRes: Int = R.color.iconTint): Drawable? {
        val tint = ContextCompat.getColor(context, tintRes)
        return drawable(context, name, tint)?.mutate()
    }

    fun iconBitmap(context: Context, name: String, sizePx: Int, strokeScale: Float = 1f): Bitmap? {
        val size = sizePx.coerceAtLeast(1)
        val icon = drawable(context, name, ContextCompat.getColor(context, R.color.iconTint)) ?: return null
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, size, size)
        icon.draw(Canvas(bitmap))
        return bitmap
    }

    fun applyIcon(view: ImageView, name: String, tintRes: Int = 0) {
        val tint = if (tintRes != 0) ContextCompat.getColor(view.context, tintRes)
        else ContextCompat.getColor(view.context, R.color.iconTint)
        val icon = drawable(view.context, name, tint)
        if (icon != null) {
            val size = resolveSize(view)
            icon.setBounds(0, 0, size, size)
            view.setImageDrawable(icon)
            view.clearColorFilter()
        } else {
            view.clearColorFilter()
            view.setImageDrawable(FallbackIconDrawable(normalizedName(name), tint))
        }
    }

    fun applyIconThin(view: ImageView, name: String, tintRes: Int = 0) {
        applyIcon(view, name, tintRes)
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun resolveSize(view: ImageView): Int {
        // Fill the ImageView's own declared size (e.g. 19dp containers across
        // Settings, Profile, Categories, browser toolbar) instead of being
        // force-capped at a fixed 16dp regardless of the container size.
        val fallback = (19f * view.resources.displayMetrics.density + 0.5f).toInt().coerceAtLeast(1)
        val lp = view.layoutParams
        val width = lp?.width?.takeIf { it > 0 } ?: view.width
        val height = lp?.height?.takeIf { it > 0 } ?: view.height
        val available = listOf(width, height).filter { it > 0 }.minOrNull()
        return available ?: fallback
    }

    private class IonSvgDrawable(private val source: String, private val tint: Int) : Drawable() {
        private val svg: SVG = SVG.getFromString(source)
        private var drawableAlpha = 255
        private var filter: ColorFilter? = null

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.width() <= 0 || b.height() <= 0) return
            val checkpoint = canvas.save()
            if (drawableAlpha < 255) canvas.saveLayerAlpha(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), drawableAlpha)
            svg.renderToCanvas(canvas, RectF(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()))
            canvas.restoreToCount(checkpoint)
        }

        override fun setAlpha(alpha: Int) { drawableAlpha = alpha.coerceIn(0, 255); invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Java") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}