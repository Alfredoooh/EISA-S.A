package com.appao

import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.transition.ChangeBounds
import android.transition.ChangeClipBounds
import android.transition.ChangeImageTransform
import android.transition.ChangeTransform
import android.transition.TransitionSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

/** Native article page. Only the hero/text card participates in the transform. */
class ArticleActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var heroContainer: FrameLayout
    private lateinit var hero: ImageView
    private lateinit var textHero: FrameLayout
    private lateinit var textHeroFavicon: ImageView
    private lateinit var textHeroSource: TextView
    private lateinit var textHeroTitle: TextView
    private lateinit var title: TextView
    private lateinit var summary: TextView
    private lateinit var body: TextView
    private lateinit var source: TextView
    private lateinit var time: TextView
    private lateinit var favicon: ImageView
    private lateinit var reactSlot: ImageView
    private lateinit var scroll: ScrollView
    private lateinit var articleBar: LinearLayout
    private lateinit var acInput: EditText
    private lateinit var acSend: FrameLayout
    private lateinit var acComposer: LinearLayout
    private lateinit var acEmoji: FrameLayout
    private lateinit var commentsAdapter: CommentAdapter

    private val comments = mutableListOf<Comment>()

    private var itemImage = ""
    private var itemLink = ""
    private var itemTitle = ""
    private var transitionName = ""
    private var articleBarOffset = 0f
    private var articleBarHeight = 0
    private var currentReact = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.sharedElementEnterTransition = containerTransition()
        window.sharedElementReturnTransition = containerTransition()
        window.exitTransition = null
        window.reenterTransition = null

        setContentView(R.layout.activity_article)
        SystemBarHelper.sync(this)

        itemImage = intent.getStringExtra("image").orEmpty()
        itemLink = intent.getStringExtra("link").orEmpty()
        itemTitle = intent.getStringExtra("title").orEmpty()

        val itemSummary = intent.getStringExtra("summary").orEmpty()
        val itemSource = intent.getStringExtra("source").orEmpty()
        val itemDate = intent.getStringExtra("date").orEmpty()
        val itemLogo = intent.getStringExtra("logo").orEmpty()
        transitionName =
            intent.getStringExtra("transition_name").orEmpty()

        root = findViewById(R.id.rootArticle)
        heroContainer = findViewById(R.id.aHeroContainer)
        hero = findViewById(R.id.aHero)
        textHero = findViewById(R.id.aTextHero)
        textHeroFavicon = findViewById(R.id.aTextHeroFavicon)
        textHeroSource = findViewById(R.id.aTextHeroSource)
        textHeroTitle = findViewById(R.id.aTextHeroTitle)
        title = findViewById(R.id.aTitle)
        summary = findViewById(R.id.aSummary)
        body = findViewById(R.id.aBody)
        source = findViewById(R.id.aSource)
        time = findViewById(R.id.aTime)
        favicon = findViewById(R.id.aFavicon)
        reactSlot = findViewById(R.id.aReactSlot)
        scroll = findViewById(R.id.aScroll)
        articleBar = findViewById(R.id.articleBar)
        acInput = findViewById(R.id.acInput)
        acSend = findViewById(R.id.acSend)
        acComposer = findViewById(R.id.acComposer)
        acEmoji = findViewById(R.id.acEmoji)

        configureHero(
            itemSource = itemSource,
            itemLogo = itemLogo
        )

        val topbar = findViewById<View>(R.id.aTopBar)
        ViewCompat.setOnApplyWindowInsetsListener(topbar) { view, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars()
            ).top
            view.updatePadding(top = top + dp(6))
            view.layoutParams = view.layoutParams.apply {
                height = dp(56) + top
            }
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            val ime = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            ).bottom
            val bottom = max(ime, bars.bottom)

            val barParams =
                articleBar.layoutParams as FrameLayout.LayoutParams
            barParams.bottomMargin = bottom
            articleBar.layoutParams = barParams

            val scrollParams =
                scroll.layoutParams as FrameLayout.LayoutParams
            scrollParams.topMargin = bars.top + dp(56)
            scrollParams.bottomMargin = articleBar.measuredHeight + dp(8)
            scroll.layoutParams = scrollParams

            articleBarHeight = articleBar.measuredHeight
            applyArticleBarOffset()
            insets
        }

        IconLoader.applySvg(
            findViewById(R.id.aBackIcon),
            "back",
            R.color.iconTint
        )
        IconLoader.applySvg(
            findViewById(R.id.acSendIcon),
            "send",
            R.color.onpri
        )
        IconLoader.applyPng(
            findViewById(R.id.aCopyIcon),
            "link"
        )
        IconLoader.applyPng(
            findViewById(R.id.aShareIcon),
            "share"
        )
        IconLoader.applyPng(
            findViewById(R.id.aSaveIcon),
            "bookmark"
        )
        IconLoader.applySvg(
            findViewById(R.id.cIcon),
            "chat",
            R.color.iconTint
        )
        IconLoader.applySvg(
            findViewById(R.id.acEmojiIcon),
            "emoji",
            R.color.iconTint
        )

        title.text = itemTitle
        source.text = itemSource
        time.text = if (itemDate.isBlank()) "" else "· ${prettyTime(itemDate)}"

        if (itemSummary.isNotBlank()) {
            summary.visibility = View.VISIBLE
            summary.text = itemSummary
        } else {
            summary.visibility = View.GONE
        }

        if (itemLogo.isNotBlank()) {
            Glide.with(this)
                .load(itemLogo)
                .override(dp(28), dp(28))
                .dontAnimate()
                .into(favicon)
        }

        if (itemImage.isNotBlank()) {
            Glide.with(this)
                .load(itemImage)
                .dontAnimate()
                .into(hero)
        }

        heroContainer.setOnClickListener {
            if (itemImage.isNotBlank()) {
                openImageViewer()
            }
        }

        findViewById<View>(R.id.aBack).setOnClickListener {
            finishAfterTransition()
        }

        commentsAdapter = CommentAdapter(comments) { index, reaction ->
            if (index !in comments.indices) return@CommentAdapter
            val comment = comments[index]
            comment.reactions[reaction] =
                (comment.reactions[reaction] ?: 0) + 1
            commentsAdapter.notifyItemChanged(index)
        }

        findViewById<RecyclerView>(R.id.cList).apply {
            layoutManager = LinearLayoutManager(this@ArticleActivity)
            adapter = commentsAdapter
            itemAnimator = null
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        acInput.setOnFocusChangeListener { _, focused ->
            animateComposer(focused)
            articleBarOffset = 0f
            articleBar.translationY = 0f
        }

        acInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    val hasText = !s.isNullOrBlank()
                    acSend.visibility =
                        if (hasText) View.VISIBLE else View.GONE

                    val lp =
                        acSend.layoutParams as LinearLayout.LayoutParams
                    lp.width = if (hasText) dp(34) else 0
                    acSend.layoutParams = lp
                }

                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
            }
        )

        acSend.setOnClickListener {
            sendComment()
        }

        acInput.setOnEditorActionListener { _, _, _ ->
            sendComment()
            true
        }

        acEmoji.setOnClickListener {
            showReactSheet()
        }

        findViewById<View>(R.id.aCopy).setOnClickListener {
            val clipboard =
                getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(
                ClipData.newPlainText(
                    "link",
                    itemLink
                )
            )
            AppAoToast.show(this, "Link copiado")
        }

        findViewById<View>(R.id.aShare).setOnClickListener {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "$itemTitle $itemLink"
                        )
                    },
                    "Partilhar"
                )
            )
        }

        findViewById<View>(R.id.aSave).setOnClickListener {
            AppAoToast.show(this, "Guardado")
        }

        articleBar.post {
            articleBarHeight = articleBar.measuredHeight
            articleBarOffset = 0f
            articleBar.translationY = 0f
        }

        lifecycleScope.launch {
            val full = try {
                NewsRepository.fetchArticleFull(itemLink)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) {
                    throw t
                }
                null
            }

            if (isFinishing || isDestroyed) return@launch

            body.text =
                if (!full.isNullOrBlank()) {
                    full
                } else {
                    itemSummary.ifBlank {
                        "Não foi possível carregar o artigo completo."
                    }
                }
        }
    }

    private fun configureHero(
        itemSource: String,
        itemLogo: String
    ) {
        if (itemImage.isNotBlank()) {
            heroContainer.visibility = View.VISIBLE
            textHero.visibility = View.GONE

            if (transitionName.isNotBlank()) {
                ViewCompat.setTransitionName(
                    heroContainer,
                    transitionName
                )
            }
        } else {
            heroContainer.visibility = View.GONE
            textHero.visibility = View.VISIBLE

            textHeroSource.text = itemSource
            textHeroTitle.text = itemTitle

            if (itemLogo.isNotBlank()) {
                Glide.with(this)
                    .load(itemLogo)
                    .override(dp(34), dp(34))
                    .dontAnimate()
                    .into(textHeroFavicon)
            }

            if (transitionName.isNotBlank()) {
                ViewCompat.setTransitionName(
                    textHero,
                    transitionName
                )
            }
        }
    }

    private fun containerTransition(): TransitionSet =
        TransitionSet().apply {
            addTransition(ChangeBounds())
            addTransition(ChangeTransform())
            addTransition(ChangeClipBounds())
            addTransition(ChangeImageTransform())
            duration = 450L
            interpolator = Curves.SMOOTH
        }

    private fun openImageViewer() {
        if (itemImage.isBlank()) return

        val name =
            ViewCompat.getTransitionName(heroContainer)
                ?: "article_image_${itemLink.hashCode().toUInt().toString(36)}"

        val options =
            ActivityOptions.makeSceneTransitionAnimation(
                this,
                heroContainer,
                name
            )

        startActivity(
            Intent(
                this,
                ImageViewerActivity::class.java
            ).apply {
                putExtra("image", itemImage)
                putExtra("transition", name)
            },
            options.toBundle()
        )
    }

    private fun updateArticleBarFromScroll(dy: Int) {
        articleBarOffset = 0f
        articleBar.translationY = 0f
    }

    private fun applyArticleBarOffset() {
        articleBarOffset = 0f
        articleBar.translationY = 0f
    }

    private fun animateArticleBarTo(target: Float) {
        articleBarOffset = 0f
        articleBar.animate()
            .translationY(0f)
            .setDuration(180L)
            .setInterpolator(Curves.IOS)
            .start()
    }

    private fun animateComposer(
        focused: Boolean
    ) {
        acComposer.pivotX = 0f
        acComposer.animate()
            .scaleX(
                if (focused) 1.02f else 1f
            )
            .setDuration(250L)
            .setInterpolator(Curves.SPRING)
            .start()
    }

    private fun showReactSheet() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(2))
        }

        val reactions = listOf(
            "like" to "Gosto",
            "love" to "Adoro",
            "haha" to "Riso",
            "wow" to "Uau",
            "sad" to "Triste",
            "angry" to "Raiva"
        )

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(
            androidx.appcompat.view.ContextThemeWrapper(
                this,
                R.style.Theme_AppAo_Material3Dialog
            )
        )
            .setTitle("Reagir com")
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .create()

        reactions.forEach { (kind, label) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = ContextCompat.getDrawable(
                    this@ArticleActivity,
                    R.drawable.bg_transparent_pressed
                )
                isClickable = true
                isFocusable = true
            }

            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            IconLoader.applyPng(icon, kind)
            row.addView(icon)

            row.addView(
                TextView(this).apply {
                    text = label
                    textSize = 15f
                    setTextColor(ContextCompat.getColor(this@ArticleActivity, R.color.text))
                    setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(14)
                }
            )

            row.setOnClickListener {
                react(kind)
                dialog.dismiss()
            }

            content.addView(
                row,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
            )
        }

        dialog.setOnShowListener {
            dialog.window?.setDimAmount(
                if ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) 0.22f else 0.12f
            )
        }

        dialog.show()
    }

    private fun sendComment() {
        val value = acInput.text
            .toString()
            .trim()

        if (value.isEmpty()) return

        comments += Comment(
            user = "Convidado",
            text = value,
            avatar = "",
            date = "agora"
        )

        commentsAdapter.notifyItemInserted(
            comments.lastIndex
        )

        findViewById<TextView>(R.id.cCount).text =
            "(${comments.size})"

        acInput.setText("")
        acInput.clearFocus()

        scroll.postDelayed(
            {
                scroll.fullScroll(
                    View.FOCUS_DOWN
                )
            },
            100L
        )
    }

    private fun react(kind: String) {
        currentReact = kind
        reactSlot.visibility = View.VISIBLE
        IconLoader.applyPng(
            reactSlot,
            kind
        )
        AppAoToast.show(
            this,
            "Reagiste: $kind"
        )
    }

    private fun prettyTime(
        value: String
    ): String {
        if (value.isBlank()) return ""

        return try {
            val raw = value
                .replace("Z", "")
                .replace(Regex("[+-]\\d{2}:?\\d{2}$"), "")

            val format = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss",
                Locale.US
            ).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val date = format.parse(
                raw.substring(
                    0,
                    raw.length.coerceAtMost(19)
                )
            ) ?: return ""

            val minutes = (
                (System.currentTimeMillis() - date.time) /
                    60000
            ).coerceAtLeast(0)

            when {
                minutes < 1 -> "agora"
                minutes < 60 -> "$minutes min"
                minutes < 1440 -> "${minutes / 60} h"
                minutes < 10080 -> "${minutes / 1440} d"
                else -> SimpleDateFormat(
                    "dd MMM",
                    Locale("pt", "PT")
                ).format(date)
            }
        } catch (_: Exception) {
            ""
        }
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
    }

    override fun onConfigurationChanged(
        newConfig: android.content.res.Configuration
    ) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
    }

    override fun dispatchTouchEvent(
        event: MotionEvent
    ): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is EditText) {
                val rect = android.graphics.Rect()
                focused.getGlobalVisibleRect(rect)
                if (!rect.contains(
                        event.rawX.toInt(),
                        event.rawY.toInt()
                    )
                ) {
                    focused.clearFocus()
                    val input = getSystemService(
                        INPUT_METHOD_SERVICE
                    ) as InputMethodManager
                    input.hideSoftInputFromWindow(
                        focused.windowToken,
                        0
                    )
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + .5f)
            .toInt()
}
