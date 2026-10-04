package com.appao

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Native right-side drawer gesture host.
 *
 * The drawer is opened with a horizontal left-to-right swipe that can begin
 * anywhere on the current screen. When already open, a right-to-left swipe
 * closes it. Vertical gestures are never intercepted so RecyclerView,
 * ScrollView and other native controls keep their normal behavior.
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
    }

    var listener: Listener? = null

    private val touchSlop =
        ViewConfiguration.get(context).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastTime = 0L

    private var tracking = false
    private var intercepted = false
    private var opening = true

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

                tracking = true
                intercepted = false
                velocityX = 0f

                opening =
                    !(listener?.isDrawerOpen() ?: false)

                /*
                 * Never reject based on the start position.
                 *
                 * The right-side drawer deliberately opens from a swipe
                 * beginning anywhere on the left/center area of the screen.
                 */
                return false
            }

            MotionEvent.ACTION_MOVE -> {

                if (!tracking) {
                    return false
                }

                val dx =
                    event.x - downX

                val dy =
                    event.y - downY

                if (!intercepted) {

                    if (
                        abs(dx) < touchSlop &&
                        abs(dy) < touchSlop
                    ) {
                        return false
                    }

                    /*
                     * Give vertical scrolling to the child.
                     */
                    if (abs(dy) >= abs(dx)) {
                        tracking = false
                        return false
                    }

                    /*
                     * Closed -> drawer opens rightwards.
                     */
                    if (
                        opening &&
                        dx <= 0f
                    ) {
                        tracking = false
                        return false
                    }

                    /*
                     * Open -> drawer closes leftwards.
                     */
                    if (
                        !opening &&
                        dx >= 0f
                    ) {
                        tracking = false
                        return false
                    }

                    intercepted = true

                    listener?.onDrawerGestureStart(
                        opening
                    )
                }

                updateVelocity(event.x)

                val progress =
                    calculateProgress(
                        event.x
                    )

                listener?.onDrawerGestureProgress(
                    if (opening) {
                        progress
                    } else {
                        1f - progress
                    }
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

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        if (!intercepted) {
            return false
        }

        when (event.actionMasked) {

            MotionEvent.ACTION_MOVE -> {

                updateVelocity(event.x)

                val progress =
                    calculateProgress(
                        event.x
                    )

                listener?.onDrawerGestureProgress(
                    if (opening) {
                        progress
                    } else {
                        1f - progress
                    }
                )

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {

                val progress =
                    calculateProgress(
                        event.x
                    )

                listener?.onDrawerGestureEnd(
                    opening,
                    if (opening) {
                        progress
                    } else {
                        1f - progress
                    },
                    abs(velocityX)
                )

                tracking = false
                intercepted = false

                return true
            }
        }

        return true
    }

    private fun calculateProgress(
        x: Float
    ): Float {

        val widthPx =
            width.coerceAtLeast(1)

        return (
            abs(x - downX) /
                widthPx.toFloat()
            )
            .coerceIn(0f, 1f)
    }

    private fun updateVelocity(
        currentX: Float
    ) {

        val now =
            System.currentTimeMillis()

        val dt =
            now - lastTime

        if (dt > 0L) {

            velocityX =
                (
                    currentX -
                        lastX
                    ) / dt
        }

        lastX =
            currentX

        lastTime =
            now
    }
}
