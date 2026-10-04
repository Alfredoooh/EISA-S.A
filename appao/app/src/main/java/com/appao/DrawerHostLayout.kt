package com.appao

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Native drawer gesture host. It waits until a horizontal gesture is
 * unmistakable, then intercepts it so taps/vertical scrolling continue to
 * belong to the child views.
 */
class DrawerHostLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface Listener {
        fun isDrawerOpen(): Boolean
        fun onDrawerGestureStart(opening: Boolean)
        fun onDrawerGestureProgress(progress: Float)
        fun onDrawerGestureEnd(
            opening: Boolean,
            progress: Float,
            velocity: Float
        )
    }

    var listener: Listener? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastTime = 0L
    private var tracking = false
    private var intercepted = false
    private var velocity = 0f
    private var opening = true

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = downX
                lastTime = System.currentTimeMillis()
                tracking = true
                intercepted = false
                velocity = 0f
                opening = !(listener?.isDrawerOpen() ?: false)
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

                    if (abs(dx) <= abs(dy)) {
                        tracking = false
                        return false
                    }

                    if (opening && dx <= 0f) {
                        tracking = false
                        return false
                    }

                    if (!opening && dx >= 0f) {
                        tracking = false
                        return false
                    }

                    // Opening is intentionally not limited to the edge. A
                    // horizontal right swipe may begin on the left/center
                    // portion of the current page.
                    if (
                        opening &&
                        downX > width * 0.97f
                    ) {
                        tracking = false
                        return false
                    }

                    intercepted = true
                    listener?.onDrawerGestureStart(opening)
                }

                updateVelocity(event.x)
                val distance = abs(event.x - downX)
                val progress =
                    (distance / width.coerceAtLeast(1))
                        .coerceIn(0f, 1f)

                listener?.onDrawerGestureProgress(
                    if (opening) progress else 1f - progress
                )

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                intercepted = false
                return false
            }
        }

        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!intercepted) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                updateVelocity(event.x)

                val distance = abs(event.x - downX)
                val progress =
                    (distance / width.coerceAtLeast(1))
                        .coerceIn(0f, 1f)

                listener?.onDrawerGestureProgress(
                    if (opening) progress else 1f - progress
                )

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                val distance = abs(event.x - downX)
                val progress =
                    (distance / width.coerceAtLeast(1))
                        .coerceIn(0f, 1f)

                listener?.onDrawerGestureEnd(
                    opening,
                    if (opening) progress else 1f - progress,
                    abs(velocity)
                )

                tracking = false
                intercepted = false
                return true
            }
        }

        return true
    }

    private fun updateVelocity(currentX: Float) {
        val now = System.currentTimeMillis()
        val dt = now - lastTime
        if (dt > 0L) {
            velocity = (currentX - lastX) / dt
        }
        lastX = currentX
        lastTime = now
    }
}
