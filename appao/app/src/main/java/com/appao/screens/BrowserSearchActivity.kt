package com.appao.screens

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.appao.BrowserAutocomplete
import com.appao.BrowserPreferences
import com.appao.BrowsingHistoryStore
import com.appao.R
import com.appao.SystemBarHelper
import com.appao.ui.IconLoader
import com.appao.ui.NativePopupMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale


/** Dedicated address/search screen. It returns a target to the browser instead of adding itself to browser history. */
class BrowserSearchActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_INITIAL_QUERY = "browser_search_initial_query"
        const val EXTRA_RESULT_VALUE = "browser_search_result_value"
    }

    private lateinit var input: EditText
    private lateinit var rows: LinearLayout
    private lateinit var engineLabel: TextView
    private lateinit var engineIcon: ImageView
    private lateinit var clearIcon: View
    private val handler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    private var generation = 0L
    private var privateMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        SystemBarHelper.sync(this)
        window.statusBarColor = ContextCompat.getColor(this, R.color.bg)
        setContentView(buildContent())

        privateMode = intent.getBooleanExtra("browser_private_mode", false)
        val initial = intent.getStringExtra(EXTRA_INITIAL_QUERY).orEmpty()
        input.setText(initial)
        input.setSelection(input.text?.length ?: 0)
        clearIcon.visibility = if (initial.isNotBlank()) View.VISIBLE else View.GONE
        renderSuggestions(initial.trim(), emptyList())
        configureInput()

        input.requestFocus()
        if (initial.isNotBlank()) input.selectAll()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finishWithFade() }
        })
        input.post {
            if (!isFinishing && !isDestroyed) {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun buildContent(): View {
        val surface = ContextCompat.getColor(this, R.color.bg)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58))
        }
        val back = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            foreground = ContextCompat.getDrawable(this@BrowserSearchActivity, R.drawable.bg_transparent_pressed)
            contentDescription = "Voltar ao browser"
            addView(ImageView(this@BrowserSearchActivity).apply {
                layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                IconLoader.applyIcon(this, "arrow_left", R.color.iconTint)
            })
            setOnClickListener { finishWithFade() }
        }
        header.addView(back, LinearLayout.LayoutParams(dp(40), dp(42)))

        // This is the same 42dp toolbar pill used by activity_app_viewer.xml.
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(5), 0)
            background = ContextCompat.getDrawable(this@BrowserSearchActivity, R.drawable.bg_toolbar_actions)
        }
        engineIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Escolher motor de pesquisa"
            isClickable = true
            isFocusable = true
            setOnClickListener { showSearchEnginePicker(this) }
        }
        pill.addView(engineIcon)
        input = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(7) }
            background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            setTextColor(ContextCompat.getColor(this@BrowserSearchActivity, R.color.text))
            setHintTextColor(ContextCompat.getColor(this@BrowserSearchActivity, R.color.dim))
            textSize = 14f
            hint = "Pesquisar ou inserir endereço"
            setPadding(0, 0, dp(4), 0)
        }
        pill.addView(input)
        clearIcon = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(34))
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            foreground = ContextCompat.getDrawable(this@BrowserSearchActivity, R.drawable.bg_transparent_pressed)
            contentDescription = "Limpar pesquisa"
            addView(ImageView(this@BrowserSearchActivity).apply {
                layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                IconLoader.applyIcon(this, "close", R.color.iconTint)
            })
            setOnClickListener {
                input.setText("")
                input.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        pill.addView(clearIcon)
        header.addView(pill, LinearLayout.LayoutParams(0, dp(42), 1f))
        root.addView(header)

        engineLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@BrowserSearchActivity, R.color.dim))
            setPadding(dp(18), dp(6), dp(18), dp(6))
        }
        root.addView(engineLabel)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(dp(8), dp(3), dp(8), dp(18))
        }
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(rows, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        updateSearchEngineIcon()
        return root
    }

    private fun updateSearchEngineIcon() {
        if (!::engineIcon.isInitialized) return
        val url = when (BrowserPreferences.searchEngine(this).lowercase(Locale.ROOT)) {
            "bing" -> "https://www.bing.com/"
            "duckduckgo" -> "https://duckduckgo.com/"
            "brave" -> "https://search.brave.com/"
            "ecosia" -> "https://www.ecosia.org/"
            else -> "https://www.google.com/"
        }
        Glide.with(this).load(BrowserTabStore.faviconFor(url)).fitCenter()
            .placeholder(R.drawable.ic_search_outline).error(R.drawable.ic_search_outline).into(engineIcon)
    }

    private fun showSearchEnginePicker(anchor: View) {
        val current = BrowserPreferences.searchEngine(this)
        NativePopupMenu.show(anchor, listOf(
            NativePopupMenu.Item(1, "Google", checked = current.equals("Google", true)),
            NativePopupMenu.Item(2, "Bing", checked = current.equals("Bing", true)),
            NativePopupMenu.Item(3, "DuckDuckGo", checked = current.equals("DuckDuckGo", true)),
            NativePopupMenu.Item(4, "Brave Search", checked = current.equals("Brave", true))
        )) { item ->
            val engine = when (item.id) { 2 -> "Bing"; 3 -> "DuckDuckGo"; 4 -> "Brave"; else -> "Google" }
            BrowserPreferences.setSearchEngine(this, engine)
            updateSearchEngineIcon()
            if (::engineLabel.isInitialized) engineLabel.text = "Motor de pesquisa: $engine"
            renderSuggestions(input.text?.toString().orEmpty().trim(), emptyList())
        }
    }

    private fun configureInput() {
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                submit(input.text?.toString().orEmpty())
                true
            } else false
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pendingSearch?.let(handler::removeCallbacks)
                val query = s?.toString().orEmpty().trim()
                clearIcon.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                renderSuggestions(query, emptyList())
                if (query.isBlank()) return
                val callGeneration = ++generation
                pendingSearch = Runnable {
                    lifecycleScope.launch {
                        val remote = withContext(Dispatchers.IO) { BrowserAutocomplete.googleSuggestions(query, 7) }
                        if (isFinishing || isDestroyed || callGeneration != generation) return@launch
                        if (input.text?.toString()?.trim() == query) renderSuggestions(query, remote)
                    }
                }
                pendingSearch?.let { handler.postDelayed(it, 130L) }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun renderSuggestions(query: String, remote: List<String>) {
        if (!::rows.isInitialized) return
        rows.removeAllViews()
        engineLabel.text = "Motor de pesquisa: ${BrowserPreferences.searchEngine(this)}"

        fun addRow(title: String, subtitle: String, iconName: String, value: String) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = ContextCompat.getDrawable(this@BrowserSearchActivity, R.drawable.bg_transparent_pressed)
                isClickable = true
                isFocusable = true
            }
            row.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                IconLoader.applyIcon(this, iconName, R.color.iconTint)
            })
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), 0, 0, 0)
            }
            labels.addView(TextView(this).apply {
                text = title
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(this@BrowserSearchActivity, R.color.text))
            })
            if (subtitle.isNotBlank()) labels.addView(TextView(this).apply {
                text = subtitle
                textSize = 11.5f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(this@BrowserSearchActivity, R.color.dim))
            })
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.setOnClickListener { submit(value) }
            rows.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        if (query.isNotBlank()) addRow("Pesquisar ‘$query’", BrowserPreferences.searchEngine(this), "search", query)
        remote.distinctBy { it.lowercase(java.util.Locale.ROOT) }.take(7).forEach {
            addRow(it, "Sugestão de pesquisa", "search", it)
        }
        val history = if (privateMode) emptyList() else BrowsingHistoryStore.all(this)
            .filter { query.isBlank() || it.title.contains(query, true) || it.url.contains(query, true) }
            .take(if (query.isBlank()) 10 else 4)
        history.forEach { addRow(it.title.ifBlank { it.url }, it.url, "history", it.url) }
        if (query.isBlank() && history.isEmpty()) addRow("Nova pesquisa", "Escreve uma palavra ou endereço", "search", "")
    }

    private fun submit(value: String) {
        val target = value.trim()
        if (target.isBlank()) return
        pendingSearch?.let(handler::removeCallbacks)
        val data = Intent().putExtra(EXTRA_RESULT_VALUE, target)
        setResult(RESULT_OK, data)
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        finishWithFade()
    }

    private fun finishWithFade() {
        finish()
        overridePendingTransition(R.anim.browser_fade_in, R.anim.browser_fade_out)
    }

    override fun onDestroy() {
        pendingSearch?.let(handler::removeCallbacks)
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}