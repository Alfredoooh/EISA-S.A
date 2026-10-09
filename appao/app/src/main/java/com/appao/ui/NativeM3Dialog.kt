package com.appao

import android.app.Dialog
import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Material 3 alert dialogs for ordinary dialog interactions.
 *
 * Bottom sheets are intentionally handled by NativeSheetDialog instead.
 * Theme/Language preference menus remain HtmlStylePopup.
 */
object NativeM3Dialog {

    fun showSelection(
        context: Context,
        title: String,
        items: List<String>,
        checkedIndex: Int,
        onSelected: (Int) -> Unit
    ) {

        val themed =
            ContextThemeWrapper(
                context,
                R.style.Theme_AppAo_Material3Dialog
            )

        val dialog =
            MaterialAlertDialogBuilder(
                themed
            )
                .setTitle(title)
                .setSingleChoiceItems(
                    items.toTypedArray(),
                    checkedIndex
                ) { d, which ->

                    onSelected(
                        which
                    )

                    d.dismiss()
                }
                .setNegativeButton(
                    "Cancelar",
                    null
                )
                .create()

        dialog.setOnShowListener {
            style(
                dialog
            )
        }

        dialog.show()
        style(dialog)
    }

    fun confirm(
        context: Context,
        title: String,
        message: String,
        positive: String = "Confirmar",
        negative: String = "Cancelar",
        onResult: (Boolean) -> Unit
    ) {

        val themed =
            ContextThemeWrapper(
                context,
                R.style.Theme_AppAo_Material3Dialog
            )

        var handled =
            false

        fun finish(
            value: Boolean
        ) {

            if (handled) {
                return
            }

            handled =
                true

            onResult(
                value
            )
        }

        val dialog =
            MaterialAlertDialogBuilder(
                themed
            )
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(
                    negative
                ) { _, _ ->
                    finish(false)
                }
                .setPositiveButton(
                    positive
                ) { _, _ ->
                    finish(true)
                }
                .create()

        dialog.setOnCancelListener {
            finish(false)
        }

        dialog.setOnShowListener {
            style(dialog)

            dialog.getButton(
                Dialog.BUTTON_POSITIVE
            )?.setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.pri
                )
            )

            dialog.getButton(
                Dialog.BUTTON_NEGATIVE
            )?.setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.dim
                )
            )
        }

        dialog.show()
        style(dialog)
    }

    fun content(
        context: Context,
        title: String,
        content: android.view.View,
        onPositive: (() -> Unit)? = null,
        positive: String = "OK",
        negative: String? = "Cancelar"
    ) {

        val themed =
            ContextThemeWrapper(
                context,
                R.style.Theme_AppAo_Material3Dialog
            )

        val builder =
            MaterialAlertDialogBuilder(
                themed
            )
                .setTitle(title)
                .setView(content)

        if (
            negative != null
        ) {

            builder.setNegativeButton(
                negative,
                null
            )
        }

        if (
            onPositive != null
        ) {

            builder.setPositiveButton(
                positive
            ) { _, _ ->
                onPositive()
            }
        }

        val dialog =
            builder.create()

        dialog.setOnShowListener {
            style(dialog)
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
        val themed = ContextThemeWrapper(context, R.style.Theme_AppAo_Material3Dialog)
        val density = context.resources.displayMetrics.density
        val horizontalPadding = (24 * density + 0.5f).toInt()
        val verticalPadding = (8 * density + 0.5f).toInt()

        val input = EditText(themed).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(initial)
            setSelection(text.length)
            hint = "Nome da pesquisa"
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }

        val builder = MaterialAlertDialogBuilder(themed)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar") { _, _ ->
                onResult(input.text?.toString())
            }
        if (message.isNotBlank()) builder.setMessage(message)

        val dialog = builder.create()
        dialog.setOnShowListener {
            style(dialog)
            dialog.getButton(Dialog.BUTTON_POSITIVE)?.setTextColor(
                ContextCompat.getColor(context, R.color.pri)
            )
            dialog.getButton(Dialog.BUTTON_NEGATIVE)?.setTextColor(
                ContextCompat.getColor(context, R.color.dim)
            )
        }
        dialog.show()
        style(dialog)
    }

    private fun style(
        dialog: Dialog
    ) {

        val window =
            dialog.window
                ?: return

        try {

            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setWindowAnimations(R.style.AppAo_ModalWindowAnimations)
            window.setDimAmount(
                if (
                    ThemeManager.resolvedDark(
                        dialog.context
                    )
                ) {
                    0.22f
                } else {
                    0.12f
                }
            )

            val surface = ContextCompat.getColor(dialog.context, R.color.dialogSurface)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = surface
            androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
                val dark = ThemeManager.resolvedDark(dialog.context)
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }

        } catch (_: Throwable) {
        }
    }
}
