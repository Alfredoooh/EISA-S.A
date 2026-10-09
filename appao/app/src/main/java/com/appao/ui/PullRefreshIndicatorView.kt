package com.appao

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** Lightweight ring used by the Instagram-style pull-to-refresh gesture. */
class PullRefreshIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.BUTT
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.BUTT
    }

    private val oval = RectF()

    var rotationDegrees: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    init {
        trackPaint.color = ContextCompat.getColor(context, R.color.line)
        accentPaint.color = ContextCompat.getColor(context, R.color.accent)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val inset = trackPaint.strokeWidth * 0.5f
        oval.set(
            inset,
            inset,
            width - inset,
            height - inset
        )

        canvas.drawOval(oval, trackPaint)
        canvas.drawArc(
            oval,
            -90f + rotationDegrees,
            180f,
            false,
            accentPaint
        )
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
