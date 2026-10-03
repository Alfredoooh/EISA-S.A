package com.appao

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.transition.ChangeBounds
import android.transition.ChangeClipBounds
import android.transition.ChangeTransform
import android.transition.TransitionSet
import android.view.MotionEvent
import android.view.View
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
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ArticleActivity : AppCompatActivity() {

    private lateinit var hero: ImageView
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
    private lateinit var root: View

    private val comments = mutableListOf<Comment>()
    private var itemImage = ""
    private var itemLink = ""
    private var itemTitle = ""
    private var currentReact = ""
    private var containerTransitionName = ""
    private var articleBarOffset = 0f
    private var lastScrollY = 0
    private var articleBarHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.sharedElementEnterTransition = containerTransition()
        window.sharedElementReturnTransition = containerTransition()

        setContentView(R.layout.activity_article)
        SystemBarHelper.sync(this)

        itemImage = intent.getStringExtra("image") ?: ""
        itemLink = intent.getStringExtra("link") ?: ""
        itemTitle = intent.getStringExtra("title") ?: ""
        val itemSummary = intent.getStringExtra("summary") ?: ""
        val itemSource = intent.getStringExtra("source") ?: ""
        val itemDate = intent.getStringExtra("date") ?: ""
        val itemLogo = intent.getStringExtra("logo") ?: ""
        containerTransitionName =
            intent.getStringExtra("transition_name") ?: ""

        root = findViewById(R.id.rootArticle)
        hero = findViewById(R.id.aHero)
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

        if (containerTransitionName.isNotBlank()) {
            ViewCompat.setTransitionName(
                root,
                containerTransitionName
            )
        }

        val topbar = findViewById<View>(R.id.aTopBar)

        ViewCompat.setOnApplyWindowInsetsListener(topbar) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            view.updatePadding(
                top = bars.top + dp(6)
            )
            view.layoutParams = view.layoutParams.apply {
                height = dp(56) + bars.top
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
            val bottom = maxOf(ime, bars.bottom)

            val articleParams =
                articleBar.layoutParams as FrameLayout.LayoutParams
            articleParams.bottomMargin = bottom
            articleBar.layoutParams = articleParams

            articleBarHeight = articleBar.height

            val scrollParams =
                scroll.layoutParams as FrameLayout.LayoutParams
            scrollParams.topMargin = bars.top + dp(56)
            scrollParams.bottomMargin = articleBar.height + dp(8)
            scroll.layoutParams = scrollParams

            applyArticleBarOffset()
            insets
        }

        scroll.setOnScrollChangeListener { _, _, y, _, oldY ->
            val dy = y - oldY
            if (dy != 0) {
                updateArticleBarFromScroll(dy)
            }
        }

        IconLoader.applySvg(
            findViewById(R.id.aBackIcon),
            "close",
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
        time.text = if (itemDate.isBlank()) {
            ""
        } else {
            "· ${prettyTime(itemDate)}"
        }

        if (itemSummary.isNotBlank()) {
            summary.visibility = View.VISIBLE
            summary.text = itemSummary
        } else {
            summary.visibility = View.GONE
        }

        if (itemImage.isNotBlank()) {
            Glide.with(this)
                .load(itemImage)
                .dontAnimate()
                .into(hero)
        }

        if (itemLogo.isNotBlank()) {
            Glide.with(this)
                .load(itemLogo)
                .override(dp(22), dp(22))
                .dontAnimate()
                .into(favicon)
        }

        if (itemImage.isNotBlank()) {
            hero.transitionName =
                "article_image_${itemLink.hashCode().toUInt().toString(36)}"

            hero.setOnClickListener {
                openImageViewer()
            }
        }

        articleBar.post {
            articleBarHeight = articleBar.height
            articleBarOffset = 0f
            articleBar.translationY = 0f
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
            layoutManager = LinearLayoutManager(
                this@ArticleActivity
            )
            adapter = commentsAdapter
            itemAnimator = null
            isNestedScrollingEnabled = false
        }

        acComposer.pivotX = 0f

        acInput.setOnFocusChangeListener { _, focused ->
            animateComposer(focused)
            if (focused) {
                animateArticleBarTo(0f)
            }
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

                    val lp = acSend.layoutParams as LinearLayout.LayoutParams
                    lp.width =
                        if (hasText) dp(32) else 0
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
            AppAoToast.show(
                this,
                "Link copiado"
            )
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
            AppAoToast.show(
                this,
                "Guardado"
            )
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

    private fun containerTransition(): TransitionSet =
        TransitionSet().apply {
            addTransition(ChangeBounds())
            addTransition(ChangeTransform())
            addTransition(ChangeClipBounds())
            duration = 450L
            interpolator = Curves.SMOOTH
        }

    private fun openImageViewer() {
        if (itemImage.isBlank()) return

        val name = hero.transitionName

        val options =
            android.app.ActivityOptions.makeSceneTransitionAnimation(
                this,
                hero,
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
        val travel = (
            if (articleBarHeight > 0) {
                articleBarHeight + dp(24)
            } else {
                dp(80)
            }
        ).toFloat()

        articleBarOffset = (
            articleBarOffset + dy.toFloat()
        ).coerceIn(0f, travel)

        if (scroll.scrollY <= 0) {
            articleBarOffset = 0f
        }

        applyArticleBarOffset()
    }

    private fun applyArticleBarOffset() {
        articleBar.translationY = articleBarOffset
    }

    private fun animateArticleBarTo(target: Float) {
        articleBar.animate()
            .translationY(target.coerceAtLeast(0f))
            .setDuration(240L)
            .setInterpolator(Curves.IOS)
            .withEndAction {
                articleBarOffset = articleBar.translationY
            }
            .start()
    }

    private fun animateComposer(focused: Boolean) {
        val target = if (focused) 1.03f else 1f
        acComposer.pivotX = 0f
        acComposer.animate()
            .scaleX(target)
            .setDuration(250L)
            .setInterpolator(Curves.SPRING)
            .start()
    }

    private fun showReactSheet() {
        val view = layoutInflater.inflate(
            R.layout.sheet_reactions,
            null,
            false
        )

        val sheet = NativeSheetDialog.show(
            this,
            view
        )

        val row1 = view.findViewById<LinearLayout>(
            R.id.reactRow1
        )
        val row2 = view.findViewById<LinearLayout>(
            R.id.reactRow2
        )

        val reactions = listOf(
            "like" to "Gosto",
            "love" to "Adoro",
            "haha" to "Riso",
            "wow" to "Uau",
            "sad" to "Triste",
            "angry" to "Raiva"
        )

        reactions.forEachIndexed { index, pair ->
            val parent = if (index < 3) row1 else row2
            val item = layoutInflater.inflate(
                R.layout.item_reaction_big,
                parent,
                false
            )

            IconLoader.applyPng(
                item.findViewById(R.id.reactIcon),
                pair.first
            )
            item.findViewById<TextView>(
                R.id.reactLabel
            ).text = pair.second

            item.setOnClickListener {
                react(pair.first)
                sheet.dismiss()
            }

            parent.addView(item)
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
                scroll.fullScroll(View.FOCUS_DOWN)
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

    private fun prettyTime(value: String): String {
        if (value.isBlank()) return ""

        return try {
            val raw = value.replace("Z", "")
            val format = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss",
                Locale.getDefault()
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

    override fun onConfigurationChanged(
        newConfig: android.content.res.Configuration
    ) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
        window.decorView.setBackgroundColor(
            androidx.core.content.ContextCompat.getColor(
                this,
                R.color.bg
            )
        )
    }

    override fun onResume() {
        super.onResume()
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
                if (
                    !rect.contains(
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
        (value * resources.displayMetrics.density + 0.5f)
            .toInt()
}
