package com.appao

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

/** A separate, lightweight saved-bookmark empty state in the same visual family as news empty state. */
class EmptySavedIllustrationDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val accent = AccentColorManager.resolvedOr(context, R.color.pri)
    private val surface = ContextCompat.getColor(context, R.color.card2)
    private val stroke = ContextCompat.getColor(context, R.color.dim)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun d(v: Float) = v * density

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(b.width() / d(72f), b.height() / d(72f))
        paint.style = Paint.Style.FILL
        paint.color = surface
        canvas.drawCircle(d(36f), d(36f), d(32f), paint)
        paint.color = accent
        paint.alpha = 26
        canvas.drawCircle(d(20f), d(18f), d(12f), paint)
        paint.alpha = 255
        paint.color = surface
        canvas.drawRoundRect(RectF(d(16f), d(13f), d(43f), d(56f)), d(5f), d(5f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = d(1.7f)
        paint.color = stroke
        canvas.drawRoundRect(RectF(d(16f), d(13f), d(43f), d(56f)), d(5f), d(5f), paint)
        canvas.drawLine(d(22f), d(24f), d(36f), d(24f), paint)
        canvas.drawLine(d(22f), d(30f), d(34f), d(30f), paint)
        canvas.drawLine(d(22f), d(36f), d(34f), d(36f), paint)
        paint.style = Paint.Style.FILL
        paint.color = accent
        val bookmark = Path().apply {
            moveTo(d(39f), d(34f)); lineTo(d(56f), d(34f)); lineTo(d(56f), d(59f))
            lineTo(d(47.5f), d(53f)); lineTo(d(39f), d(59f)); close()
        }
        canvas.drawPath(bookmark, paint)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}