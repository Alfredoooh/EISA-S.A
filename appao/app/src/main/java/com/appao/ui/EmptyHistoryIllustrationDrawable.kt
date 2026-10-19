package com.appao

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

/** Compact, theme-aware illustration for an empty browsing history. */
class EmptyHistoryIllustrationDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val accent = AccentColorManager.resolvedOr(context, R.color.pri)
    private val surface = ContextCompat.getColor(context, R.color.card2)
    private val line = ContextCompat.getColor(context, R.color.dim)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun d(v: Float) = v * density

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val checkpoint = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(b.width() / d(112f), b.height() / d(100f))
        paint.style = Paint.Style.FILL
        paint.color = surface
        canvas.drawCircle(d(56f), d(48f), d(42f), paint)
        paint.color = accent
        paint.alpha = 30
        canvas.drawCircle(d(77f), d(24f), d(17f), paint)
        paint.alpha = 255
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(d(26f), d(18f), d(70f), d(76f)), d(8f), d(8f), paint)
        paint.color = line
        paint.strokeWidth = d(2.2f)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(d(35f), d(33f), d(60f), d(33f), paint)
        canvas.drawLine(d(35f), d(42f), d(56f), d(42f), paint)
        canvas.drawLine(d(35f), d(51f), d(54f), d(51f), paint)
        paint.color = accent
        canvas.drawCircle(d(72f), d(64f), d(20f), paint)
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = d(2.7f)
        canvas.drawCircle(d(72f), d(64f), d(11f), paint)
        canvas.drawLine(d(72f), d(64f), d(72f), d(57f), paint)
        canvas.drawLine(d(72f), d(64f), d(78f), d(68f), paint)
        paint.style = Paint.Style.FILL
        canvas.restoreToCount(checkpoint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}