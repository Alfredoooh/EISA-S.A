package com.appao

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Renders UI icons from the official Remix Icon font at the view's actual bounds.
 * The font is downloaded from the pinned official Remix Icon release by the Gradle
 * preBuild task; no SVG or raster icon assets are used for app UI.
 */
object IconLoader {
    private val bitmapCache = LruCache<String, Bitmap>(48)
    @Volatile private var cachedTypeface: Typeface? = null

    private val glyphs: Map<String, Int> = mapOf(
        "menu" to 0xEF3E, "more_vert" to 0xEF77, "more_horizontal" to 0xEF79,
        "arrow_left" to 0xEA60, "arrow_right" to 0xEA6C, "arrow_up" to 0xEA76,
        "arrow_down" to 0xEA4C, "arrow_down2" to 0xEA4E,
        "arrow_top_right" to 0xEA70, "arrow_top_right2" to 0xECAF,
        "plus" to 0xEA13, "add" to 0xEA13, "search" to 0xF0D1,
        "search_line" to 0xF0D1, "home" to 0xEE2B, "home_filled" to 0xEE26,
        "apps" to 0xEA44, "apps_filled" to 0xEA43,
        "compass" to 0xEBBE, "compass_filled" to 0xEBC3,
        "flame" to 0xED33, "fire" to 0xED33, "sparkles" to 0xF36B,
        "write" to 0xEFE0, "edit" to 0xEFE0, "edit_square" to 0xEC82,
        "settings" to 0xF0E6, "contrast" to 0xEBD4, "language" to 0xEDCF,
        "location" to 0xEF14, "filters" to 0xED27, "filter" to 0xED27,
        "camera" to 0xEB31, "folder" to 0xED6A, "archive" to 0xEA48,
        "bookmark" to 0xEAE5, "heart" to 0xEE0F, "heart_filled" to 0xEE0E,
        "heart_filled_color" to 0xEE0E, "chart_pie" to 0xEFFA,
        "chart_bars" to 0xEA96, "check_mark" to 0xEB7B,
        "check_mark_circle" to 0xEB81, "close" to 0xEB99,
        "close_circle_mini" to 0xEB97, "link" to 0xEEAF,
        "share" to 0xF0FE, "refresh" to 0xF064, "trash_bin" to 0xEC2A, "delete_bin" to 0xEC2A,
        "file_text" to 0xED0F, "profile" to 0xF256, "user" to 0xF264,
        "speedometer" to 0xF371, "locked" to 0xEECE, "no_locked" to 0xEED2,
        "image" to 0xEE4B, "attachment" to 0xEA86,
        "translate" to 0xF226, "moon" to 0xEF75, "sun" to 0xF1BF,
        "shield_check" to 0xF100, "download" to 0xEC5A,
        "download_2" to 0xEC54, "external_link" to 0xECAF,
        "zoom_in" to 0xF2DB, "zoom_out" to 0xF2DD,
        "arrow_go_back" to 0xEA58, "arrow_go_forward" to 0xEA5A,
        "history" to 0xEE17, "message_new" to 0xEB63,
        "new_chat" to 0xEC82, "file_list" to 0xECEF, "terms" to 0xECEF,
        "global" to 0xEDCF, "browser" to 0xEC7A,
        "rocket" to 0xF094, "clear" to 0xEC2A,
        "account_circle" to 0xEA09, "notification" to 0xEF94,
        "eye" to 0xECB5, "eye_off" to 0xECB7,
        "archive_box" to 0xEA48, "pencil" to 0xEFE0,
        "bookmark_filled" to 0xEAE4, "back" to 0xEA60,
        "forward" to 0xEA6C, "check" to 0xEB7B,
        "arrow_up_right" to 0xEA70, "map_pin" to 0xEF14,
        "chat" to 0xEB51, "chat_history" to 0xEB61,
        "download_folder" to 0xED60, "newspaper" to 0xEF8F,
        "article" to 0xEA7E, "settings_list" to 0xEEBD,
        "wifi" to 0xF2A5, "calendar" to 0xEB27,
        "time" to 0xF20F, "globe" to 0xEDD1,
        "check_circle" to 0xEB81, "plus_circle" to 0xEA11,
        "filter_settings" to 0xED25, "menu_line" to 0xEF3E,
        "apps_line" to 0xEA44, "apps_fill" to 0xEA43,
        "compass_line" to 0xEBC4, "compass_fill" to 0xEBC3,
        "heart_line" to 0xEE0F, "heart_fill" to 0xEE0E,
        "history_line" to 0xEE17, "settings_line" to 0xF0E6,
        "fire_line" to 0xED33, "sparkling_2_line" to 0xF36B
    )

    fun clearMemory() {
        bitmapCache.evictAll()
    }

    private fun typeface(context: Context): Typeface? {
        cachedTypeface?.let { return it }
        return runCatching {
            Typeface.createFromAsset(context.assets, "fonts/remixicon.ttf").also {
                cachedTypeface = it
            }
        }.getOrNull()
    }

    private fun normalizedName(name: String): String = name.lowercase(Locale.ROOT)
        .replace('-', '_').trim()

    private fun codepoint(name: String): Int? = glyphs[normalizedName(name)]

    private fun drawable(context: Context, name: String, tint: Int): Drawable? {
        val font = typeface(context) ?: return null
        val cp = codepoint(name) ?: return null
        return RemixGlyphDrawable(font, String(Character.toChars(cp)), tint, normalizedName(name) == "heart_filled_color")
    }

    /** High-quality bitmap variant for compact UI (for example snackbars). */
    fun iconBitmap(context: Context, name: String, sizePx: Int, strokeScale: Float = 1f): Bitmap? {
        val normalized = normalizedName(name)
        val key = "$normalized:$sizePx:$strokeScale"
        bitmapCache.get(key)?.let { return it }
        val glyph = drawable(context, normalized, ContextCompat.getColor(context, R.color.iconTint)) ?: return null
        val scaleSize = (sizePx * 3).coerceAtLeast(72)
        val large = Bitmap.createBitmap(scaleSize, scaleSize, Bitmap.Config.ARGB_8888)
        glyph.setBounds(0, 0, scaleSize, scaleSize)
        Canvas(large).drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        glyph.draw(Canvas(large))
        val result = Bitmap.createScaledBitmap(large, sizePx.coerceAtLeast(1), sizePx.coerceAtLeast(1), true)
        if (result !== large) large.recycle()
        bitmapCache.put(key, result)
        return result
    }

    fun applyIcon(view: ImageView, name: String, tintRes: Int = 0) {
        val tint = if (tintRes != 0) ContextCompat.getColor(view.context, tintRes)
        else ContextCompat.getColor(view.context, R.color.iconTint)
        val normalized = normalizedName(name)
        val icon = drawable(view.context, normalized, tint)
        if (icon != null) {
            icon.setBounds(0, 0, resolveSize(view), resolveSize(view))
            view.setImageDrawable(icon)
            view.clearColorFilter()
        } else {
            view.clearColorFilter()
            view.setImageDrawable(FallbackIconDrawable(normalized, tint))
        }
    }

    /** Remix Icon font outlines are crisp already; this sizing variant is for thin toolbar glyphs. */
    fun applyIconThin(view: ImageView, name: String, tintRes: Int = 0) {
        applyIcon(view, name, tintRes)
        view.scaleX = 0.94f
        view.scaleY = 0.94f
    }

    private fun resolveSize(view: ImageView): Int {
        val lp = view.layoutParams
        val width = lp?.width?.takeIf { it > 0 } ?: view.width
        return width.takeIf { it > 0 } ?: 96
    }

    private class RemixGlyphDrawable(
        private val font: Typeface,
        private val glyph: String,
        private val tint: Int,
        private val gradientHeart: Boolean
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = font
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
            color = tint
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.width() <= 0 || b.height() <= 0) return
            val width = b.width().toFloat()
            val height = b.height().toFloat()
            paint.textSize = minOf(width, height) * 0.92f
            paint.shader = if (gradientHeart) LinearGradient(
                b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                intArrayOf(Color.rgb(255, 70, 105), Color.rgb(244, 58, 100), Color.rgb(255, 135, 93)),
                null, Shader.TileMode.CLAMP
            ) else null
            val fm = paint.fontMetrics
            val baseline = b.exactCenterY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(glyph, b.exactCenterX(), baseline, paint)
            paint.shader = null
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Java") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
