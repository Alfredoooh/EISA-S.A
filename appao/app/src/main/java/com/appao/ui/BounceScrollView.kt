package com.appao

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import kotlin.math.abs

/** Small elastic over-scroll effect used by settings and secondary screens. */
class BounceScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private var lastY = 0f
    private var overDragging = false
    private val maxStretch = (48f * resources.displayMetrics.density)

    init {
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = event.y
                overDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastY
                val top = !canScrollVertically(-1)
                val bottom = !canScrollVertically(1)
                val pullingPastTop = top && dy > 0f
                val pullingPastBottom = bottom && dy < 0f

                if ((pullingPastTop || pullingPastBottom) && abs(dy) > 0f) {
                    val child = getChildAt(0)
                    if (child != null) {
                        val target = (child.translationY + dy * 0.42f)
                            .coerceIn(-maxStretch, maxStretch)
                        child.translationY = target
                        lastY = event.y
                        overDragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                } else {
                    getChildAt(0)?.translationY = 0f
                }
                lastY = event.y
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (overDragging) {
                    animateBack()
                    overDragging = false
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }

    private fun animateBack() {
        getChildAt(0)?.animate()
            ?.translationY(0f)
            ?.setDuration(380L)
            ?.setInterpolator(Curves.SPRING)
            ?.start()
    }
}
