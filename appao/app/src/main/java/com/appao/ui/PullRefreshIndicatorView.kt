package com.appao

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils

/** Native Canvas gradient ring shared by pull-to-refresh and the browser favicon loader. */
class PullRefreshIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.25f)
        strokeCap = Paint.Cap.ROUND
    }
    private val oval = RectF()
    private var cachedPrimary = Int.MIN_VALUE
    private var cachedWidth = -1
    private var cachedHeight = -1

    var rotationDegrees: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val inset = ringPaint.strokeWidth / 2f + dp(0.4f)
        oval.set(inset, inset, width - inset, height - inset)
        val cx = width / 2f
        val cy = height / 2f
        val primary = AccentColorManager.resolvedOr(context, R.color.pri)
        if (cachedPrimary != primary || cachedWidth != width || cachedHeight != height || ringPaint.shader == null) {
            val light = ColorUtils.blendARGB(primary, android.graphics.Color.WHITE, 0.42f)
            val cool = ColorUtils.blendARGB(primary, android.graphics.Color.rgb(70, 160, 255), 0.48f)
            val deeper = ColorUtils.blendARGB(primary, android.graphics.Color.BLACK, 0.10f)
            ringPaint.shader = SweepGradient(
                cx,
                cy,
                intArrayOf(deeper, primary, light, cool, primary, deeper),
                floatArrayOf(0f, 0.20f, 0.42f, 0.62f, 0.82f, 1f)
            )
            cachedPrimary = primary
            cachedWidth = width
            cachedHeight = height
        }

        val save = canvas.save()
        canvas.rotate(rotationDegrees, cx, cy)
        canvas.drawOval(oval, ringPaint)
        canvas.restoreToCount(save)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
