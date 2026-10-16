package com.appao

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.snackbar.Snackbar

/** Floating rounded snackbar styled to match the app surface and the standard Ionicons close glyph. */
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
                cornerRadius = dp(24).toFloat()
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
            val iconBitmap = IconLoader.iconBitmap(context, "close", dp(16))
            val baseIcon: Drawable = iconBitmap?.let { BitmapDrawable(context.resources, it) }
                ?: ContextCompat.getDrawable(context, android.R.drawable.ic_menu_close_clear_cancel)!!
            val icon = DrawableCompat.wrap(baseIcon).mutate().apply {
                DrawableCompat.setTint(this, ContextCompat.getColor(context, R.color.iconTint))
                setBounds(0, 0, dp(16), dp(16))
            }
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
