package com.appao.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.appao.FallbackIconDrawable
import com.appao.R
import com.caverock.androidsvg.SVG
import java.util.Locale

/**
 * Uses Google's official Material Symbols Rounded SVG assets. The YAML build step downloads
 * the pinned icon set before Gradle runs. Names such as home_filled use Google's fill1 SVGs.
 */
object IconLoader {
    private data class Symbol(val file: String, val filled: Boolean = false)
    private val sourceCache = LruCache<String, String>(192)

    private val symbols: Map<String, Symbol> = buildMap {
        fun add(symbol: String, vararg aliases: String) {
            aliases.forEach { put(norm(it), Symbol(symbol)) }
        }
        fun filled(symbol: String, vararg aliases: String) {
            aliases.forEach { put(norm(it), Symbol(symbol, true)) }
        }
        add("menu", "menu", "menu_line", "menu-outline")
        add("more_vert", "more_vert", "ellipsis_vertical", "more_vertical")
        add("more_horiz", "more_horizontal", "more_horiz", "ellipsis_horizontal")
        add("arrow_back", "arrow_left", "back", "arrow_back", "arrow_back_outline")
        add("arrow_forward", "arrow_right", "forward", "arrow_forward")
        add("arrow_upward", "arrow_up", "arrow_upward")
        add("arrow_downward", "arrow_down", "arrow_downward")
        add("keyboard_arrow_down", "arrow_down2", "chevron_down", "keyboard_arrow_down")
        add("keyboard_arrow_right", "chevron_right", "arrow_right_small")
        add("open_in_new", "arrow_top_right2", "arrow_up_right", "external_link", "open_outline", "open_in_new")
        add("add", "plus", "add", "add_outline")
        add("add_circle", "plus_circle", "add_circle")
        add("search", "search", "search_line", "search_outline")
        add("home", "home", "home_outline")
        filled("home", "home_filled", "home_fill", "home_selected")
        add("apps", "apps", "apps_line", "apps_outline", "apps-outline")
        filled("apps", "apps_filled", "apps_fill", "apps_selected")
        add("explore", "compass", "compass_line", "compass_outline", "explore")
        filled("explore", "compass_filled", "compass_fill", "compass_selected", "explore_filled")
        add("person", "profile", "user", "person", "person_outline", "person-outline", "person_circle_mini")
        filled("person", "profile_filled", "person_filled", "profile_selected", "person_fill")
        add("account_circle", "person_circle", "account_circle", "account_circle_outline")
        filled("account_circle", "account_circle_filled")
        add("local_fire_department", "flame", "fire", "fire_line", "local_fire_department")
        add("auto_awesome", "sparkles", "sparkling_2_line", "auto_awesome")
        add("edit", "write", "edit", "pencil", "edit_square", "create_outline")
        add("settings", "settings", "settings_line", "settings_outline")
        add("contrast", "contrast", "contrast_outline")
        add("language", "language", "translate", "language_outline")
        add("public", "global", "globe", "browser", "public", "earth")
        add("location_on", "location", "map_pin", "location_on", "location_outline")
        add("filter_list", "filters", "filter", "filter_list", "funnel")
        add("tune", "filter_settings", "options", "options_outline", "settings_list", "tune")
        add("photo_camera", "camera", "camera_outline", "photo_camera")
        add("folder", "folder", "folder_outline")
        add("folder_zip", "folder_zip", "download_folder")
        add("archive", "archive", "archive_box", "archive_outline")
        add("bookmark", "bookmark", "bookmark_outline")
        filled("bookmark", "bookmark_filled", "bookmark_fill", "bookmark_selected")
        add("favorite", "heart", "heart_line", "favorite_outline")
        filled("favorite", "heart_filled", "heart_fill", "heart_filled_color", "favorite_filled")
        add("pie_chart", "chart_pie", "pie_chart")
        add("bar_chart", "chart_bars", "bar_chart")
        add("check", "check_mark", "check", "checkmark_outline")
        add("check_circle", "check_mark_circle", "check_circle", "checkmark_circle_outline")
        add("cancel", "close_circle_mini", "cancel")
        add("close", "close", "close_outline")
        add("link", "link", "link_outline")
        add("share", "share", "share_social", "share_social_outline")
        add("refresh", "refresh", "refresh_outline")
        add("delete", "trash_bin", "delete_bin", "clear", "delete", "trash_outline")
        add("description", "file_text", "terms", "description", "document_text_outline")
        add("article", "file_list", "article", "reader_outline")
        add("newspaper", "newspaper", "newspaper_outline")
        add("speed", "speedometer", "speed", "speedometer_outline")
        add("lock", "locked", "lock", "lock_closed_outline")
        add("lock_open", "no_locked", "lock_open", "lock_open_outline")
        add("image", "image", "image_outline")
        add("attach_file", "attachment", "attach", "attachment_outline")
        add("light_mode", "sun", "light_mode", "sunny_outline")
        add("dark_mode", "moon", "dark_mode", "moon_outline")
        add("verified_user", "shield_check", "verified_user", "shield_checkmark_outline")
        add("download", "download", "download_2", "downloads", "download_outline")
        add("zoom_in", "zoom_in", "expand_outline")
        add("zoom_out", "zoom_out", "contract_outline")
        add("undo", "arrow_go_back", "arrow_undo", "undo")
        add("redo", "arrow_go_forward", "arrow_redo", "redo")
        add("history", "history", "history_line", "time", "time_outline")
        add("chat_bubble", "chat", "chatbubble", "chatbubble_outline")
        add("forum", "chat_history", "chat_history", "chatbubbles_outline", "forum")
        add("rocket_launch", "rocket", "rocket_launch")
        add("notifications", "notification", "notifications", "notifications_outline")
        add("desktop_windows", "desktop", "desktop_windows", "desktop_outline")
        add("key", "key", "key_outline")
        add("visibility", "eye", "visibility", "eye_outline")
        add("visibility_off", "eye_off", "visibility_off", "eye_off_outline")
        add("wifi", "wifi", "wifi_outline")
        add("calendar_month", "calendar", "calendar_month", "calendar_outline")
        add("content_copy", "copy", "content_copy", "copy_outline")
        add("mail", "mail", "mail_outline")
        add("north_east", "north_east")
        add("bluetooth", "bluetooth")
        add("send", "send", "paper_plane", "paper_plane_outline")
        add("swap_vert", "swap_vert", "sort")
        add("translate", "translate")
        // Explicit aliases used by existing XML/Kotlin callers.
        add("arrow_back", "arrow_left_outline", "arrow_back_outline")
        add("description", "document_text", "document_text_outline")
        add("public", "globe_outline")
    }

    // Keep the exact legacy vector resource name for callers not yet represented in the symbol map.
    private val legacyAliases = mapOf(
        "brand-google" to "logo-google", "logo-google" to "logo-google",
        "brand-brave" to "search-brave", "search-google" to "search-google",
        "search-bing" to "search-bing", "search-duckduckgo" to "search-duckduckgo",
        "search-brave" to "search-brave"
    )

    private fun norm(name: String): String = name.lowercase(Locale.ROOT).trim().replace('-', '_').replace(' ', '_')

    private fun symbolFor(name: String): Symbol? {
        val normalized = norm(name)
        symbols[normalized]?.let { return it }
        // Normalise older Ionicons-style name strings into the equivalent Rounded symbol.
        val withoutOutline = normalized.removeSuffix("_outline").removeSuffix("_line")
        symbols[withoutOutline]?.let { return it }
        return null
    }

    fun clearMemory() = sourceCache.evictAll()

    private fun source(context: Context, name: String, tint: Int): String? {
        val symbol = symbolFor(name) ?: return null
        val key = "${symbol.file}|${if (symbol.filled) 1 else 0}|${tint.toUInt().toString(16)}"
        sourceCache.get(key)?.let { return it }
        val filename = "${symbol.file}${if (symbol.filled) "_fill1" else ""}_24px.svg"
        val raw = runCatching {
            context.assets.open("icons/material-symbols-rounded/$filename").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull() ?: return null
        // Material Symbols SVG paths default to black. Store the requested tint in the official path
        // fill so ImageView's layout size is the only geometry deciding their apparent size.
        val hex = String.format(Locale.US, "#%06X", 0xFFFFFF and tint)
        val colored = Regex("<path\\b(?![^>]*\\bfill=)").replace(raw) { "<path fill=\"$hex\"" }
        sourceCache.put(key, colored)
        return colored
    }

    private fun drawable(context: Context, name: String, tint: Int): Drawable? {
        val material = source(context, name, tint)
        if (material != null) return runCatching {
            RoundedSvgDrawable(material, context.resources.displayMetrics.density)
        }.getOrNull()

        val normalized = norm(name)
        val resourceCandidate = legacyAliases[normalized] ?: normalized.replace('_', '-')
        val drawableName = "ic_${resourceCandidate.replace('-', '_')}"
        val drawableId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
        if (drawableId != 0) {
            val supplied = ContextCompat.getDrawable(context, drawableId)?.mutate() ?: return null
            val keepColor = resourceCandidate.startsWith("brand-") || resourceCandidate.startsWith("search-") || resourceCandidate == "logo-google"
            if (!keepColor) supplied.setTint(tint)
            return supplied
        }
        return null
    }

    fun menuDrawable(context: Context, name: String, tintRes: Int = R.color.iconTint): Drawable? =
        drawable(context, name, ContextCompat.getColor(context, tintRes))?.mutate()

    fun iconBitmap(context: Context, name: String, sizePx: Int, strokeScale: Float = 1f): Bitmap? {
        val size = sizePx.coerceAtLeast(1)
        val icon = drawable(context, name, ContextCompat.getColor(context, R.color.iconTint)) ?: return null
        return runCatching {
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
                icon.setBounds(0, 0, size, size)
                icon.draw(Canvas(bitmap))
            }
        }.getOrNull()
    }

    fun applyIcon(view: ImageView, name: String, tintRes: Int = 0) {
        val tint = ContextCompat.getColor(view.context, if (tintRes != 0) tintRes else R.color.iconTint)
        val icon = drawable(view.context, name, tint)
        if (icon != null) {
            val size = resolveSize(view)
            icon.setBounds(0, 0, size, size)
            view.setImageDrawable(icon)
            view.clearColorFilter()
        } else {
            view.clearColorFilter()
            view.setImageDrawable(FallbackIconDrawable(norm(name), tint))
        }
    }

    fun applyIconThin(view: ImageView, name: String, tintRes: Int = 0) {
        applyIcon(view, name, tintRes)
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun resolveSize(view: ImageView): Int {
        // Match the ImageView's real declared size. Layout definitions are brought to the standard
        // 24dp Material glyph size where the surrounding component can accommodate it.
        val fallback = (24f * view.resources.displayMetrics.density + 0.5f).toInt().coerceAtLeast(1)
        val lp = view.layoutParams
        val width = lp?.width?.takeIf { it > 0 } ?: view.width
        val height = lp?.height?.takeIf { it > 0 } ?: view.height
        val available = listOf(width, height).filter { it > 0 }.minOrNull()
        return available ?: fallback
    }

    private class RoundedSvgDrawable(source: String, density: Float) : Drawable() {
        private val svg: SVG = SVG.getFromString(source)
        private val intrinsicSize = (24f * density + 0.5f).toInt().coerceAtLeast(1)
        override fun getIntrinsicWidth(): Int = intrinsicSize
        override fun getIntrinsicHeight(): Int = intrinsicSize
        override fun draw(canvas: Canvas) {
            val bounds = bounds
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            val save = canvas.save()
            svg.renderToCanvas(canvas, RectF(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat()))
            canvas.restoreToCount(save)
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit
        @Deprecated("Deprecated in Java") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}