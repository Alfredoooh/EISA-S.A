package com.appao

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Horizontal edge-gesture host for the right drawer and the opposite left panel.
 *
 * Right drawer: opens with a right-to-left swipe from the right edge and closes
 * with a left-to-right swipe. Left panel: opens with a left-to-right swipe from
 * the left edge. Vertical gestures remain available to child scrolling views.
 */
class DrawerHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface Listener {
        fun isDrawerOpen(): Boolean

        fun onDrawerGestureStart(
            opening: Boolean
        )

        fun onDrawerGestureProgress(
            progress: Float
        )

        fun onDrawerGestureEnd(
            opening: Boolean,
            progress: Float,
            velocity: Float
        )

        fun onLeftPanelSwipe()
    }

    var listener: Listener? = null

    private val touchSlop =
        ViewConfiguration.get(context).scaledTouchSlop

    private val edgeSize =
        (72f * resources.displayMetrics.density + 0.5f).toInt()

    private enum class GestureMode {
        NONE,
        RIGHT_OPEN,
        RIGHT_CLOSE,
        LEFT_PANEL
    }

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastTime = 0L

    private var tracking = false
    private var intercepted = false
    private var mode = GestureMode.NONE
    private var velocityX = 0f

    override fun onInterceptTouchEvent(
        event: MotionEvent
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = downX
                lastTime = System.currentTimeMillis()
                velocityX = 0f
                tracking = true
                intercepted = false

                mode = when {
                    listener?.isDrawerOpen() == true -> GestureMode.RIGHT_CLOSE
                    else -> GestureMode.NONE
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false

                val dx = event.x - downX
                val dy = event.y - downY

                if (!intercepted) {
                    if (abs(dx) < touchSlop && abs(dy) < touchSlop) {
                        return false
                    }

                    if (abs(dy) >= abs(dx)) {
                        tracking = false
                        mode = GestureMode.NONE
                        return false
                    }

                    val screenWidth = width.coerceAtLeast(1).toFloat()
                    val startNearRight = downX >= screenWidth - edgeSize
                    val startNearLeft = downX <= edgeSize

                    mode = when {
                        listener?.isDrawerOpen() == true -> {
                            if (dx > 0f) GestureMode.RIGHT_CLOSE
                            else {
                                tracking = false
                                GestureMode.NONE
                            }
                        }
                        startNearRight && dx < 0f -> GestureMode.RIGHT_OPEN
                        startNearLeft && dx > 0f -> GestureMode.LEFT_PANEL
                        else -> {
                            tracking = false
                            GestureMode.NONE
                        }
                    }

                    when (mode) {
                        GestureMode.LEFT_PANEL -> {
                            // The opposite screen is an Activity; once the edge
                            // gesture crosses the slop it opens as one smooth motion.
                            tracking = false
                            intercepted = true
                            listener?.onLeftPanelSwipe()
                            return true
                        }

                        GestureMode.RIGHT_OPEN -> {
                            intercepted = true
                            listener?.onDrawerGestureStart(true)
                        }

                        GestureMode.RIGHT_CLOSE -> {
                            intercepted = true
                            listener?.onDrawerGestureStart(false)
                        }

                        GestureMode.NONE -> return false
                    }
                }

                if (mode == GestureMode.RIGHT_OPEN || mode == GestureMode.RIGHT_CLOSE) {
                    updateVelocity(event.x)
                    dispatchDrawerProgress(event.x)
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                intercepted = false
                mode = GestureMode.NONE
            }
        }

        return false
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {
        if (!intercepted) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (mode == GestureMode.RIGHT_OPEN || mode == GestureMode.RIGHT_CLOSE) {
                    updateVelocity(event.x)
                    dispatchDrawerProgress(event.x)
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (mode == GestureMode.RIGHT_OPEN || mode == GestureMode.RIGHT_CLOSE) {
                    val progress = calculateProgress(event.x)
                    listener?.onDrawerGestureEnd(
                        mode == GestureMode.RIGHT_OPEN,
                        progress,
                        velocityX
                    )
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
        listener?.onDrawerGestureProgress(
            if (mode == GestureMode.RIGHT_OPEN) raw else 1f - raw
        )
    }

    private fun calculateProgress(
        x: Float
    ): Float {
        val widthPx = width.coerceAtLeast(1)
        return (
            abs(x - downX) / widthPx.toFloat()
        ).coerceIn(0f, 1f)
    }

    private fun updateVelocity(
        currentX: Float
    ) {
        val now = System.currentTimeMillis()
        val dt = now - lastTime

        if (dt > 0L) {
            velocityX = (currentX - lastX) / dt
        }

        lastX = currentX
        lastTime = now
    }
}
