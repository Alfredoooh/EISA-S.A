package com.appao

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.caverock.androidsvg.SVG

object IconLoader {

    private val cache = LruCache<String, Drawable>(128)

    fun svg(context: Context, name: String, sizePx: Int = 72, tintColor: Int = 0): Drawable? {
        val key = "svg:$name:$sizePx:$tintColor"
        cache.get(key)?.let { return it }

        return try {
            val stream = context.assets.open("icons/svg/$name.svg")
            val svg = SVG.getFromInputStream(stream)
            stream.close()
            svg.setDocumentWidth(sizePx.toFloat())
            svg.setDocumentHeight(sizePx.toFloat())

            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawPicture(svg.renderToPicture())

            val drawable = BitmapDrawable(context.resources, bmp)
            if (tintColor != 0) drawable.setTint(tintColor)

            cache.put(key, drawable)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun applySvg(view: ImageView, name: String, tintRes: Int = 0) {
        val size = resolveSize(view)
        val tint = if (tintRes != 0) ContextCompat.getColor(view.context, tintRes) else 0
        val d = svg(view.context, name, size, tint) ?: return
        view.setImageDrawable(d)
        view.clearColorFilter()
    }

    fun applyPng(view: ImageView, name: String, tintRes: Int = 0) {
        view.clearColorFilter()
        Glide.with(view)
            .load("file:///android_asset/icons/png/$name.png")
            .into(view)
        if (tintRes != 0) {
            view.post { view.setColorFilter(ContextCompat.getColor(view.context, tintRes)) }
        }
    }

    fun applyPngAt(view: ImageView, path: String, tintRes: Int = 0) {
        view.clearColorFilter()
        Glide.with(view)
            .load("file:///android_asset/$path")
            .into(view)
        if (tintRes != 0) {
            view.post { view.setColorFilter(ContextCompat.getColor(view.context, tintRes)) }
        }
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
        return if (w > 0) w else 72
    }
}
