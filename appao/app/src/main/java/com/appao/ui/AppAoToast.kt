package com.appao

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.appao.ui.IconLoader

/**
 * iOS-style floating toast: appears docked to the TOP of the screen, fully rounded pill shape,
 * with a real backdrop blur (RenderEffect) of whatever is behind it on Android 12+, and a strong
 * translucent app-surface fallback on older OS versions where RenderEffect does not exist.
 *
 * This intentionally does not use com.google.android.material.snackbar.Snackbar — Snackbar is
 * bottom-anchored and opaque by design, neither of which fits the requested look. All 60+
 * existing call sites keep working unchanged since the public signature (context, message) is
 * the same.
 */
object AppAoToast {
    private val handler = Handler(Looper.getMainLooper())
    private var activePopup: PopupWindow? = null
    private var activeDismiss: Runnable? = null

    fun show(context: Context, message: String) {
        val activity = context as? Activity
        val decor = activity?.window?.decorView as? ViewGroup
        val contentRoot = activity?.findViewById<ViewGroup>(android.R.id.content)
        if (activity == null || activity.isFinishing || activity.isDestroyed || decor == null || contentRoot == null) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Only one of our toasts on screen at a time, same as the old Snackbar behaviour.
        activeDismiss?.let(handler::removeCallbacks)
        activePopup?.let { runCatching { it.dismiss() } }

        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()

        val card = buildCard(context, message, dp)

        val popup = PopupWindow(card, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            isOutsideTouchable = false
            isFocusable = false
            isTouchable = true
            elevation = dp(10).toFloat()
            animationStyle = 0
        }
        activePopup = popup

        val topInset = topSafeInsetPx(activity, dp)
        card.alpha = 0f
        card.translationY = -dp(28).toFloat()

        // Snapshot-and-blur needs the card to exist (for sizing) before it's shown, and the
        // popup needs to be shown before we can grab a real screenshot of what's behind it.
        decor.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            runCatching {
                popup.showAtLocation(decor, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, topInset)
            }.onFailure { return@post }

            applyBackdropBlur(activity, decor, card, dp)

            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(320L)
                .setInterpolator(Curves.IOS)
                .start()
        }

        val dismissRunnable = Runnable {
            if (activePopup !== popup) return@Runnable
            runCatching {
                card.animate()
                    .alpha(0f)
                    .translationY(-dp(20).toFloat())
                    .setDuration(220L)
                    .setInterpolator(Curves.IOS)
                    .withEndAction { runCatching { popup.dismiss() } }
                    .start()
            }.onFailure { runCatching { popup.dismiss() } }
            if (activePopup === popup) activePopup = null
        }
        activeDismiss = dismissRunnable
        handler.postDelayed(dismissRunnable, 3600L)

        card.setOnClickListener {
            activeDismiss?.let(handler::removeCallbacks)
            dismissRunnable.run()
        }
    }

    private fun topSafeInsetPx(activity: Activity, dp: (Int) -> Int): Int {
        val insets = activity.window.decorView.rootWindowInsets
        val systemTop = if (Build.VERSION.SDK_INT >= 23 && insets != null) {
            insets.systemWindowInsetTop
        } else 0
        return systemTop.coerceAtLeast(dp(28)) + dp(6)
    }

    private fun buildCard(context: Context, message: String, dp: (Int) -> Int): LinearLayout {
        val surfaceColor = ContextCompat.getColor(context, R.color.card)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(18), dp(12), dp(10), dp(12))
            clipToOutline = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(28).toFloat()
                // Not fully transparent: this is the fallback/tint layer that sits on top of the
                // blurred snapshot (or, on old OS versions, is the only layer). A blur with zero
                // tint looks like smeared glass; a light tint of the app surface color is what
                // reads as "frosted glass" rather than "transparent".
                setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, 235))
            }
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(28).toFloat())
                }
            }

            addView(TextView(context).apply {
                text = message
                setTextColor(ContextCompat.getColor(context, R.color.text))
                textSize = 15f
                maxLines = 2
                gravity = Gravity.CENTER_VERTICAL
                includeFontPadding = false
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(10)
            })

            addView(ImageView(context).apply {
                val iconBitmap = IconLoader.iconBitmap(context, "close", dp(16))
                val baseIcon: Drawable = iconBitmap?.let { BitmapDrawable(context.resources, it) }
                    ?: ContextCompat.getDrawable(context, android.R.drawable.ic_menu_close_clear_cancel)!!
                val icon = DrawableCompat.wrap(baseIcon).mutate().apply {
                    DrawableCompat.setTint(this, ContextCompat.getColor(context, R.color.iconTint))
                }
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Fechar"
            }, LinearLayout.LayoutParams(dp(28), dp(28)))
        }
    }

    /** Real backdrop blur on Android 12+, via RenderEffect on a snapshot of what's behind the
        card. RenderEffect/RenderNode blur does not exist below API 31 — RenderScript (the old
        alternative) is deprecated and was removed as a recommended path, so older OS versions
        keep the solid frosted-tint background set in buildCard instead of a blurred one. */
    private fun applyBackdropBlur(activity: Activity, decor: ViewGroup, card: View, dp: (Int) -> Int) {
        if (Build.VERSION.SDK_INT < 31) return
        if (card.width <= 0 || card.height <= 0) {
            card.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    card.viewTreeObserver.removeOnPreDrawListener(this)
                    applyBackdropBlur(activity, decor, card, dp)
                    return true
                }
            })
            return
        }
        runCatching {
            val location = IntArray(2)
            card.getLocationInWindow(location)
            val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            decor.draw(canvas)
            val cropX = location[0].coerceIn(0, (bitmap.width - card.width).coerceAtLeast(0))
            val cropY = location[1].coerceIn(0, (bitmap.height - card.height).coerceAtLeast(0))
            val cropW = card.width.coerceAtMost(bitmap.width - cropX)
            val cropH = card.height.coerceAtMost(bitmap.height - cropY)
            if (cropW <= 0 || cropH <= 0) return
            val cropped = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
            val blurDrawable = BitmapDrawable(activity.resources, cropped).apply {
                setBounds(0, 0, cropW, cropH)
            }
            val blurHost = ImageView(activity).apply {
                setImageDrawable(blurDrawable)
                scaleType = ImageView.ScaleType.FIT_XY
                setRenderEffect(RenderEffect.createBlurEffect(dp(18).toFloat(), dp(18).toFloat(), Shader.TileMode.CLAMP))
                clipToOutline = true
                outlineProvider = card.outlineProvider
            }
            if (card is ViewGroup) {
                card.addView(blurHost, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
    }
}