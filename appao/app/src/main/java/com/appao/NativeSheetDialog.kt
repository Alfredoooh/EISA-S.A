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

/** Lightweight native bottom sheet with the same motion language as the HTML sheets. */
object NativeSheetDialog {
    fun show(context: Context, content: View): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val wrap = FrameLayout(context).apply {
            setPadding(dp(context, 20), dp(context, 12), dp(context, 20), dp(context, 20))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(androidx.core.content.ContextCompat.getColor(context, R.color.bg))
                cornerRadius = dp(context, 32).toFloat()
            }
        }
        wrap.addView(content, FrameLayout.LayoutParams(-1, -2))
        dialog.setContentView(wrap)

        dialog.setOnShowListener {
            val w = dialog.window ?: return@setOnShowListener
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            w.setDimAmount(0.12f)
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            w.setLayout(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.BOTTOM)
            w.decorView.translationY = wrap.height.toFloat() + dp(context, 30)
            w.decorView.alpha = 0f
            w.decorView.post {
                w.decorView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(500)
                    .setInterpolator(Curves.IOS)
                    .start()
            }
        }
        dialog.setOnDismissListener { }
        dialog.show()
        dialog.window?.setLayout(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(Gravity.BOTTOM)
        return dialog
    }

    private fun dp(context: Context, v: Int): Int = (v * context.resources.displayMetrics.density + 0.5f).toInt()
}
