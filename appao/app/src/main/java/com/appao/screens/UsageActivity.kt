package com.appao

import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UsageActivity : AppCompatActivity() {

    private var rootReady = false
    private var firstResumeHandled = false
    private var showAllVisits = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_usage)
        SystemBarHelper.sync(this)

        IconLoader.applySvg(findViewById(R.id.usageBackIcon), "arrow_left", R.color.iconTint)
        findViewById<View>(R.id.usageBack).setOnClickListener { finish() }
        render()
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
        if (rootReady && firstResumeHandled) render()
        firstResumeHandled = true
    }

    private fun render() {
        rootReady = true
        val week = UsageTracker.dailyMillis(this)
        val labels = ArrayList<String>()
        val dayFmt = SimpleDateFormat("EEE", Locale("pt", "PT"))
        for (i in 0..6) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i - 6) }
            labels += dayFmt.format(cal.time).take(3)
        }

        val totalMinutes = UsageTracker.totalMillis(this) / 60000L
        findViewById<TextView>(R.id.usageTotal).text =
            if (totalMinutes < 60) "$totalMinutes min" else "${totalMinutes / 60} h ${totalMinutes % 60} min"

        IconLoader.applySvg(findViewById(R.id.usagePieIcon), "chart_pie", R.color.iconTint)
        findViewById<UsageChartView>(R.id.usageBars).setBarData(week, labels)

        val counts = UsageTracker.sourceCounts(this)
        findViewById<UsageChartView>(R.id.usagePie).setPieData(
            counts.map { it.second }, counts.map { it.first }
        )
        renderTodayVisits(UsageTracker.todaysVisits(this))
    }

    private fun renderTodayVisits(visits: List<UsageTracker.Visit>) {
        val list = findViewById<LinearLayout>(R.id.usageSourcesList)
        val seeAll = findViewById<TextView>(R.id.usageSeeAll)
        list.removeAllViews()
        seeAll.visibility = if (visits.size > 5) View.VISIBLE else View.GONE
        seeAll.text = if (showAllVisits) "Ver menos" else "Ver todas"
        seeAll.setOnClickListener {
            showAllVisits = !showAllVisits
            renderTodayVisits(UsageTracker.todaysVisits(this))
        }

        if (visits.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Ainda não visitaste fontes de notícias hoje. Abre um artigo para começares o histórico."
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.dim))
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = ContextCompat.getDrawable(context, R.drawable.bg_settings_single)
            })
            return
        }

        val visible = if (showAllVisits) visits else visits.take(5)
        visible.forEachIndexed { index, visit ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(62)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = ContextCompat.getDrawable(context, when {
                    visible.size == 1 -> R.drawable.bg_settings_single
                    index == 0 -> R.drawable.bg_settings_top
                    index == visible.lastIndex -> R.drawable.bg_settings_bottom
                    else -> R.drawable.bg_settings_middle
                })
                alpha = 0f
                translationY = dp(6).toFloat()
            }

            val faviconFrame = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(context, R.color.bgElevated))
                }
                clipToOutline = true
            }
            val favicon = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            faviconFrame.addView(favicon, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
            val faviconUrl = visit.logo.takeIf { it.isNotBlank() } ?: visit.link.takeIf { it.isNotBlank() }?.let { link ->
                val host = runCatching { Uri.parse(link).host }.getOrNull().orEmpty()
                if (host.isNotBlank()) "https://www.google.com/s2/favicons?domain=$host&sz=64" else ""
            }.orEmpty()
            if (faviconUrl.isNotBlank()) {
                Glide.with(favicon).load(faviconUrl).circleCrop().placeholder(R.drawable.bg_circle_card).into(favicon)
            }
            row.addView(faviconFrame, LinearLayout.LayoutParams(dp(36), dp(36)))

            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this).apply {
                text = visit.source.ifBlank { "Fonte desconhecida" }
                textSize = 13.5f
                setTextColor(ContextCompat.getColor(context, R.color.text))
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            labels.addView(TextView(this).apply {
                text = visit.title.ifBlank { visit.link }
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.dim))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(3), 0, 0)
            })
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            })

            val time = TextView(this).apply {
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(visit.timestamp))
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.dim))
            }
            row.addView(time)
            row.setOnClickListener {
                if (visit.link.isNotBlank()) runCatching {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(visit.link)))
                }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (index > 0) lp.topMargin = dp(2)
            list.addView(row, lp)
            row.animate().alpha(1f).translationY(0f).setStartDelay(index * 22L)
                .setDuration(260L).setInterpolator(Curves.SMOOTH).start()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
}
