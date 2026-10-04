package com.appao

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
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
        sizePx: Int
    ): Bitmap? {
        val key = "svg:$name:$sizePx"
        cache.get(key)?.let { return it }

        return try {
            openSvg(context, name).use { stream ->
                val svg = SVG.getFromInputStream(stream)
                svg.setDocumentWidth(sizePx.toFloat())
                svg.setDocumentHeight(sizePx.toFloat())

                val bmp = Bitmap.createBitmap(
                    sizePx,
                    sizePx,
                    Bitmap.Config.ARGB_8888
                )

                Canvas(bmp).drawPicture(
                    svg.renderToPicture()
                )

                cache.put(key, bmp)
                bmp
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun openSvg(
        context: Context,
        name: String
    ): java.io.InputStream {
        return try {
            context.assets.open("icons/svg/$name.svg")
        } catch (_: Throwable) {
            context.assets.open(
                "icons/svg/${name.replace("-", "_")}.svg"
            )
        }
    }

    private fun assetExists(
        context: Context,
        path: String
    ): Boolean {
        return try {
            context.assets.open(path).use { }
            true
        } catch (_: Throwable) {
            false
        }
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
        val size = resolveSize(view)
        val bmp = svgBitmap(
            view.context,
            name,
            size
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

    fun applyPng(
        view: ImageView,
        name: String,
        tintRes: Int = 0
    ) {
        view.clearColorFilter()

        val path = "icons/png/$name.png"

        if (!assetExists(view.context, path)) {
            view.setImageDrawable(
                FallbackIconDrawable(
                    name,
                    tintColor(view, tintRes)
                )
            )
            return
        }

        try {
            Glide.with(view)
                .load("file:///android_asset/$path")
                .dontAnimate()
                .into(view)

            if (tintRes != 0) {
                view.setColorFilter(
                    ContextCompat.getColor(
                        view.context,
                        tintRes
                    ),
                    PorterDuff.Mode.SRC_IN
                )
            }
        } catch (_: Throwable) {
            view.setImageDrawable(
                FallbackIconDrawable(
                    name,
                    tintColor(view, tintRes)
                )
            )
        }
    }

    fun applyPngAt(
        view: ImageView,
        path: String,
        tintRes: Int = 0
    ) {
        val normalized = path.removePrefix("/")

        if (!assetExists(view.context, normalized)) {
            view.setImageDrawable(
                FallbackIconDrawable(
                    normalized
                        .substringAfterLast('/')
                        .substringBefore('.'),
                    tintColor(view, tintRes)
                )
            )
            return
        }

        try {
            Glide.with(view)
                .load("file:///android_asset/$normalized")
                .dontAnimate()
                .into(view)

            if (tintRes != 0) {
                view.setColorFilter(
                    ContextCompat.getColor(
                        view.context,
                        tintRes
                    ),
                    PorterDuff.Mode.SRC_IN
                )
            }
        } catch (_: Throwable) {
            view.setImageDrawable(
                FallbackIconDrawable(
                    normalized
                        .substringAfterLast('/')
                        .substringBefore('.'),
                    tintColor(view, tintRes)
                )
            )
        }
    }

    fun drawable(
        context: Context,
        name: String,
        sizePx: Int,
        tintColor: Int = 0
    ): Drawable? {
        val bmp = svgBitmap(
            context,
            name,
            sizePx
        )

        if (bmp != null) {
            return BitmapDrawable(
                context.resources,
                bmp
            ).apply {
                if (tintColor != 0) {
                    setTint(tintColor)
                }
            }
        }

        return FallbackIconDrawable(
            name,
            tintColor.takeIf { it != 0 }
                ?: ContextCompat.getColor(
                    context,
                    R.color.iconTint
                )
        )
    }

    fun loadPngDrawable(
        context: Context,
        name: String,
        sizePx: Int
    ): Drawable? {
        val path = "icons/png/$name.png"

        if (!assetExists(context, path)) {
            return FallbackIconDrawable(
                name,
                ContextCompat.getColor(
                    context,
                    R.color.iconTint
                )
            ).apply {
                setBounds(
                    0,
                    0,
                    sizePx,
                    sizePx
                )
            }
        }

        return try {
            context.assets.open(path).use { stream ->
                val bmp = android.graphics.BitmapFactory
                    .decodeStream(stream)
                    ?: return@use null

                BitmapDrawable(
                    context.resources,
                    bmp
                ).apply {
                    setBounds(
                        0,
                        0,
                        sizePx,
                        sizePx
                    )
                }
            }
        } catch (_: Throwable) {
            FallbackIconDrawable(
                name,
                ContextCompat.getColor(
                    context,
                    R.color.iconTint
                )
            ).apply {
                setBounds(
                    0,
                    0,
                    sizePx,
                    sizePx
                )
            }
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
