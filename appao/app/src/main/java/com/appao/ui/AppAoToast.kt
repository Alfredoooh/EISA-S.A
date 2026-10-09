package com.appao

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

/** Uses the platform Material Snackbar visuals with a single close.svg affordance. */
object AppAoToast {
    fun show(context: Context, message: String) {
        val activity = context as? Activity
        val root = activity?.findViewById<ViewGroup>(android.R.id.content)
        if (root == null) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val snackbar = Snackbar.make(root, message, Snackbar.LENGTH_SHORT)
        snackbar.setAction(" ") { snackbar.dismiss() }
        snackbar.show()

        val action = snackbar.view.findViewById<Button>(com.google.android.material.R.id.snackbar_action)
        action?.post {
            val size = (18f * context.resources.displayMetrics.density + 0.5f).toInt()
            val drawable = IconLoader.drawable(
                context,
                "close",
                size,
                ContextCompat.getColor(context, R.color.iconTint)
            )
            action.text = ""
            action.contentDescription = "Fechar"
            action.minWidth = 0
            action.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
        }
    }
}
