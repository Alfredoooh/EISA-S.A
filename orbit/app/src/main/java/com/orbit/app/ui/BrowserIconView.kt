package com.orbit.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/** Small vector icons drawn natively on Canvas; no icon-font or Material dependency required. */
class BrowserIconView(context: Context, private val iconName: String) : View(context) {
    var tint: Int = 0xFF3C4043.toInt()
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = min(width, height).toFloat()
        if (side <= 0f) return
        val save = canvas.save()
        canvas.translate((width - side) / 2f, (height - side) / 2f)
        canvas.scale(side / 24f, side / 24f)
        paint.color = tint
        paint.strokeWidth = if (iconName == "menu") 2.1f else 1.9f
        path.reset()

        when (iconName) {
            "back" -> {
                path.moveTo(19.5f, 12f); path.lineTo(4.5f, 12f)
                path.moveTo(11f, 5.5f); path.lineTo(4.5f, 12f); path.lineTo(11f, 18.5f)
                canvas.drawPath(path, paint)
            }
            "forward" -> {
                path.moveTo(4.5f, 12f); path.lineTo(19.5f, 12f)
                path.moveTo(13f, 5.5f); path.lineTo(19.5f, 12f); path.lineTo(13f, 18.5f)
                canvas.drawPath(path, paint)
            }
            "menu" -> {
                canvas.drawLine(5f, 7f, 19f, 7f, paint)
                canvas.drawLine(5f, 12f, 19f, 12f, paint)
                canvas.drawLine(5f, 17f, 19f, 17f, paint)
            }
            "lock" -> {
                rect.set(5.7f, 10.2f, 18.3f, 20f)
                canvas.drawRoundRect(rect, 2.1f, 2.1f, paint)
                path.moveTo(8.4f, 10.2f); path.lineTo(8.4f, 7.8f)
                path.cubicTo(8.4f, 3.6f, 15.6f, 3.6f, 15.6f, 7.8f)
                path.lineTo(15.6f, 10.2f)
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(12f, 14.4f, 1.0f, paint)
                paint.style = Paint.Style.STROKE
            }
            "search" -> {
                canvas.drawCircle(10.5f, 10.5f, 6.2f, paint)
                canvas.drawLine(15f, 15f, 20f, 20f, paint)
            }
            "close" -> {
                canvas.drawLine(6.5f, 6.5f, 17.5f, 17.5f, paint)
                canvas.drawLine(17.5f, 6.5f, 6.5f, 17.5f, paint)
            }
            "add" -> {
                canvas.drawLine(12f, 5f, 12f, 19f, paint)
                canvas.drawLine(5f, 12f, 19f, 12f, paint)
            }
            "reload" -> {
                path.moveTo(19f, 8.5f); path.cubicTo(16.2f, 4.5f, 10.1f, 3.8f, 6.8f, 7.1f)
                path.cubicTo(3.1f, 10.8f, 4.2f, 16.5f, 8.2f, 18.6f)
                path.cubicTo(12.1f, 20.7f, 17.2f, 18.9f, 18.8f, 15f)
                canvas.drawPath(path, paint)
                path.reset(); path.moveTo(18.8f, 4.8f); path.lineTo(19f, 9.2f); path.lineTo(14.7f, 8.7f)
                canvas.drawPath(path, paint)
            }
            "home" -> {
                path.moveTo(3.5f, 10.5f); path.lineTo(12f, 3.5f); path.lineTo(20.5f, 10.5f)
                path.moveTo(5.5f, 9.5f); path.lineTo(5.5f, 20f); path.lineTo(18.5f, 20f); path.lineTo(18.5f, 9.5f)
                path.moveTo(9.5f, 20f); path.lineTo(9.5f, 14f); path.lineTo(14.5f, 14f); path.lineTo(14.5f, 20f)
                canvas.drawPath(path, paint)
            }
            "share" -> {
                canvas.drawLine(7f, 12f, 17f, 6.5f, paint)
                canvas.drawLine(7f, 12f, 17f, 17.5f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(5.5f, 12f, 2.3f, paint)
                canvas.drawCircle(18.5f, 5.5f, 2.3f, paint)
                canvas.drawCircle(18.5f, 18.5f, 2.3f, paint)
                paint.style = Paint.Style.STROKE
            }
            "copy" -> {
                rect.set(8f, 4f, 19f, 17f); canvas.drawRoundRect(rect, 1.6f, 1.6f, paint)
                path.moveTo(15.5f, 17f); path.lineTo(15.5f, 20f); path.lineTo(4.5f, 20f)
                path.lineTo(4.5f, 7f); path.lineTo(8f, 7f)
                canvas.drawPath(path, paint)
            }
            "desktop" -> {
                rect.set(3f, 4.5f, 21f, 17f); canvas.drawRoundRect(rect, 1.4f, 1.4f, paint)
                canvas.drawLine(12f, 17.5f, 12f, 21f, paint)
                canvas.drawLine(8f, 21f, 16f, 21f, paint)
            }
            "globe" -> {
                canvas.drawCircle(12f, 12f, 8.7f, paint)
                rect.set(7f, 3.5f, 17f, 20.5f); canvas.drawOval(rect, paint)
                canvas.drawLine(3.7f, 12f, 20.3f, 12f, paint)
                path.moveTo(5.4f, 7.6f); path.quadTo(12f, 10f, 18.6f, 7.6f)
                path.moveTo(5.4f, 16.4f); path.quadTo(12f, 14f, 18.6f, 16.4f)
                canvas.drawPath(path, paint)
            }
            "history" -> {
                canvas.drawCircle(12f, 12f, 8.6f, paint)
                path.moveTo(12f, 6.5f); path.lineTo(12f, 12f); path.lineTo(16f, 14.2f)
                canvas.drawPath(path, paint)
            }
            "tabs" -> {
                rect.set(4f, 3.5f, 20f, 20.5f); canvas.drawRoundRect(rect, 3f, 3f, paint)
            }
            "desktopMode" -> {
                rect.set(3f, 4f, 21f, 17f); canvas.drawRoundRect(rect, 1.5f, 1.5f, paint)
                canvas.drawLine(12f, 17.5f, 12f, 20.5f, paint)
                canvas.drawLine(8f, 20.5f, 16f, 20.5f, paint)
                canvas.drawLine(7f, 8f, 17f, 8f, paint)
            }
            else -> {
                canvas.drawCircle(12f, 12f, 8f, paint)
            }
        }
        canvas.restoreToCount(save)
    }
}
