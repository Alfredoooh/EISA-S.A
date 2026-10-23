package com.appao

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Top-anchored native snackbar matching the rounded, springing HTML reference. */
object AppAoToast {
    private val handler = Handler(Looper.getMainLooper())
    private var activePopup: PopupWindow? = null
    private var activeDismiss: Runnable? = null
    private var activeAnimation: AnimatorSet? = null

    fun show(context: Context, message: String) {
        val activity = context as? Activity
        val decor = activity?.window?.decorView as? ViewGroup
        if (activity == null || activity.isFinishing || activity.isDestroyed || decor == null) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        dismissCurrentImmediately()
        val density = context.resources.displayMetrics.density
        fun dp(value: Float) = (value * density + 0.5f).toInt()
        val card = buildCard(context, message, density)
        val popup = PopupWindow(card, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            isOutsideTouchable = false
            isFocusable = false
            isTouchable = true
            elevation = dp(8f).toFloat()
            animationStyle = 0
        }
        activePopup = popup
        card.alpha = 0f
        card.translationY = -dp(60f).toFloat()
        card.scaleX = 0.85f
        card.scaleY = 0.85f

        val hide = Runnable {
            if (activePopup !== popup) return@Runnable
            activeDismiss = null
            card.isClickable = false
            activeAnimation?.cancel()
            val exit = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(card, View.ALPHA, card.alpha, 0f),
                    ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, card.translationY, -dp(50f).toFloat()),
                    ObjectAnimator.ofFloat(card, View.SCALE_X, card.scaleX, 0.9f),
                    ObjectAnimator.ofFloat(card, View.SCALE_Y, card.scaleY, 0.9f)
                )
                duration = 350L
                interpolator = AccelerateInterpolator(1.5f)
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        runCatching { popup.dismiss() }
                        if (activePopup === popup) activePopup = null
                        if (activeAnimation === animation) activeAnimation = null
                    }
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        runCatching { popup.dismiss() }
                        if (activePopup === popup) activePopup = null
                        if (activeAnimation === animation) activeAnimation = null
                    }
                })
            }
            activeAnimation = exit
            runCatching { exit.start() }.onFailure {
                runCatching { popup.dismiss() }
                if (activePopup === popup) activePopup = null
            }
        }
        activeDismiss = hide

        val topOffset = topSafeInsetPx(activity, ::dp)
        decor.post {
            if (activePopup !== popup || activity.isFinishing || activity.isDestroyed) return@post
            runCatching {
                popup.showAtLocation(decor, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, topOffset)
            }.onFailure {
                if (activePopup === popup) activePopup = null
                return@post
            }
            activeAnimation = animateEntrance(card, ::dp)
        }
        handler.postDelayed(hide, 3000L)
        card.setOnClickListener {
            activeDismiss?.let(handler::removeCallbacks)
            hide.run()
        }
    }

    private fun dismissCurrentImmediately() {
        activeDismiss?.let(handler::removeCallbacks)
        activeDismiss = null
        activeAnimation?.cancel()
        activeAnimation = null
        activePopup?.let { popup -> runCatching { popup.dismiss() } }
        activePopup = null
    }

    private fun animateEntrance(card: View, dp: (Float) -> Int): AnimatorSet {
        // HTML keyframes: -60px/.85 -> +6px/1.02 at 60%, then settle at 0/1.
        val overshoot = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, -dp(60f).toFloat(), dp(6f).toFloat()),
                ObjectAnimator.ofFloat(card, View.SCALE_X, 0.85f, 1.02f),
                ObjectAnimator.ofFloat(card, View.SCALE_Y, 0.85f, 1.02f)
            )
            duration = 270L
            interpolator = DecelerateInterpolator(1.2f)
        }
        val settle = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, dp(6f).toFloat(), 0f),
                ObjectAnimator.ofFloat(card, View.SCALE_X, 1.02f, 1f),
                ObjectAnimator.ofFloat(card, View.SCALE_Y, 1.02f, 1f)
            )
            duration = 180L
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        }
        return AnimatorSet().apply {
            playSequentially(overshoot, settle)
            start()
        }
    }

    private fun topSafeInsetPx(activity: Activity, dp: (Float) -> Int): Int {
        val insetTop = if (Build.VERSION.SDK_INT >= 23) {
            activity.window.decorView.rootWindowInsets?.systemWindowInsetTop ?: dp(24f)
        } else dp(24f)
        return insetTop.coerceAtLeast(0) + dp(24f)
    }

    private fun buildCard(context: Context, message: String, density: Float): TextView {
        fun dp(value: Float) = (value * density + 0.5f).toInt()
        val horizontalPadding = dp(26f)
        val verticalPadding = dp(14f)
        val maxWidth = (context.resources.displayMetrics.widthPixels - dp(32f)).coerceAtLeast(dp(180f))
        return TextView(context).apply {
            text = message
            setTextColor(ContextCompat.getColor(context, R.color.snackbarText))
            textSize = 14f
            maxLines = 2
            includeFontPadding = false
            gravity = Gravity.CENTER
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            setMaxWidth(maxWidth)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999f).toFloat()
                setColor(ContextCompat.getColor(context, R.color.snackbarBg))
            }
            isClickable = true
            isFocusable = false
            contentDescription = message
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}
