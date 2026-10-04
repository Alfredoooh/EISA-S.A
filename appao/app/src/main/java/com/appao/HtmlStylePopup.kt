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

/** Native popup matching the supplied HTML menu geometry and motion. */
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
    private const val ITEM_TRANSLATE_DP = 8

    fun show(
        anchor: View,
        items: List<Item>,
        placeAbove: Boolean = false
    ): PopupWindow {
        val context = anchor.context
        val density = context.resources.displayMetrics.density

        fun dp(value: Int) = (value * density + .5f).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(MENU_PADDING_DP), dp(MENU_PADDING_DP),
                dp(MENU_PADDING_DP), dp(MENU_PADDING_DP)
            )
            background = popupBackground(context, dp(MENU_RADIUS_DP))
            clipToOutline = true
            clipChildren = true
        }

        val popup = PopupWindow(
            root,
            dp(MIN_WIDTH_DP),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isFocusable = true
            isOutsideTouchable = true
            elevation = dp(18).toFloat()
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            animationStyle = 0
        }

        items.forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(dp(14), dp(13), dp(14), dp(13))
                background = rowBackground(context, density)
                isClickable = true
                isFocusable = true
                alpha = 0f
                translationY = -dp(ITEM_TRANSLATE_DP).toFloat()
            }

            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                if (!item.iconName.isNullOrBlank()) {
                    IconLoader.applyPng(this, item.iconName!!)
                }
            }
            row.addView(icon)

            val label = TextView(context).apply {
                text = item.label
                textSize = 15f
                setTextColor(
                    ContextCompat.getColor(context, R.color.text)
                )
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
                ).apply { marginStart = dp(14) }
            )

            if (item.checked) {
                val check = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
                    IconLoader.applySvg(this, "check", R.color.iconTint)
                }
                row.addView(check)
            }

            row.setOnClickListener {
                // Execute while the hosting Activity is still alive, then
                // dismiss. This is critical for theme changes and navigation:
                // a delayed callback can be dropped when AppCompat recreates
                // the Activity immediately after the selection.
                runCatching {
                    item.onClick()
                }.onFailure {
                    android.util.Log.e(
                        "HtmlStylePopup",
                        "Popup action failed",
                        it
                    )
                }
                popup.dismiss()
            }

            root.addView(row)
        }

        root.measure(
            View.MeasureSpec.makeMeasureSpec(
                dp(MAX_WIDTH_DP),
                View.MeasureSpec.AT_MOST
            ),
            View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            )
        )

        val width = max(
            dp(MIN_WIDTH_DP),
            root.measuredWidth
        )
        val height = root.measuredHeight
        popup.width = width

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)

        val frame = android.graphics.Rect()
        anchor.getWindowVisibleDisplayFrame(frame)

        val anchorLeft = location[0]
        val anchorTop = location[1]
        val anchorRight = anchorLeft + anchor.width
        val anchorBottom = anchorTop + anchor.height
        val margin = dp(12)

        val left = if (frame.right - anchorRight >= width + margin) {
            anchorRight - width
        } else {
            anchorLeft - max(0, width - anchor.width)
        }.coerceIn(
            frame.left + dp(8),
            frame.right - width - dp(8)
        )

        val naturalTop = if (placeAbove) {
            anchorTop - height - margin
        } else {
            anchorBottom
        }

        val top = naturalTop.coerceIn(
            frame.top + dp(8),
            frame.bottom - height - dp(8)
        )

        popup.showAtLocation(
            anchor,
            Gravity.TOP or Gravity.START,
            left,
            top
        )

        root.pivotX = (
            anchorRight - left
        ).toFloat().coerceIn(
            0f,
            width.toFloat()
        )
        root.pivotY = if (placeAbove) height.toFloat() else 0f
        root.scaleX = .5f
        root.scaleY = .5f
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

        for (index in 0 until root.childCount) {
            val row = root.getChildAt(index)
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
                index * ITEM_DELAY_MS
            )
        }

        return popup
    }

    private fun popupBackground(
        context: Context,
        radius: Int
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(
            ContextCompat.getColor(
                context,
                R.color.popupBg
            )
        )
        cornerRadius = radius.toFloat()
        setStroke(
            (context.resources.displayMetrics.density + .5f).toInt(),
            ContextCompat.getColor(
                context,
                R.color.popupBorder
            )
        )
    }

    private fun rowBackground(
        context: Context,
        density: Float
    ): StateListDrawable {
        val radius = 18f * density
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(
                        ContextCompat.getColor(
                            context,
                            R.color.card2
                        )
                    )
                    cornerRadius = radius
                }
            )
            addState(
                intArrayOf(),
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                    cornerRadius = radius
                }
            )
        }
    }
}
