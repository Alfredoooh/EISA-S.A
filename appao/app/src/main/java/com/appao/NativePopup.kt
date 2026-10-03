package com.appao

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

object NativePopup {

    data class Choice(val label: String, val icon: String? = null, val selected: Boolean = false)

    fun showChoice(anchor: View, title: String? = null, choices: List<Choice>, onPick: (Int) -> Unit) {
        val context = anchor.context
        val card = MaterialCardView(context).apply {
            radius = dp(context, 26).toFloat()
            cardElevation = dp(context, 18).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.bgElevated))
            strokeWidth = 0
            isClickable = true
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
        }

        title?.takeIf { it.isNotBlank() }?.let {
            val tv = TextView(context).apply {
                text = it
                setTextColor(ContextCompat.getColor(context, R.color.text))
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8))
            }
            content.addView(tv)
        }

        choices.forEachIndexed { index, choice ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(context, 50)
                setPadding(dp(context, 14), dp(context, 11), dp(context, 12), dp(context, 11))
                background = roundedState(context, dp(context, 18))
                isClickable = true
                isFocusable = true
            }

            choice.icon?.let { icon ->
                val iv = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(context, 19), dp(context, 19))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                IconLoader.applySvg(iv, icon, R.color.iconTint)
                row.addView(iv)
            }

            val label = TextView(context).apply {
                text = choice.label
                setTextColor(ContextCompat.getColor(context, R.color.text))
                textSize = 15f
                setTypeface(typeface, if (choice.selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(context, if (choice.icon == null) 0 else 14)
                }
            }
            row.addView(label)

            if (choice.selected) {
                val check = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(context, 19), dp(context, 19))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                IconLoader.applySvg(check, "check", R.color.iconTint)
                row.addView(check)
            }

            row.setOnClickListener { onPick(index) }
            content.addView(row)
        }

        card.addView(content)
        val popup = PopupWindow(card, dp(context, 248), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(context, 18).toFloat()
        }

        popup.setOnDismissListener { card.animate().cancel() }
        card.tag = popup

        card.measure(
            View.MeasureSpec.makeMeasureSpec(dp(context, 248), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(context, 640), View.MeasureSpec.AT_MOST)
        )
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels
        val x = (location[0] + anchor.width - dp(context, 248)).coerceIn(dp(context, 10), screenW - dp(context, 258))
        val desiredY = location[1] + anchor.height / 2 - card.measuredHeight / 2
        val y = desiredY.coerceIn(dp(context, 10), screenH - card.measuredHeight - dp(context, 10))
        popup.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.START, x, y)

        card.pivotX = card.measuredWidth - dp(context, 18).toFloat()
        card.pivotY = card.measuredHeight / 2f
        card.alpha = 0f
        card.scaleX = 0.5f
        card.scaleY = 0.5f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(420)
            .setInterpolator(Curves.SPRING)
            .start()

        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            child.alpha = 0f
            child.translationY = -dp(context, 8).toFloat()
            child.animate().alpha(1f).translationY(0f)
                .setStartDelay((i * 35L).coerceAtMost(175L))
                .setDuration(320)
                .setInterpolator(Curves.SMOOTH)
                .start()
        }

        // Selection closes with the same short spring used by the HTML menu.
        choices.forEachIndexed { index, _ ->
            val child = content.getChildAt(if (title.isNullOrBlank()) index else index + 1)
            child.setOnClickListener {
                onPick(index)
                card.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f)
                    .setDuration(160)
                    .setInterpolator(Curves.SMOOTH)
                    .withEndAction { popup.dismiss() }
                    .start()
            }
        }
    }

    fun showConfirm(
        context: Context,
        title: String,
        message: String,
        positive: String = "Confirmar",
        negative: String = "Cancelar",
        onResult: (Boolean) -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(buildDialogCard(context, title, message, null, positive, negative) { result, _ ->
            onResult(result)
            dialog.dismiss()
        })
        configureDialogWindow(dialog, bottom = true)
        dialog.setOnShowListener { animateDialogIn(dialog.findViewById(R.id.nativePopupCard)) }
        dialog.show()
        configureDialogWindow(dialog, bottom = true)
    }

    fun showAlert(context: Context, title: String, message: String, button: String = "OK", onDone: () -> Unit = {}) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(buildDialogCard(context, title, message, null, button, null) { _, _ ->
            onDone()
            dialog.dismiss()
        })
        configureDialogWindow(dialog, bottom = true)
        dialog.setOnShowListener { animateDialogIn(dialog.findViewById(R.id.nativePopupCard)) }
        dialog.show()
        configureDialogWindow(dialog, bottom = true)
    }

    fun showPrompt(
        context: Context,
        title: String,
        message: String,
        initial: String,
        positive: String = "OK",
        negative: String = "Cancelar",
        onResult: (String?) -> Unit
    ) {
        val edit = EditText(context).apply {
            setText(initial)
            setTextColor(ContextCompat.getColor(context, R.color.text))
            setHintTextColor(ContextCompat.getColor(context, R.color.dim))
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = backgroundField(context)
            setPadding(dp(context, 16), 0, dp(context, 16), 0)
        }
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(buildDialogCard(context, title, message, edit, positive, negative) { result, _ ->
            onResult(if (result) edit.text.toString() else null)
            dialog.dismiss()
        })
        configureDialogWindow(dialog, bottom = true)
        dialog.setOnShowListener {
            animateDialogIn(dialog.findViewById(R.id.nativePopupCard))
        }
        dialog.show()
        configureDialogWindow(dialog, bottom = true)
    }

    private fun buildDialogCard(
        context: Context,
        title: String,
        message: String,
        input: EditText?,
        positive: String?,
        negative: String?,
        onAction: (Boolean, View?) -> Unit
    ): View {
        val card = MaterialCardView(context).apply {
            id = R.id.nativePopupCard
            radius = dp(context, 32).toFloat()
            cardElevation = dp(context, 18).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.bgElevated))
            strokeWidth = 0
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 20))
        }
        if (title.isNotBlank()) {
            TextView(context).apply {
                text = title
                setTextColor(ContextCompat.getColor(context, R.color.text))
                textSize = 19f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                box.addView(this)
            }
        }
        TextView(context).apply {
            text = message
            setTextColor(ContextCompat.getColor(context, R.color.dim))
            textSize = 14f
            setLineSpacing(0f, 1.25f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 6) }
            box.addView(this)
        }
        input?.let {
            it.layoutParams = LinearLayout.LayoutParams(-1, dp(context, 52)).apply { topMargin = dp(context, 16) }
            box.addView(it)
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(-1, dp(context, 54)).apply { topMargin = dp(context, 18) }
        }
        if (negative != null) {
            val b = TextView(context).apply {
                text = negative
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.text))
                textSize = 14.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = roundedState(context, dp(context, 18), outline = true)
                isClickable = true
                isFocusable = true
            }
            b.layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(context, 6) }
            b.setOnClickListener { onAction(false, input) }
            actions.addView(b)
        }
        positive?.let {
            val b = TextView(context).apply {
                text = it
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.onpri))
                textSize = 14.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = GradientDrawable().apply {
                    cornerRadius = dp(context, 18).toFloat()
                    setColor(ContextCompat.getColor(context, R.color.pri))
                }
                isClickable = true
                isFocusable = true
            }
            b.layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            b.setOnClickListener { onAction(true, input) }
            actions.addView(b)
        }
        box.addView(actions)
        card.addView(box)
        return card
    }

    private fun configureDialogWindow(dialog: Dialog, bottom: Boolean) {
        val w = dialog.window ?: return
        w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        w.attributes = w.attributes.apply {
            dimAmount = if (isDarkWindow(dialog.context)) 0.22f else 0.12f
            gravity = if (bottom) Gravity.BOTTOM else Gravity.CENTER
            y = if (bottom) dp(dialog.context, 10) else 0
        }
        val maxWidth = dp(dialog.context, 480)
        val width = (dialog.context.resources.displayMetrics.widthPixels - dp(dialog.context, 20)).coerceAtMost(maxWidth)
        w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun animateDialogIn(view: View?) {
        view ?: return
        view.translationY = dp(view.context, 36).toFloat()
        view.alpha = 0f
        view.scaleX = 0.96f
        view.scaleY = 0.96f
        view.animate().translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(460)
            .setInterpolator(Curves.IOS)
            .start()
    }

    private fun roundedState(context: Context, radius: Int, outline: Boolean = false): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(ContextCompat.getColor(context, R.color.card))
            if (outline) setStroke(dp(context, 1), ContextCompat.getColor(context, R.color.line))
        }
    }

    private fun backgroundField(context: Context): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(context, 18).toFloat()
        setColor(ContextCompat.getColor(context, R.color.card))
        setStroke(dp(context, 1), ContextCompat.getColor(context, R.color.border))
    }

    private fun isDarkWindow(context: Context): Boolean =
        when (ThemeManager.current(context)) {
            "dark" -> true
            "light" -> false
            else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
