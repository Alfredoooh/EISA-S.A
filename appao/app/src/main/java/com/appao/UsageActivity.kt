package com.appao

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UsageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_usage)
        SystemBarHelper.sync(this)

        val root = findViewById<View>(R.id.usageRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        IconLoader.applySvg(findViewById(R.id.usageBackIcon), "back", R.color.iconTint)
        findViewById<View>(R.id.usageBack).setOnClickListener { finish() }
        render()
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
        if (rootReady) render()
    }

    private var rootReady = false

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

        val bar = findViewById<UsageChartView>(R.id.usageBars)
        bar.setBarData(week, labels)

        val sources = UsageTracker.sourceCounts(this)
        val pie = findViewById<UsageChartView>(R.id.usagePie)
        pie.setPieData(sources.map { it.second }, sources.map { it.first })

        val list = findViewById<LinearLayout>(R.id.usageSourcesList)
        list.removeAllViews()
        if (sources.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Ainda não há fontes visitadas suficientes para apresentar."
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.dim))
                setPadding(dp(4), dp(8), dp(4), dp(8))
            })
        } else {
            sources.forEachIndexed { index, pair ->
                list.addView(TextView(this).apply {
                    text = "${index + 1}. ${pair.first}    ${pair.second} visita(s)"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.text))
                    gravity = Gravity.CENTER_VERTICAL
                    minHeight = dp(42)
                    setPadding(dp(4), 0, dp(4), 0)
                })
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + .5f).toInt()
}
