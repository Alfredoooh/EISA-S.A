package com.appao

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
        val useSvg: Boolean = false,
        val iconColor: Int? = null,
        val highlightColor: Int? = null,
        val onClick: () -> Unit
    )

    private const val MIN_WIDTH_DP = 230
    private const val MAX_WIDTH_DP = 320
    private const val MENU_PADDING_DP = 8
    private const val MENU_RADIUS_DP = 26
    private const val ITEM_RADIUS_DP = 18
    private const val ITEM_DELAY_MS = 20L
    private const val ITEM_TRANSLATE_DP = 6

    fun show(
        anchor: View,
        items: List<Item>,
        placeAbove: Boolean = true,
        alignLeft: Boolean = false
    ): PopupWindow {

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

        val rootView = anchor.rootView as? ViewGroup
        val decorRoot = rootView?.findViewById<ViewGroup>(android.R.id.content) ?: rootView
        fun owningActivity(ctx: Context): Activity? = when (ctx) {
            is Activity -> ctx
            is ContextWrapper -> owningActivity(ctx.baseContext)
            else -> null
        }
        val activity = owningActivity(context)
        val originalStatusBarColor = activity?.window?.statusBarColor
        var popupRef: PopupWindow? = null
        val dimOverlay = decorRoot?.let { decor ->
            View(context).apply {
                setBackgroundColor(Color.argb(22, 0, 0, 0))
                alpha = 0f
                isClickable = true
                isFocusable = false
                setOnClickListener { popupRef?.dismiss() }
            }
        }

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

        val popup =
            PopupWindow(
                root,
                dp(MIN_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                popupRef = this

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
                    dp(18).toFloat()

                inputMethodMode =
                    PopupWindow.INPUT_METHOD_NOT_NEEDED

                animationStyle =
                    0

                setOnDismissListener {
                    if (dimOverlay != null && decorRoot != null && dimOverlay.parent === decorRoot) {
                        runCatching { decorRoot.removeView(dimOverlay) }
                    }
                    if (activity != null && originalStatusBarColor != null) {
                        runCatching { activity.window.statusBarColor = originalStatusBarColor }
                    }
                    root.animate().cancel()

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

            val icon =
                ImageView(context).apply {

                    layoutParams =
                        LinearLayout.LayoutParams(
                            dp(if (compactColorMenu) 20 else 24),
                            dp(if (compactColorMenu) 20 else 24)
                        )

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
                        if (item.useSvg) {
                            IconLoader.applySvg(this, item.iconName!!, R.color.iconTint)
                        } else {
                            IconLoader.applyPng(this, item.iconName!!, 0)
                        }
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
                        dp(14)
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

                        IconLoader.applySvg(this, "state_selected", R.color.iconTint)
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

            root.addView(
                row
            )
        }

        val frame = android.graphics.Rect()
        anchor.getWindowVisibleDisplayFrame(frame)
        val edgeInset = dp(10)
        val availableWidth = (frame.width() - edgeInset * 2).coerceAtLeast(dp(180))
        val measureWidth = minOf(dp(MAX_WIDTH_DP), availableWidth)

        root.measure(
            View.MeasureSpec.makeMeasureSpec(
                measureWidth,
                View.MeasureSpec.AT_MOST
            ),
            View.MeasureSpec.makeMeasureSpec(
                0,
                View.MeasureSpec.UNSPECIFIED
            )
        )

        val width = max(
            minOf(dp(MIN_WIDTH_DP), availableWidth),
            root.measuredWidth
        ).coerceAtMost(availableWidth)
        val height = root.measuredHeight
        popup.width = width

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)

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

        val margin = dp(8)
        val edge = dp(8)
        val maxLeft = (frame.right - width - edge).coerceAtLeast(frame.left + edge)
        // Menus intentionally overlap the activated control rather than opening
        // above/below it. Align the right edge with the anchor by default, which
        // keeps option menus close to the trailing side of Settings cards.
        val preferredLeft = if (alignLeft) anchorLeft else anchorRight - width
        val left = preferredLeft.coerceIn(frame.left + edge, maxLeft)

        val minTop = frame.top + edge
        val maxTop = (frame.bottom - height - edge).coerceAtLeast(minTop)
        val centeredTop = anchorTop + (anchor.height - height) / 2
        val top = centeredTop.coerceIn(minTop, maxTop)
        val overlapsAnchorFromTop = top < anchorTop

        if (activity != null && originalStatusBarColor != null) {
            runCatching {
                // SystemBarHelper keeps the status bar transparent. Darken the
                // underlying theme surface (not transparent black) for the same
                // subtle scrim as the popup body, then restore the exact prior value.
                val surface = ContextCompat.getColor(activity, R.color.bg)
                activity.window.statusBarColor = androidx.core.graphics.ColorUtils.blendARGB(
                    surface,
                    Color.BLACK,
                    0.08f
                )
            }
        }

        if (dimOverlay != null && decorRoot != null) {
            runCatching {
                decorRoot.addView(
                    dimOverlay,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }.onSuccess {
                dimOverlay.animate().alpha(1f).setDuration(120L).start()
            }
        }

        popup.showAtLocation(
            anchor,
            Gravity.TOP or
                Gravity.START,
            left,
            top
        )

        /*
         * HTML .menu:
         * opacity 0 + scale .5 -> opacity 1 + scale 1
         */
        root.pivotX =
            (anchorRight - left)
                .toFloat()
                .coerceIn(0f, width.toFloat())

        root.pivotY = height / 2f
        root.scaleX = 0.96f
        root.scaleY = 0.96f
        root.alpha = 0f
        root.translationY = (if (overlapsAnchorFromTop) -dp(4) else dp(4)).toFloat()

        root.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(290L)
            .setInterpolator(Curves.EMPHASIZED)
            .withLayer()
            .start()

        for (index in 0 until root.childCount) {
            val row = root.getChildAt(index)
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
