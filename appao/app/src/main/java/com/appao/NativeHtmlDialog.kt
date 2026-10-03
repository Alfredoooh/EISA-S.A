package com.appao

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import androidx.core.content.ContextCompat

/** Native dialog that follows the HTML .sp/.sheet motion, spacing and theme colors. */
object NativeHtmlDialog {
    fun alert(context: Context, title: String, message: String, onDone: (() -> Unit)? = null) {
        show(context, title, message, prompt = false, confirm = false, onResult = { onDone?.invoke() })
    }

    fun confirm(context: Context, title: String, message: String, onResult: (Boolean) -> Unit) {
        show(context, title, message, prompt = false, confirm = true, onResult = { value -> onResult(value as? Boolean == true) })
    }

    fun prompt(context: Context, title: String, message: String, initial: String = "", onResult: (String?) -> Unit) {
        show(context, title, message, prompt = true, confirm = true, initial = initial, onResult = { value -> onResult(value as? String) })
    }

    private fun show(
        context: Context,
        title: String,
        message: String,
        prompt: Boolean,
        confirm: Boolean,
        initial: String = "",
        onResult: (Any?) -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 20))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.bg))
                cornerRadius = dp(context, 32).toFloat()
            }
            alpha = 1f
            translationY = dp(context, 30).toFloat()
        }

        val handle = ViewDivider(context).view
        card.addView(handle, LinearLayout.LayoutParams(dp(context, 38), dp(context, 4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(context, 16)
        })

        val tvTitle = TextView(context).apply {
            text = title
            setTextColor(ContextCompat.getColor(context, R.color.text))
            textSize = 19f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        card.addView(tvTitle, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 6) })

        val tvMessage = TextView(context).apply {
            text = message
            setTextColor(ContextCompat.getColor(context, R.color.dim))
            textSize = 14f
            setLineSpacing(0f, 1.5f)
        }
        card.addView(tvMessage, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 18) })

        val input = if (prompt) EditText(context).apply {
            setText(initial)
            setTextColor(ContextCompat.getColor(context, R.color.text))
            setHintTextColor(ContextCompat.getColor(context, R.color.dim))
            textSize = 15f
            background = rounded(ContextCompat.getColor(context, R.color.card), dp(context, 18))
            setPadding(dp(context, 16), 0, dp(context, 16), 0)
        } else null
        if (input != null) card.addView(input, LinearLayout.LayoutParams(-1, dp(context, 52)).apply { bottomMargin = dp(context, 14) })

        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        if (confirm) {
            actions.addView(button(context, "Cancelar", true) { dialog.dismiss(); onResult(if (prompt) null else false) }, LinearLayout.LayoutParams(0, dp(context, 46), 1f).apply { marginEnd = dp(context, 5) })
            actions.addView(button(context, if (prompt) "Confirmar" else "Confirmar", false) {
                dialog.dismiss(); onResult(if (prompt) input?.text?.toString() else true)
            }, LinearLayout.LayoutParams(0, dp(context, 46), 1f).apply { marginStart = dp(context, 5) })
        } else {
            actions.addView(button(context, "OK", false) { dialog.dismiss(); onResult(Unit) }, LinearLayout.LayoutParams(-1, dp(context, 46)))
        }
        card.addView(actions)

        dialog.setContentView(card)
        dialog.setOnShowListener {
            val window = dialog.window ?: return@setOnShowListener
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.12f)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setLayout(min(dp(context, 480), context.resources.displayMetrics.widthPixels - dp(context, 20)), ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.BOTTOM)
            card.post {
                card.translationY = card.height.toFloat() + dp(context, 30)
                card.animate().translationY(0f).setDuration(500).setInterpolator(Curves.IOS).start()
            }
        }
        dialog.show()
        dialog.window?.setLayout(min(dp(context, 480), context.resources.displayMetrics.widthPixels - dp(context, 20)), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(Gravity.BOTTOM)
    }

    private fun button(context: Context, label: String, outlined: Boolean, click: () -> Unit): Button = Button(context).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(ContextCompat.getColor(context, if (outlined) R.color.text else R.color.onpri))
        background = GradientDrawable().apply {
            setColor(if (outlined) Color.TRANSPARENT else ContextCompat.getColor(context, R.color.pri))
            cornerRadius = dp(context, 18).toFloat()
            if (outlined) setStroke(dp(context, 1), ContextCompat.getColor(context, R.color.line))
        }
        setOnClickListener { click() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun dp(context: Context, v: Int) = (v * context.resources.displayMetrics.density + 0.5f).toInt()

    private class ViewDivider(context: Context) {
        val view = android.view.View(context).apply {
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.line))
                cornerRadius = dp(context, 4).toFloat()
            }
        }
        private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun min(a: Int, b: Int) = kotlin.math.min(a, b)
}
