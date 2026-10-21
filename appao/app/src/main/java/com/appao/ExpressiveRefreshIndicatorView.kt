package com.appao

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import com.appao.core.AccentColorManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight native Canvas indicator for pull-to-refresh. Its separate implementation is
 * intentionally independent from PullRefreshIndicatorView, the browser's favicon ring.
 * Seven rounded silhouettes morph into one another, inspired by Material 3 Expressive motion.
 */
class ExpressiveRefreshIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val pointCount = 72
    private val shapeCount = 7
    private val shapeRadii: Array<FloatArray> = Array(shapeCount) { shape ->
        FloatArray(pointCount) { point ->
            val angle = point * 2.0 * PI / pointCount
            radiusFor(shape, angle)
        }
    }
    private var cachedPrimary = Int.MIN_VALUE
    private var palette = intArrayOf(0, 0, 0, 0)

    var rotationDegrees: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    /** Fraction of the pull threshold; used before the refresh actually starts. */
    var pullProgress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1.3f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val primary = AccentColorManager.resolvedOr(context, R.color.pri)
        if (primary != cachedPrimary) {
            palette = intArrayOf(
                primary,
                ColorUtils.blendARGB(primary, android.graphics.Color.WHITE, 0.28f),
                ColorUtils.blendARGB(primary, android.graphics.Color.BLACK, 0.12f),
                ColorUtils.blendARGB(primary, android.graphics.Color.WHITE, 0.55f)
            )
            cachedPrimary = primary
        }

        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val wrappedRotation = ((rotationDegrees % 360f) + 360f) % 360f
        val shapePosition = wrappedRotation / 360f * shapeCount
        val firstShape = shapePosition.toInt().coerceIn(0, shapeCount - 1)
        val secondShape = (firstShape + 1) % shapeCount
        val rawBlend = shapePosition - firstShape
        val blend = rawBlend * rawBlend * (3f - 2f * rawBlend)
        val progressScale = (0.88f + pullProgress.coerceIn(0f, 1f) * 0.12f).coerceIn(0.88f, 1f)
        val radius = size * 0.39f * progressScale

        path.reset()
        for (point in 0 until pointCount) {
            val a = point * 2.0 * PI / pointCount
            val start = shapeRadii[firstShape][point]
            val end = shapeRadii[secondShape][point]
            val r = (start + (end - start) * blend) * radius
            val x = cx + cos(a).toFloat() * r
            val y = cy + sin(a).toFloat() * r
            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        val save = canvas.save()
        canvas.rotate(wrappedRotation * 0.24f, cx, cy)
        paint.color = ColorUtils.blendARGB(palette[firstShape % palette.size], palette[secondShape % palette.size], blend)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
    }

    private fun radiusFor(shape: Int, angle: Double): Float {
        val c = abs(cos(angle))
        val s = abs(sin(angle))
        val value = when (shape) {
            0 -> 1.0
            // Soft square / squircle.
            1 -> (1.0 / Math.pow(Math.pow(c, 4.0) + Math.pow(s, 4.0), 0.25)) / 1.189207115
            // Rounded diamond.
            2 -> 0.82 + 0.18 * cos(4.0 * (angle - PI / 4.0))
            // Rounded five-point silhouette.
            3 -> 0.78 + 0.22 * cos(5.0 * angle)
            // Four-lobed flower.
            4 -> 0.82 + 0.18 * cos(4.0 * angle)
            // Soft three-lobed shape.
            5 -> 0.84 + 0.16 * cos(3.0 * (angle - PI / 2.0))
            // A rounded ellipse.
            else -> 1.0 / kotlin.math.sqrt(c * c + (s * s) / (0.78 * 0.78))
        }
        return value.toFloat().coerceIn(0.52f, 1f)
    }
}
