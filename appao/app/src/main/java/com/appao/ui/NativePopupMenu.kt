package com.appao.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.appao.AccentColorManager
import com.appao.R


/** Compact, scrollable popup used by the browser. Rows keep the same restrained dimensions on every screen. */
object NativePopupMenu {
    data class Item(
        val id: Int,
        val title: String,
        val iconName: String? = null,
        val iconColor: Int? = null,
        val checked: Boolean = false,
        val iconResId: Int? = null
    )

    fun show(
        anchor: View,
        items: List<Item>,
        alignLeft: Boolean = false,
        placeAbove: Boolean = true,
        onClick: (Item) -> Unit
    ): PopupWindow {
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()

        val surface = ContextCompat.getColor(context, R.color.dialogSurface)
        val border = ContextCompat.getColor(context, R.color.border)
        val textColor = ContextCompat.getColor(context, R.color.text)
        val iconColor = ContextCompat.getColor(context, R.color.iconTint)
        val primaryColor = runCatching {
            AccentColorManager.resolvedOr(context, R.color.pri)
        }.getOrDefault(ContextCompat.getColor(context, R.color.pri))

        var popupWindow: PopupWindow? = null
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(surface)
                setStroke(dp(1), border)
            }
            clipToOutline = true
        }
        items.forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(40)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                isClickable = true
                isFocusable = true
                foreground = ContextCompat.getDrawable(context, R.drawable.bg_transparent_pressed)
            }
            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val source = if (item.iconResId != null) {
                    ContextCompat.getDrawable(context, item.iconResId)?.mutate()
                } else {
                    IconLoader.menuDrawable(context, item.iconName ?: "circle")?.mutate()
                }
                if (source != null) {
                    source.setTint(item.iconColor ?: iconColor)
                    setImageDrawable(source)
                } else {
                    visibility = View.INVISIBLE
                }
            }
            row.addView(icon)
            val label = TextView(context).apply {
                text = item.title
                textSize = 13.5f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(textColor)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(11), 0, dp(6), 0)
            }
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (item.checked) {
                val check = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(16), dp(16))
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    val drawable = IconLoader.menuDrawable(context, "check")?.mutate()
                    drawable?.setTint(primaryColor)
                    setImageDrawable(drawable)
                }
                row.addView(check)
            }
            row.setOnClickListener {
                popupWindow?.dismiss()
                onClick(item)
            }
            card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
        }

        val width = minOf(dp(306), (context.resources.displayMetrics.widthPixels - dp(20)).coerceAtLeast(dp(220)))
        val maxHeight = minOf(
            dp(430),
            (context.resources.displayMetrics.heightPixels - dp(112)).coerceAtLeast(dp(180))
        )
        card.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val height = card.measuredHeight.coerceAtMost(maxHeight).coerceAtLeast(dp(42))
        val scroller = ScrollView(context).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(card, ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
            background = ColorDrawable(Color.TRANSPARENT)
        }
        val popup = PopupWindow(scroller, width, height, true).apply {
            isOutsideTouchable = true
            elevation = dp(12).toFloat()
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) overlapAnchor = false
        }
        popupWindow = popup

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val screenHeight = context.resources.displayMetrics.heightPixels
        val belowSpace = screenHeight - (location[1] + anchor.height)
        val aboveSpace = location[1]
        val openAbove = (placeAbove && aboveSpace >= minOf(height, dp(180))) || belowSpace < height && aboveSpace > belowSpace
        val xOffset = if (alignLeft) 0 else anchor.width - width
        val yOffset = if (openAbove) -height - anchor.height else 0
        popup.showAsDropDown(anchor, xOffset, yOffset)
        return popup
    }

}
