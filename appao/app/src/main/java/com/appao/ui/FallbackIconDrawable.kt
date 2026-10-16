package com.appao

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.Gravity

/** Lightweight vector fallback used only when an optional packaged asset is absent. */
class FallbackIconDrawable(
    private val icon: String,
    private var tint: Int = Color.WHITE
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun draw(canvas: Canvas) {
        paint.color = tint
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = b.left + w / 2f
        val cy = b.top + h / 2f
        val r = minOf(w, h) * 0.28f

        when (icon.lowercase()) {
            "menu" -> {
                canvas.drawLine(cx - r, cy - 4.5f, cx + r, cy - 4.5f, paint)
                canvas.drawLine(cx - r, cy, cx + r, cy, paint)
                canvas.drawLine(cx - r, cy + 4.5f, cx + r, cy + 4.5f, paint)
            }
            "plus" -> {
                canvas.drawLine(cx - r, cy, cx + r, cy, paint)
                canvas.drawLine(cx, cy - r, cx, cy + r, paint)
            }
            "arrow_up" -> {
                canvas.drawLine(cx, cy + r, cx, cy - r, paint)
                canvas.drawLine(cx, cy - r, cx - r * .55f, cy - r * .45f, paint)
                canvas.drawLine(cx, cy - r, cx + r * .55f, cy - r * .45f, paint)
            }
            "arrow_left" -> {
                canvas.drawLine(cx + r * .7f, cy, cx - r * .7f, cy, paint)
                canvas.drawLine(cx - r * .7f, cy, cx - r * .1f, cy - r * .6f, paint)
                canvas.drawLine(cx - r * .7f, cy, cx - r * .1f, cy + r * .6f, paint)
            }
            "arrow_right" -> {
                canvas.drawLine(cx - r * .7f, cy, cx + r * .7f, cy, paint)
                canvas.drawLine(cx + r * .7f, cy, cx + r * .1f, cy - r * .6f, paint)
                canvas.drawLine(cx + r * .7f, cy, cx + r * .1f, cy + r * .6f, paint)
            }
            "close_circle_mini" -> {
                canvas.drawCircle(cx, cy, r * .9f, paint)
                canvas.drawLine(cx - r * .36f, cy - r * .36f, cx + r * .36f, cy + r * .36f, paint)
                canvas.drawLine(cx + r * .36f, cy - r * .36f, cx - r * .36f, cy + r * .36f, paint)
            }
            "compass_filled" -> {
                canvas.drawCircle(cx, cy, r * .94f, paint)
                val needle = Path().apply {
                    moveTo(cx + r * .08f, cy - r * .76f)
                    lineTo(cx + r * .42f, cy + r * .1f)
                    lineTo(cx - r * .08f, cy + r * .02f)
                    lineTo(cx - r * .42f, cy - r * .1f)
                    close()
                }
                val fill = Paint(paint).apply { style = Paint.Style.FILL }
                canvas.drawPath(needle, fill)
                canvas.drawCircle(cx, cy, r * .12f, fill)
            }
            "new_chat" -> {
                val bubble = Path().apply {
                    moveTo(cx - r * .8f, cy - r * .6f)
                    lineTo(cx + r * .62f, cy - r * .6f)
                    quadTo(cx + r * .82f, cy - r * .6f, cx + r * .82f, cy - r * .4f)
                    lineTo(cx + r * .82f, cy + r * .35f)
                    quadTo(cx + r * .82f, cy + r * .55f, cx + r * .62f, cy + r * .55f)
                    lineTo(cx + r * .05f, cy + r * .55f)
                    lineTo(cx - r * .45f, cy + r * .95f)
                    lineTo(cx - r * .35f, cy + r * .55f)
                    lineTo(cx - r * .8f, cy + r * .55f)
                    quadTo(cx - r, cy + r * .55f, cx - r, cy + r * .35f)
                    lineTo(cx - r, cy - r * .4f)
                    quadTo(cx - r, cy - r * .6f, cx - r * .8f, cy - r * .6f)
                    close()
                }
                canvas.drawPath(bubble, paint)
                canvas.drawLine(cx + r * .18f, cy - r * .25f, cx + r * .18f, cy + r * .2f, paint)
                canvas.drawLine(cx - r * .05f, cy - r * .025f, cx + r * .4f, cy - r * .025f, paint)
            }
            "x" -> {
                canvas.drawLine(cx - r * .6f, cy - r * .6f, cx + r * .6f, cy + r * .6f, paint)
                canvas.drawLine(cx + r * .6f, cy - r * .6f, cx - r * .6f, cy + r * .6f, paint)
            }
            "chevron_down" -> {
                canvas.drawLine(cx - r * .55f, cy - r * .2f, cx, cy + r * .38f, paint)
                canvas.drawLine(cx, cy + r * .38f, cx + r * .55f, cy - r * .2f, paint)
            }
            "check" -> {
                canvas.drawLine(cx - r * .75f, cy, cx - r * .15f, cy + r * .55f, paint)
                canvas.drawLine(cx - r * .15f, cy + r * .55f, cx + r * .8f, cy - r * .6f, paint)
            }
            "profile" -> {
                canvas.drawCircle(cx, cy - r * .5f, r * .34f, paint)
                val oval = RectF(cx - r, cy, cx + r, cy + r * 1.2f)
                canvas.drawArc(oval, 205f, 130f, false, paint)
            }
            "bookmark", "stack" -> {
                val path = Path().apply {
                    moveTo(cx - r * .65f, cy - r)
                    lineTo(cx + r * .65f, cy - r)
                    lineTo(cx + r * .65f, cy + r)
                    lineTo(cx, cy + r * .45f)
                    lineTo(cx - r * .65f, cy + r)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "settings" -> {
                canvas.drawCircle(cx, cy, r * .55f, paint)
                canvas.drawCircle(cx, cy, r * .14f, paint)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val x1 = cx + kotlin.math.cos(a).toFloat() * r * .7f
                    val y1 = cy + kotlin.math.sin(a).toFloat() * r * .7f
                    val x2 = cx + kotlin.math.cos(a).toFloat() * r
                    val y2 = cy + kotlin.math.sin(a).toFloat() * r
                    canvas.drawLine(x1, y1, x2, y2, paint)
                }
            }
            "language" -> {
                canvas.drawCircle(cx, cy, r, paint)
                val ovalV = RectF(cx - r * .48f, cy - r, cx + r * .48f, cy + r)
                canvas.drawOval(ovalV, paint)
                canvas.drawLine(cx - r, cy, cx + r, cy, paint)
            }
            "sun", "sunny" -> drawSun(canvas, cx, cy, r)
            "moon", "night" -> drawMoon(canvas, cx, cy, r)
            "cloudly", "cloud", "mist" -> {
                drawCloud(canvas, cx, cy, r)
                if (icon.equals("mist", true)) {
                    for (offset in listOf(-0.25f, 0.05f, 0.35f)) {
                        canvas.drawLine(cx - r * 0.7f, cy + r * offset, cx + r * 0.7f, cy + r * offset, paint)
                    }
                }
            }
            "partly_cloudly", "partly_cloudly_night" -> {
                if (icon.endsWith("night", true)) drawMoon(canvas, cx - r * 0.25f, cy - r * 0.25f, r * 0.62f)
                else drawSun(canvas, cx - r * 0.25f, cy - r * 0.25f, r * 0.62f)
                drawCloud(canvas, cx + r * 0.12f, cy + r * 0.2f, r * 0.78f)
            }
            "rain", "rain_night" -> {
                if (icon.endsWith("night", true)) drawMoon(canvas, cx - r * 0.3f, cy - r * 0.35f, r * 0.55f)
                drawCloud(canvas, cx, cy - r * 0.12f, r * 0.78f)
                for (offset in listOf(-0.45f, 0f, 0.45f)) {
                    canvas.drawLine(cx + r * offset, cy + r * 0.55f, cx + r * (offset - 0.12f), cy + r * 0.92f, paint)
                }
            }
            "snow", "snow_night" -> {
                if (icon.endsWith("night", true)) drawMoon(canvas, cx - r * 0.3f, cy - r * 0.35f, r * 0.55f)
                drawCloud(canvas, cx, cy - r * 0.16f, r * 0.76f)
                for (offset in listOf(-0.42f, 0f, 0.42f)) {
                    val sx = cx + r * offset
                    val sy = cy + r * 0.72f
                    canvas.drawLine(sx - r * 0.1f, sy, sx + r * 0.1f, sy, paint)
                    canvas.drawLine(sx, sy - r * 0.1f, sx, sy + r * 0.1f, paint)
                }
            }
            "thunder", "thunder_night" -> {
                if (icon.endsWith("night", true)) drawMoon(canvas, cx - r * 0.3f, cy - r * 0.35f, r * 0.55f)
                drawCloud(canvas, cx, cy - r * 0.16f, r * 0.76f)
                val bolt = Path().apply {
                    moveTo(cx + r * 0.05f, cy + r * 0.3f)
                    lineTo(cx - r * 0.18f, cy + r * 0.68f)
                    lineTo(cx + r * 0.02f, cy + r * 0.68f)
                    lineTo(cx - r * 0.05f, cy + r)
                    lineTo(cx + r * 0.23f, cy + r * 0.57f)
                    lineTo(cx + r * 0.05f, cy + r * 0.57f)
                    close()
                }
                canvas.drawPath(bolt, paint)
            }
            "phone" -> {
                canvas.drawRoundRect(
                    RectF(cx - r * .55f, cy - r, cx + r * .55f, cy + r),
                    r * .18f,
                    r * .18f,
                    paint
                )
                canvas.drawCircle(cx, cy + r * .68f, r * .08f, paint)
            }
            "message_circle" -> {
                val path = Path().apply {
                    moveTo(cx - r, cy - r * .75f)
                    lineTo(cx + r, cy - r * .75f)
                    lineTo(cx + r, cy + r * .55f)
                    lineTo(cx + r * .1f, cy + r * .55f)
                    lineTo(cx - r * .45f, cy + r)
                    lineTo(cx - r * .28f, cy + r * .55f)
                    lineTo(cx - r, cy + r * .55f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            else -> {
                canvas.drawCircle(cx, cy, r * .7f, paint)
            }
        }
    }


    private fun drawSun(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawCircle(cx, cy, r * 0.42f, paint)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            canvas.drawLine(
                cx + kotlin.math.cos(a).toFloat() * r * 0.72f,
                cy + kotlin.math.sin(a).toFloat() * r * 0.72f,
                cx + kotlin.math.cos(a).toFloat() * r,
                cy + kotlin.math.sin(a).toFloat() * r,
                paint
            )
        }
    }

    private fun drawMoon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val p = Path().apply {
            moveTo(cx + r * 0.28f, cy - r)
            cubicTo(cx - r * 0.65f, cy - r * 0.75f, cx - r * 0.7f, cy + r * 0.7f, cx + r * 0.2f, cy + r)
            cubicTo(cx - r * 0.1f, cy + r * 0.2f, cx - r * 0.05f, cy - r * 0.2f, cx + r * 0.28f, cy - r)
            close()
        }
        canvas.drawPath(p, paint)
    }

    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val path = Path().apply {
            moveTo(cx - r * 0.82f, cy + r * 0.28f)
            cubicTo(cx - r * 1.05f, cy - r * 0.02f, cx - r * 0.76f, cy - r * 0.34f, cx - r * 0.43f, cy - r * 0.27f)
            cubicTo(cx - r * 0.32f, cy - r * 0.78f, cx + r * 0.38f, cy - r * 0.77f, cx + r * 0.47f, cy - r * 0.25f)
            cubicTo(cx + r * 0.94f, cy - r * 0.33f, cx + r * 1.02f, cy + r * 0.13f, cx + r * 0.78f, cy + r * 0.3f)
            lineTo(cx - r * 0.82f, cy + r * 0.3f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    fun setTintColor(color: Int) {
        tint = color
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
