package com.appao

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat

/** Native bottom sheet matching the HTML #sheet/.sp geometry and motion. */
object NativeSheetDialog {

    fun show(
        context: Context,
        content: View
    ): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val outer = FrameLayout(context).apply {
            setPadding(
                dp(context, 10),
                0,
                dp(context, 10),
                dp(context, 10)
            )
            setBackgroundColor(Color.TRANSPARENT)
        }

        val panel = FrameLayout(context).apply {
            setPadding(
                dp(context, 20),
                dp(context, 12),
                dp(context, 20),
                dp(context, 20)
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(
                    ContextCompat.getColor(
                        context,
                        R.color.bg
                    )
                )
                cornerRadius = dp(context, 32).toFloat()
            }
            clipToOutline = true
            alpha = 0f
            translationY = dp(context, 34).toFloat()
            scaleX = 0.985f
            scaleY = 0.985f
        }

        panel.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        outer.addView(
            panel,
            FrameLayout.LayoutParams(
                minOf(
                    dp(context, 480),
                    context.resources.displayMetrics.widthPixels - dp(context, 20)
                ),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        )

        dialog.setContentView(outer)
        dialog.setCanceledOnTouchOutside(true)

        dialog.setOnShowListener {
            val window = dialog.window ?: return@setOnShowListener

            window.setBackgroundDrawable(
                ColorDrawable(Color.TRANSPARENT)
            )

            window.addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )

            window.setDimAmount(0.12f)
            window.setGravity(Gravity.BOTTOM)
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            panel.post {
                if (!dialog.isShowing) return@post

                panel.animate()
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(500L)
                    .setInterpolator(Curves.IOS)
                    .start()
            }
        }

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.show()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(Gravity.BOTTOM)

        return dialog
    }

    private fun dp(
        context: Context,
        value: Int
    ): Int = (
        value * context.resources.displayMetrics.density + 0.5f
    ).toInt()
}
