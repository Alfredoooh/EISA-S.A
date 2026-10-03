package com.appao

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

object AppAoToast {
    fun show(context: Context, message: String) {
        val toast = Toast(context)
        val tv = TextView(context).apply {
            text = message
            setTextColor(ContextCompat.getColor(context, R.color.onpri))
            textSize = 13.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(context, 20), dp(context, 13), dp(context, 20), dp(context, 13))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.pri))
                cornerRadius = dp(context, 99).toFloat()
            }
            layoutParams = ViewGroup.LayoutParams(-2, -2)
        }
        toast.view = tv
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(context, 120))
        toast.duration = Toast.LENGTH_SHORT
        toast.show()
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
