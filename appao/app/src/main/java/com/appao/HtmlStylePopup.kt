package com.appao

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.graphics.drawable.StateListDrawable
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.max

/**
 * Native PopupWindow that mirrors the HTML .menu behavior:
 * 230dp minimum width, 26dp radius, scale(.5)->1, alpha fade,
 * 35ms item staggering, 18dp item radius and spring/out curves.
 */
object HtmlStylePopup {
    data class Item(
        val label: String,
        val iconName: String? = null,
        val checked: Boolean = false,
        val onClick: () -> Unit
    )

    private const val MIN_WIDTH_DP = 230
    private const val H_PADDING_DP = 8
    private const val ITEM_RADIUS_DP = 18
    private const val MENU_RADIUS_DP = 26
    private const val ITEM_DELAY_MS = 35L

    fun show(anchor: View, items: List<Item>, placeAbove: Boolean = false): PopupWindow {
        val ctx = anchor.context
        val d = ctx.resources.displayMetrics.density
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(d, H_PADDING_DP), dp(d, H_PADDING_DP), dp(d, H_PADDING_DP), dp(d, H_PADDING_DP))
            background = rounded(ContextCompatColor.popup(ctx), dp(d, MENU_RADIUS_DP))
            clipToOutline = true
        }

        val popup = PopupWindow(
            root,
            dp(d, MIN_WIDTH_DP),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(d, 18).toFloat()
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setOnDismissListener {
                root.animate().cancel()
            }
        }

        items.forEachIndexed { index, item ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(d, 48)
                setPadding(dp(d, 14), dp(d, 13), dp(d, 14), dp(d, 13))
                background = rowBackground(ctx, d)
                isClickable = true
                isFocusable = true
                alpha = 0f
                translationY = -dp(d, 8).toFloat()
                scaleX = 1f
                scaleY = 1f
            }

            val icon = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(d, 24), dp(d, 24))
                if (!item.iconName.isNullOrBlank()) IconLoader.applyPng(this, item.iconName!!)
            }
            row.addView(icon)

            val label = TextView(ctx).apply {
                text = item.label
                setTextColor(ContextCompatColor.text(ctx))
                textSize = 15f
                setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))
                maxLines = 1
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginStart = dp(d, 14)
            label.layoutParams = lp
            row.addView(label)

            if (item.checked) {
                val check = ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(d, 18), dp(d, 18))
                    IconLoader.applySvg(this, "check", R.color.iconTint)
                }
                row.addView(check)
            }

            row.setOnClickListener {
                popup.dismiss()
                row.post { item.onClick() }
            }
            root.addView(row)

        }

        root.measure(
            View.MeasureSpec.makeMeasureSpec(dp(d, 320), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val width = max(dp(d, MIN_WIDTH_DP), root.measuredWidth)
        val height = root.measuredHeight
        popup.width = width

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val visible = android.graphics.Rect()
        anchor.getWindowVisibleDisplayFrame(visible)
        val screenRight = visible.right
        val screenTop = visible.top
        val screenBottom = visible.bottom
        val anchorLeft = loc[0]
        val anchorTop = loc[1]
        val anchorRight = anchorLeft + anchor.width

        val x = if (screenRight - anchorRight >= width + dp(d, 12)) {
            anchorRight - width
        } else {
            anchorLeft.coerceAtLeast(dp(d, 12))
        }.coerceIn(
            visible.left + dp(d, 8),
            screenRight - width - dp(d, 8)
        )
        val naturalTop = if (placeAbove) anchorTop - height - dp(d, 12) else anchorTop
        val y = naturalTop.coerceIn(screenTop + dp(d, 8), screenBottom - height - dp(d, 8))

        popup.showAtLocation(anchor, Gravity.TOP or Gravity.START, x, y)

        root.pivotX = (anchorRight - x).toFloat().coerceIn(0f, width.toFloat())
        root.pivotY = if (placeAbove) height.toFloat() else 0f
        root.scaleX = 0.5f
        root.scaleY = 0.5f
        root.alpha = 0f
        root.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500L)
            .setInterpolator(Curves.SPRING)
            .start()
        root.animate().alpha(1f).setDuration(220L).setInterpolator(Curves.SMOOTH).start()

        for (i in 0 until root.childCount) {
            val row = root.getChildAt(i)
            row.postDelayed({
                row.animate()
                    .translationY(0f)
                    .setDuration(450L)
                    .setInterpolator(Curves.SPRING)
                    .start()
                row.animate()
                    .alpha(1f)
                    .setDuration(300L)
                    .setInterpolator(Curves.SMOOTH)
                    .start()
            }, i * ITEM_DELAY_MS)
        }

        anchor.tag = popup
        return popup
    }

    private object ContextCompatColor {
        fun popup(ctx: Context): Int = ContextCompat.getColor(ctx, R.color.popupBg)
        fun text(ctx: Context): Int = androidx.core.content.ContextCompat.getColor(ctx, R.color.text)
        fun card2(ctx: Context): Int = androidx.core.content.ContextCompat.getColor(ctx, R.color.card2)
    }

    private fun rowBackground(ctx: Context, density: Float): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(ContextCompatColor.card2(ctx), dp(density, ITEM_RADIUS_DP)))
        addState(intArrayOf(), rounded(Color.TRANSPARENT, dp(density, ITEM_RADIUS_DP)))
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(density: Float, value: Int): Int = (value * density + 0.5f).toInt()
}
