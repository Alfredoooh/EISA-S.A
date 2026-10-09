package com.appao

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout

/** FrameLayout whose corners can be animated during shared-container transforms. */
class RoundedClipFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val path = Path()
    private val bounds = RectF()
    private var radiusPx = 0f
    private var cornerAnimator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
    }

    fun setCornerRadiusDp(value: Float) {
        cornerAnimator?.cancel()
        radiusPx = value * resources.displayMetrics.density
        invalidate()
    }

    fun animateCornerRadiusDp(from: Float, to: Float, duration: Long = 420L) {
        cornerAnimator?.cancel()
        val density = resources.displayMetrics.density
        cornerAnimator = ValueAnimator.ofFloat(from * density, to * density).apply {
            this.duration = duration
            interpolator = Curves.SMOOTH
            addUpdateListener {
                radiusPx = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0 || radiusPx <= 0.5f) {
            super.dispatchDraw(canvas)
            return
        }

        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        path.reset()
        path.addRoundRect(bounds, radiusPx, radiusPx, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(path)
        super.dispatchDraw(canvas)
        canvas.restore()
    }
}
