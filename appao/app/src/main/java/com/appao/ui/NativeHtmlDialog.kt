package com.appao

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText

/** Native JS dialog bridge using Material 3 dialogs. */
object NativeHtmlDialog {

    fun alert(
        context: Context,
        title: String,
        message: String,
        onDone: (() -> Unit)? = null
    ) {
        val themed = ContextThemeWrapper(
            context,
            R.style.Theme_AppAo_Material3Dialog
        )

        val dialog = MaterialAlertDialogBuilder(themed)
            .setTitle(title.takeIf { it.isNotBlank() })
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                onDone?.invoke()
            }
            .create()

        dialog.setOnCancelListener {
            onDone?.invoke()
        }

        dialog.show()
        style(dialog)
    }

    fun confirm(
        context: Context,
        title: String,
        message: String,
        onResult: (Boolean) -> Unit
    ) {
        val themed = ContextThemeWrapper(
            context,
            R.style.Theme_AppAo_Material3Dialog
        )

        var handled = false

        fun finish(value: Boolean) {
            if (handled) return
            handled = true
            onResult(value)
        }

        val dialog = MaterialAlertDialogBuilder(themed)
            .setTitle(title.takeIf { it.isNotBlank() })
            .setMessage(message)
            .setNegativeButton("Cancelar") { _, _ ->
                finish(false)
            }
            .setPositiveButton("Confirmar") { _, _ ->
                finish(true)
            }
            .create()

        dialog.setOnCancelListener {
            finish(false)
        }

        dialog.show()
        style(dialog)
    }

    fun prompt(
        context: Context,
        title: String,
        message: String,
        initial: String = "",
        onResult: (String?) -> Unit
    ) {
        val themed = ContextThemeWrapper(
            context,
            R.style.Theme_AppAo_Material3Dialog
        )

        val input = TextInputEditText(themed).apply {
            setText(initial)
            setSelection(text?.length ?: 0)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
            setSingleLine(true)
            setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    context,
                    R.color.text
                )
            )
            setHintTextColor(
                androidx.core.content.ContextCompat.getColor(
                    context,
                    R.color.dim
                )
            )
        }

        val container = FrameLayout(themed).apply {
            setPadding(
                dp(context, 4),
                dp(context, 4),
                dp(context, 4),
                0
            )
            addView(
                input,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dp(context, 56)
                )
            )
        }

        var handled = false

        fun finish(value: String?) {
            if (handled) return
            handled = true
            onResult(value)
        }

        val builder = MaterialAlertDialogBuilder(themed)
            .setTitle(title.takeIf { it.isNotBlank() })
            .setMessage(message)
            .setView(container)
            .setNegativeButton("Cancelar") { _, _ ->
                finish(null)
            }
            .setPositiveButton("OK") { _, _ ->
                finish(input.text?.toString() ?: "")
            }

        val dialog = builder.create()

        dialog.setOnCancelListener {
            finish(null)
        }

        dialog.setOnShowListener {
            style(dialog)
            input.requestFocus()
        }

        dialog.show()
        style(dialog)
    }

    private fun style(
        dialog: android.app.Dialog
    ) {
        val window = dialog.window ?: return

        try {
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT)
            window.setNavigationBarColor(
                androidx.core.content.ContextCompat.getColor(
                    dialog.context,
                    R.color.dialogSurface
                )
            )
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !ThemeManager.resolvedDark(dialog.context)
        } catch (_: Throwable) {
        }

        try {
            window.setDimAmount(
                if (
                    (
                        dialog.context.resources.configuration.uiMode and
                            android.content.res.Configuration.UI_MODE_NIGHT_MASK
                        ) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                ) 0.22f else 0.12f
            )
        } catch (_: Throwable) {
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
