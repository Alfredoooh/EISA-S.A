package com.appao

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

/** Floating rounded snackbar styled to match the app's surface and close.svg affordance. */
object AppAoToast {
    fun show(context: Context, message: String) {
        val activity = context as? Activity
        val root = activity?.findViewById<ViewGroup>(android.R.id.content)
        if (root == null) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val surfaceColor = ContextCompat.getColor(context, R.color.card)
        val snackbar = Snackbar.make(root, message, 3600)
        snackbar.setAction(" ") { snackbar.dismiss() }
        snackbar.animationMode = Snackbar.ANIMATION_MODE_SLIDE
        snackbar.setActionTextColor(ContextCompat.getColor(context, R.color.iconTint))
        snackbar.show()

        snackbar.view.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(surfaceColor)
            }
            backgroundTintList = ColorStateList.valueOf(surfaceColor)
            elevation = dp(10).toFloat()
            minimumHeight = dp(62)
            setPadding(dp(14), dp(4), dp(8), dp(4))
            val params = layoutParams
            if (params is ViewGroup.MarginLayoutParams) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                params.setMargins(dp(14), params.topMargin, dp(14), dp(16))
                layoutParams = params
            }
        }

        snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.apply {
            setTextColor(ContextCompat.getColor(context, R.color.text))
            textSize = 15f
            maxLines = 2
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }

        snackbar.view.findViewById<Button>(com.google.android.material.R.id.snackbar_action)?.post {
            val action = snackbar.view.findViewById<Button>(com.google.android.material.R.id.snackbar_action)
            val icon = IconLoader.drawable(context, "action_close", dp(20), ContextCompat.getColor(context, R.color.iconTint))
            action.text = ""
            action.contentDescription = "Fechar"
            action.minimumWidth = 0
            action.minimumHeight = dp(44)
            action.setPadding(dp(8), 0, dp(4), 0)
            action.setCompoundDrawables(icon, null, null, null)
            action.compoundDrawablePadding = 0
        }
    }
}
