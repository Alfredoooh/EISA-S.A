package com.appao

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen category selector populated only from the server taxonomy. */
class CategoryManagerActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("appao", MODE_PRIVATE) }
    private val hiddenIds = mutableSetOf<String>()
    private lateinit var categoryList: LinearLayout
    private lateinit var loadingLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_categories)
        SystemBarHelper.sync(this)

        categoryList = findViewById(R.id.categoryList)
        loadingLabel = findViewById(R.id.categoryLoading)
        hiddenIds += (prefs.getStringSet("feed_hidden_categories", emptySet()) ?: emptySet())
        IconLoader.applySvg(findViewById(R.id.categoryBackIcon), "arrow_left", R.color.iconTint)
        findViewById<View>(R.id.categoryBack).background = ContextCompat.getDrawable(this, R.drawable.bg_circle_card)
        findViewById<View>(R.id.categoryBack).setOnClickListener { closeWithResult() }

        // Render the last known server taxonomy immediately, then refresh it quietly.
        render(NewsRepository.categoriesSnapshot())
        loadingLabel.visibility = View.GONE
        lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) { NewsRepository.fetchCategories(forceRefresh = true) }
            if (isFinishing || isDestroyed) return@launch
            render(categories)
        }
    }

    private fun render(categories: List<NewsCategory>) {
        categoryList.removeAllViews()
        categories.forEachIndexed { index, category ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(12), dp(12))
                background = ContextCompat.getDrawable(this@CategoryManagerActivity, R.drawable.bg_settings_single)
                minimumHeight = dp(68)
                isClickable = true
                isFocusable = true
            }
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            labels.addView(TextView(this).apply {
                text = category.label
                setTextColor(ContextCompat.getColor(this@CategoryManagerActivity, R.color.text))
                textSize = 15.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 2
            })
            if (category.providers.isNotEmpty()) {
                labels.addView(TextView(this).apply {
                    text = category.providers.joinToString(" · ") { it.uppercase() }
                    setTextColor(ContextCompat.getColor(this@CategoryManagerActivity, R.color.dim))
                    textSize = 11.5f
                    setPadding(0, dp(3), 0, 0)
                })
            }
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val toggle = SwitchCompat(this).apply {
                isChecked = category.id !in hiddenIds
                contentDescription = "Mostrar ${category.label}"
                setOnCheckedChangeListener { _, checked ->
                    if (checked) hiddenIds.remove(category.id) else hiddenIds.add(category.id)
                    prefs.edit().putStringSet("feed_hidden_categories", hiddenIds.toSet()).apply()
                    setResult(RESULT_OK)
                }
            }
            row.addView(toggle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
            categoryList.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) topMargin = dp(6)
            })
        }
    }

    private fun closeWithResult() {
        setResult(RESULT_OK)
        finish()
        overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit)
    }

    @Deprecated("Deprecated in Android API; retained for back behavior on older versions")
    override fun onBackPressed() = closeWithResult()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
