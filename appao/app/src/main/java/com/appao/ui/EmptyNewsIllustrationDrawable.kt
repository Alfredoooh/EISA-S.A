package com.appao

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

/** Lightweight native empty-news illustration, independent of remote image assets. */
class EmptyNewsIllustrationDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val accent = AccentColorManager.resolvedOr(context, R.color.pri)
    private val surface = ContextCompat.getColor(context, R.color.card2)
    private val line = ContextCompat.getColor(context, R.color.dim)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun d(value: Float) = value * density
    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() == 0 || b.height() == 0) return
        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(b.width() / d(72f), b.height() / d(72f))
        paint.style = Paint.Style.FILL
        paint.color = surface
        canvas.drawCircle(d(36f), d(36f), d(32f), paint)
        paint.color = accent
        paint.alpha = 28
        canvas.drawCircle(d(52f), d(20f), d(10f), paint)
        paint.alpha = 255
        val paper = RectF(d(19f), d(12f), d(48f), d(55f))
        paint.color = Color.WHITE
        canvas.drawRoundRect(paper, d(5f), d(5f), paint)
        paint.color = line
        paint.alpha = 190
        paint.strokeWidth = d(1.8f)
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0..3) {
            val y = d(24f + i * 7f)
            canvas.drawLine(d(25f), y, d(if (i == 1) 39f else 42f), y, paint)
        }
        paint.alpha = 255
        paint.color = accent
        val badge = RectF(d(37f), d(39f), d(58f), d(60f))
        canvas.drawRoundRect(badge, d(7f), d(7f), paint)
        val luminance = (0.299f * Color.red(accent) + 0.587f * Color.green(accent) + 0.114f * Color.blue(accent)) / 255f
        paint.color = if (luminance > .55f) Color.BLACK else Color.WHITE
        paint.strokeWidth = d(1.8f)
        canvas.drawLine(d(42f), d(46f), d(53f), d(46f), paint)
        canvas.drawLine(d(42f), d(51f), d(50f), d(51f), paint)
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
