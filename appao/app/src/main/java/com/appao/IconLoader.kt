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

object IconLoader {

    private val cache = LruCache<String, Bitmap>(32)

    fun clearMemory() { cache.evictAll() }

    fun svgBitmap(context: Context, name: String, sizePx: Int): Bitmap? {
        val key = "svg:$name:$sizePx"
        cache.get(key)?.let { return it }
        return try {
            openSvg(context, name).use { stream ->
                val svg = SVG.getFromInputStream(stream)
                svg.setDocumentWidth(sizePx.toFloat())
                svg.setDocumentHeight(sizePx.toFloat())
                val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                Canvas(bmp).drawPicture(svg.renderToPicture())
                cache.put(key, bmp)
                bmp
            }
        } catch (e: Exception) { null }
    }

    private fun openSvg(context: Context, name: String): java.io.InputStream {
        return try {
            context.assets.open("icons/svg/$name.svg")
        } catch (_: Exception) {
            context.assets.open("icons/svg/${name.replace("-", "_")}.svg")
        }
    }

    fun applySvg(view: ImageView, name: String, tintRes: Int = 0) {
        val size = resolveSize(view)
        val bmp = svgBitmap(view.context, name, size) ?: return
        view.setImageBitmap(bmp)
        if (tintRes != 0) {
            val color = ContextCompat.getColor(view.context, tintRes)
            view.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        } else view.clearColorFilter()
    }

    fun applyPng(view: ImageView, name: String, tintRes: Int = 0) {
        view.clearColorFilter()
        Glide.with(view).load("file:///android_asset/icons/png/$name.png").into(view)
        if (tintRes != 0) {
            view.post {
                view.setColorFilter(ContextCompat.getColor(view.context, tintRes), PorterDuff.Mode.SRC_IN)
            }
        }
    }

    fun applyPngAt(view: ImageView, path: String, tintRes: Int = 0) {
        view.clearColorFilter()
        Glide.with(view).load("file:///android_asset/$path").into(view)
        if (tintRes != 0) {
            view.post {
                view.setColorFilter(ContextCompat.getColor(view.context, tintRes), PorterDuff.Mode.SRC_IN)
            }
        }
    }

    fun drawable(context: Context, name: String, sizePx: Int, tintColor: Int = 0): Drawable? {
        val bmp = svgBitmap(context, name, sizePx) ?: return null
        val d = BitmapDrawable(context.resources, bmp)
        if (tintColor != 0) d.setTint(tintColor)
        return d
    }

    fun loadPngDrawable(context: Context, name: String, sizePx: Int): Drawable? {
        return try {
            val stream = context.assets.open("icons/png/$name.png")
            val bmp = android.graphics.BitmapFactory.decodeStream(stream)
            stream.close()
            val d = BitmapDrawable(context.resources, bmp)
            d.setBounds(0, 0, sizePx, sizePx)
            d
        } catch (e: Exception) { null }
    }

    private fun resolveSize(view: ImageView): Int {
        val lp = view.layoutParams
        val w = if (lp != null && lp.width > 0) lp.width else view.width
        return if (w > 0) w else 96
    }
}
