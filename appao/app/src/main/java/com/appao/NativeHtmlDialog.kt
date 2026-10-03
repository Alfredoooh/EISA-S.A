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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Native dialog following the HTML .sp dimensions, colors and bottom-sheet motion. */
object NativeHtmlDialog {

    fun alert(
        context: Context,
        title: String,
        message: String,
        onDone: (() -> Unit)? = null
    ) {
        show(
            context = context,
            title = title,
            message = message,
            prompt = false,
            confirm = false,
            onResult = {
                onDone?.invoke()
            }
        )
    }

    fun confirm(
        context: Context,
        title: String,
        message: String,
        onResult: (Boolean) -> Unit
    ) {
        show(
            context = context,
            title = title,
            message = message,
            prompt = false,
            confirm = true,
            onResult = { result ->
                onResult(result as? Boolean == true)
            }
        )
    }

    fun prompt(
        context: Context,
        title: String,
        message: String,
        initial: String = "",
        onResult: (String?) -> Unit
    ) {
        show(
            context = context,
            title = title,
            message = message,
            prompt = true,
            confirm = true,
            initial = initial,
            onResult = { result ->
                onResult(result as? String)
            }
        )
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
            setPadding(
                dp(context, 20),
                dp(context, 14),
                dp(context, 20),
                dp(context, 20)
            )
            background = rounded(
                ContextCompat.getColor(
                    context,
                    R.color.popupBg
                ),
                dp(context, 32)
            )
            clipToOutline = true
            alpha = 0f
            translationY = dp(context, 34).toFloat()
            scaleX = 0.96f
            scaleY = 0.96f
        }

        val handle = View(context).apply {
            background = rounded(
                ContextCompat.getColor(
                    context,
                    R.color.line
                ),
                dp(context, 4)
            )
        }

        card.addView(
            handle,
            LinearLayout.LayoutParams(
                dp(context, 38),
                dp(context, 4)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(context, 16)
            }
        )

        val titleView = TextView(context).apply {
            text = title
            setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.text
                )
            )
            textSize = 19f
            setTypeface(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
        }
        card.addView(
            titleView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, 6)
            }
        )

        val messageView = TextView(context).apply {
            text = message
            setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.dim
                )
            )
            textSize = 14f
            setLineSpacing(0f, 1.5f)
        }
        card.addView(
            messageView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, 18)
            }
        )

        val input = if (prompt) {
            EditText(context).apply {
                setText(initial)
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.text
                    )
                )
                setHintTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.dim
                    )
                )
                textSize = 15f
                background = rounded(
                    ContextCompat.getColor(
                        context,
                        R.color.card
                    ),
                    dp(context, 18)
                )
                setPadding(
                    dp(context, 16),
                    0,
                    dp(context, 16),
                    0
                )
                setSingleLine(true)
            }
        } else {
            null
        }

        input?.let {
            card.addView(
                it,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 52)
                ).apply {
                    bottomMargin = dp(context, 14)
                }
            )
        }

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        if (confirm) {
            actions.addView(
                button(
                    context,
                    "Cancelar",
                    outlined = true
                ) {
                    dialog.dismiss()
                    onResult(
                        if (prompt) null else false
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    dp(context, 46),
                    1f
                ).apply {
                    marginEnd = dp(context, 5)
                }
            )

            actions.addView(
                button(
                    context,
                    if (prompt) "Confirmar" else "Confirmar",
                    outlined = false
                ) {
                    dialog.dismiss()
                    onResult(
                        if (prompt) {
                            input?.text?.toString()
                        } else {
                            true
                        }
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    dp(context, 46),
                    1f
                ).apply {
                    marginStart = dp(context, 5)
                }
            )
        } else {
            actions.addView(
                button(
                    context,
                    "OK",
                    outlined = false
                ) {
                    dialog.dismiss()
                    onResult(Unit)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 46)
                )
            )
        }

        card.addView(actions)

        val outer = LinearLayout(context).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                dp(context, 10),
                0,
                dp(context, 10),
                dp(context, 10)
            )
        }

        outer.addView(
            card,
            LinearLayout.LayoutParams(
                minOf(
                    dp(context, 480),
                    context.resources.displayMetrics.widthPixels - dp(context, 20)
                ),
                ViewGroup.LayoutParams.WRAP_CONTENT
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

            card.post {
                if (!dialog.isShowing) return@post

                card.animate()
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(500L)
                    .setInterpolator(Curves.IOS)
                    .start()
            }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )
        dialog.window?.setGravity(Gravity.BOTTOM)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun button(
        context: Context,
        label: String,
        outlined: Boolean,
        click: () -> Unit
    ): Button = Button(context).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTypeface(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD
        )
        setTextColor(
            ContextCompat.getColor(
                context,
                if (outlined) R.color.text else R.color.onpri
            )
        )
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(
                if (outlined) {
                    Color.TRANSPARENT
                } else {
                    ContextCompat.getColor(
                        context,
                        R.color.pri
                    )
                }
            )
            cornerRadius = dp(context, 18).toFloat()
            if (outlined) {
                setStroke(
                    dp(context, 1),
                    ContextCompat.getColor(
                        context,
                        R.color.line
                    )
                )
            }
        }
        setOnClickListener {
            click()
        }
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(
        context: Context,
        value: Int
    ): Int = (
        value * context.resources.displayMetrics.density + 0.5f
    ).toInt()
}
