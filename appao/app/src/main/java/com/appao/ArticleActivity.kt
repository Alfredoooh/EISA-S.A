package com.appao

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.net.Uri
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
    private lateinit var heroContainer: RoundedClipFrameLayout
    private lateinit var hero: ImageView
    private lateinit var textHero: RoundedClipFrameLayout
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
    private var closingArticle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.sharedElementEnterTransition = containerTransition()
        window.sharedElementReturnTransition = containerTransition()
        window.exitTransition = null
        window.reenterTransition = null

        setContentView(R.layout.activity_article)
        SystemBarHelper.sync(this)

        itemImage = intent.getStringExtra("image").orEmpty()
        itemLink = intent.getStringExtra("link").orEmpty()
        itemTitle = intent.getStringExtra("title").orEmpty()
        UsageTracker.recordNewsSource(this, intent.getStringExtra("source").orEmpty())

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

        if (itemImage.isNotBlank()) {
            heroContainer.setCornerRadiusDp(18f)
            heroContainer.post {
                if (!isFinishing && !isDestroyed) {
                    heroContainer.animateCornerRadiusDp(18f, 0f, 480L)
                }
            }
        } else {
            textHero.setCornerRadiusDp(18f)
        }

        val topbar = findViewById<View>(R.id.aTopBar)
        ViewCompat.setOnApplyWindowInsetsListener(topbar) { view, insets ->
            view.updatePadding(top = dp(6))
            view.layoutParams = view.layoutParams.apply {
                height = dp(56)
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
            scrollParams.topMargin = dp(56)
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
        IconLoader.applySvg(findViewById(R.id.aBrowserIcon), "launch", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.aShareIcon), "share", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.aSaveIcon), "bookmark", R.color.iconTint)
        IconLoader.applySvg(
            findViewById(R.id.cIcon),
            "chat",
            R.color.iconTint
        )
        IconLoader.applySvg(
            findViewById(R.id.acEmojiIcon),
            "emojis",
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
            finishArticle()
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

        findViewById<View>(R.id.aBrowser).setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(itemLink)))
            }.onFailure {
                AppAoToast.show(this, "Não foi possível abrir o navegador")
            }
        }

        findViewById<View>(R.id.aShare).setOnClickListener {
            showShareSheet()
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
                NewsRepository.fetchArticleFull(itemLink, itemTitle)
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

    private fun finishArticle() {
        if (closingArticle) return
        closingArticle = true

        val target = if (itemImage.isNotBlank()) heroContainer else textHero
        target.animateCornerRadiusDp(0f, 18f, 320L)
        target.postDelayed({
            if (!isFinishing && !isDestroyed) {
                finishAfterTransition()
            }
        }, 90L)
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

    private fun showShareSheet() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(20))
        }

        root.addView(TextView(this).apply {
            text = "Partilhar notícia"
            gravity = android.view.Gravity.CENTER
            textSize = 17f
            setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@ArticleActivity, R.color.text))
            setPadding(dp(18), dp(12), dp(18), dp(16))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val apps = listOf(
            Triple("WhatsApp", "whatsapp", "com.whatsapp"),
            Triple("Instagram", "instagram", "com.instagram.android"),
            Triple("X", "x", "com.twitter.android"),
            Triple("Facebook", "facebook", "com.facebook.katana"),
            Triple("Messenger", "messenger", "com.facebook.orca"),
            Triple("Telegram", "telegram", "org.telegram.messenger"),
            Triple("LinkedIn", "linkedin", "com.linkedin.android"),
            Triple("Mais", "share", null)
        )

        val grid = android.widget.GridLayout(this).apply {
            columnCount = 4
            useDefaultMargins = false
        }

        lateinit var shareDialog: com.google.android.material.bottomsheet.BottomSheetDialog

        apps.forEachIndexed { index, (label, iconName, packageName) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                background = ContextCompat.getDrawable(this@ArticleActivity, R.drawable.bg_sheet_item)
                isClickable = true
                isFocusable = true
                setPadding(dp(4), dp(10), dp(4), dp(10))
            }

            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            IconLoader.applyPng(icon, iconName)
            item.addView(icon)

            item.addView(TextView(this).apply {
                text = label
                textSize = 11.5f
                gravity = android.view.Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@ArticleActivity, R.color.dim))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            })

            item.setOnClickListener {
                shareDialog.dismiss()
                if (packageName == null) {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "$itemTitle\n$itemLink")
                    }, "Partilhar"))
                } else {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "$itemTitle\n$itemLink")
                        `package` = packageName
                    }
                    if (share.resolveActivity(packageManager) != null) {
                        startActivity(share)
                    } else {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "$itemTitle\n$itemLink")
                        }, "Partilhar"))
                    }
                }
                (root.parent as? View)?.let { }
            }

            grid.addView(item, android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = dp(92)
                columnSpec = android.widget.GridLayout.spec(index % 4, 1f)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }

        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        shareDialog = NativeSheetDialog.show(this, root)
    }

    private fun showReactSheet() {

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(8),
                    dp(4),
                    dp(8),
                    dp(20)
                )
            }

        val handle =
            View(this).apply {

                background =
                    android.graphics.drawable.GradientDrawable().apply {
                        shape =
                            android.graphics.drawable.GradientDrawable.RECTANGLE

                        setColor(
                            ContextCompat.getColor(
                                this@ArticleActivity,
                                R.color.line
                            )
                        )

                        cornerRadius =
                            dp(2).toFloat()
                    }
            }

        root.addView(
            handle,
            LinearLayout.LayoutParams(
                dp(38),
                dp(4)
            ).apply {

                gravity =
                    android.view.Gravity.CENTER_HORIZONTAL

                topMargin =
                    dp(10)

                bottomMargin =
                    dp(12)
            }
        )

        root.addView(
            TextView(this).apply {

                text =
                    "Reagir com"

                gravity =
                    android.view.Gravity.CENTER

                textSize =
                    17f

                setTypeface(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )

                setTextColor(
                    ContextCompat.getColor(
                        this@ArticleActivity,
                        R.color.text
                    )
                )

                setPadding(
                    dp(18),
                    dp(4),
                    dp(18),
                    dp(14)
                )
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val reactions =
            listOf(
                "like" to "Gosto",
                "love" to "Adoro",
                "haha" to "Riso",
                "wow" to "Uau",
                "sad" to "Triste",
                "angry" to "Raiva"
            )

        val grid =
            android.widget.GridLayout(this).apply {

                columnCount =
                    3

                rowCount =
                    2

                useDefaultMargins =
                    false
            }

        reactions.forEach { (kind, label) ->

            val item =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.VERTICAL

                    gravity =
                        android.view.Gravity.CENTER

                    background =
                        ContextCompat.getDrawable(
                            this@ArticleActivity,
                            R.drawable.bg_sheet_item
                        )

                    isClickable =
                        true

                    isFocusable =
                        true

                    setPadding(
                        dp(4),
                        dp(10),
                        dp(4),
                        dp(10)
                    )
                }

            val icon =
                ImageView(this).apply {

                    layoutParams =
                        LinearLayout.LayoutParams(
                            dp(48),
                            dp(48)
                        )

                    scaleType =
                        ImageView.ScaleType.CENTER_INSIDE
                }

            IconLoader.applyPng(
                icon,
                kind
            )

            item.addView(
                icon
            )

            item.addView(
                TextView(this).apply {

                    text =
                        label

                    textSize =
                        12.5f

                    gravity =
                        android.view.Gravity.CENTER

                    setTextColor(
                        ContextCompat.getColor(
                            this@ArticleActivity,
                            R.color.dim
                        )
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(6)
                }
            )

            grid.addView(
                item,
                android.widget.GridLayout.LayoutParams().apply {

                    width =
                        0

                    height =
                        dp(94)

                    columnSpec =
                        android.widget.GridLayout.spec(
                            android.widget.GridLayout.UNDEFINED,
                            1f
                        )

                    rowSpec =
                        android.widget.GridLayout.spec(
                            android.widget.GridLayout.UNDEFINED,
                            1f
                        )

                    setMargins(
                        dp(2),
                        dp(2),
                        dp(2),
                        dp(2)
                    )
                }
            )
        }

        root.addView(
            grid,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val dialog =
            NativeSheetDialog.show(
                this,
                root
            )

        reactions.forEachIndexed { index, (kind, _) ->
            grid.getChildAt(
                index
            ).setOnClickListener {
                react(kind)
                dialog.dismiss()
            }
        }
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

    override fun onBackPressed() {
        finishArticle()
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
