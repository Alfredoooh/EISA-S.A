package com.appao

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

object AppAoToast {
    private var current: Snackbar? = null

    fun show(context: Context, message: String) {
        val activity = context as? Activity
        val root = activity?.findViewById<ViewGroup>(android.R.id.content)

        if (root == null) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        current?.dismiss()

        val snackbar = Snackbar.make(root, message, Snackbar.LENGTH_SHORT).apply {
            animationMode = BaseTransientBottomBar.ANIMATION_MODE_SLIDE
        }

        val view = snackbar.view
        view.setBackgroundColor(Color.TRANSPARENT)
        view.setPadding(0, 0, 0, 0)
        view.elevation = dp(context, 8).toFloat()

        val params = view.layoutParams as? ViewGroup.MarginLayoutParams
        if (params != null) {
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params.setMargins(
                dp(context, 16),
                0,
                dp(context, 16),
                dp(context, 46)
            )
            view.layoutParams = params
        }

        val text = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        text?.apply {
            setTextColor(ContextCompat.getColor(context, R.color.onpri))
            textSize = 13.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 12))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.pri))
                cornerRadius = dp(context, 99).toFloat()
            }
        }

        view.alpha = 0f
        view.translationY = dp(context, 22).toFloat()
        snackbar.addCallback(object : Snackbar.Callback() {
            override fun onShown(sb: Snackbar?) {
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(480L)
                    .setInterpolator(Curves.SMOOTH)
                    .start()
            }
        })

        current = snackbar
        snackbar.show()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
