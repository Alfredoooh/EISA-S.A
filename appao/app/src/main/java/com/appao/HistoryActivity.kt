package com.appao

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.appao.screens.AppViewerActivity
import com.appao.screens.BrowserTabStore
import com.appao.ui.IconLoader
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/** Real browser history, separate from history/privacy settings. */
class HistoryActivity : AppCompatActivity() {
    private lateinit var historyList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        SystemBarHelper.sync(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@HistoryActivity, R.color.bg))
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(5), dp(14), dp(5))
        }
        val back = FrameLayout(this).apply {
            background = ContextCompat.getDrawable(this@HistoryActivity, R.drawable.bg_transparent_pressed)
            isClickable = true; isFocusable = true; contentDescription = "Voltar"
            addView(ImageView(this@HistoryActivity).apply {
                layoutParams = FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER)
                scaleType = ImageView.ScaleType.FIT_CENTER
                IconLoader.applyIcon(this, "arrow_left", R.color.iconTint)
            })
            setOnClickListener { finish(); overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit) }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(40), dp(44)))
        toolbar.addView(TextView(this).apply {
            text = "Histórico"; textSize = 19f; setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.text))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        val clear = TextView(this).apply {
            text = "Limpar"; textSize = 13f; setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.pri))
            gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(4), 0); isClickable = true; isFocusable = true
            setOnClickListener {
                NativeM3Dialog.confirm(this@HistoryActivity, "Limpar histórico", "Apagar todas as páginas visitadas?", positive = "Limpar") { accepted ->
                    if (accepted) { BrowsingHistoryStore.clear(this@HistoryActivity); if (::historyList.isInitialized) render(historyList) }
                }
            }
        }
        toolbar.addView(clear, LinearLayout.LayoutParams(-2, dp(44)))
        root.addView(toolbar)
        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER; isFillViewport = true }
        historyList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(24))
        }
        scroll.addView(historyList, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render(historyList)
    }

    private fun render(parent: LinearLayout) {
        parent.removeAllViews()
        val entries = BrowsingHistoryStore.all(this)
        if (entries.isEmpty()) {
            val empty = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(24), dp(70), dp(24), dp(24)) }
            empty.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(132), dp(112)).apply { gravity = Gravity.CENTER }
                setImageDrawable(com.appao.EmptyHistoryIllustrationDrawable(this@HistoryActivity))
                scaleType = ImageView.ScaleType.FIT_CENTER
            })
            empty.addView(TextView(this).apply {
                text = "Ainda não há páginas no histórico"; textSize = 16f; gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.text))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setPadding(0, dp(14), 0, dp(5))
            })
            empty.addView(TextView(this).apply {
                text = "As páginas visitadas aparecerão aqui."; textSize = 13f; gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.dim))
            })
            parent.addView(empty, LinearLayout.LayoutParams(-1, -1))
            return
        }
        entries.forEachIndexed { index, entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(64); setPadding(dp(13), dp(9), dp(12), dp(9))
                background = ContextCompat.getDrawable(this@HistoryActivity, when {
                    entries.size == 1 -> R.drawable.bg_settings_single
                    index == 0 -> R.drawable.bg_settings_top
                    index == entries.lastIndex -> R.drawable.bg_settings_bottom
                    else -> R.drawable.bg_settings_middle
                })
                isClickable = true; isFocusable = true
                setOnClickListener {
                    val tab = BrowserTabStore.create(this@HistoryActivity, entry.url)
                    startActivity(Intent(this@HistoryActivity, AppViewerActivity::class.java).apply {
                        putExtra("browser_mode", true); putExtra("browser_tab_id", tab.id); putExtra("url", tab.url)
                    })
                    overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
                }
            }
            val icon = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            IconLoader.applyIcon(icon, "globe", R.color.iconTint)
            val faviconFallback = icon.drawable
            val faviconUrl = BrowserTabStore.faviconFor(entry.url)
            if (faviconUrl.isNotBlank()) {
                Glide.with(this)
                    .load(faviconUrl)
                    .fitCenter()
                    .placeholder(faviconFallback)
                    .error(faviconFallback)
                    .into(icon)
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(19), dp(19)))
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(4), 0) }
            column.addView(TextView(this).apply {
                text = entry.title.ifBlank { entry.url }; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 14f; setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.text))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            val date = if (entry.visitedAt > 0) SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault()).format(Date(entry.visitedAt)) else ""
            column.addView(TextView(this).apply {
                text = entry.url; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 11.5f; setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.dim)); setPadding(0, dp(3), 0, 0)
            })
            row.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(this).apply {
                text = date; textSize = 10.5f; maxLines = 1; maxWidth = dp(112)
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.dim)); gravity = Gravity.CENTER_VERTICAL
            })
            parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(1) })
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
