package com.appao

import android.app.Dialog
import android.content.Context
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Compatibility wrapper retained for existing callers.
 * Ordinary modal content now uses the Material 3 dialog component instead of
 * the old hand-built floating/paper sheet implementation.
 */
object NativeSheetDialog {

    fun show(
        context: Context,
        content: View
    ): Dialog {
        val themed = ContextThemeWrapper(
            context,
            R.style.Theme_AppAo_Material3Dialog
        )

        return MaterialAlertDialogBuilder(themed)
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.window?.setDimAmount(
                        if (
                            (
                                context.resources.configuration.uiMode and
                                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                                ) ==
                                android.content.res.Configuration.UI_MODE_NIGHT_YES
                        ) 0.22f else 0.12f
                    )
                }
                dialog.show()
            }
    }
}
