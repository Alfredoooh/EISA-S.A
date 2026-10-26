package com.appao

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat

/**
 * WebView que participa corretamente no nested scrolling do CoordinatorLayout/AppBarLayout.
 * O WebView nativo não implementa NestedScrollingChild, por isso o AppBarLayout nunca recebe
 * os deltas de scroll da página e o colapso falha ou fica preso. Esta classe resolve isso
 * despachando pre-scroll e scroll para o pai nested (o AppBarLayout.Behavior) antes/depois
 * de o próprio WebView consumir o movimento.
 */
class NestedScrollWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs), NestedScrollingChild3 {

    private val childHelper = NestedScrollingChildHelper(this)
    private var lastY = 0
    private val scrollOffset = IntArray(2)
    private val scrollConsumed = IntArray(2)
    private var nestedOffsetY = 0

    init {
        isNestedScrollingEnabled = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val trackedEvent = MotionEvent.obtain(event)
        val action = event.actionMasked

        if (action == MotionEvent.ACTION_DOWN) {
            nestedOffsetY = 0
        }

        val eventY = event.y.toInt()
        trackedEvent.offsetLocation(0f, nestedOffsetY.toFloat())

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                lastY = eventY
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
            }
            MotionEvent.ACTION_MOVE -> {
                var deltaY = lastY - eventY

                if (dispatchNestedPreScroll(0, deltaY, scrollConsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                    deltaY -= scrollConsumed[1]
                    nestedOffsetY += scrollOffset[1]
                    trackedEvent.offsetLocation(0f, scrollOffset[1].toFloat())
                }

                lastY = eventY - scrollOffset[1]

                val oldScrollY = scrollY
                val result = super.onTouchEvent(trackedEvent)
                val consumedByWebView = scrollY - oldScrollY
                val unconsumedY = deltaY - consumedByWebView

                if (dispatchNestedScroll(0, consumedByWebView, 0, unconsumedY, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                    lastY -= scrollOffset[1]
                    trackedEvent.offsetLocation(0f, scrollOffset[1].toFloat())
                    nestedOffsetY += scrollOffset[1]
                }

                trackedEvent.recycle()
                return result
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                trackedEvent.recycle()
                val result = super.onTouchEvent(event)
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
                return result
            }
        }

        val result = super.onTouchEvent(trackedEvent)
        trackedEvent.recycle()
        return result
    }

    // ---- NestedScrollingChild3 / NestedScrollingChild2 / NestedScrollingChild ----

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean = childHelper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int): Boolean = childHelper.startNestedScroll(axes)

    override fun startNestedScroll(axes: Int, type: Int): Boolean = childHelper.startNestedScroll(axes, type)

    override fun stopNestedScroll() = childHelper.stopNestedScroll()

    override fun stopNestedScroll(type: Int) = childHelper.stopNestedScroll(type)

    override fun hasNestedScrollingParent(): Boolean = childHelper.hasNestedScrollingParent()

    override fun hasNestedScrollingParent(type: Int): Boolean = childHelper.hasNestedScrollingParent(type)

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean = childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow)

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?, type: Int
    ): Boolean = childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?, type: Int, consumed: IntArray
    ) = childHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed)

    override fun dispatchNestedPreScroll(
        dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?
    ): Boolean = childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

    override fun dispatchNestedPreScroll(
        dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int
    ): Boolean = childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean =
        childHelper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean =
        childHelper.dispatchNestedPreFling(velocityX, velocityY)
}