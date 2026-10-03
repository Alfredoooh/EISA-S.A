package com.appao

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.max

/** Native popup reproducing the HTML .menu motion and geometry. */
object HtmlStylePopup {

    data class Item(
        val label: String,
        val iconName: String? = null,
        val checked: Boolean = false,
        val onClick: () -> Unit
    )

    private const val MIN_WIDTH_DP = 230
    private const val MAX_WIDTH_DP = 320
    private const val MENU_PADDING_DP = 8
    private const val MENU_RADIUS_DP = 26
    private const val ITEM_RADIUS_DP = 18
    private const val ITEM_DELAY_MS = 35L
    private const val ITEM_START_TRANSLATE_DP = -8

    fun show(
        anchor: View,
        items: List<Item>,
        placeAbove: Boolean = false
    ): PopupWindow {
        val context = anchor.context
        val density = context.resources.displayMetrics.density

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(density, MENU_PADDING_DP),
                dp(density, MENU_PADDING_DP),
                dp(density, MENU_PADDING_DP),
                dp(density, MENU_PADDING_DP)
            )
            background = rounded(
                ContextCompat.getColor(context, R.color.popupBg),
                dp(density, MENU_RADIUS_DP).toFloat()
            )
            clipToOutline = true
            clipChildren = true
            clipToPadding = true
        }

        lateinit var popup: PopupWindow
        popup = PopupWindow(
            root,
            dp(density, MIN_WIDTH_DP),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isFocusable = true
            isOutsideTouchable = true
            elevation = dp(density, 18).toFloat()
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setOnDismissListener {
                root.animate().cancel()
                for (i in 0 until root.childCount) {
                    root.getChildAt(i).animate().cancel()
                }
            }
        }

        items.forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(density, 48)
                setPadding(
                    dp(density, 14),
                    dp(density, 13),
                    dp(density, 14),
                    dp(density, 13)
                )
                background = rowBackground(context, density)
                isClickable = true
                isFocusable = true
                alpha = 0f
                translationY = dp(density, ITEM_START_TRANSLATE_DP).toFloat()
            }

            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dp(density, 24),
                    dp(density, 24)
                )
                item.iconName?.takeIf { it.isNotBlank() }?.let {
                    IconLoader.applyPng(this, it)
                }
            }
            row.addView(icon)

            val label = TextView(context).apply {
                text = item.label
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.text))
                setTypeface(
                    android.graphics.Typeface.create(
                        "sans-serif-medium",
                        android.graphics.Typeface.NORMAL
                    )
                )
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            row.addView(
                label,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginStart = dp(density, 14) }
            )

            if (item.checked) {
                val check = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        dp(density, 18),
                        dp(density, 18)
                    )
                    IconLoader.applySvg(this, "check", R.color.iconTint)
                }
                row.addView(check)
            }

            row.setOnClickListener {
                /*
                 * Do NOT use row.post { ... } here. When PopupWindow.dismiss()
                 * detaches the content view, a Runnable posted to that row can
                 * be dropped. Dispatch through the still-attached anchor.
                 */
                val action = item.onClick
                popup.dismiss()
                anchor.postDelayed(
                    {
                        if (anchor.isAttachedToWindow) {
                            try {
                                action()
                            } catch (t: Throwable) {
                                android.util.Log.e(
                                    "HtmlStylePopup",
                                    "Popup action failed",
                                    t
                                )
                            }
                        } else {
                            try {
                                action()
                            } catch (t: Throwable) {
                                android.util.Log.e(
                                    "HtmlStylePopup",
                                    "Popup action failed after detach",
                                    t
                                )
                            }
                        }
                    },
                    16L
                )
            }

            root.addView(row)
        }

        root.measure(
            View.MeasureSpec.makeMeasureSpec(
                dp(density, MAX_WIDTH_DP),
                View.MeasureSpec.AT_MOST
            ),
            View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            )
        )

        val width = max(
            dp(density, MIN_WIDTH_DP),
            root.measuredWidth
        )
        val height = root.measuredHeight
        popup.width = width

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)

        val visible = android.graphics.Rect()
        anchor.getWindowVisibleDisplayFrame(visible)

        val anchorLeft = location[0]
        val anchorTop = location[1]
        val anchorRight = anchorLeft + anchor.width
        val anchorBottom = anchorTop + anchor.height
        val margin = dp(density, 12)

        val x = if (visible.right - anchorRight >= width + margin) {
            anchorRight - width
        } else {
            anchorLeft
        }.coerceIn(
            visible.left + dp(density, 8),
            visible.right - width - dp(density, 8)
        )

        val naturalY = if (placeAbove) {
            anchorTop - height - margin
        } else {
            anchorBottom
        }

        val y = naturalY.coerceIn(
            visible.top + dp(density, 8),
            visible.bottom - height - dp(density, 8)
        )

        popup.showAtLocation(
            anchor,
            Gravity.TOP or Gravity.START,
            x,
            y
        )

        root.pivotX = (anchorRight - x)
            .toFloat()
            .coerceIn(0f, width.toFloat())
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

        root.animate()
            .alpha(1f)
            .setDuration(220L)
            .setInterpolator(Curves.SMOOTH)
            .start()

        for (i in 0 until root.childCount) {
            val row = root.getChildAt(i)
            row.postDelayed(
                {
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
                },
                i * ITEM_DELAY_MS
            )
        }

        anchor.tag = popup
        return popup
    }

    private fun rowBackground(
        context: Context,
        density: Float
    ): StateListDrawable = StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_pressed),
            rounded(
                ContextCompat.getColor(context, R.color.card2),
                dp(density, ITEM_RADIUS_DP).toFloat()
            )
        )
        addState(
            intArrayOf(),
            rounded(
                Color.TRANSPARENT,
                dp(density, ITEM_RADIUS_DP).toFloat()
            )
        )
    }

    private fun rounded(
        color: Int,
        radius: Float
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(
        density: Float,
        value: Int
    ): Int = (value * density + 0.5f).toInt()
}
