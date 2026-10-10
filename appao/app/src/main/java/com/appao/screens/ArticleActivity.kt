package com.appao

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
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
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

/** Native article page. Only the hero/text card participates in the transform. */
class ArticleActivity : AppCompatActivity() {
    companion object { private const val REQUEST_COMMENTS = 414 }

    private lateinit var root: View
    private lateinit var heroContainer: RoundedClipFrameLayout
    private lateinit var hero: ImageView
    private lateinit var sheetCard: RoundedClipFrameLayout
    private lateinit var heroSpacer: View
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
        val storedComments = getSharedPreferences("appao", MODE_PRIVATE).getString(commentsPrefsKey(), "[]").orEmpty()
        comments.addAll(decodeComments(storedComments))
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
        sheetCard = findViewById(R.id.aSheetCard)
        heroSpacer = findViewById(R.id.aHeroSpacer)
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

        configureHero(itemSource, itemLogo)

        sheetCard.setTopCornerRadiusDp(22f)
        sheetCard.minimumHeight = (resources.displayMetrics.heightPixels - dp(if (itemImage.isNotBlank()) 280 else 120)).coerceAtLeast(0)
        if (itemImage.isNotBlank()) {
            heroContainer.setCornerRadiusDp(18f)
            // Previously this ran immediately via heroContainer.post, racing
            // the 450ms shared-element enter transition (ChangeBounds +
            // ChangeTransform + ChangeClipBounds) with its own separate
            // 480ms corner-radius animation. The two competed visually,
            // reading as a glitchy double-animation. Wait for the shared
            // element transition to actually finish before starting the
            // corner-radius flatten, so only one motion plays at a time.
            val enterTransition = window.sharedElementEnterTransition
            if (enterTransition != null) {
                enterTransition.addListener(object : android.transition.Transition.TransitionListener {
                    override fun onTransitionEnd(transition: android.transition.Transition) {
                        transition.removeListener(this)
                        if (!isFinishing && !isDestroyed) {
                            heroContainer.animateCornerRadiusDp(18f, 0f, 260L)
                        }
                    }
                    override fun onTransitionStart(transition: android.transition.Transition) {}
                    override fun onTransitionCancel(transition: android.transition.Transition) {
                        transition.removeListener(this)
                        if (!isFinishing && !isDestroyed) {
                            heroContainer.setCornerRadiusDp(0f)
                        }
                    }
                    override fun onTransitionPause(transition: android.transition.Transition) {}
                    override fun onTransitionResume(transition: android.transition.Transition) {}
                })
            } else {
                heroContainer.post {
                    if (!isFinishing && !isDestroyed) {
                        heroContainer.animateCornerRadiusDp(18f, 0f, 260L)
                    }
                }
            }
        }

        val topbar = findViewById<View>(R.id.aTopBar)
        val topbarContentHeight = dp(56)
        ViewCompat.setOnApplyWindowInsetsListener(topbar) { view, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarTop)
            view.layoutParams = view.layoutParams.apply {
                height = topbarContentHeight + statusBarTop
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
            scrollParams.topMargin = 0
            scrollParams.bottomMargin = articleBar.measuredHeight + dp(8)
            scroll.layoutParams = scrollParams

            articleBarHeight = articleBar.measuredHeight
            applyArticleBarOffset()
            insets
        }

        scroll.setOnScrollChangeListener { _, _, _, _, _ ->
            updateSheetChrome()
        }
        sheetCard.post { updateSheetChrome() }

        IconLoader.applySvg(
            findViewById(R.id.aBackIcon),
            "nav_back",
            R.color.iconTint
        )
        IconLoader.applySvg(
            findViewById(R.id.acSendIcon),
            "action_send",
            android.R.color.black
        )
        IconLoader.applySvg(findViewById(R.id.aMoreIcon), "action_overflow", R.color.iconTint)
        IconLoader.applySvg(
            findViewById(R.id.cIcon),
            "action_chat",
            R.color.iconTint
        )
        IconLoader.applySvg(
            findViewById(R.id.acEmojiIcon),
            "action_reactions",
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
            favicon.background = ContextCompat.getDrawable(this, R.drawable.bg_circle_card)
            favicon.clipToOutline = true
            Glide.with(this)
                .load(itemLogo)
                .override(dp(24), dp(24))
                .dontAnimate()
                .into(favicon)
        }

        if (itemImage.isNotBlank()) {
            Glide.with(this)
                .load(itemImage)
                .dontAnimate()
                .into(object : CustomTarget<Drawable>() {
                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                        hero.setImageDrawable(resource)
                        val sourceWidth = resource.intrinsicWidth
                        val sourceHeight = resource.intrinsicHeight
                        if (sourceWidth > 0 && sourceHeight > 0) {
                            val screenWidth = resources.displayMetrics.widthPixels
                            // Preserve the source aspect ratio and avoid an oversized portrait hero.
                            val maxHeroHeight = (resources.displayMetrics.heightPixels * 0.46f).toInt()
                            val ratioHeight = (screenWidth.toFloat() * sourceHeight / sourceWidth).toInt()
                            val heroHeight = ratioHeight.coerceAtLeast(dp(100)).coerceAtMost(maxHeroHeight.coerceAtLeast(dp(180)))
                            heroContainer.layoutParams = heroContainer.layoutParams.apply { height = heroHeight }
                            // The description card starts below the image and rises only when the user scrolls.
                            heroSpacer.layoutParams = heroSpacer.layoutParams.apply { height = heroHeight }
                            sheetCard.minimumHeight = (resources.displayMetrics.heightPixels - heroHeight).coerceAtLeast(dp(260))
                        }
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {
                        hero.setImageDrawable(null)
                    }
                })
        }

        heroContainer.setOnClickListener {
            if (itemImage.isNotBlank()) {
                openImageViewer()
            }
        }

        findViewById<View>(R.id.aBack).setOnClickListener {
            finishArticle()
        }

        findViewById<View>(R.id.aCommentsSection).visibility = View.GONE
        acSend.visibility = View.GONE
        acEmoji.visibility = View.GONE
        acInput.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isCursorVisible = false
            keyListener = null
            contentDescription = "Abrir comentários"
            setOnClickListener { openCommentsScreen() }
        }
        acComposer.setOnClickListener { openCommentsScreen() }
        findViewById<View>(R.id.aMore).setOnClickListener { showArticleActionsPopup(it) }

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

    private fun refreshArticleTheme() {
        val bg = ContextCompat.getColor(this, R.color.bg)
        val elevated = ContextCompat.getColor(this, R.color.bgElevated)
        root.setBackgroundColor(bg)
        sheetCard.setBackgroundColor(bg)
        articleBar.setBackgroundColor(elevated)
        acComposer.background = ContextCompat.getDrawable(this, R.drawable.bg_pill_card)
        title.setTextColor(ContextCompat.getColor(this, R.color.text))
        source.setTextColor(ContextCompat.getColor(this, R.color.text))
        time.setTextColor(ContextCompat.getColor(this, R.color.dim))
        summary.setTextColor(ContextCompat.getColor(this, R.color.dim))
        body.setTextColor(ContextCompat.getColor(this, R.color.text))
        IconLoader.applySvg(findViewById(R.id.aBackIcon), "nav_back", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.aMoreIcon), "action_overflow", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.acSendIcon), "action_send", android.R.color.black)
        updateSheetChrome()
    }

    private fun finishArticle() {
        if (closingArticle) return
        closingArticle = true

        sheetCard.animateCornerRadiusDp(0f, 22f, 440L)
        if (itemImage.isNotBlank()) {
            heroContainer.animateCornerRadiusDp(0f, 18f, 440L)
        }

        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                supportFinishAfterTransition()
            }
        }, 220L)
    }

    private fun updateSheetChrome() {
        if (!::sheetCard.isInitialized || !::scroll.isInitialized) return
        val appbarHeight = findViewById<View>(R.id.aTopBar).height.coerceAtLeast(dp(56))
        val location = IntArray(2)
        sheetCard.getLocationOnScreen(location)
        val cardTop = location[1].toFloat()
        val distance = cardTop - appbarHeight
        val flattenZone = dp(50).toFloat()
        sheetCard.setTopCornerRadiusDp(22f)

        val solid = distance <= flattenZone
        updateArticleAppbar(solid)
    }

    private fun updateArticleAppbar(solid: Boolean) {
        val topbar = findViewById<View>(R.id.aTopBar)
        val bg = if (solid) ContextCompat.getColor(this, R.color.bg) else Color.TRANSPARENT
        topbar.background = android.graphics.drawable.ColorDrawable(bg)
        listOf(R.id.aBack, R.id.aMore).forEach { id ->
            findViewById<View>(id).background = ContextCompat.getDrawable(this, R.drawable.bg_circle_card)
        }
        listOf(R.id.aBackIcon, R.id.aMoreIcon).forEach { id ->
            findViewById<ImageView>(id).setColorFilter(
                ContextCompat.getColor(this, R.color.iconTint),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
    }

    private fun configureHero(
        itemSource: String,
        itemLogo: String
    ) {
        if (itemImage.isNotBlank()) {
            heroContainer.visibility = View.VISIBLE
            heroSpacer.layoutParams = heroSpacer.layoutParams.apply { height = dp(260) }

            if (transitionName.isNotBlank()) {
                ViewCompat.setTransitionName(heroContainer, transitionName)
            }
        } else {
            heroContainer.visibility = View.GONE
            heroSpacer.layoutParams = heroSpacer.layoutParams.apply { height = dp(120) }
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

    private fun showArticleActionsPopup(anchor: View) {
        NativePopupMenu.show(
            anchor,
            listOf(
                NativePopupMenu.Item(1, "Abrir no navegador", svg = "action_external_link"),
                NativePopupMenu.Item(2, if (isArticleSaved()) "Remover dos guardados" else "Guardar", svg = "action_bookmark"),
                NativePopupMenu.Item(3, "Partilhar", svg = "action_share")
            )
        ) { item ->
            when (item.id) {
                1 -> runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(itemLink)))
                }.onFailure { AppAoToast.show(this, "Não foi possível abrir o navegador") }
                2 -> toggleSavedArticle()
                3 -> showShareSheet()
            }
        }
    }

    private fun isArticleSaved(): Boolean {
        if (itemLink.isBlank()) return false
        val raw = getSharedPreferences("appao", MODE_PRIVATE).getString("library_json", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).any { index ->
                val link = array.optJSONObject(index)?.optString("link").orEmpty()
                normalizeArticleUrl(link) == normalizeArticleUrl(itemLink)
            }
        }.getOrDefault(false)
    }

    private fun toggleSavedArticle() {
        val prefs = getSharedPreferences("appao", MODE_PRIVATE)
        val raw = prefs.getString("library_json", "[]") ?: "[]"
        val existing = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val normalizedCurrent = normalizeArticleUrl(itemLink)
        val wasSaved = isArticleSaved()
        val updated = JSONArray()

        for (index in 0 until existing.length()) {
            val item = existing.optJSONObject(index) ?: continue
            val link = item.optString("link")
            if (wasSaved && normalizeArticleUrl(link) == normalizedCurrent) continue
            updated.put(item)
        }

        if (!wasSaved) {
            updated.put(JSONObject().apply {
                put("id", itemLink.hashCode().toUInt().toString(36))
                put("title", itemTitle)
                put("summary", intent.getStringExtra("summary").orEmpty())
                put("link", itemLink)
                put("source", intent.getStringExtra("source").orEmpty())
                put("date", intent.getStringExtra("date").orEmpty())
                put("image", itemImage)
                put("logo", intent.getStringExtra("logo").orEmpty())
            })
        }

        prefs.edit().putString("library_json", updated.toString()).apply()
        AppAoToast.show(this, if (wasSaved) "Removido dos guardados" else "Notícia guardada")
    }

    private fun commentsPrefsKey(): String =
        "article_comments_${normalizeArticleUrl(itemLink).hashCode().toUInt().toString(36)}"

    private fun persistComments() {
        getSharedPreferences("appao", MODE_PRIVATE).edit()
            .putString(commentsPrefsKey(), encodeComments())
            .apply()
    }

    private fun normalizeArticleUrl(value: String): String = value.trim()
        .lowercase(Locale.ROOT)
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('#')
        .trimEnd('/')

    private fun encodeComments(): String = JSONArray().apply {
        comments.forEach { comment ->
            put(JSONObject().apply {
                put("user", comment.user)
                put("text", comment.text)
                put("avatar", comment.avatar)
                put("date", comment.date)
                put("reactions", JSONObject(comment.reactions as Map<*, *>))
            })
        }
    }.toString()

    private fun decodeComments(raw: String): List<Comment> = try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val reactionJson = item.optJSONObject("reactions") ?: JSONObject()
                val reactions = mutableMapOf<String, Int>()
                val keys = reactionJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    reactions[key] = reactionJson.optInt(key, 0)
                }
                add(Comment(
                    user = item.optString("user", "Convidado"),
                    text = item.optString("text"),
                    avatar = item.optString("avatar"),
                    date = item.optString("date", "agora"),
                    reactions = reactions
                ))
            }
        }
    } catch (_: Throwable) { emptyList() }

    private fun openCommentsScreen() {
        startActivityForResult(
            Intent(this, CommentsActivity::class.java).apply {
                putExtra("article_title", itemTitle)
                putExtra("comments_json", encodeComments())
                putExtra("article_link", itemLink)
                putExtra("focus_composer", true)
            },
            REQUEST_COMMENTS
        )
        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
    }

    @Deprecated("Deprecated API retained for compatibility with this screen's result flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_COMMENTS && resultCode == RESULT_OK) {
            comments.clear()
            comments.addAll(decodeComments(data?.getStringExtra("comments_json").orEmpty()))
            persistComments()
        }
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
            Triple("Mais", "action_share", null)
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
        refreshArticleTheme()
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