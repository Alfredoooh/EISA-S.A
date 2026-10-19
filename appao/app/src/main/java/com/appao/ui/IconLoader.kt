package com.appao.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.appao.R
import com.caverock.androidsvg.SVG
import java.util.Locale


object IconLoader {
    private val sourceCache = LruCache<String, String>(128)
    private val legacyAliases: Map<String, String> = mapOf(
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
        "swap_vertical" to "swap-vertical-outline", "swap_vertical_outline" to "swap-vertical-outline",
        "history" to "time-outline", "history_line" to "time-outline", "time" to "time-outline",
        "message_new" to "create-outline", "new_chat" to "create-outline", "chat" to "chatbubble-outline", "chat_history" to "chatbubbles-outline",
        "browser" to "globe-outline", "rocket" to "rocket-outline", "notification" to "notifications-outline",
        "downloads" to "download-outline", "desktop" to "desktop-outline", "key" to "key-outline",
        "eye" to "eye-outline", "eye_off" to "eye-off-outline", "settings_list" to "options-outline",
        "wifi" to "wifi-outline", "calendar" to "calendar-outline", "copy" to "copy-outline",
        "apps_filled_color" to "apps", "compass_line_color" to "compass-outline"
    )

    private val materialAliases: Map<String, String> = mapOf(
        "menu" to "menu",
        "menu_line" to "menu",
        "more_vert" to "more_vert",
        "more_horizontal" to "more_horiz",
        "arrow_left" to "arrow_back",
        "back" to "arrow_back",
        "arrow_right" to "arrow_forward",
        "forward" to "arrow_forward",
        "arrow_up" to "arrow_upward",
        "arrow_down" to "arrow_downward",
        "arrow_down2" to "keyboard_arrow_down",
        "arrow_top_right" to "north_east",
        "arrow_top_right2" to "open_in_new",
        "arrow_up_right" to "open_in_new",
        "open_outline" to "open_in_new",
        "open_in_new" to "open_in_new",
        "plus" to "add",
        "add" to "add",
        "plus_circle" to "add_circle",
        "search" to "search",
        "search_line" to "search",
        "home" to "home",
        "home_filled" to "home",
        "apps" to "apps",
        "apps_line" to "apps",
        "apps_outline" to "apps",
        "apps_filled" to "apps",
        "apps_fill" to "apps",
        "apps_filled_color" to "apps",
        "compass" to "explore",
        "compass_line" to "explore",
        "compass_filled" to "explore",
        "compass_fill" to "explore",
        "compass_line_color" to "explore",
        "circle" to "circle",
        "flame" to "local_fire_department",
        "fire" to "local_fire_department",
        "fire_line" to "local_fire_department",
        "sparkles" to "auto_awesome",
        "sparkling_2_line" to "auto_awesome",
        "write" to "edit",
        "edit" to "edit",
        "pencil" to "edit",
        "edit_square" to "edit_square",
        "settings" to "settings",
        "settings_line" to "settings",
        "contrast" to "contrast",
        "language" to "language",
        "global" to "public",
        "globe" to "public",
        "browser" to "public",
        "location" to "location_on",
        "map_pin" to "location_on",
        "filters" to "filter_list",
        "filter" to "filter_list",
        "filter_settings" to "tune",
        "settings_list" to "tune",
        "options" to "tune",
        "options_outline" to "tune",
        "camera" to "camera",
        "folder" to "folder",
        "archive" to "archive",
        "archive_box" to "archive",
        "bookmark" to "bookmark",
        "bookmark_filled" to "bookmark",
        "heart" to "favorite",
        "heart_line" to "favorite",
        "heart_filled" to "favorite",
        "heart_fill" to "favorite",
        "heart_filled_color" to "favorite",
        "chart_pie" to "pie_chart",
        "chart_bars" to "bar_chart",
        "check_mark" to "check",
        "check" to "check",
        "checkmark_outline" to "check",
        "check_mark_circle" to "check_circle",
        "check_circle" to "check_circle",
        "close" to "close",
        "close_circle_mini" to "cancel",
        "link" to "link",
        "share" to "share",
        "share_social_outline" to "share",
        "refresh" to "refresh",
        "trash_bin" to "delete",
        "delete_bin" to "delete",
        "clear" to "delete",
        "file_text" to "description",
        "terms" to "policy",
        "file_list" to "view_list",
        "article" to "article",
        "newspaper" to "newspaper",
        "profile" to "person",
        "user" to "person",
        "person_outline" to "person",
        "person_circle" to "account_circle",
        "account_circle" to "account_circle",
        "speedometer" to "speed",
        "locked" to "lock",
        "no_locked" to "lock_open",
        "image" to "image",
        "attachment" to "attach_file",
        "translate" to "translate",
        "moon" to "dark_mode",
        "sun" to "light_mode",
        "shield_check" to "verified_user",
        "download" to "download",
        "download_2" to "download",
        "downloads" to "download",
        "download_folder" to "folder_zip",
        "external_link" to "open_in_new",
        "zoom_in" to "zoom_in",
        "zoom_out" to "zoom_out",
        "arrow_go_back" to "undo",
        "arrow_go_forward" to "redo",
        "history" to "history",
        "history_line" to "history",
        "time" to "history",
        "message_new" to "edit_square",
        "new_chat" to "edit_square",
        "chat" to "chat_bubble",
        "chatbubble_outline" to "chat_bubble",
        "chat_history" to "forum",
        "rocket" to "rocket_launch",
        "notification" to "notifications",
        "desktop" to "desktop_windows",
        "key" to "key",
        "eye" to "visibility",
        "eye_off" to "visibility_off",
        "wifi" to "wifi",
        "calendar" to "calendar_month",
        "copy" to "content_copy",
        "paper_plane_outline" to "send",
        "mail_outline" to "mail",
        "bluetooth_outline" to "bluetooth",
        "arrow_back_outline" to "arrow_back",
        "arrow_forward_outline" to "arrow_forward",
        "arrow_up_outline" to "arrow_upward",
        "arrow_down_outline" to "arrow_downward",
        "chevron_down_outline" to "keyboard_arrow_down",
        "add_outline" to "add",
        "add_circle_outline" to "add_circle",
        "search_outline" to "search",
        "home_outline" to "home",
        "compass_outline" to "explore",
        "flame_outline" to "local_fire_department",
        "sparkles_outline" to "auto_awesome",
        "create_outline" to "edit",
        "settings_outline" to "settings",
        "globe_outline" to "public",
        "location_outline" to "location_on",
        "funnel_outline" to "filter_list",
        "camera_outline" to "camera",
        "archive_outline" to "archive",
        "bookmark_outline" to "bookmark",
        "heart_outline" to "favorite",
        "pie_chart_outline" to "pie_chart",
        "bar_chart_outline" to "bar_chart",
        "checkmark_circle_outline" to "check_circle",
        "close_outline" to "close",
        "close_circle_outline" to "cancel",
        "link_outline" to "link",
        "refresh_outline" to "refresh",
        "trash_outline" to "delete",
        "document_text_outline" to "description",
        "reader_outline" to "article",
        "newspaper_outline" to "newspaper",
        "person_circle_outline" to "account_circle",
        "speedometer_outline" to "speed",
        "lock_closed_outline" to "lock",
        "lock_open_outline" to "lock_open",
        "image_outline" to "image",
        "attach_outline" to "attach_file",
        "language_outline" to "language",
        "moon_outline" to "dark_mode",
        "sunny_outline" to "light_mode",
        "shield_checkmark_outline" to "verified_user",
        "download_outline" to "download",
        "file_tray_full_outline" to "folder_zip",
        "expand_outline" to "zoom_in",
        "contract_outline" to "zoom_out",
        "arrow_undo_outline" to "undo",
        "arrow_redo_outline" to "redo",
        "swap_vertical" to "swap_vert",
        "swap_vertical_outline" to "swap_vert",
        "time_outline" to "history",
        "chatbubbles_outline" to "forum",
        "notifications_outline" to "notifications",
        "desktop_outline" to "desktop_windows",
        "key_outline" to "key",
        "eye_outline" to "visibility",
        "eye_off_outline" to "visibility_off",
        "wifi_outline" to "wifi",
        "calendar_outline" to "calendar_month",
        "copy_outline" to "content_copy",
        "folder_outline" to "folder",
        "checkmark_circle_green" to "check_circle",
    )

    fun clearMemory() { sourceCache.evictAll() }

    private fun normalizedName(name: String): String = name.lowercase(Locale.ROOT).trim().replace('-', '_')

    private fun readAsset(context: Context, path: String, cacheKey: String): String? {
        sourceCache.get(cacheKey)?.let { return it }
        val raw = runCatching {
            context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull() ?: return null
        sourceCache.put(cacheKey, raw)
        return raw
    }

    private fun source(context: Context, name: String): String? {
        val normalized = normalizedName(name)
        val materialName = materialAliases[normalized]
        if (materialName != null) {
            readAsset(context, "icons/material-symbols-rounded/$materialName.svg", "material:$materialName")?.let { return it }
        }
        val iconFile = legacyAliases[normalized] ?: run {
            val direct = name.lowercase(Locale.ROOT).trim()
            if (direct.matches(Regex("[a-z0-9-]+"))) direct else return null
        }
        return readAsset(context, "icons/ionicons/$iconFile.svg", "ionicons:$iconFile")
    }

    private fun drawable(context: Context, name: String, tint: Int): Drawable? {
        val normalized = normalizedName(name)
        val materialName = materialAliases[normalized]

        // Brand marks and search-engine logos are intentionally kept in their original colors.
        // They are not general UI symbols and must not be replaced by monochrome Material symbols.
        val preserveColor = normalized.startsWith("brand_") || normalized.startsWith("search_") ||
            normalized == "logo_google" || normalized == "heart_filled_color" || normalized == "ad_block"

        fun loadLegacyDrawable(iconFile: String, keepColor: Boolean): Drawable? {
            val drawableName = "ic_" + iconFile.replace('-', '_')
            val drawableId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
            if (drawableId == 0) return null
            val supplied = ContextCompat.getDrawable(context, drawableId)?.mutate() ?: return null
            if (!keepColor) supplied.setTint(tint)
            return supplied
        }

        // If the caller explicitly asked for a branded asset, preserve the supplied vector/PNG.
        if (preserveColor) {
            val customIcon = legacyAliases[normalized] ?: normalized.replace('_', '-')
            loadLegacyDrawable(customIcon, true)?.let { return it }
        }

        // Material Symbols Rounded always has priority over legacy vector drawables. Every symbol
        // uses the official 24 px viewBox and is rendered into the ImageView's exact square bounds.
        if (materialName != null) {
            val rawMaterial = readAsset(
                context,
                "icons/material-symbols-rounded/$materialName.svg",
                "material:$materialName"
            )
            if (rawMaterial != null) return runCatching { IonSvgDrawable(rawMaterial, tint) }.getOrNull()
        }

        val iconFile = legacyAliases[normalized] ?: run {
            val direct = name.lowercase(Locale.ROOT).trim()
            if (direct.matches(Regex("[a-z0-9-]+"))) direct else return null
        }
        loadLegacyDrawable(iconFile, preserveColor)?.let { return it }

        val rawLegacy = readAsset(context, "icons/ionicons/$iconFile.svg", "ionicons:$iconFile") ?: return null
        return runCatching { IonSvgDrawable(rawLegacy, tint) }.getOrNull()
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
        private var filter: ColorFilter? = android.graphics.PorterDuffColorFilter(tint, android.graphics.PorterDuff.Mode.SRC_IN)

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.width() <= 0 || b.height() <= 0) return
            val checkpoint = canvas.save()
            if (filter != null || drawableAlpha < 255) {
                // Render to a layer so drawable/image-view tint is actually applied to SVG paths.
                // Without this layer, Android color filters were silently ignored by toolbar icons.
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = filter
                    alpha = drawableAlpha
                }
                val layer = canvas.saveLayer(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), paint)
                svg.renderToCanvas(canvas, RectF(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()))
                canvas.restoreToCount(layer)
            } else {
                svg.renderToCanvas(canvas, RectF(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()))
            }
            canvas.restoreToCount(checkpoint)
        }

        override fun setAlpha(alpha: Int) { drawableAlpha = alpha.coerceIn(0, 255); invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Java") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
