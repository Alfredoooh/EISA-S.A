package com.appao

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

/**
 * Native paper-sheet style modal.
 * Full-width, bottom anchored, curved top corners, no floating card margin,
 * interactive downward drag and no yellow pressed feedback.
 */
object NativeSheetDialog {

    fun show(
        context: Context,
        content: View
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val panel = FrameLayout(context).apply {
            background = sheetBackground(context)
            clipToOutline = true
            alpha = 0f
            translationY = dp(context, 36).toFloat()
        }

        val shell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        val handle = View(context).apply {
            background = rounded(
                ContextCompat.getColor(context, R.color.line),
                dp(context, 4)
            )
        }

        shell.addView(
            handle,
            LinearLayout.LayoutParams(
                dp(context, 38),
                dp(context, 4)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(context, 10)
                bottomMargin = dp(context, 10)
            }
        )

        shell.addView(
            content,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        panel.addView(
            shell,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        dialog.setContentView(panel)
        dialog.setCanceledOnTouchOutside(true)

        val window = dialog.window
            ?: return dialog

        window.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )
        window.setGravity(Gravity.BOTTOM)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND
        )
        window.setDimAmount(
            if (ThemeManager.current(context) == "dark") {
                0.22f
            } else {
                0.12f
            }
        )

        dialog.setOnShowListener {
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            panel.post {
                if (!dialog.isShowing) return@post

                panel.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(500L)
                    .setInterpolator(Curves.IOS)
                    .start()
            }
        }

        attachDrag(
            context = context,
            dialog = dialog,
            panel = panel,
            handle = handle
        )

        dialog.show()

        window.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        return dialog
    }

    private fun attachDrag(
        context: Context,
        dialog: Dialog,
        panel: View,
        handle: View
    ) {
        var downY = 0f
        var lastY = 0f
        var lastTime = 0L
        var velocity = 0f
        var dragging = false

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    lastY = downY
                    lastTime = System.currentTimeMillis()
                    velocity = 0f
                    dragging = true
                    panel.animate().cancel()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) return@setOnTouchListener true

                    val y = event.rawY
                    val dy = (y - downY).coerceAtLeast(0f)
                    val now = System.currentTimeMillis()
                    val dt = now - lastTime

                    if (dt > 0L) {
                        velocity = (y - lastY) / dt
                    }

                    lastY = y
                    lastTime = now

                    panel.translationY = dy
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (!dragging) return@setOnTouchListener true
                    dragging = false

                    val dismiss =
                        panel.translationY > panel.height * 0.22f ||
                            velocity > 0.65f

                    if (dismiss) {
                        panel.animate()
                            .translationY(panel.height.toFloat())
                            .alpha(0f)
                            .setDuration(280L)
                            .setInterpolator(Curves.SMOOTH)
                            .withEndAction {
                                if (dialog.isShowing) {
                                    dialog.dismiss()
                                }
                            }
                            .start()
                    } else {
                        panel.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(360L)
                            .setInterpolator(Curves.SPRING)
                            .start()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun sheetBackground(
        context: Context
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(
                ContextCompat.getColor(
                    context,
                    R.color.bgElevated
                )
            )
            cornerRadii = floatArrayOf(
                dp(context, 28).toFloat(),
                dp(context, 28).toFloat(),
                dp(context, 28).toFloat(),
                dp(context, 28).toFloat(),
                0f,
                0f,
                0f,
                0f
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

    private fun dp(
        context: Context,
        value: Int
    ): Int = (
        value *
            context.resources.displayMetrics.density +
            0.5f
    ).toInt()
}
