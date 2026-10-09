package com.appao

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

/**
 * Native drawing of the supplied HTML bottombar__container surface.
 * Uses an actual 1.5dp anti-aliased stroke instead of GradientDrawable's
 * integer-pixel stroke so the border geometry stays as close as possible to
 * the CSS definition.
 */
class HtmlComposerBackgroundDrawable(
    context: Context,
    dark: Boolean
) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val rect = RectF()
    private val radius = 24f * density

    init {
        fillPaint.color = if (dark) {
            Color.rgb(26, 30, 35)
        } else {
            Color.rgb(250, 250, 250)
        }
        borderPaint.color = if (dark) {
            Color.TRANSPARENT
        } else {
            Color.rgb(235, 235, 235)
        }
    }

    override fun draw(canvas: Canvas) {
        val halfStroke = borderPaint.strokeWidth * 0.5f
        rect.set(
            bounds.left + halfStroke,
            bounds.top + halfStroke,
            bounds.right - halfStroke,
            bounds.bottom - halfStroke
        )
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        if (borderPaint.color != Color.TRANSPARENT) {
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        borderPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        borderPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
