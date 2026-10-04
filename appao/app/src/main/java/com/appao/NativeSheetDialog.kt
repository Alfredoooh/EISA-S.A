package com.appao

import android.content.Context
import android.graphics.Color
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.MaterialShapeDrawable

/**
 * Real Material 3 modal bottom sheet.
 *
 * This is intentionally not a floating dialog: it is bottom aligned, has
 * rounded top corners, can be dragged, and uses the application's own colors.
 */
object NativeSheetDialog {

    fun show(
        context: Context,
        content: View
    ): BottomSheetDialog {

        val dialog =
            BottomSheetDialog(
                context,
                R.style.AppAo_BottomSheet
            )

        dialog.setContentView(
            content
        )

        dialog.setCanceledOnTouchOutside(
            true
        )

        dialog.setOnShowListener {

            val sheet =
                dialog.findViewById<FrameLayout>(
                    com.google.android.material.R.id.design_bottom_sheet
                )

            sheet?.let {

                val surfaceColor =
                    ContextCompat.getColor(
                        context,
                        R.color.dialogSurface
                    )

                val currentBackground =
                    it.background

                val shape =
                    if (currentBackground is MaterialShapeDrawable) {
                        currentBackground
                    } else {
                        MaterialShapeDrawable()
                    }

                shape.fillColor =
                    ColorStateList.valueOf(
                        surfaceColor
                    )

                shape.shapeAppearanceModel =
                    shape.shapeAppearanceModel
                        .toBuilder()
                        .setTopLeftCornerSize(dp(context, 28).toFloat())
                        .setTopRightCornerSize(dp(context, 28).toFloat())
                        .setBottomLeftCornerSize(0f)
                        .setBottomRightCornerSize(0f)
                        .build()

                it.background = shape

                val behavior =
                    BottomSheetBehavior.from(
                        it
                    )

                behavior.isFitToContents =
                    true

                behavior.isHideable =
                    true

                behavior.skipCollapsed =
                    true

                behavior.state =
                    BottomSheetBehavior.STATE_EXPANDED
            }

            dialog.window?.let { window ->

                window.setDimAmount(
                    if (
                        ThemeManager.resolvedDark(
                            context
                        )
                    ) {
                        0.22f
                    } else {
                        0.18f
                    }
                )

                window.statusBarColor =
                    Color.TRANSPARENT

                window.navigationBarColor =
                    Color.TRANSPARENT
            }
        }

        dialog.show()

        return dialog
    }

    fun showSelection(
        context: Context,
        title: String,
        items: List<String>,
        checkedIndex: Int,
        onSelected: (Int) -> Unit
    ): BottomSheetDialog {

        val root =
            LinearLayout(context).apply {

                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        R.color.dialogSurface
                    )
                )

                setPadding(
                    dp(context, 8),
                    0,
                    dp(context, 8),
                    dp(context, 18)
                )
            }

        val handle =
            View(context).apply {

                background =
                    rounded(
                        ContextCompat.getColor(
                            context,
                            R.color.line
                        ),
                        dp(context, 2)
                    )
            }

        root.addView(
            handle,
            LinearLayout.LayoutParams(
                dp(context, 38),
                dp(context, 4)
            ).apply {
                gravity =
                    Gravity.CENTER_HORIZONTAL

                topMargin =
                    dp(context, 10)

                bottomMargin =
                    dp(context, 12)
            }
        )

        val titleView =
            TextView(context).apply {

                text =
                    title

                gravity =
                    Gravity.CENTER

                textSize =
                    17f

                setTypeface(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )

                setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.text
                    )
                )

                setPadding(
                    dp(context, 18),
                    dp(context, 4),
                    dp(context, 18),
                    dp(context, 14)
                )
            }

        root.addView(
            titleView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val list =
            LinearLayout(context).apply {
                orientation =
                    LinearLayout.VERTICAL
            }

        items.forEachIndexed { index, label ->

            val row =
                TextView(context).apply {

                    text =
                        if (
                            index ==
                            checkedIndex
                        ) {
                            "✓  $label"
                        } else {
                            label
                        }

                    gravity =
                        Gravity.CENTER_VERTICAL

                    minHeight =
                        dp(context, 54)

                    textSize =
                        15f

                    setTypeface(
                        android.graphics.Typeface.create(
                            "sans-serif-medium",
                            android.graphics.Typeface.NORMAL
                        )
                    )

                    setTextColor(
                        ContextCompat.getColor(
                            context,
                            R.color.text
                        )
                    )

                    setPadding(
                        dp(context, 14),
                        0,
                        dp(context, 14),
                        0
                    )

                    background =
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.bg_sheet_item
                        )

                    isClickable =
                        true

                    isFocusable =
                        true

                    setOnClickListener {

                        onSelected(
                            index
                        )
                    }
                }

            list.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 54)
                ).apply {
                    topMargin =
                        dp(context, 2)
                }
            )
        }

        root.addView(
            list,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val dialog =
            show(
                context,
                root
            )

        /*
         * Selecting an item must close the sheet only after the callback has
         * been accepted, preventing touch callbacks from being lost.
         */
        for (
            i in 0 until list.childCount
        ) {

            list.getChildAt(i)
                .setOnClickListener {

                    val index =
                        list.indexOfChild(
                            it
                        )

                    onSelected(
                        index
                    )

                    dialog.dismiss()
                }
        }

        return dialog
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): android.graphics.drawable.GradientDrawable {

        return android.graphics.drawable.GradientDrawable().apply {
            shape =
                android.graphics.drawable.GradientDrawable.RECTANGLE

            setColor(
                color
            )

            cornerRadius =
                radius.toFloat()
        }
    }

    private fun dp(
        context: Context,
        value: Int
    ): Int =
        (
            value *
                context.resources
                    .displayMetrics
                    .density +
                0.5f
            ).toInt()
}
