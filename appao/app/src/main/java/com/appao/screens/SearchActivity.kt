package com.appao

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.view.animation.PathInterpolator

/** Internal native search surface. It uses the exact same composer geometry as the main page. */
class SearchActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var resultQuery: TextView
    private lateinit var composerBar: View
    private lateinit var composerPill: View
    private lateinit var input: EditText
    private lateinit var send: FrameLayout
    private var heightAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_search)
        SystemBarHelper.sync(this)

        root = findViewById(R.id.searchRoot)
        resultQuery = findViewById(R.id.searchQuery)
        composerBar = findViewById(R.id.searchComposerBar)
        composerPill = findViewById(R.id.searchComposerPill)
        input = findViewById(R.id.searchInput)
        send = findViewById(R.id.searchSend)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            composerBar.updatePadding(bottom = maxOf(ime, bars.bottom) + dp(10))
            insets
        }

        IconLoader.applySvg(findViewById(R.id.searchCloseIcon), "action_close", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.searchMoreIcon), "action_overflow", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.searchAddIcon), "action_add", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.searchSliderIcon), "action_filters", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.searchSendIcon), "action_send_arrow", android.R.color.black)

        findViewById<View>(R.id.searchClose).setOnClickListener { finish() }
        findViewById<View>(R.id.searchMore).setOnClickListener { showMorePopup(it) }
        findViewById<View>(R.id.searchAdd).setOnClickListener {
            AppsPopup.show(it) { action ->
                when (action) {
                    "file" -> startActivity(Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    })
                    "image" -> startActivity(Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    })
                    "camera" -> startActivityForResult(android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), 901)
                    "think" -> AppAoToast.show(this, "Pensar mais")
                }
            }
        }
        findViewById<View>(R.id.searchSend).setOnClickListener { performSearch() }
        input.setOnEditorActionListener { _, _, _ -> performSearch(); true }

        input.setSingleLine(false)
        input.setHorizontallyScrolling(false)
        input.setLineSpacing(0f, 1.6f)
        input.gravity = Gravity.TOP or Gravity.START
        input.includeFontPadding = true
        input.setPadding(0, 0, 0, 0)
        input.maxLines = Int.MAX_VALUE

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                input.post { resizeInput(true) }
                updateSendState()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        val initial = intent.getStringExtra("query").orEmpty()
        input.setText(initial)
        input.setSelection(input.text.length)
        resultQuery.text = initial

        applyTheme()
        input.post {
            resizeInput(false)
            updateSendState()
        }

        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
    }

    private fun showMorePopup(anchor: View) {
        NativePopupMenu.show(
            anchor,
            listOf(
                NativePopupMenu.Item(1, "Eliminar pesquisa", svg = "action_delete"),
                NativePopupMenu.Item(2, "Arquivar pesquisa", svg = "action_archive"),
                NativePopupMenu.Item(3, "Iniciar nova pesquisa", svg = "action_add"),
                NativePopupMenu.Item(4, "Renomear pesquisa", svg = "action_edit")
            )
        ) { item ->
            when (item.id) {
                1 -> {
                    input.setText("")
                    resultQuery.text = ""
                }
                2 -> AppAoToast.show(this, "Pesquisa arquivada")
                3 -> {
                    input.setText("")
                    resultQuery.text = ""
                    input.requestFocus()
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                }
                4 -> NativeM3Dialog.prompt(
                    this,
                    "Renomear pesquisa",
                    "",
                    initial = resultQuery.text.toString()
                ) { renamed ->
                    if (!renamed.isNullOrBlank()) resultQuery.text = renamed
                }
            }
        }
    }

    private fun performSearch() {
        val query = input.text.toString().trim()
        if (query.isBlank()) return
        resultQuery.text = query
        input.clearFocus()
        AppAoToast.show(this, "Pesquisa atualizada")
    }

    private fun resizeInput(animate: Boolean) {
        if (input.width <= 0) return
        val minHeight = dp(26)
        val maxHeight = dp(200)
        val measured = if (input.text.isEmpty()) {
            minHeight
        } else {
            StaticLayout.Builder
                .obtain(input.text, 0, input.text.length, input.paint, input.width.coerceAtLeast(1))
                .setIncludePad(true)
                .setLineSpacing(0f, 1.6f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build().height.coerceAtLeast(minHeight)
        }
        val target = measured.coerceAtMost(maxHeight)
        val current = input.height.takeIf { it > 0 } ?: minHeight
        heightAnimator?.cancel()
        if (animate && current != target) {
            heightAnimator = ValueAnimator.ofInt(current, target).apply {
                duration = 300L
                interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
                addUpdateListener { a ->
                    input.layoutParams = input.layoutParams.apply { height = a.animatedValue as Int }
                }
                start()
            }
        } else {
            input.layoutParams = input.layoutParams.apply { height = target }
        }
        if (target < maxHeight) input.scrollTo(0, 0)
    }

    private fun updateSendState() {
        val active = input.text?.isNotBlank() == true
        val fill = ContextCompat.getColor(
            this,
            if (active) R.color.pri else R.color.card2
        )
        send.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(fill)
        }
        findViewById<ImageView>(R.id.searchSendIcon).setColorFilter(
            if (active) android.graphics.Color.BLACK else ContextCompat.getColor(this, R.color.dim),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }

    private fun applyTheme() {
        val bg = ContextCompat.getColor(this, R.color.bg)
        root.setBackgroundColor(bg)
        composerBar.setBackgroundColor(bg)
        composerPill.background = HtmlComposerBackgroundDrawable(this, ThemeManager.resolvedDark(this))
        resultQuery.setTextColor(ContextCompat.getColor(this, R.color.dim))
        updateSendState()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
        applyTheme()
    }

    override fun onBackPressed() {
        finish()
        overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit)
    }

    override fun onDestroy() {
        heightAnimator?.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
