package com.appao

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import androidx.core.content.ContextCompat
import kotlin.math.min

class UsageChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class ChartType { BAR, PIE }

    var type: ChartType = ChartType.BAR
        set(value) {
            field = value
            invalidate()
        }

    private var values = LongArray(0)
    private var labels = emptyList<String>()
    private var sourceLabels = emptyList<String>()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animationFraction = 1f
    private var animator: ValueAnimator? = null

    fun setBarData(values: LongArray, labels: List<String>) {
        type = ChartType.BAR
        this.values = values.copyOf()
        this.labels = labels.toList()
        animateIn()
    }

    fun setPieData(values: List<Int>, labels: List<String>) {
        type = ChartType.PIE
        this.values = values.map { it.coerceAtLeast(0).toLong() }.toLongArray()
        sourceLabels = labels.toList()
        animateIn()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (type) {
            ChartType.BAR -> drawBars(canvas)
            ChartType.PIE -> drawPie(canvas)
        }
    }

    private fun drawBars(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val text = ContextCompat.getColor(context, R.color.dim)
        val primary = ContextCompat.getColor(context, R.color.pri)
        val line = ContextCompat.getColor(context, R.color.line)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1).toFloat()
        paint.color = line
        val top = dp(14).toFloat()
        val bottom = h - dp(28)
        canvas.drawLine(dp(4).toFloat(), bottom, w - dp(4), bottom, paint)

        val maxValue = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val gap = dp(10).toFloat()
        val slot = (w - gap * (values.size + 1)) / values.size.coerceAtLeast(1)
        val barWidth = min(dp(36).toFloat(), slot.coerceAtLeast(dp(12).toFloat()))

        paint.style = Paint.Style.FILL
        paint.color = primary
        paint.textSize = sp(11f)
        paint.textAlign = Paint.Align.CENTER

        values.forEachIndexed { index, value ->
            val x = gap + index * (slot + gap) + slot / 2f
            val ratio = (value.toFloat() / maxValue.toFloat()) * animationFraction
            val barTop = bottom - (bottom - top) * ratio
            val rect = RectF(x - barWidth / 2f, barTop, x + barWidth / 2f, bottom)
            canvas.drawRoundRect(rect, dp(8).toFloat(), dp(8).toFloat(), paint)
            if (index < labels.size) {
                paint.color = text
                canvas.drawText(labels[index], x, h - dp(8).toFloat(), paint)
                paint.color = primary
            }
        }
    }

    private fun drawPie(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val total = values.sum().coerceAtLeast(1L).toFloat()
        val radius = min(w, h) * 0.30f
        val cx = w * 0.34f
        val cy = h * 0.48f

        val palette = listOf(
            R.color.pri,
            R.color.accent,
            R.color.bubbleBlue,
            R.color.bubbleCyan,
            R.color.authYellow,
            R.color.progressStart,
            R.color.progressEnd,
            R.color.dim
        )

        var start = -90f
        paint.style = Paint.Style.FILL
        values.forEachIndexed { index, value ->
            if (value <= 0L) return@forEachIndexed
            val sweep = 360f * value.toFloat() / total * animationFraction
            paint.color = ContextCompat.getColor(context, palette[index % palette.size])
            canvas.drawArc(
                cx - radius,
                cy - radius,
                cx + radius,
                cy + radius,
                start,
                sweep,
                true,
                paint
            )
            start += sweep
        }

        paint.color = ContextCompat.getColor(context, R.color.bg)
        canvas.drawCircle(cx, cy, radius * 0.75f, paint)

        paint.textSize = sp(11.5f)
        val dotX = w * 0.66f
        val labelX = dotX + dp(12)
        val rightX = w - dp(8)
        var y = dp(28).toFloat()
        sourceLabels.take(values.size).forEachIndexed { index, label ->
            if (values.getOrElse(index) { 0L } <= 0L) return@forEachIndexed

            paint.textAlign = Paint.Align.LEFT
            paint.color = ContextCompat.getColor(context, palette[index % palette.size])
            canvas.drawCircle(dotX, y - dp(4), dp(4).toFloat(), paint)

            val pct = (values[index] * 100f / total).toFloat()
            val pctText = "${pct.toInt()}%"
            paint.color = ContextCompat.getColor(context, R.color.text)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(pctText, rightX, y, paint)

            paint.textAlign = Paint.Align.LEFT
            val labelMaxWidth = (rightX - paint.measureText(pctText) - dp(10) - labelX).coerceAtLeast(dp(30).toFloat())
            val safeLabel = fitText(label, labelMaxWidth, paint)
            canvas.drawText(safeLabel, labelX, y, paint)

            y += dp(24)
            if (y > h - dp(8)) return
        }
    }

    private fun animateIn() {
        animator?.cancel()
        animationFraction = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 760L
            interpolator = Curves.SMOOTH
            addUpdateListener {
                animationFraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }


    private fun fitText(value: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(value) <= maxWidth) return value
        val ellipsis = "…"
        var end = value.length
        while (end > 1 && paint.measureText(value, 0, end) + paint.measureText(ellipsis) > maxWidth) {
            end--
        }
        return if (end <= 1) ellipsis else value.substring(0, end) + ellipsis
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun sp(value: Float): Float =
        value * resources.displayMetrics.scaledDensity
}
