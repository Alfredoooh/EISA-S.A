package com.appao

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs

/**
 * Keeps SwipeRefreshLayout's mature nested-scroll behaviour while exposing pull distance to the
 * separate expressive indicator. Horizontal gestures (for example, embedded site carousels) are
 * deliberately ignored so a tiny vertical component cannot start a refresh animation.
 */
class ExpressiveSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    var canExpressivePull: (() -> Boolean)? = null
    var onExpressivePullProgress: ((Float) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var lastProgress = 0f
    private var horizontalGesture = false
    private val directionSlop = dp(6).toFloat()
    private val pullDistance = dp(76).toFloat().coerceAtLeast(1f)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastProgress = 0f
                horizontalGesture = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                downX = event.x
                downY = event.y
                lastProgress = 0f
            }
        }

        val handled = super.dispatchTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                val dy = event.y - downY
                if (dx > directionSlop && dx > abs(dy) * 1.15f) horizontalGesture = true
                val verticalPull = !horizontalGesture && dy > directionSlop && dy > dx * 1.15f
                val enabled = canExpressivePull?.invoke() ?: true
                if (verticalPull && enabled && !isRefreshing) {
                    lastProgress = (dy / pullDistance).coerceIn(0f, 1.3f)
                    onExpressivePullProgress?.invoke(lastProgress)
                } else if (lastProgress > 0f && (!verticalPull || !enabled)) {
                    lastProgress = 0f
                    onExpressivePullProgress?.invoke(0f)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastProgress = 0f
                onExpressivePullProgress?.invoke(0f)
                horizontalGesture = false
            }
        }
        return handled
    }

    /** Decide gesture direction before SwipeRefreshLayout has a chance to steal a horizontal drag. */
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                horizontalGesture = false
                lastProgress = 0f
                return super.onInterceptTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (dx > directionSlop && dx > dy * 1.15f) horizontalGesture = true
                if (horizontalGesture) return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val intercepted = super.onInterceptTouchEvent(event)
                horizontalGesture = false
                return intercepted
            }
        }
        return if (horizontalGesture) false else super.onInterceptTouchEvent(event)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
