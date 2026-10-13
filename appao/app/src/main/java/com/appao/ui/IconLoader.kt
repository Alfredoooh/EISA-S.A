package com.appao

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.SVG

/**
 * Loads packaged icons when they exist and always falls back to a lightweight
 * native drawable when the optional asset is not packaged.
 */
object IconLoader {

    private val cache = LruCache<String, Bitmap>(32)

    fun clearMemory() {
        cache.evictAll()
    }

    fun svgBitmap(
        context: Context,
        name: String,
        sizePx: Int,
        strokeScale: Float = 0.82f
    ): Bitmap? {
        val key = "svg:$name:$sizePx:$strokeScale"
        cache.get(key)?.let { return it }

        return try {
            val raw = openSvg(context, name).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            val source = normalizeSvgStrokeWeight(raw, strokeScale)
            val renderSize = (sizePx * 4).coerceAtLeast(96)
            val svg = SVG.getFromString(source)
            svg.setDocumentWidth(renderSize.toFloat())
            svg.setDocumentHeight(renderSize.toFloat())

            val large = Bitmap.createBitmap(
                renderSize,
                renderSize,
                Bitmap.Config.ARGB_8888
            )

            Canvas(large).apply {
                drawFilter = android.graphics.PaintFlagsDrawFilter(0, 3)
                drawPicture(svg.renderToPicture())
            }

            val bmp = Bitmap.createScaledBitmap(large, sizePx, sizePx, true)
            if (large !== bmp) large.recycle()
            cache.put(key, bmp)
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    private fun normalizeSvgStrokeWeight(raw: String, strokeScale: Float): String {
        fun reduce(value: String): String {
            val number = value.toFloatOrNull() ?: return value
            return String.format(java.util.Locale.US, "%.3f", number * strokeScale)
        }

        var out = raw.replace(
            Regex("(stroke-width\\s*=\\s*[\"\\'])([0-9]*\\.?[0-9]+)([\"\\'])")
        ) { match ->
            match.groupValues[1] + reduce(match.groupValues[2]) + match.groupValues[3]
        }
        out = out.replace(
            Regex("(stroke-width\\s*:\\s*)([0-9]*\\.?[0-9]+)")
        ) { match ->
            match.groupValues[1] + reduce(match.groupValues[2])
        }
        return out
    }

    private fun openSvg(
        context: Context,
        name: String
    ): java.io.InputStream {
        require(name.matches(Regex("[a-z0-9_]+"))) { "Icon names must use lowercase letters, digits and underscores: $name" }
        return context.assets.open("icons/svg/$name.svg")
    }

    private fun tintColor(
        view: ImageView,
        tintRes: Int
    ): Int = if (tintRes != 0) {
        ContextCompat.getColor(view.context, tintRes)
    } else {
        ContextCompat.getColor(
            view.context,
            R.color.iconTint
        )
    }

    fun applySvg(
        view: ImageView,
        name: String,
        tintRes: Int = 0
    ) {
        applySvgInternal(view, name, tintRes, 0.82f)
    }

    fun applySvgThin(
        view: ImageView,
        name: String,
        tintRes: Int = 0
    ) {
        applySvgInternal(view, name, tintRes, 0.58f)
    }

    private fun applySvgInternal(
        view: ImageView,
        name: String,
        tintRes: Int,
        strokeScale: Float
    ) {
        val size = resolveSize(view)
        val bmp = svgBitmap(
            view.context,
            name,
            size,
            strokeScale
        )

        if (bmp != null) {
            view.setImageBitmap(bmp)

            if (tintRes != 0) {
                view.setColorFilter(
                    ContextCompat.getColor(
                        view.context,
                        tintRes
                    ),
                    PorterDuff.Mode.SRC_IN
                )
            } else {
                view.clearColorFilter()
            }
        } else {
            view.clearColorFilter()
            view.setImageDrawable(
                FallbackIconDrawable(
                    name,
                    tintColor(view, tintRes)
                )
            )
        }
    }

    private fun resolveSize(
        view: ImageView
    ): Int {
        val lp = view.layoutParams
        val w = if (lp != null && lp.width > 0) {
            lp.width
        } else {
            view.width
        }

        return if (w > 0) w else 96
    }
}
