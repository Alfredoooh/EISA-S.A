package com.appao

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

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
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ctx.getDrawable(R.drawable.bg_popup)
            setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8))
            elevation = dp(ctx, 16).toFloat()
        }

        for (a in APPS) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
                isClickable = true
                isFocusable = true
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

        val popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.setBackgroundDrawable(ColorDrawable(0x00000000))
        popup.elevation = dp(ctx, 16).toFloat()
        popup.animationStyle = R.style.AppAo_PopupAnim
        anchor.tag = popup

        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val h = container.measuredHeight
        popup.showAsDropDown(anchor, 0, -(h + dp(ctx, 12)))
    }

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}
