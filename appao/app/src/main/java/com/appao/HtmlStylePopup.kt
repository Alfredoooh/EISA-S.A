package com.appao

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

/**
 * Popup nativo seguindo os parâmetros visuais do menu HTML:
 *
 * HTML:
 *   min-width: 230px
 *   padding: 8px
 *   border-radius: 26px
 *   scale(.5) -> scale(1)
 *   opacity 0 -> 1
 *   item delay: 35ms
 *   item translateY: -8px
 *   item radius: 18px
 */
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

    fun show(
        anchor: View,
        items: List<Item>,
        placeAbove: Boolean = false
    ): PopupWindow {

        val context = anchor.context
        val density = context.resources.displayMetrics.density

        fun dp(value: Int): Int {
            return (value * density + 0.5f).toInt()
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(MENU_PADDING_DP),
                dp(MENU_PADDING_DP),
                dp(MENU_PADDING_DP),
                dp(MENU_PADDING_DP)
            )

            background = rounded(
                ContextCompat.getColor(context, R.color.popupBg),
                dp(MENU_RADIUS_DP)
            )

            clipToOutline = true
        }

        val popup = PopupWindow(
            root,
            dp(MIN_WIDTH_DP),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = true
            elevation = dp(18).toFloat()
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED

            setOnDismissListener {
                root.animate().cancel()

                for (index in 0 until root.childCount) {
                    root.getChildAt(index).animate().cancel()
                }
            }
        }

        items.forEach { item ->

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                minimumHeight = dp(48)

                setPadding(
                    dp(14),
                    dp(13),
                    dp(14),
                    dp(13)
                )

                background = rowBackground(context, density)

                isClickable = true
                isFocusable = true

                alpha = 0f
                translationY = -dp(8).toFloat()
            }

            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dp(24),
                    dp(24)
                )

                if (!item.iconName.isNullOrBlank()) {
                    IconLoader.applyPng(
                        this,
                        item.iconName
                    )
                }
            }

            row.addView(icon)

            val label = TextView(context).apply {
                text = item.label
                textSize = 15f

                setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.text
                    )
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

            val labelParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(14)
            }

            row.addView(
                label,
                labelParams
            )

            if (item.checked) {
                val check = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        dp(18),
                        dp(18)
                    )

                    IconLoader.applySvg(
                        this,
                        "check",
                        R.color.iconTint
                    )
                }

                row.addView(check)
            }

            row.setOnClickListener {
                popup.dismiss()

                row.post {
                    try {
                        item.onClick()
                    } catch (t: Throwable) {
                        android.util.Log.e(
                            "HtmlStylePopup",
                            "Popup action failed",
                            t
                        )
                    }
                }
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

        val visibleFrame = android.graphics.Rect()
        anchor.getWindowVisibleDisplayFrame(visibleFrame)

        val screenLeft = visibleFrame.left
        val screenTop = visibleFrame.top
        val screenRight = visibleFrame.right
        val screenBottom = visibleFrame.bottom

        val anchorLeft = location[0]
        val anchorTop = location[1]
        val anchorRight = anchorLeft + anchor.width
        val anchorBottom = anchorTop + anchor.height

        val margin = dp(12)

        val x = if (screenRight - anchorRight >= width + margin) {
            anchorRight - width
        } else {
            anchorLeft
        }.coerceIn(
            screenLeft + dp(8),
            screenRight - width - dp(8)
        )

        val naturalY = if (placeAbove) {
            anchorTop - height - margin
        } else {
            anchorBottom
        }

        val y = naturalY.coerceIn(
            screenTop + dp(8),
            screenBottom - height - dp(8)
        )

        popup.showAtLocation(
            anchor,
            Gravity.TOP or Gravity.START,
            x,
            y
        )

        /*
         * Mesmo comportamento base do HTML:
         *
         * transform: scale(.5)
         * opacity: 0
         *
         * -> scale(1)
         * -> opacity(1)
         */
        root.pivotX = (
            anchorRight - x
        ).coerceIn(
            0f,
            width.toFloat()
        )

        root.pivotY = if (placeAbove) {
            height.toFloat()
        } else {
            0f
        }

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

        /*
         * Animação dos itens:
         *
         * HTML:
         * opacity: 0
         * translateY(-8px)
         * delay de 35ms
         */
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

    private fun rowBackground(
        context: android.content.Context,
        density: Float
    ): StateListDrawable {

        val pressed = rounded(
            ContextCompat.getColor(
                context,
                R.color.card2
            ),
            18f * density
        )

        val normal = rounded(
            Color.TRANSPARENT,
            18f * density
        )

        return StateListDrawable().apply {

            addState(
                intArrayOf(android.R.attr.state_pressed),
                pressed
            )

            addState(
                intArrayOf(),
                normal
            )
        }
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun rounded(
        color: Int,
        radius: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
        }
    }
}