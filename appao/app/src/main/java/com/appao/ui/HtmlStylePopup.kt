package com.appao

import android.app.Dialog
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
import android.view.WindowManager
import android.widget.TextView
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import kotlin.math.max

/**
 * Anchored popup that follows the HTML .menu geometry and motion.
 *
 * This remains the popup used by Settings for Theme and Language.
 * Bottom sheets and Material dialogs are separate components.
 */
object HtmlStylePopup {

    data class Item(
        val label: String,
        val iconName: String? = null,
        val checked: Boolean = false,
        val iconColor: Int? = null,
        val highlightColor: Int? = null,
        val onClick: () -> Unit
    )

    private const val MIN_WIDTH_DP = 230
    private const val MAX_WIDTH_DP = 320
    private const val MENU_PADDING_DP = 8
    private const val MENU_RADIUS_DP = 22
    private const val ITEM_RADIUS_DP = 18
    private const val ITEM_DELAY_MS = 20L
    private const val ITEM_TRANSLATE_DP = 6

    fun show(
        anchor: View,
        items: List<Item>,
        placeAbove: Boolean = true,
        alignLeft: Boolean = false
    ): Dialog {

        val context =
            anchor.context

        val density =
            context.resources
                .displayMetrics
                .density

        fun dp(
            value: Int
        ): Int =
            (
                value *
                    density +
                    0.5f
                ).toInt()

        // Dim-behind is provided by Android's native Dialog window. No custom
        // decor overlay or manually tinted status bar is inserted.
        val compactColorMenu = items.size >= 6 && items.all { it.iconColor != null }

        val root =
            LinearLayout(context).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(MENU_PADDING_DP),
                    dp(MENU_PADDING_DP),
                    dp(MENU_PADDING_DP),
                    dp(MENU_PADDING_DP)
                )

                background =
                    popupBackground(
                        context,
                        dp(MENU_RADIUS_DP)
                    )

                clipToOutline =
                    true

                clipChildren =
                    true
            }

        val rowsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            addView(rowsContainer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val popup = Dialog(context).apply {
            setContentView(root)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                // Native Android window dim; no view overlay and no status-bar tint hacks.
                setDimAmount(0.20f)
            }
            setOnDismissListener {
                root.animate().cancel()
                for (index in 0 until rowsContainer.childCount) {
                    rowsContainer.getChildAt(index).animate().cancel()
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
                        dp(if (compactColorMenu) 36 else 48)

                    setPadding(
                        dp(if (compactColorMenu) 12 else 14),
                        dp(if (compactColorMenu) 5 else 13),
                        dp(if (compactColorMenu) 12 else 14),
                        dp(if (compactColorMenu) 5 else 13)
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
                        dp(ITEM_TRANSLATE_DP).toFloat()
                }

            val hasIcon = item.iconColor != null || !item.iconName.isNullOrBlank()
            val icon =
                ImageView(context).apply {

                    layoutParams =
                        LinearLayout.LayoutParams(
                            if (hasIcon) dp(if (compactColorMenu) 20 else 24) else 0,
                            if (hasIcon) dp(if (compactColorMenu) 20 else 24) else 0
                        )
                    visibility = if (hasIcon) View.VISIBLE else View.GONE

                    scaleType =
                        ImageView.ScaleType.CENTER_INSIDE

                    if (item.iconColor != null) {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(item.iconColor)
                            if (item.checked) {
                                setStroke(dp(2), AccentColorManager.onPrimaryColor(context))
                            }
                        }
                    } else if (!item.iconName.isNullOrBlank()) {
                        IconLoader.applyIcon(this, item.iconName!!, if (item.highlightColor == null) R.color.iconTint else 0)
                        item.highlightColor?.let { setColorFilter(it, android.graphics.PorterDuff.Mode.SRC_IN) }
                    }
                }

            row.addView(
                icon
            )

            val label =
                TextView(context).apply {

                    text =
                        item.label

                    textSize =
                        if (compactColorMenu) 13.5f else 15f

                    setTextColor(
                        item.highlightColor ?: ContextCompat.getColor(
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

                    maxLines =
                        1

                    ellipsize =
                        android.text.TextUtils.TruncateAt.END
                }

            row.addView(
                label,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {

                    marginStart =
                        if (hasIcon) dp(14) else 0
                }
            )

            if (
                item.checked
            ) {

                val check =
                    ImageView(context).apply {

                        layoutParams =
                            LinearLayout.LayoutParams(
                                dp(18),
                                dp(18)
                            )

                        scaleType =
                            ImageView.ScaleType.CENTER

                        IconLoader.applyIcon(this, "check_mark", if (item.highlightColor == null) R.color.iconTint else 0)
                        item.highlightColor?.let { setColorFilter(it, android.graphics.PorterDuff.Mode.SRC_IN) }
                    }

                row.addView(
                    check
                )
            }

            row.setOnClickListener {

                /*
                 * Dismiss before executing the callback. Theme changes can
                 * recreate the Activity; the callback runs from the still-live
                 * anchor after the popup has released its window token.
                 */
                popup.dismiss()

                anchor.post {

                    runCatching {
                        item.onClick()
                    }.onFailure { error ->

                        android.util.Log.e(
                            "HtmlStylePopup",
                            "Popup action failed",
                            error
                        )
                    }
                }
            }

            rowsContainer.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }

        // Use the anchor's screen position and translate it into the dialog window's
        // content coordinate system. This keeps the popup in front of the button even
        // when system bars or edge-to-edge insets are present.
        val decor = anchor.rootView
        val display = context.resources.displayMetrics
        val visibleFrame = android.graphics.Rect()
        decor.getWindowVisibleDisplayFrame(visibleFrame)
        val decorScreen = IntArray(2).also { decor.getLocationOnScreen(it) }
        val anchorScreen = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val edge = dp(8)
        val windowOriginX = decorScreen[0]
        val windowOriginY = decorScreen[1]
        val availableLeftScreen = maxOf(visibleFrame.left, windowOriginX) + edge
        val availableTopScreen = maxOf(visibleFrame.top, windowOriginY) + edge
        val availableRightScreen = minOf(visibleFrame.right, windowOriginX + (decor.width.takeIf { it > 0 } ?: display.widthPixels)) - edge
        val availableBottomScreen = minOf(visibleFrame.bottom, windowOriginY + (decor.height.takeIf { it > 0 } ?: display.heightPixels)) - edge
        val availableWidth = (availableRightScreen - availableLeftScreen).coerceAtLeast(dp(180))
        val measureWidth = minOf(dp(MAX_WIDTH_DP), availableWidth)
        val maxHeight = (availableBottomScreen - availableTopScreen).coerceAtLeast(dp(160)).coerceAtMost(dp(660))

        root.measure(
            View.MeasureSpec.makeMeasureSpec(measureWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
        )
        val width = root.measuredWidth.coerceAtMost(availableWidth)
        val height = root.measuredHeight.coerceAtMost(maxHeight)

        val anchorLeftScreen = anchorScreen[0]
        val anchorTopScreen = anchorScreen[1]
        val anchorRightScreen = anchorLeftScreen + anchor.width
        val anchorBottomScreen = anchorTopScreen + anchor.height
        val anchorCenterX = anchorLeftScreen + anchor.width / 2
        val preferLeftEdge = alignLeft || anchorCenterX < (availableLeftScreen + availableRightScreen) / 2
        val preferredLeftScreen = if (preferLeftEdge) anchorLeftScreen else anchorRightScreen - width
        val leftScreen = preferredLeftScreen.coerceIn(availableLeftScreen, (availableRightScreen - width).coerceAtLeast(availableLeftScreen))

        val centerTarget = anchorTopScreen + anchor.height / 2
        // Overlap the triggering control itself, rather than rendering above or
        // below it. Clamp only at the actual visible system-window edges.
        val centeredTopScreen = centerTarget - height / 2
        val topScreen = centeredTopScreen.coerceIn(availableTopScreen, (availableBottomScreen - height).coerceAtLeast(availableTopScreen))

        val left = (leftScreen - windowOriginX).coerceAtLeast(edge)
        val top = (topScreen - windowOriginY).coerceAtLeast(edge)
        // Prepare the menu transform before the window is shown to avoid a
        // one-frame flash. Background dimming itself remains fully native.
        root.pivotX = (anchorCenterX - leftScreen).toFloat().coerceIn(0f, width.toFloat())
        root.pivotY = height / 2f
        root.scaleX = 0.96f
        root.scaleY = 0.96f
        root.alpha = 0f
        root.translationY = if (topScreen < anchorTopScreen) -dp(3).toFloat() else dp(3).toFloat()

        val window = popup.window
        if (window != null) {
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // Position the real Dialog window over the activated control.
            // Its FLAG_DIM_BEHIND applies the system's native scrim to content
            // and status bar; there is no manually drawn overlay.
            window.setGravity(Gravity.TOP or Gravity.START)
            window.setLayout(width, height)
            val attributes = window.attributes
            attributes.x = left
            attributes.y = top
            window.attributes = attributes
        }
        popup.show()
        popup.window?.apply {
            // Android may recalculate dialog gravity when the window is attached.
            // Reapply the measured coordinates after show() to keep the popup
            // overlapping its trigger instead of dropping below it.
            setGravity(Gravity.TOP or Gravity.START)
            setLayout(width, height)
            val shownAttributes = attributes
            shownAttributes.x = left
            shownAttributes.y = top
            attributes = shownAttributes
        }

        /*
         * HTML .menu:
         * opacity 0 + slight scale -> opacity 1 + scale 1
         */
        root.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(290L)
            .setInterpolator(Curves.EMPHASIZED)
            .withLayer()
            .start()

        for (index in 0 until rowsContainer.childCount) {
            val row = rowsContainer.getChildAt(index)
            row.postDelayed({
                if (!row.isAttachedToWindow) return@postDelayed
                row.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(230L)
                    .setInterpolator(Curves.SMOOTH)
                    .start()
            }, index * ITEM_DELAY_MS)
        }

        return popup
    }

    private fun popupBackground(
        context: Context,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            shape =
                GradientDrawable.RECTANGLE

            setColor(
                ContextCompat.getColor(
                    context,
                    R.color.popupBg
                )
            )

            cornerRadius =
                radius.toFloat()

        }
    }

    private fun rowBackground(
        context: Context,
        density: Float
    ): StateListDrawable {

        val radius =
            ITEM_RADIUS_DP *
                density

        return StateListDrawable().apply {

            addState(
                intArrayOf(
                    android.R.attr.state_pressed
                ),
                GradientDrawable().apply {

                    shape =
                        GradientDrawable.RECTANGLE

                    setColor(
                        ContextCompat.getColor(
                            context,
                            R.color.card2
                        )
                    )

                    cornerRadius =
                        radius
                }
            )

            addState(
                intArrayOf(),
                GradientDrawable().apply {

                    shape =
                        GradientDrawable.RECTANGLE

                    setColor(
                        Color.TRANSPARENT
                    )

                    cornerRadius =
                        radius
                }
            )
        }
    }
}
