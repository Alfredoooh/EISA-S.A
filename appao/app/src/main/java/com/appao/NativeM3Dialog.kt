package com.appao

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Material 3 dialogs used for app-level modals. This deliberately avoids the
 * old custom paper-sheet implementation for ordinary modal interactions.
 */
object NativeM3Dialog {

    fun showSelection(
        context: Context,
        title: String,
        items: List<String>,
        checkedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        val themed = ContextThemeWrapper(
            context,
            R.style.Theme_AppAo_Material3Dialog
        )

        val dialog = MaterialAlertDialogBuilder(themed)
            .setTitle(title)
            .setSingleChoiceItems(
                items.toTypedArray(),
                checkedIndex
            ) { dialog, which ->
                try {
                    onSelected(which)
                } finally {
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            styleDialog(dialog)
        }

        dialog.show()
        styleDialog(dialog)
    }

    fun confirm(
        context: Context,
        title: String,
        message: String,
        positive: String = "Confirmar",
        negative: String = "Cancelar",
        onResult: (Boolean) -> Unit
    ) {
        val themed = ContextThemeWrapper(
            context,
            R.style.Theme_AppAo_Material3Dialog
        )

        var called = false

        fun finish(value: Boolean) {
            if (called) return
            called = true
            onResult(value)
        }

        val dialog = MaterialAlertDialogBuilder(themed)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(negative) { _, _ ->
                finish(false)
            }
            .setPositiveButton(positive) { _, _ ->
                finish(true)
            }
            .create()

        dialog.setOnCancelListener {
            finish(false)
        }

        dialog.setOnShowListener {
            styleDialog(dialog)
        }

        dialog.show()
        styleDialog(dialog)
    }

    fun content(
        context: Context,
        title: String,
        content: View,
        onPositive: (() -> Unit)? = null,
        positive: String = "OK",
        negative: String? = "Cancelar"
    ) {
        val themed = ContextThemeWrapper(
            context,
            R.style.Theme_AppAo_Material3Dialog
        )

        val builder = MaterialAlertDialogBuilder(themed)
            .setTitle(title)
            .setView(content)

        if (negative != null) {
            builder.setNegativeButton(
                negative,
                null
            )
        }

        if (onPositive != null) {
            builder.setPositiveButton(
                positive
            ) { _, _ ->
                onPositive()
            }
        }

        val dialog = builder.create()

        dialog.setOnShowListener {
            styleDialog(dialog)
        }

        dialog.show()
        styleDialog(dialog)
    }

    private fun styleDialog(
        dialog: android.app.Dialog
    ) {
        val window = dialog.window ?: return

        try {
            window.setDimAmount(
                if (isDark(dialog.context)) 0.22f else 0.12f
            )
        } catch (_: Throwable) {
        }
    }

    private fun isDark(
        context: Context
    ): Boolean = (
        context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        ) == android.content.res.Configuration.UI_MODE_NIGHT_YES
}
