package com.appao.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.appao.R
import kotlin.math.ceil

/** Native, repeatable version of the profile cover's 220dp Material-style doodle tile. */
class ProfileDoodleDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.profileCoverBg)
        style = Paint.Style.FILL
    }
    private val doodlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.profileDoodle)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        canvas.drawRect(b, backgroundPaint)
        val tilePx = 220f * density
        val columns = ceil(b.width() / tilePx).toInt() + 1
        val rows = ceil(b.height() / tilePx).toInt() + 1
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val save = canvas.save()
                canvas.translate(b.left + column * tilePx, b.top + row * tilePx)
                canvas.scale(density, density)
                drawPhone(canvas)
                drawLeaf(canvas)
                drawCamera(canvas)
                drawMusic(canvas)
                drawHeart(canvas)
                drawClock(canvas)
                drawPaperPlane(canvas)
                canvas.restoreToCount(save)
            }
        }
    }

    private fun drawPhone(canvas: Canvas) {
        val p = Path().apply {
            moveTo(30f, 30f)
            quadTo(26f, 30f, 26f, 34f)
            quadTo(26f, 48f, 36f, 58f)
            quadTo(46f, 68f, 60f, 68f)
            quadTo(64f, 68f, 64f, 64f)
            lineTo(64f, 58f)
            quadTo(64f, 56f, 62f, 55f)
            lineTo(55f, 52f)
            quadTo(53f, 51f, 52f, 52f)
            lineTo(49f, 55f)
            quadTo(42f, 51f, 38f, 44f)
            lineTo(41f, 41f)
            quadTo(42f, 40f, 41f, 38f)
            lineTo(38f, 31f)
            quadTo(37f, 29f, 35f, 29f)
            close()
        }
        canvas.drawPath(p, doodlePaint)
    }

    private fun drawLeaf(canvas: Canvas) {
        canvas.drawOval(RectF(100f, 40f, 140f, 80f), doodlePaint)
        canvas.drawLine(120f, 40f, 120f, 80f, doodlePaint)
    }

    private fun drawCamera(canvas: Canvas) {
        canvas.drawRoundRect(RectF(40f, 100f, 76f, 126f), 4f, 4f, doodlePaint)
        canvas.drawCircle(58f, 113f, 8f, doodlePaint)
        canvas.drawRoundRect(RectF(50f, 94f, 66f, 102f), 2f, 2f, doodlePaint)
    }

    private fun drawMusic(canvas: Canvas) {
        canvas.drawCircle(150f, 120f, 7f, doodlePaint)
        canvas.drawCircle(178f, 128f, 7f, doodlePaint)
        val p = Path().apply {
            moveTo(157f, 120f)
            lineTo(157f, 90f)
            lineTo(185f, 84f)
            lineTo(185f, 128f)
        }
        canvas.drawPath(p, doodlePaint)
    }

    private fun drawHeart(canvas: Canvas) {
        val p = Path().apply {
            moveTo(30f, 160f)
            quadTo(30f, 152f, 38f, 152f)
            quadTo(44f, 152f, 46f, 158f)
            quadTo(48f, 152f, 56f, 152f)
            quadTo(64f, 152f, 64f, 160f)
            quadTo(64f, 172f, 48f, 182f)
            quadTo(32f, 172f, 30f, 160f)
            close()
        }
        canvas.drawPath(p, doodlePaint)
    }

    private fun drawClock(canvas: Canvas) {
        canvas.drawCircle(150f, 60f, 16f, doodlePaint)
        canvas.drawLine(150f, 50f, 150f, 60f, doodlePaint)
        canvas.drawLine(150f, 60f, 158f, 64f, doodlePaint)
    }

    private fun drawPaperPlane(canvas: Canvas) {
        val p = Path().apply {
            moveTo(100f, 150f)
            lineTo(180f, 170f)
            lineTo(140f, 180f)
            lineTo(130f, 198f)
            lineTo(120f, 175f)
            close()
        }
        canvas.drawPath(p, doodlePaint)
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
        doodlePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        doodlePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
