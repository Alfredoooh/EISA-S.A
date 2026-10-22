package com.appao.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.ViewGroup
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

    /** Standard Material Symbols Rounded glyph footprint shared throughout the UI. */
    const val UNIVERSAL_ICON_SIZE_DP: Int = 28

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
        add("arrow_back", "arrow_left_outline", "arrow_back_outline")
        add("description", "document_text", "document_text_outline")
        add("public", "globe_outline")
    }

    private val legacyAliases = mapOf(
        "brand-google" to "logo-google", "logo-google" to "logo-google",
        "brand-brave" to "search-brave", "search-google" to "search-google",
        "search-bing" to "search-bing", "search-duckduckgo" to "search-duckduckgo",
        "search-brave" to "search-brave"
    )

    private val fallbackDrawableAliases = mapOf(
        "home" to "home", "home_outline" to "home_outline", "home_filled" to "home",
        "compass" to "compass_outline", "compass_outline" to "compass_outline", "compass_filled" to "compass",
        "apps" to "apps_outline", "apps_outline" to "apps_outline", "apps_filled" to "apps",
        "person" to "person_outline", "person_outline" to "person_outline", "person-outline" to "person_outline",
        "profile" to "person_outline", "profile_filled" to "person_circle_outline",
        "account_circle" to "person_circle_outline", "account_circle_filled" to "person_circle_outline",
        "copy" to "copy_outline", "content_copy" to "copy_outline",
        "arrow_left" to "arrow_back_outline", "back" to "arrow_back_outline", "arrow_back" to "arrow_back_outline",
        "arrow_right" to "arrow_forward_outline", "forward" to "arrow_forward_outline", "arrow_forward" to "arrow_forward_outline",
        "arrow_go_back" to "arrow_undo_outline", "undo" to "arrow_undo_outline",
        "arrow_go_forward" to "arrow_redo_outline", "redo" to "arrow_redo_outline",
        "chevron_down" to "chevron_down_outline", "keyboard_arrow_down" to "chevron_down_outline",
        "chevron_right" to "chevron_right_outline", "keyboard_arrow_right" to "chevron_right_outline", "arrow_right_small" to "chevron_right_outline",
        "open_in_new" to "open_outline", "external_link" to "open_outline",
        "search" to "search_outline", "search_outline" to "search_outline",
        "settings" to "settings_outline", "options" to "options_outline", "tune" to "options_outline",
        "shield_check" to "shield_checkmark_outline", "verified_user" to "shield_checkmark_outline",
        "download" to "download_outline", "downloads" to "download_outline",
        "history" to "time_outline", "time" to "time_outline",
        "delete_bin" to "trash_outline", "delete" to "trash_outline", "trash_bin" to "trash_outline",
        "desktop" to "desktop_outline", "desktop_windows" to "desktop_outline",
        "moon" to "moon_outline", "dark_mode" to "moon_outline", "sun" to "sunny_outline",
        "contrast" to "contrast_outline", "share" to "share_social_outline", "share_social" to "share_social_outline",
        "language" to "language_outline", "global" to "globe_outline", "public" to "globe_outline",
        "mail" to "mail_outline", "camera" to "camera_outline", "bookmark" to "bookmark_outline",
        "heart" to "heart_outline", "favorite" to "heart_outline", "pie_chart" to "pie_chart_outline",
        "bar_chart" to "bar_chart_outline", "chat" to "chatbubble_outline", "chat_bubble" to "chatbubble_outline",
        "forum" to "chatbubbles_outline", "archive" to "archive_outline", "folder" to "folder_outline",
        "eye" to "eye_outline", "eye_off" to "eye_off_outline", "wifi" to "wifi_outline",
        "bluetooth" to "bluetooth_outline", "link" to "link_outline", "newspaper" to "newspaper_outline",
        "article" to "reader_outline", "refresh" to "refresh_outline", "image" to "image_outline",
        "attach_file" to "attach_outline", "calendar" to "calendar_outline", "notifications" to "notifications_outline",
        "flame" to "flame_outline", "sparkles" to "sparkles_outline", "edit" to "create_outline",
        "write" to "create_outline", "plus" to "add_outline", "add" to "add_outline",
        "close" to "close_outline", "cancel" to "close_circle_outline", "check" to "checkmark_outline",
        "check_circle" to "checkmark_circle_outline", "speed" to "speedometer_outline", "rocket" to "speedometer_outline"
    )

    private fun norm(name: String): String = name.lowercase(Locale.ROOT).trim().replace('-', '_').replace(' ', '_')

    private fun symbolFor(name: String): Symbol? {
        val normalized = norm(name)
        symbols[normalized]?.let { return it }
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
        val candidates = listOfNotNull(
            legacyAliases[normalized],
            fallbackDrawableAliases[normalized],
            normalized.replace('_', '-'),
            normalized.removeSuffix("_filled").removeSuffix("_outline").replace('_', '-') + "-outline"
        ).distinct()
        for (resourceCandidate in candidates) {
            val drawableName = "ic_${resourceCandidate.replace('-', '_')}"
            val drawableId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
            if (drawableId == 0) continue
            val supplied = ContextCompat.getDrawable(context, drawableId)?.mutate() ?: continue
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
        applyIconColor(view, name, tint)
    }

    /** Applies an already-resolved ARGB tint (useful for runtime accent colors and night mode). */
    fun applyIconColor(view: ImageView, name: String, tint: Int) {
        standardizeIconView(view)
        val icon = drawable(view.context, name, tint)
        if (icon != null) {
            view.setImageDrawable(icon)
            view.clearColorFilter()
        } else {
            view.clearColorFilter()
            view.setImageDrawable(FallbackIconDrawable(norm(name), tint, view.resources.displayMetrics.density))
        }
    }

    /** One consistent visible glyph size. The old CENTER_INSIDE 24dp cap made icons look tiny. */
    private fun standardizeIconView(view: ImageView) {
        val standardSize = (UNIVERSAL_ICON_SIZE_DP * view.resources.displayMetrics.density + 0.5f).toInt().coerceAtLeast(1)
        view.scaleX = 1f
        view.scaleY = 1f
        view.adjustViewBounds = false
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        view.setPadding(0, 0, 0, 0)
        view.minimumWidth = standardSize
        view.minimumHeight = standardSize
        val params = view.layoutParams
        if (params != null) {
            var changed = false
            // Always force the universal icon size, regardless of whatever size the XML or
            // caller originally declared. Icons must never render smaller OR larger than this.
            if (params.width > 0 && params.width != standardSize) { params.width = standardSize; changed = true }
            if (params.height > 0 && params.height != standardSize) { params.height = standardSize; changed = true }
            if (changed) view.layoutParams = params
        } else {
            // layoutParams is still null (view not yet attached to a parent). Pre-set explicit
            // LayoutParams now so the size is already correct at attach/measure time.
            view.layoutParams = ViewGroup.LayoutParams(standardSize, standardSize)
        }
    }

    fun applyIconThin(view: ImageView, name: String, tintRes: Int = 0) {
        applyIcon(view, name, tintRes)
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private class RoundedSvgDrawable(source: String, density: Float) : Drawable() {
        private val svg: SVG = SVG.getFromString(source)
        private val intrinsicSize = (UNIVERSAL_ICON_SIZE_DP * density + 0.5f).toInt().coerceAtLeast(1)
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