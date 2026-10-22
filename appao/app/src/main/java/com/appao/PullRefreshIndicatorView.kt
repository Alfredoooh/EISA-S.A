package com.appao

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Small rotating ring used as an inline page-load spinner (overlaid on the browser favicon).
 *
 * Draws a 270° arc that advances clockwise as [rotationDegrees] increases from 0 to 360,
 * matching the direction driven by the caller's ValueAnimator (0f -> 360f, linear).
 */
class PullRefreshIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * Current rotation in degrees. The caller keeps an infinite ValueAnimator running
     * with `ofFloat(0f, 360f)` so the arc completes one full clockwise revolution every
     * cycle. Increasing this value moves the arc clockwise by Android Canvas convention
     * (positive angles advance clockwise).
     */
    var rotationDegrees: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.pri)
    }

    private val arcBounds = RectF()

    /** Allows the caller to retint the ring for adaptive page surfaces. */
    fun setIndicatorColor(color: Int) {
        if (ringPaint.color != color) {
            ringPaint.color = color
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = ringPaint.strokeWidth / 2f + dp(1f)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= inset * 2f || h <= inset * 2f) return
        arcBounds.set(inset, inset, w - inset, h - inset)

        // drawArc: startAngle 0 = 3 o'clock; positive angles advance clockwise.
        // -90 starts the arc at the top of the circle (12 o'clock). Adding rotationDegrees
        // keeps the visible arc sweeping clockwise as the animator runs 0 -> 360.
        val startAngle = -90f + rotationDegrees
        val sweepAngle = 270f
        canvas.drawArc(arcBounds, startAngle, sweepAngle, false, ringPaint)
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}