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
        strokeWidth = 2.3f
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
        val r = minOf(w, h) * 0.34f

        when (icon.lowercase()) {
            "menu" -> {
                canvas.drawLine(cx - r, cy - 6f, cx + r, cy - 6f, paint)
                canvas.drawLine(cx - r, cy, cx + r, cy, paint)
                canvas.drawLine(cx - r, cy + 6f, cx + r, cy + 6f, paint)
            }
            "add" -> {
                canvas.drawLine(cx - r, cy, cx + r, cy, paint)
                canvas.drawLine(cx, cy - r, cx, cy + r, paint)
            }
            "arrow-up", "send" -> {
                canvas.drawLine(cx, cy + r, cx, cy - r, paint)
                canvas.drawLine(cx, cy - r, cx - r * .55f, cy - r * .45f, paint)
                canvas.drawLine(cx, cy - r, cx + r * .55f, cy - r * .45f, paint)
            }
            "back", "close" -> {
                canvas.drawLine(cx + r * .7f, cy, cx - r * .7f, cy, paint)
                canvas.drawLine(cx - r * .7f, cy, cx - r * .1f, cy - r * .6f, paint)
                canvas.drawLine(cx - r * .7f, cy, cx - r * .1f, cy + r * .6f, paint)
            }
            "chevron-down" -> {
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
            "sun", "sunny" -> {
                canvas.drawCircle(cx, cy, r * .48f, paint)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    canvas.drawLine(
                        cx + kotlin.math.cos(a).toFloat() * r * .85f,
                        cy + kotlin.math.sin(a).toFloat() * r * .85f,
                        cx + kotlin.math.cos(a).toFloat() * r,
                        cy + kotlin.math.sin(a).toFloat() * r,
                        paint
                    )
                }
            }
            "moon" -> {
                val p = Path().apply {
                    moveTo(cx + r * .28f, cy - r)
                    cubicTo(cx - r * .65f, cy - r * .75f, cx - r * .7f, cy + r * .7f, cx + r * .2f, cy + r)
                    cubicTo(cx - r * .1f, cy + r * .2f, cx - r * .05f, cy - r * .2f, cx + r * .28f, cy - r)
                    close()
                }
                canvas.drawPath(p, paint)
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
            "chat" -> {
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
