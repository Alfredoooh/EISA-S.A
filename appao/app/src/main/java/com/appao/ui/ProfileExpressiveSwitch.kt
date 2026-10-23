package com.appao.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.appao.AccentColorManager
import com.appao.R

/** Native Canvas implementation of the HTML reference's M3 Expressive 52x32 switch. */
class ProfileExpressiveSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var checked: Boolean = false
        private set
    var onCheckedChange: ((Boolean) -> Unit)? = null

    private var animatedPosition = 0f
    private var positionAnimator: ValueAnimator? = null
    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(3f)
    }

    private val primaryColor get() = AccentColorManager.resolvedOr(context, R.color.pri)
    private val offOutlineColor get() = ContextCompat.getColor(context, R.color.switchOutline)
    private val offThumbColor get() = ContextCompat.getColor(context, R.color.switchOutline)
    private val thumbOnColor get() = ContextCompat.getColor(context, R.color.profileBg)

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Modo escuro"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(52f).toInt()
        val desiredHeight = dp(32f).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = animatedPosition
        val normalized = p.coerceIn(0f, 1f)
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = dp(2f)
        val track = RectF(stroke / 2f, stroke / 2f, w - stroke / 2f, h - stroke / 2f)
        val radius = h / 2f

        // The OFF state has no fill and a 2dp outline; ON fills the whole track.
        val fillAlpha = (normalized * 255f).toInt().coerceIn(0, 255)
        if (fillAlpha > 0) {
            trackPaint.style = Paint.Style.FILL
            trackPaint.color = primaryColor
            trackPaint.alpha = fillAlpha
            canvas.drawRoundRect(track, radius, radius, trackPaint)
        }
        val outlineAlpha = 255 - fillAlpha
        if (outlineAlpha > 0) {
            trackPaint.style = Paint.Style.STROKE
            trackPaint.strokeWidth = stroke
            trackPaint.color = offOutlineColor
            trackPaint.alpha = outlineAlpha
            canvas.drawRoundRect(track, radius, radius, trackPaint)
        }

        val isPressedNow = isPressed
        val thumbSize = if (isPressedNow) dp(28f) else dp(16f + 8f * p)
        val thumbLeft = if (isPressedNow) {
            dp(if (checked) 20f else 2f)
        } else {
            dp(4f + 20f * p)
        }
        val thumbTop = (h - thumbSize) / 2f
        thumbPaint.style = Paint.Style.FILL
        thumbPaint.color = ColorUtils.blendARGB(offThumbColor, thumbOnColor, normalized)
        thumbPaint.alpha = 255
        canvas.drawRoundRect(
            RectF(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize),
            thumbSize / 2f,
            thumbSize / 2f,
            thumbPaint
        )

        if (normalized > 0f) {
            val checkSize = dp(14f) * (0.5f + 0.5f * normalized)
            val checkLeft = thumbLeft + (thumbSize - checkSize) / 2f
            val checkTop = thumbTop + (thumbSize - checkSize) / 2f
            val scale = checkSize / 24f
            val checkPath = Path().apply {
                moveTo(checkLeft + 4f * scale, checkTop + 12.6f * scale)
                lineTo(checkLeft + 9f * scale, checkTop + 17.6f * scale)
                lineTo(checkLeft + 20f * scale, checkTop + 6.6f * scale)
            }
            checkPaint.color = primaryColor
            checkPaint.alpha = (normalized * 255f).toInt().coerceIn(0, 255)
            canvas.drawPath(checkPath, checkPaint)
        }
    }

    fun setChecked(value: Boolean, animate: Boolean = true) {
        if (checked == value && ((value && animatedPosition >= 0.999f) || (!value && animatedPosition <= 0.001f))) return
        checked = value
        positionAnimator?.cancel()
        val target = if (value) 1f else 0f
        if (!animate || !isLaidOut) {
            animatedPosition = target
            invalidate()
            refreshDrawableState()
            return
        }
        positionAnimator = ValueAnimator.ofFloat(animatedPosition, target).apply {
            duration = 350L
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener {
                animatedPosition = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        refreshDrawableState()
    }

    override fun performClick(): Boolean {
        super.performClick()
        setChecked(!checked, animate = true)
        onCheckedChange?.invoke(checked)
        return true
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"
        info.isCheckable = true
        info.isChecked = checked
    }

    private fun dp(value: Float): Float = value * density
}
