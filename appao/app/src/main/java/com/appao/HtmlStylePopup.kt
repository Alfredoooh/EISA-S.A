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

/**
 * Popup nativo seguindo o comportamento visual do .menu do HTML.
 *
 * HTML:
 *
 * .menu {
 *     min-width:230px;
 *     padding:8px;
 *     border-radius:26px;
 *     opacity:0;
 *     transform:scale(.5);
 * }
 *
 * .menu.in {
 *     opacity:1;
 *     transform:none;
 * }
 *
 * Cada item:
 *
 *     opacity:0;
 *     transform:translateY(-8px);
 *     transition:
 *         opacity .3s,
 *         transform .45s var(--spring);
 *
 * Com atraso de 35ms entre os itens.
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

    private const val ITEM_TRANSLATE_DP = 8

    fun show(
        anchor: View,
        items: List<Item>,
        placeAbove: Boolean = false
    ): PopupWindow {

        val context =
            anchor.context

        val density =
            context.resources
                .displayMetrics
                .density

        val root =
            LinearLayout(context).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(
                        density,
                        MENU_PADDING_DP
                    ),
                    dp(
                        density,
                        MENU_PADDING_DP
                    ),
                    dp(
                        density,
                        MENU_PADDING_DP
                    ),
                    dp(
                        density,
                        MENU_PADDING_DP
                    )
                )

                background =
                    rounded(
                        popupColor(context),
                        dp(
                            density,
                            MENU_RADIUS_DP
                        )
                    )

                clipToOutline =
                    true

                clipChildren =
                    true

                clipToPadding =
                    true
            }

        val popup =
            PopupWindow(
                root,
                dp(
                    density,
                    MIN_WIDTH_DP
                ),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {

                setBackgroundDrawable(
                    ColorDrawable(
                        Color.TRANSPARENT
                    )
                )

                isFocusable =
                    true

                isOutsideTouchable =
                    true

                elevation =
                    dp(
                        density,
                        18
                    ).toFloat()

                inputMethodMode =
                    PopupWindow.INPUT_METHOD_NOT_NEEDED

                setOnDismissListener {

                    root.animate()
                        .cancel()

                    for (
                        index in 0 until root.childCount
                    ) {

                        root.getChildAt(
                            index
                        )
                            .animate()
                            .cancel()
                    }
                }
            }

        items.forEach { item ->

            val row =
                LinearLayout(context).apply {

                    orientation =
                        LinearLayout.HORIZONTAL

                    gravity =
                        Gravity.CENTER_VERTICAL

                    minimumHeight =
                        dp(
                            density,
                            48
                        )

                    setPadding(
                        dp(density, 14),
                        dp(density, 13),
                        dp(density, 14),
                        dp(density, 13)
                    )

                    background =
                        rowBackground(
                            context,
                            density
                        )

                    isClickable =
                        true

                    isFocusable =
                        true

                    alpha =
                        0f

                    translationY =
                        -dp(
                            density,
                            ITEM_TRANSLATE_DP
                        ).toFloat()
                }

            val icon =
                ImageView(context).apply {

                    layoutParams =
                        LinearLayout.LayoutParams(
                            dp(density, 24),
                            dp(density, 24)
                        )

                    if (
                        !item.iconName
                            .isNullOrBlank()
                    ) {

                        IconLoader.applyPng(
                            this,
                            item.iconName!!
                        )
                    }
                }

            row.addView(icon)

            val label =
                TextView(context).apply {

                    text =
                        item.label

                    textSize =
                        15f

                    setTextColor(
                        textColor(context)
                    )

                    setTypeface(
                        android.graphics.Typeface.create(
                            "sans-serif-medium",
                            android.graphics.Typeface.NORMAL
                        )
                    )

                    maxLines =
                        1

                    ellipsize =
                        android.text.TextUtils.TruncateAt.END
                }

            val labelParams =
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {

                    marginStart =
                        dp(
                            density,
                            14
                        )
                }

            row.addView(
                label,
                labelParams
            )

            if (item.checked) {

                val check =
                    ImageView(context).apply {

                        layoutParams =
                            LinearLayout.LayoutParams(
                                dp(density, 18),
                                dp(density, 18)
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
                            "Popup item action failed",
                            t
                        )
                    }
                }
            }

            root.addView(row)
        }

        root.measure(
            View.MeasureSpec.makeMeasureSpec(
                dp(
                    density,
                    MAX_WIDTH_DP
                ),
                View.MeasureSpec.AT_MOST
            ),
            View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            )
        )

        val width =
            max(
                dp(
                    density,
                    MIN_WIDTH_DP
                ),
                root.measuredWidth
            )

        val height =
            root.measuredHeight

        popup.width =
            width

        val location =
            IntArray(2)

        anchor.getLocationOnScreen(
            location
        )

        val visibleFrame =
            android.graphics.Rect()

        anchor.getWindowVisibleDisplayFrame(
            visibleFrame
        )

        val screenLeft =
            visibleFrame.left

        val screenRight =
            visibleFrame.right

        val screenTop =
            visibleFrame.top

        val screenBottom =
            visibleFrame.bottom

        val anchorLeft =
            location[0]

        val anchorTop =
            location[1]

        val anchorRight =
            anchorLeft +
                anchor.width

        val anchorBottom =
            anchorTop +
                anchor.height

        val margin =
            dp(
                density,
                12
            )

        val left =
            if (
                screenRight -
                    anchorRight >=
                width +
                    margin
            ) {

                anchorRight -
                    width

            } else {

                anchorLeft -
                    max(
                        0,
                        width -
                            anchor.width
                    )
            }
                .coerceIn(
                    screenLeft +
                        dp(
                            density,
                            8
                        ),
                    screenRight -
                        width -
                        dp(
                            density,
                            8
                        )
                )

        val naturalTop =
            if (placeAbove) {

                anchorTop -
                    height -
                    margin

            } else {

                anchorBottom
            }

        val top =
            naturalTop.coerceIn(
                screenTop +
                    dp(
                        density,
                        8
                    ),
                screenBottom -
                    height -
                    dp(
                        density,
                        8
                    )
            )

        popup.showAtLocation(
            anchor,
            Gravity.TOP or
                Gravity.START,
            left,
            top
        )

        /*
         * HTML:
         *
         * transform:scale(.5)
         * opacity:0
         *
         * -> scale(1)
         * -> opacity:1
         */

        root.pivotX =
            (
                anchorRight -
                    left
                )
                .toFloat()
                .coerceIn(
                    0f,
                    width.toFloat()
                )

        root.pivotY =
            if (placeAbove) {
                height.toFloat()
            } else {
                0f
            }

        root.scaleX =
            0.5f

        root.scaleY =
            0.5f

        root.alpha =
            0f

        root.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(
                500L
            )
            .setInterpolator(
                Curves.SPRING
            )
            .start()

        root.animate()
            .alpha(1f)
            .setDuration(
                220L
            )
            .setInterpolator(
                Curves.SMOOTH
            )
            .start()

        /*
         * HTML:
         *
         * opacity:0
         * translateY(-8px)
         *
         * delay:
         * 35ms
         */

        for (
            index in 0 until root.childCount
        ) {

            val row =
                root.getChildAt(
                    index
                )

            row.postDelayed(
                {

                    row.animate()
                        .translationY(0f)
                        .setDuration(
                            450L
                        )
                        .setInterpolator(
                            Curves.SPRING
                        )
                        .start()

                    row.animate()
                        .alpha(1f)
                        .setDuration(
                            300L
                        )
                        .setInterpolator(
                            Curves.SMOOTH
                        )
                        .start()

                },
                index *
                    ITEM_DELAY_MS
            )
        }

        anchor.tag =
            popup

        return popup
    }

    private fun popupColor(
        context: Context
    ): Int {

        return ContextCompat.getColor(
            context,
            R.color.popupBg
        )
    }

    private fun textColor(
        context: Context
    ): Int {

        return ContextCompat.getColor(
            context,
            R.color.text
        )
    }

    private fun pressedColor(
        context: Context
    ): Int {

        return ContextCompat.getColor(
            context,
            R.color.card2
        )
    }

    private fun rowBackground(
        context: Context,
        density: Float
    ): StateListDrawable {

        val radius =
            dp(
                density,
                ITEM_RADIUS_DP
            )

        return StateListDrawable().apply {

            addState(
                intArrayOf(
                    android.R.attr.state_pressed
                ),
                rounded(
                    pressedColor(context),
                    radius
                )
            )

            addState(
                intArrayOf(),
                rounded(
                    Color.TRANSPARENT,
                    radius
                )
            )
        }
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            shape =
                GradientDrawable.RECTANGLE

            setColor(
                color
            )

            cornerRadius =
                radius.toFloat()
        }
    }

    private fun dp(
        density: Float,
        value: Int
    ): Int {

        return (
            value *
                density +
                0.5f
        ).toInt()
    }
}