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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Native implementation for WebView JS alert/confirm/prompt.
 * It uses the same paper-sheet language as the app's other modals.
 */
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
            onResult = { value ->
                onResult(value as? Boolean == true)
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
            onResult = { value ->
                onResult(value as? String)
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

        var callbackCalled = false

        fun callback(value: Any?) {
            if (callbackCalled) return
            callbackCalled = true
            try {
                onResult(value)
            } catch (t: Throwable) {
                android.util.Log.e(
                    "NativeHtmlDialog",
                    "Callback failed",
                    t
                )
            }
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(context, 20),
                dp(context, 10),
                dp(context, 20),
                dp(context, 20)
            )
            background = sheetBackground(context)
            clipToOutline = true
            alpha = 0f
            translationY = dp(context, 36).toFloat()
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

        panel.addView(
            handle,
            LinearLayout.LayoutParams(
                dp(context, 38),
                dp(context, 4)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(context, 16)
            }
        )

        if (title.isNotBlank()) {
            panel.addView(
                TextView(context).apply {
                    text = title
                    setTextColor(
                        ContextCompat.getColor(
                            context,
                            R.color.text
                        )
                    )
                    textSize = 19f
                    setTypeface(
                        android.graphics.Typeface.create(
                            "sans-serif",
                            android.graphics.Typeface.BOLD
                        )
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(context, 7)
                }
            )
        }

        panel.addView(
            TextView(context).apply {
                text = message
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.dim
                    )
                )
                textSize = 14.5f
                setLineSpacing(0f, 1.5f)
            },
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
                background = roundedWithStroke(
                    ContextCompat.getColor(
                        context,
                        R.color.card
                    ),
                    ContextCompat.getColor(
                        context,
                        R.color.border
                    ),
                    dp(context, 18),
                    dp(context, 1)
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
            panel.addView(
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
            gravity = Gravity.CENTER
        }

        if (confirm) {
            actions.addView(
                button(
                    context,
                    "Cancelar",
                    outlined = true
                ) {
                    dialog.dismiss()
                    callback(
                        if (prompt) null else false
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    dp(context, 48),
                    1f
                ).apply {
                    marginEnd = dp(context, 5)
                }
            )

            actions.addView(
                button(
                    context,
                    "Confirmar",
                    outlined = false
                ) {
                    dialog.dismiss()
                    callback(
                        if (prompt) {
                            input?.text?.toString()
                        } else {
                            true
                        }
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    dp(context, 48),
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
                    callback(Unit)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 48)
                )
            )
        }

        panel.addView(actions)

        val windowRoot = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                panel,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
                )
            )
        }

        dialog.setContentView(windowRoot)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(true)

        dialog.setOnCancelListener {
            callback(
                if (prompt) null
                else if (confirm) false
                else Unit
            )
        }

        val window = dialog.window ?: return

        window.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )
        window.setGravity(Gravity.BOTTOM)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND
        )
        window.setDimAmount(
            if (ThemeManager.current(context) == "dark") 0.22f else 0.12f
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
            dialog = dialog,
            panel = panel,
            handle = handle
        )

        dialog.show()

        window.setLayout(
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
            android.graphics.Typeface.create(
                "sans-serif",
                android.graphics.Typeface.BOLD
            )
        )
        setTextColor(
            ContextCompat.getColor(
                context,
                if (outlined) R.color.text else R.color.onpri
            )
        )
        background = if (outlined) {
            roundedWithStroke(
                Color.TRANSPARENT,
                ContextCompat.getColor(
                    context,
                    R.color.border
                ),
                dp(context, 18),
                dp(context, 1)
            )
        } else {
            rounded(
                ContextCompat.getColor(
                    context,
                    R.color.pri
                ),
                dp(context, 18)
            )
        }
        stateListAnimator = null
        setOnClickListener {
            click()
        }
    }

    private fun attachDrag(
        dialog: Dialog,
        panel: View,
        handle: View
    ) {
        var startY = 0f
        var lastY = 0f
        var lastTime = 0L
        var velocity = 0f
        var dragging = false

        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    lastY = startY
                    lastTime = System.currentTimeMillis()
                    velocity = 0f
                    dragging = true
                    panel.animate().cancel()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) return@setOnTouchListener true

                    val currentY = event.rawY
                    val dy = (currentY - startY).coerceAtLeast(0f)
                    val now = System.currentTimeMillis()
                    val dt = now - lastTime

                    if (dt > 0L) {
                        velocity = (currentY - lastY) / dt
                    }

                    lastY = currentY
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
                                    dialog.cancel()
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
                    R.color.popupBg
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
            setStroke(
                dp(context, 1),
                ContextCompat.getColor(
                    context,
                    R.color.popupBorder
                )
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

    private fun roundedWithStroke(
        color: Int,
        strokeColor: Int,
        radius: Int,
        strokeWidth: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
            setStroke(strokeWidth, strokeColor)
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
