package com.appao

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

object AppsPopup {

    data class App(val name: String, val png: String, val url: String?)

    val APPS = listOf(
        App("IA",          "magic_wand",      "apps/ai.html"),
        App("Loja",        "store",           "apps/store.html"),
        App("Notícias",    "news_feed",       "apps/news.html"),
        App("Jogos",       "game_controller", "apps/games.html"),
        App("Verificados", "verified",        "apps/verified.html"),
        App("Guardados",   "stack",           "apps/saved.html")
    )

    fun show(anchor: View, onPick: (App) -> Unit) {
        val ctx = anchor.context

        val card = MaterialCardView(ctx).apply {
            radius = dp(ctx, 24).toFloat()
            cardElevation = dp(ctx, 12).toFloat()
            setCardBackgroundColor(ctx.getColor(R.color.bgElevated))
            strokeWidth = 0
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 6), dp(ctx, 8), dp(ctx, 6), dp(ctx, 8))
        }

        for (a in APPS) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
                isClickable = true; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    dp(ctx, 220),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                background = ctx.getDrawable(R.drawable.bg_chip_ripple)
            }
            val iv = ImageView(ctx)
            iv.layoutParams = LinearLayout.LayoutParams(dp(ctx, 24), dp(ctx, 24))
            IconLoader.applyPng(iv, a.png)
            row.addView(iv)

            val tv = TextView(ctx).apply {
                text = a.name
                setTextColor(ctx.getColor(R.color.text))
                textSize = 15f
            }
            val tvLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tvLp.marginStart = dp(ctx, 14)
            tv.layoutParams = tvLp
            row.addView(tv)

            row.setOnClickListener {
                onPick(a)
                (anchor.tag as? PopupWindow)?.dismiss()
            }
            container.addView(row)
        }

        card.addView(container)

        val popup = PopupWindow(
            card,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.setBackgroundDrawable(ColorDrawable(0x00000000))
        popup.elevation = dp(ctx, 12).toFloat()
        anchor.tag = popup

        card.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val h = card.measuredHeight
        popup.showAsDropDown(anchor, 0, -(h + dp(ctx, 12)))

        card.pivotX = card.measuredWidth.toFloat()
        card.pivotY = 0f
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        card.alpha = 0f
        card.animate()
            .scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}
