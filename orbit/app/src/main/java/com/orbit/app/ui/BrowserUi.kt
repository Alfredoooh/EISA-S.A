package com.orbit.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

internal fun Context.dp(value: Number): Int =
    (resources.displayMetrics.density * value.toFloat()).roundToInt()

internal fun roundedBackground(
    color: Int,
    radiusPx: Float,
    strokeColor: Int = Color.TRANSPARENT,
    strokeWidthPx: Int = 0,
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    setColor(color)
    cornerRadius = radiusPx
    if (strokeWidthPx > 0) setStroke(strokeWidthPx, strokeColor)
}

internal fun rippleBackground(
    context: Context,
    baseColor: Int,
    radiusDp: Float = 50f,
    pressedColor: Int = 0x1F5F6368,
    strokeColor: Int = Color.TRANSPARENT,
    strokeWidthDp: Int = 0,
): RippleDrawable {
    val shape = roundedBackground(
        baseColor,
        context.dp(radiusDp).toFloat(),
        strokeColor,
        context.dp(strokeWidthDp),
    )
    return RippleDrawable(ColorStateList.valueOf(pressedColor), shape, null)
}

internal fun makeIconButton(
    context: Context,
    icon: String,
    sizeDp: Int = 40,
    iconDp: Int = 22,
    tint: Int = 0xFF3C4043.toInt(),
    backgroundColor: Int = Color.TRANSPARENT,
    pressedColor: Int = 0x1F5F6368,
    description: String = icon,
    onClick: () -> Unit,
): FrameLayout {
    val button = FrameLayout(context).apply {
        isClickable = true
        isFocusable = true
        contentDescription = description
        background = rippleBackground(context, backgroundColor, sizeDp / 2f, pressedColor)
        minimumWidth = context.dp(sizeDp)
        minimumHeight = context.dp(sizeDp)
        setOnClickListener { onClick() }
    }
    val iconView = BrowserIconView(context, icon).apply { this.tint = tint }
    button.addView(
        iconView,
        FrameLayout.LayoutParams(context.dp(iconDp), context.dp(iconDp), Gravity.CENTER),
    )
    return button
}

internal fun makeText(
    context: Context,
    text: CharSequence,
    sizeSp: Float,
    color: Int,
    weight: Int = Typeface.NORMAL,
): TextView = TextView(context).apply {
    this.text = text
    textSize = sizeSp
    setTextColor(color)
    typeface = Typeface.create("sans-serif", weight)
    gravity = Gravity.CENTER_VERTICAL
    includeFontPadding = false
}

internal fun addLinearChild(
    parent: LinearLayout,
    child: View,
    width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    weight: Float = 0f,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
) {
    val params = LinearLayout.LayoutParams(width, height, weight)
    params.setMargins(left, top, right, bottom)
    parent.addView(child, params)
}
