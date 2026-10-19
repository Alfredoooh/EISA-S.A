package com.appao

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs

/** Edge gesture host for the feed and Apps/Conversations pages plus pull refresh. */
class DrawerHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface Listener {
        fun isDrawerOpen(): Boolean
        fun isSidePanelOpen(): Boolean
        fun canStartPull(x: Float, y: Float): Boolean
        fun onPullGestureStart()
        fun onPullGestureProgress(distance: Float)
        fun onPullGestureEnd()
        fun onDrawerGestureStart(opening: Boolean)
        fun onDrawerGestureProgress(progress: Float)
        fun onDrawerGestureEnd(opening: Boolean, progress: Float, velocity: Float)
        fun onSidePanelGestureStart(opening: Boolean)
        fun onSidePanelGestureProgress(progress: Float)
        fun onSidePanelGestureEnd(opening: Boolean, progress: Float, velocity: Float)
    }

    var listener: Listener? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Let nested horizontal content (category chips, carousels, text fields) own
    // horizontal gestures. This host handles pull-to-refresh only; the legacy
    // drawer/side-panel swipe gesture is intentionally disabled.
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    private enum class GestureMode {
        NONE,
        RIGHT_OPEN,
        RIGHT_CLOSE,
        LEFT_PANEL_OPEN,
        LEFT_PANEL_CLOSE,
        PULL_REFRESH
    }

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastTime = 0L
    private var tracking = false
    private var intercepted = false
    private var mode = GestureMode.NONE
    private var velocityX = 0f

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = downX
                lastTime = System.currentTimeMillis()
                velocityX = 0f
                tracking = true
                intercepted = false
                mode = GestureMode.NONE
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false

                val dx = event.x - downX
                val dy = event.y - downY
                if (!intercepted) {
                    if (abs(dx) < touchSlop && abs(dy) < touchSlop) return false

                    if (abs(dy) > abs(dx)) {
                        if (dy > touchSlop && listener?.canStartPull(downX, downY) == true) {
                            mode = GestureMode.PULL_REFRESH
                            intercepted = true
                            listener?.onPullGestureStart()
                            listener?.onPullGestureProgress(dy)
                            return true
                        }
                        tracking = false
                        mode = GestureMode.NONE
                        return false
                    }

                    // Never intercept a horizontal movement to reveal the old drawer.
                    // Native child scrolling and normal navigation now own these gestures.
                    tracking = false
                    mode = GestureMode.NONE
                    return false
                }

                when (mode) {
                    GestureMode.RIGHT_OPEN,
                    GestureMode.RIGHT_CLOSE -> {
                        updateVelocity(event.x)
                        dispatchDrawerProgress(event.x)
                        return true
                    }
                    GestureMode.LEFT_PANEL_OPEN,
                    GestureMode.LEFT_PANEL_CLOSE -> {
                        updateVelocity(event.x)
                        dispatchSidePanelProgress(event.x)
                        return true
                    }
                    GestureMode.PULL_REFRESH -> {
                        listener?.onPullGestureProgress((event.y - downY).coerceAtLeast(0f))
                        return true
                    }
                    GestureMode.NONE -> return false
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (intercepted) return false
                tracking = false
                mode = GestureMode.NONE
                return false
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!intercepted) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                when (mode) {
                    GestureMode.RIGHT_OPEN,
                    GestureMode.RIGHT_CLOSE -> {
                        updateVelocity(event.x)
                        dispatchDrawerProgress(event.x)
                    }
                    GestureMode.LEFT_PANEL_OPEN,
                    GestureMode.LEFT_PANEL_CLOSE -> {
                        updateVelocity(event.x)
                        dispatchSidePanelProgress(event.x)
                    }
                    GestureMode.PULL_REFRESH -> listener?.onPullGestureProgress((event.y - downY).coerceAtLeast(0f))
                    GestureMode.NONE -> Unit
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                when (mode) {
                    GestureMode.RIGHT_OPEN,
                    GestureMode.RIGHT_CLOSE -> {
                        val progress = calculateProgress(event.x)
                        listener?.onDrawerGestureEnd(
                            mode == GestureMode.RIGHT_OPEN,
                            if (mode == GestureMode.RIGHT_OPEN) progress else 1f - progress,
                            velocityX
                        )
                    }
                    GestureMode.LEFT_PANEL_OPEN,
                    GestureMode.LEFT_PANEL_CLOSE -> {
                        val progress = calculateProgress(event.x)
                        listener?.onSidePanelGestureEnd(
                            mode == GestureMode.LEFT_PANEL_OPEN,
                            if (mode == GestureMode.LEFT_PANEL_OPEN) progress else 1f - progress,
                            velocityX
                        )
                    }
                    GestureMode.PULL_REFRESH -> listener?.onPullGestureEnd()
                    GestureMode.NONE -> Unit
                }
                tracking = false
                intercepted = false
                mode = GestureMode.NONE
                return true
            }
        }
        return true
    }

    private fun dispatchDrawerProgress(x: Float) {
        val raw = calculateProgress(x)
        listener?.onDrawerGestureProgress(if (mode == GestureMode.RIGHT_OPEN) raw else 1f - raw)
    }

    private fun dispatchSidePanelProgress(x: Float) {
        val raw = calculateProgress(x)
        listener?.onSidePanelGestureProgress(if (mode == GestureMode.LEFT_PANEL_OPEN) raw else 1f - raw)
    }

    private fun calculateProgress(x: Float): Float = (abs(x - downX) / width.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    private fun updateVelocity(currentX: Float) {
        val now = System.currentTimeMillis()
        val dt = now - lastTime
        if (dt > 0L) velocityX = (currentX - lastX) / dt
        lastX = currentX
        lastTime = now
    }
}