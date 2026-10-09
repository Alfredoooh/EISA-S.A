package com.appao

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.content.res.ColorStateList
import com.google.android.material.materialswitch.MaterialSwitch
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
        IconLoader.applySvg(findViewById(R.id.categoryBackIcon), "nav_back", R.color.iconTint)
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
                setPadding(dp(14), dp(9), dp(10), dp(9))
                val cardDrawable = when {
                    categories.size == 1 -> R.drawable.bg_settings_single
                    index == 0 -> R.drawable.bg_settings_top
                    index == categories.lastIndex -> R.drawable.bg_settings_bottom
                    else -> R.drawable.bg_settings_middle
                }
                background = ContextCompat.getDrawable(this@CategoryManagerActivity, cardDrawable)
                minimumHeight = dp(58)
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
                textSize = 14.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 2
            })
            if (category.providers.isNotEmpty()) {
                labels.addView(TextView(this).apply {
                    text = category.providers.joinToString(" · ") { it.uppercase() }
                    setTextColor(ContextCompat.getColor(this@CategoryManagerActivity, R.color.dim))
                    textSize = 10.5f
                    setPadding(0, dp(2), 0, 0)
                })
            }
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val checkedState = intArrayOf(android.R.attr.state_checked)
            val defaultState = intArrayOf()
            val toggle = MaterialSwitch(this).apply {
                thumbTintList = ColorStateList(arrayOf(checkedState, defaultState), intArrayOf(
                    ContextCompat.getColor(this@CategoryManagerActivity, R.color.pri),
                    ContextCompat.getColor(this@CategoryManagerActivity, R.color.dim)
                ))
                trackTintList = ColorStateList(arrayOf(checkedState, defaultState), intArrayOf(
                    ContextCompat.getColor(this@CategoryManagerActivity, R.color.pri),
                    ContextCompat.getColor(this@CategoryManagerActivity, R.color.card2)
                ))
                minimumWidth = dp(50)
                minimumHeight = dp(32)
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
                if (index > 0) topMargin = dp(2)
                bottomMargin = dp(2)
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
