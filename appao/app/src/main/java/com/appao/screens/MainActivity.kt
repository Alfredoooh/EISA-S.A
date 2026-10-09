package com.appao

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var rootHost: DrawerHostLayout
    private lateinit var homePage: View
    private lateinit var homeHeader: View
    private lateinit var feedPanel: View
    private lateinit var feedHeader: View
    private lateinit var feedTabsHost: LinearLayout
    private lateinit var feedTabsScroll: android.widget.HorizontalScrollView
    private lateinit var appsPanel: View
    private lateinit var appsBottomTabs: View
    private lateinit var appsTabIndicator: View
    private lateinit var appsContent: View
    private lateinit var conversationsContent: View

    private lateinit var adapter: NewsAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var progress: View
    private lateinit var biBar: View
    private lateinit var biPill: View
    private lateinit var biInput: EditText
    private lateinit var biSend: FrameLayout
    private lateinit var pullIndicator: PullRefreshIndicatorView
    private lateinit var homeFeedStack: View
    private lateinit var stackBack: ImageView
    private lateinit var stackFront: ImageView

    private val items = mutableListOf<NewsItem>()
    private val categories = mutableListOf(
        NewsCategory("world", "Internacional", listOf("gnews")),
        NewsCategory("business", "Negócios", listOf("gnews", "currents")),
        NewsCategory("technology", "Tecnologia", listOf("gnews", "currents")),
        NewsCategory("entertainment", "Entretenimento", listOf("gnews", "currents")),
        NewsCategory("sports", "Desporto", listOf("gnews", "currents")),
        NewsCategory("science", "Ciência", listOf("gnews", "currents")),
        NewsCategory("health", "Saúde", listOf("gnews", "currents")),
        NewsCategory("society", "Sociedade", listOf("currents")),
        NewsCategory("politics_government", "Política e Governo", listOf("currents")),
        NewsCategory("lifestyle_leisure", "Estilo de Vida e Lazer", listOf("currents")),
        NewsCategory("human_interest", "Interesse Humano", listOf("currents")),
        NewsCategory("crime_law_justice", "Crime, Direito e Justiça", listOf("currents")),
        NewsCategory("education", "Educação", listOf("currents")),
        NewsCategory("environment", "Ambiente", listOf("currents")),
        NewsCategory("labour", "Trabalho", listOf("currents")),
        NewsCategory("automotive", "Automóvel", listOf("currents")),
        NewsCategory("real_estate", "Imobiliário", listOf("currents"))
    )

    private var currentCat = "Internacional"
    private var feedLoadJob: Job? = null
    private var feedRequestGeneration: Long = 0L
    private var page = 1
    private var loading = false
    private var exhausted = false

    private var feedOpen = false
    private var appsOpen = false
    private var feedAnimator: ValueAnimator? = null
    private var appsAnimator: ValueAnimator? = null

    private var appsTabIsApps = true
    private var appsReady = false

    private var feedHeaderHeight = 0
    private var feedBarProgress = 0f
    private var feedBarAnimator: ValueAnimator? = null

    private var bottomBarHeight = 0
    private var composerHeightAnimator: ValueAnimator? = null

    private val categoryPrefs by lazy { getSharedPreferences("appao", MODE_PRIVATE) }
    private val hiddenCategoryPrefsKey = "feed_hidden_categories"

    private var pullDragging = false
    private var pullRefreshing = false
    private var pullCurrentY = 0f
    private var pullTargetY = 0f
    private var pullVelocity = 0f
    private var pullLastTimeNs = 0L
    private var pullFramePosted = false
    private var pullRotation = 0f
    private var pullSpinnerAnimator: ValueAnimator? = null
    private var pullFinishPosted = false

    private var restoredState = false
    private var restoredFeedPosition = 0
    private var restoredFeedOffset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)
        try { SystemBarHelper.sync(this) } catch (_: Throwable) { }

        rootHost = findViewById(R.id.rootMain)
        homePage = findViewById(R.id.homePage)
        homeHeader = findViewById(R.id.homeHeader)
        feedPanel = findViewById(R.id.feedPanel)
        feedHeader = findViewById(R.id.feedHeader)
        feedTabsHost = findViewById(R.id.feedTabsHost)
        feedTabsScroll = findViewById(R.id.feedTabsScroll)
        appsPanel = findViewById(R.id.appsPanel)
        appsBottomTabs = findViewById(R.id.appsBottomTabs)
        appsTabIndicator = findViewById(R.id.appsTabIndicator)
        appsContent = findViewById(R.id.appsContent)
        conversationsContent = findViewById(R.id.conversationsContent)
        recycler = findViewById(R.id.feedRecycler)
        progress = findViewById(R.id.appProgress)
        biBar = findViewById(R.id.biBar)
        biPill = findViewById(R.id.biPill)
        biInput = findViewById(R.id.biInput)
        biSend = findViewById(R.id.biSend)
        pullIndicator = findViewById(R.id.pullIndicator)
        homeFeedStack = findViewById(R.id.homeFeedStack)
        stackBack = findViewById(R.id.stackBack)
        stackFront = findViewById(R.id.stackFront)

        restoreState(savedInstanceState)

        safeStartup { setupInsets() }
        safeStartup { setupHome() }
        safeStartup { setupFeed() }
        safeStartup { setupFeedHeader() }
        safeStartup { setupBottomInput() }
        safeStartup { setupAppsPanel() }
        safeStartup { setupTouchHost() }

        recycler.post {
            if (isFinishing || isDestroyed) return@post
            if (restoredState && items.isNotEmpty()) {
                adapter.submit(items)
                updateFeedStack()
                restoreFeedScroll()
                if (feedOpen) openFeedPanelImmediate()
                if (appsOpen) openAppsPanelImmediate()
            } else {
                setupInitialData()
            }
        }

    }

    private inline fun safeStartup(block: () -> Unit) {
        try { block() } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Startup step failed", t)
        }
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        val state = savedInstanceState ?: return
        currentCat = (state.getString("main.category", "Internacional") ?: "Internacional").let { saved ->
            if (saved == "Todas" || saved == "Para você" || saved == "Seguindo") "Internacional" else saved
        }
        page = state.getInt("main.page", 1).coerceAtLeast(1)
        feedOpen = state.getBoolean("main.feed_open", false)
        appsOpen = state.getBoolean("main.apps_open", false)
        restoredFeedPosition = state.getInt("main.feed_position", 0).coerceAtLeast(0)
        restoredFeedOffset = state.getInt("main.feed_offset", 0)
        appsTabIsApps = state.getBoolean("main.apps_tab_apps", true)

        val raw = state.getString("main.items").orEmpty()
        if (raw.isNotBlank()) {
            runCatching {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    items += NewsItem(
                        id = o.optString("id"),
                        title = NewsRepository.cleanForDisplay(o.optString("title")),
                        summary = NewsRepository.cleanForDisplay(o.optString("summary")),
                        link = o.optString("link"),
                        source = NewsRepository.cleanForDisplay(o.optString("source")),
                        date = o.optString("date"),
                        image = o.optString("image"),
                        logo = o.optString("logo")
                    )
                }
            }
        }
        restoredState = items.isNotEmpty()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("main.category", currentCat)
        outState.putInt("main.page", page)
        outState.putBoolean("main.feed_open", feedOpen)
        outState.putBoolean("main.apps_open", appsOpen)
        outState.putBoolean("main.apps_tab_apps", appsTabIsApps)

        val lm = recycler.layoutManager as? LinearLayoutManager
        val position = lm?.findFirstVisibleItemPosition() ?: 0
        val offset = if (lm != null && position >= 0) {
            lm.findViewByPosition(position)?.top ?: 0
        } else {
            0
        }
        outState.putInt("main.feed_position", position)
        outState.putInt("main.feed_offset", offset)
        outState.putString("main.items", itemsToJson(items))
    }

    private fun itemsToJson(source: List<NewsItem>): String = JSONArray().apply {
        source.take(60).forEach { item ->
            put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("summary", item.summary)
                put("link", item.link)
                put("source", item.source)
                put("date", item.date)
                put("image", item.image)
                put("logo", item.logo)
            })
        }
    }.toString()

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(biBar) { view, insets ->
            val systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val safeBottom = if (systemBottom > imeBottom) systemBottom else imeBottom
            val frame = view as ViewGroup
            frame.setPadding(dp(14), dp(10), dp(14), dp(10) + safeBottom)
            bottomBarHeight = view.measuredHeight
            insets
        }

        biBar.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            bottomBarHeight = v.height
        }

        feedHeaderHeight = dp(104)
        recycler.updatePadding(top = feedHeaderHeight, bottom = dp(28))
        ViewCompat.requestApplyInsets(rootHost)
    }

    private fun setupHome() {
        homeHeader.elevation = 0f
        homeHeader.translationZ = 0f
        IconLoader.applySvg(findViewById(R.id.homeMenuIcon), "menu", R.color.iconTint)

        homeFeedStack.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        homeFeedStack.foreground = null
        homeFeedStack.stateListAnimator = null
        homeFeedStack.isFocusable = false
        homeFeedStack.setOnClickListener { openFeedPanel() }
        findViewById<View>(R.id.homeMenu).setOnClickListener { openAppsPanel() }

        stackBack.scaleType = ImageView.ScaleType.CENTER_CROP
        stackFront.scaleType = ImageView.ScaleType.CENTER_CROP
        stackBack.rotation = -8.5f
        stackFront.rotation = 7.5f
        stackBack.translationX = -dp(5).toFloat()
        stackBack.translationY = dp(2).toFloat()
        stackFront.translationX = dp(4).toFloat()
        stackFront.translationY = 0f
    }

    private fun setupFeed() {
        adapter = NewsAdapter(
            initial = items.toList(),
            onClick = { item, source -> openArticle(item, source) },
            showTopCards = false
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        recycler.itemAnimator = null
        recycler.setHasFixedSize(false)
        recycler.setItemViewCacheSize(5)
        recycler.overScrollMode = View.OVER_SCROLL_ALWAYS

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0) updateFeedBarsFromScroll(dy)
                if (!rv.canScrollVertically(1) && !loading && !exhausted) loadMore()
            }
        })

        pullIndicator.alpha = 0f
        pullIndicator.scaleX = 0f
        pullIndicator.scaleY = 0f
    }

    private fun setupFeedHeader() {
        feedHeader.elevation = 0f
        feedHeader.translationZ = 0f
        IconLoader.applySvg(findViewById(R.id.feedBackIcon), "arrow_left", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.feedPublishIcon), "publish", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.feedMoreIcon), "more_vert", R.color.iconTint)

        findViewById<TextView>(R.id.feedToolbarTitle).apply {
            text = "Descobrir"
            visibility = View.VISIBLE
        }
        findViewById<View>(R.id.feedBack).setOnClickListener { closeFeedPanel() }
        findViewById<View>(R.id.feedPublish).setOnClickListener { openPublish() }
        findViewById<View>(R.id.feedMore).setOnClickListener { showFeedMorePopup(it) }

        refreshFeedTabs(scrollToSelection = false)
    }

    private fun hiddenCategories(): MutableSet<String> {
        val raw = categoryPrefs.getStringSet(hiddenCategoryPrefsKey, emptySet()) ?: emptySet()
        return raw.toMutableSet()
    }

    private fun refreshFeedTabs(scrollToSelection: Boolean = true) {
        if (!::feedTabsHost.isInitialized) return
        val previousScrollX = if (::feedTabsScroll.isInitialized) feedTabsScroll.scrollX else 0
        feedTabsHost.removeAllViews()

        val hidden = hiddenCategories()
        val visibleCategories = categories.filter { it.id !in hidden }
        val tabs = visibleCategories.map { it.label }

        tabs.forEach { label ->
            val tab = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumWidth = dp(72)
                setPadding(dp(9), 0, dp(9), 0)
                isClickable = true
                isFocusable = true
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val tv = TextView(this).apply {
                text = label
                textSize = 14.5f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text))
                setTypeface(android.graphics.Typeface.DEFAULT, if (label == currentCat) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                maxLines = 1
            }
            row.addView(tv)

            tab.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)))
            val indicator = View(this).apply {
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, if (label == currentCat) R.color.text else android.R.color.transparent))
                alpha = if (label == currentCat) 1f else 0f
            }
            tab.addView(indicator, LinearLayout.LayoutParams(dp(54), dp(2)))

            tab.setOnClickListener {
                if (label != currentCat) {
                    currentCat = label
                    updateFeedCategoryAndReload(true)
                }
            }

            feedTabsHost.addView(tab, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        val plus = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.MATCH_PARENT)
            isClickable = true
            isFocusable = true
        }
        val plusIcon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        IconLoader.applySvg(plusIcon, "plus", R.color.iconTint)
        plus.addView(plusIcon)
        plus.setOnClickListener { showCategoryManager() }
        feedTabsHost.addView(plus)

        feedTabsHost.post {
            if (scrollToSelection) {
                val selectedIndex = tabs.indexOf(currentCat).coerceAtLeast(0)
                val selected = feedTabsHost.getChildAt(selectedIndex)
                selected?.let { feedTabsScroll.smoothScrollTo((it.left - dp(12)).coerceAtLeast(0), 0) }
            } else {
                feedTabsScroll.scrollTo(previousScrollX, 0)
            }
        }
    }

    private fun selectedServerCategoryId(): String =
        categories.firstOrNull { it.label == currentCat }?.id ?: "world"

    private fun updateFeedCategoryAndReload(force: Boolean) {
        findViewById<TextView>(R.id.feedToolbarTitle).text = "Descobrir"
        page = 1
        exhausted = false
        NewsRepository.resetDedup()
        refreshFeedTabs(false)

        // Render the selected category's disk cache immediately; never show articles
        // from the previous category while this category is fetched.
        val categoryId = selectedServerCategoryId()
        val cached = NewsRepository.readCategoryCache(this, categoryId)
        recycler.animate().cancel()
        if (cached.isNotEmpty()) {
            items.clear()
            items.addAll(deduplicate(cached))
            adapter.submit(items)
            updateFeedStack()
            recycler.alpha = 0.94f
        } else {
            items.clear()
            adapter.submit(emptyList())
            updateFeedStack()
            recycler.alpha = 0.86f
        }
        loadNews(force && cached.isEmpty())
    }

    private fun showFeedMorePopup(anchor: View) {
        NativePopupMenu.show(
            anchor,
            listOf(
                NativePopupMenu.Item(1, "Editar categorias", svg = "edit"),
                NativePopupMenu.Item(2, "Atualizar notícias", svg = "refresh")
            )
        ) { item ->
            when (item.id) {
                1 -> showCategoryManager()
                2 -> loadNews(true)
            }
        }
    }

    private fun showCategoryManager() {
        startActivity(Intent(this, CategoryManagerActivity::class.java))
        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
    }

    private fun setupBottomInput() {
        IconLoader.applySvg(findViewById(R.id.biAddIcon), "plus", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.biSliderIcon), "slider", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.biSendIcon), "arrow_up", R.color.onpri)

        // Native reproduction of the supplied HTML textarea. The action row is
        // structurally separate, so it stays anchored while the textarea grows.
        biInput.setSingleLine(false)
        biInput.maxLines = Int.MAX_VALUE
        biInput.setHorizontallyScrolling(false)
        biInput.setLineSpacing(0f, 1.6f)
        biInput.gravity = Gravity.TOP or Gravity.START
        biInput.includeFontPadding = true
        biInput.setPadding(0, 0, 0, 0)
        biInput.isVerticalScrollBarEnabled = false
        biInput.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

        biInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                biInput.post { resizeComposerInput(true) }
                updateComposerSendState()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        biSend.setOnClickListener { sendToAi() }
        biInput.setOnEditorActionListener { _, _, _ ->
            sendToAi()
            true
        }
        // Keep the existing More popup and its native sheet implementation.
        findViewById<View>(R.id.biAdd).setOnClickListener { showAppsPopup(it) }

        applyComposerSurface()
        biInput.post {
            resizeComposerInput(false)
            updateComposerSendState()
        }
    }

    private fun updateComposerSendState() {
        val dark = ThemeManager.resolvedDark(this)
        val active = biInput.text?.isNotBlank() == true
        val fill = when {
            active && dark -> android.graphics.Color.WHITE
            active -> android.graphics.Color.BLACK
            else -> ContextCompat.getColor(this, R.color.card2)
        }
        biSend.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(fill)
        }
        findViewById<ImageView>(R.id.biSendIcon).setColorFilter(
            if (active && dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }

    private fun resizeComposerInput(animate: Boolean) {
        if (!::biInput.isInitialized || biInput.width <= 0) return

        val density = resources.displayMetrics.density
        val minHeight = (26f * density + 0.5f).toInt()
        val maxHeight = (200f * density + 0.5f).toInt()
        val width = biInput.width.coerceAtLeast(1)
        val text = biInput.text

        val measuredHeight = if (text.isEmpty()) {
            minHeight
        } else {
            val measured = StaticLayout.Builder
                .obtain(text, 0, text.length, biInput.paint, width)
                .setIncludePad(true)
                .setLineSpacing(0f, 1.6f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()
                .height
            measured.coerceAtLeast(minHeight)
        }

        val desired = measuredHeight.coerceAtMost(maxHeight)
        val current = biInput.height.takeIf { it > 0 } ?: minHeight

        composerHeightAnimator?.cancel()
        if (animate && current != desired) {
            composerHeightAnimator = ValueAnimator.ofInt(current, desired).apply {
                duration = 300L
                interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
                addUpdateListener { animator ->
                    val lp = biInput.layoutParams
                    lp.height = animator.animatedValue as Int
                    biInput.layoutParams = lp
                }
                start()
            }
        } else {
            val lp = biInput.layoutParams
            lp.height = desired
            biInput.layoutParams = lp
        }

        val atMax = desired >= maxHeight
        biInput.isVerticalScrollBarEnabled = atMax
        biInput.overScrollMode = if (atMax) View.OVER_SCROLL_ALWAYS else View.OVER_SCROLL_IF_CONTENT_SCROLLS
        if (!atMax) biInput.scrollTo(0, 0)
    }

    private fun applyComposerSurface() {
        val dark = ThemeManager.resolvedDark(this)
        biBar.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        biPill.background = HtmlComposerBackgroundDrawable(this, dark)
        biInput.setTextColor(if (dark) android.graphics.Color.rgb(245, 245, 245) else android.graphics.Color.rgb(51, 51, 51))
        biInput.setHintTextColor(if (dark) android.graphics.Color.rgb(150, 155, 162) else android.graphics.Color.rgb(153, 153, 153))
        findViewById<View>(R.id.biAddIcon).alpha = 0.4f
        findViewById<View>(R.id.biSliderIcon).alpha = 0.4f
        updateComposerSendState()
    }

    private fun setupAppsPanel() {
        if (appsReady) return
        appsReady = true

        val appsHeader = findViewById<View>(R.id.appsHeaderBar)
        appsHeader.elevation = 0f
        appsHeader.translationZ = 0f
        IconLoader.applyPng(findViewById(R.id.appsProfileAvatar), "avatar")
        IconLoader.applySvg(findViewById(R.id.appsBackIcon), "arrow_right", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.appsSettingsIcon), "settings", R.color.iconTint)

        findViewById<View>(R.id.appsProfile).setOnClickListener {
            startActivity(Intent(this, AuthActivity::class.java))
        }
        findViewById<View>(R.id.appsBack).setOnClickListener { closeAppsPanel() }
        findViewById<View>(R.id.appsSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val appsTab = findViewById<TextView>(R.id.appsTab)
        val conversationsTab = findViewById<TextView>(R.id.conversationsTab)
        appsTab.setOnClickListener { selectAppsTab(true, true) }
        conversationsTab.setOnClickListener { selectAppsTab(false, true) }

        populateAppsGrid()
        appsBottomTabs.post { selectAppsTab(appsTabIsApps, false) }
    }

    private fun selectAppsTab(apps: Boolean, animated: Boolean) {
        appsTabIsApps = apps
        appsContent.visibility = if (apps) View.VISIBLE else View.GONE
        conversationsContent.visibility = if (apps) View.GONE else View.VISIBLE

        val appsTab = findViewById<TextView>(R.id.appsTab)
        val conversationsTab = findViewById<TextView>(R.id.conversationsTab)
        val active = ContextCompat.getColor(this, R.color.onpri)
        val inactive = ContextCompat.getColor(this, R.color.dim)
        appsTab.setTextColor(if (apps) active else inactive)
        conversationsTab.setTextColor(if (apps) inactive else active)

        val available = (appsBottomTabs.width - appsBottomTabs.paddingLeft - appsBottomTabs.paddingRight).coerceAtLeast(2)
        val tabWidth = available / 2
        val tabHeight = (appsBottomTabs.height - appsBottomTabs.paddingTop - appsBottomTabs.paddingBottom).coerceAtLeast(2)
        val baseX = appsBottomTabs.paddingLeft

        appsTabIndicator.layoutParams = (appsTabIndicator.layoutParams as FrameLayout.LayoutParams).apply {
            width = tabWidth
            height = tabHeight
            leftMargin = baseX
            topMargin = appsBottomTabs.paddingTop
        }
        appsTab.layoutParams = (appsTab.layoutParams as FrameLayout.LayoutParams).apply {
            width = tabWidth
            height = tabHeight
            leftMargin = baseX
            topMargin = appsBottomTabs.paddingTop
        }
        conversationsTab.layoutParams = (conversationsTab.layoutParams as FrameLayout.LayoutParams).apply {
            width = tabWidth
            height = tabHeight
            leftMargin = baseX + tabWidth
            topMargin = appsBottomTabs.paddingTop
        }

        appsTabIndicator.visibility = View.VISIBLE
        appsTabIndicator.alpha = 1f
        appsTabIndicator.animate().cancel()
        val target = if (apps) 0f else tabWidth.toFloat()
        if (animated) {
            appsTabIndicator.animate()
                .translationX(target)
                .setDuration(280L)
                .setInterpolator(Curves.SMOOTH)
                .start()
        } else {
            appsTabIndicator.translationX = target
        }
    }

    private fun populateAppsGrid() {
        // Apps tab remains available but intentionally contains no app cards.
        findViewById<android.widget.GridLayout>(R.id.appsGrid).removeAllViews()
    }

    private fun setupTouchHost() {
        rootHost.listener = object : DrawerHostLayout.Listener {
            override fun isDrawerOpen(): Boolean = feedOpen
            override fun isSidePanelOpen(): Boolean = appsOpen

            override fun canStartPull(x: Float, y: Float): Boolean {
                if (!feedOpen || appsOpen || pullRefreshing || loading) return false
                if (!::recycler.isInitialized || !recycler.isShown) return false
                return !recycler.canScrollVertically(-1)
            }

            override fun onPullGestureStart() {
                pullDragging = true
                pullVelocity = 0f
                pullTargetY = pullCurrentY
                pullLastTimeNs = 0L
                recycler.stopScroll()
                feedPanel.animate().cancel()
                startPullLoop()
            }

            override fun onPullGestureProgress(distance: Float) {
                if (!pullDragging || pullRefreshing) return
                val maxPull = dp(130).toFloat()
                val threshold = dp(72).toFloat()
                val k = 0.55f
                pullTargetY = (1f - kotlin.math.exp(-distance * k / maxPull))
                    .times(maxPull)
                    .coerceAtMost(maxPull)
                if (pullCurrentY > threshold && pullTargetY < threshold) {
                    // Keep the indicator state continuous around the trigger boundary.
                }
                startPullLoop()
            }

            override fun onPullGestureEnd() {
                if (!pullDragging || pullRefreshing) return
                pullDragging = false
                if (pullCurrentY >= dp(72)) beginPullRefresh() else {
                    pullTargetY = 0f
                    pullVelocity = 0f
                    startPullLoop()
                }
            }

            override fun onDrawerGestureStart(opening: Boolean) {
                feedAnimator?.cancel()
                feedPanel.animate().cancel()
                if (opening) beginFeedOpenGesture() else beginFeedCloseGesture()
            }

            override fun onDrawerGestureProgress(progress: Float) {
                setFeedProgress(progress)
            }

            override fun onDrawerGestureEnd(opening: Boolean, progress: Float, velocity: Float) {
                finishFeedGesture(opening, progress, velocity)
            }

            override fun onSidePanelGestureStart(opening: Boolean) {
                appsAnimator?.cancel()
                appsPanel.animate().cancel()
                if (opening) beginAppsOpenGesture() else beginAppsCloseGesture()
            }

            override fun onSidePanelGestureProgress(progress: Float) {
                setAppsProgress(progress)
            }

            override fun onSidePanelGestureEnd(opening: Boolean, progress: Float, velocity: Float) {
                finishAppsGesture(opening, progress, velocity)
            }
        }
    }

    private fun beginFeedOpenGesture() {
        feedOpen = true
        appsOpen = false
        feedPanel.visibility = View.VISIBLE
        feedHeader.translationY = 0f
        setFeedProgress(0f)
    }

    private fun beginFeedCloseGesture() {
        feedOpen = true
        feedPanel.visibility = View.VISIBLE
        setFeedProgress(1f)
    }

    private fun finishFeedGesture(opening: Boolean, progress: Float, velocity: Float) {
        val shouldOpen = if (opening) progress > 0.18f || velocity < -0.45f
        else progress > 0.62f && velocity <= 0.45f
        if (opening) {
            if (shouldOpen) openFeedPanel(progress) else closeFeedPanel(progress)
        } else {
            if (shouldOpen) openFeedPanel(progress) else closeFeedPanel(progress)
        }
    }

    private fun beginAppsOpenGesture() {
        feedOpen = false
        appsOpen = true
        appsPanel.visibility = View.VISIBLE
        setAppsProgress(0f)
    }

    private fun beginAppsCloseGesture() {
        appsOpen = true
        appsPanel.visibility = View.VISIBLE
        setAppsProgress(1f)
    }

    private fun finishAppsGesture(opening: Boolean, progress: Float, velocity: Float) {
        val shouldOpen = if (opening) progress > 0.18f || velocity > 0.45f
        else progress > 0.62f && velocity >= -0.45f
        if (opening) {
            if (shouldOpen) openAppsPanel(progress) else closeAppsPanel(progress)
        } else {
            if (shouldOpen) openAppsPanel(progress) else closeAppsPanel(progress)
        }
    }

    private fun setFeedProgress(progressValue: Float) {
        val p = progressValue.coerceIn(0f, 1f)
        val width = rootHost.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        feedPanel.translationX = width * (1f - p)
        homePage.translationX = -homePage.width.coerceAtLeast(width) * 0.34f * p
    }

    private fun setAppsProgress(progressValue: Float) {
        val p = progressValue.coerceIn(0f, 1f)
        val width = rootHost.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        appsPanel.translationX = -width * (1f - p)
        homePage.translationX = homePage.width.coerceAtLeast(width) * 0.34f * p
    }

    private fun openFeedPanelImmediate() {
        feedOpen = true
        appsOpen = false
        appsPanel.visibility = View.GONE
        feedPanel.visibility = View.VISIBLE
        setFeedProgress(1f)
        resetFeedBar()
    }

    private fun openAppsPanelImmediate() {
        appsOpen = true
        feedOpen = false
        feedPanel.visibility = View.GONE
        appsPanel.visibility = View.VISIBLE
        setAppsProgress(1f)
    }

    private fun openFeedPanel(gestureProgress: Float? = null) {
        if (isFinishing || isDestroyed) return
        feedOpen = true
        appsOpen = false
        appsPanel.visibility = View.GONE
        feedPanel.visibility = View.VISIBLE
        resetFeedBar()

        val current = gestureProgress ?: ((rootHost.width - feedPanel.translationX) / rootHost.width.coerceAtLeast(1)).coerceIn(0f, 1f)
        animateFeedTo(1f, current)
    }

    private fun closeFeedPanel(gestureProgress: Float? = null) {
        feedOpen = false
        val width = rootHost.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val current = gestureProgress ?: ((width - feedPanel.translationX) / width).coerceIn(0f, 1f)
        animateFeedTo(0f, current) {
            if (!feedOpen) {
                feedPanel.visibility = View.GONE
                feedPanel.translationX = width.toFloat()
                homePage.translationX = 0f
            }
        }
    }

    private fun animateFeedTo(target: Float, current: Float, end: (() -> Unit)? = null) {
        feedAnimator?.cancel()
        feedAnimator = ValueAnimator.ofFloat(current, target).apply {
            duration = 500L
            interpolator = Curves.IOS
            addUpdateListener { setFeedProgress(it.animatedValue as Float) }
            if (end != null) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { end() }
                })
            }
            start()
        }
    }

    private fun openAppsPanel(gestureProgress: Float? = null) {
        if (isFinishing || isDestroyed) return
        appsOpen = true
        feedOpen = false
        feedPanel.visibility = View.GONE
        appsPanel.visibility = View.VISIBLE
        val width = rootHost.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val current = gestureProgress ?: ((appsPanel.translationX + width) / width).coerceIn(0f, 1f)
        animateAppsTo(1f, current)
    }

    private fun closeAppsPanel(gestureProgress: Float? = null) {
        appsOpen = false
        val width = rootHost.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val current = gestureProgress ?: ((appsPanel.translationX + width) / width).coerceIn(0f, 1f)
        animateAppsTo(0f, current) {
            if (!appsOpen) {
                appsPanel.visibility = View.GONE
                appsPanel.translationX = -width.toFloat()
                homePage.translationX = 0f
            }
        }
    }

    private fun animateAppsTo(target: Float, current: Float, end: (() -> Unit)? = null) {
        appsAnimator?.cancel()
        appsAnimator = ValueAnimator.ofFloat(current, target).apply {
            duration = 500L
            interpolator = Curves.IOS
            addUpdateListener { setAppsProgress(it.animatedValue as Float) }
            if (end != null) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { end() }
                })
            }
            start()
        }
    }

    private fun setupInitialData() {
        adapter.showSkeleton(8)
        lifecycleScope.launch {
            val categoryJob = async(Dispatchers.IO) { NewsRepository.fetchCategories() }
            val cached = withContext(Dispatchers.IO) {
                NewsRepository.readCategoryCache(this@MainActivity, "world")
                    .ifEmpty { NewsRepository.readCache(this@MainActivity) }
            }
            if (isFinishing || isDestroyed) return@launch
            if (cached.isNotEmpty()) {
                val unique = deduplicate(cached)
                items.clear()
                items.addAll(unique)
                adapter.submit(unique)
                updateFeedStack()
            }
            loadNews(false)
            val liveCategories = runCatching { categoryJob.await() }.getOrDefault(emptyList())
            if (!isFinishing && !isDestroyed && liveCategories.isNotEmpty()) {
                categories.clear()
                categories.addAll(liveCategories)
                if (categories.none { it.label == currentCat }) {
                    currentCat = categories.firstOrNull { it.id == "world" }?.label ?: "Internacional"
                    page = 1
                    exhausted = false
                    loadNews(false)
                }
                refreshFeedTabs(false)
            }
        }
    }

    private fun updateFeedBarsFromScroll(dy: Int) {
        val travel = dp(56).toFloat()
        val direction = if (dy > 0) 1f else -1f
        feedBarProgress = (feedBarProgress + abs(dy).toFloat() / travel * direction).coerceIn(0f, 1f)
        if (recycler.computeVerticalScrollOffset() <= 0) feedBarProgress = 0f
        feedHeader.translationY = -travel * feedBarProgress
    }

    private fun resetFeedBar() {
        feedBarAnimator?.cancel()
        feedBarProgress = 0f
        feedHeader.translationY = 0f
        recycler.updatePadding(top = dp(104))
    }

    private suspend fun fetchFeedCategory(category: String, pageNumber: Int): List<NewsItem> {
        val serverCategory = categories.firstOrNull { it.label == category }
        return NewsRepository.fetchByCategory(serverCategory?.id ?: "world", pageNumber)
    }

    private fun loadNews(force: Boolean) {
        feedLoadJob?.cancel()
        val requestToken = ++feedRequestGeneration
        val requestedCategory = currentCat
        val categoryId = selectedServerCategoryId()
        val requestedPage = 1
        loading = true
        exhausted = false
        page = requestedPage
        if (force) adapter.showSkeleton(8)

        feedLoadJob = lifecycleScope.launch {
            val result = try {
                fetchFeedCategory(requestedCategory, requestedPage)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                android.util.Log.e("MainActivity", "News load failed", t)
                emptyList()
            }

            // Ignore stale responses if the user switched category during the request.
            if (isFinishing || isDestroyed || requestToken != feedRequestGeneration ||
                requestedCategory != currentCat || categoryId != selectedServerCategoryId()) return@launch

            loading = false
            recycler.animate().alpha(1f).setDuration(180L).setInterpolator(Curves.SMOOTH).start()
            val clean = deduplicate(result)

            if (clean.isNotEmpty()) {
                items.clear()
                items.addAll(clean)
                NewsRepository.writeCategoryCache(this@MainActivity, categoryId, clean)
                if (categoryId == "world") NewsRepository.writeCache(this@MainActivity, clean)
                adapter.submit(clean)
                updateFeedStack()
            } else if (items.isNotEmpty()) {
                adapter.submit(items)
                updateFeedStack()
            } else {
                adapter.submit(emptyList())
                exhausted = true
            }
        }
    }

    private fun loadMore() {
        if (loading || exhausted) return
        loading = true
        exhausted = false
        adapter.showBottomSkeleton(3)
        val requestToken = feedRequestGeneration
        val categoryAtStart = currentCat
        val categoryIdAtStart = selectedServerCategoryId()

        lifecycleScope.launch {
            var candidatePage = page + 1
            var fresh: List<NewsItem> = emptyList()

            for (attempt in 0 until 5) {
                val result = try {
                    fetchFeedCategory(categoryAtStart, candidatePage)
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    android.util.Log.e("MainActivity", "Load more failed", t)
                    emptyList()
                }

                if (isFinishing || isDestroyed || requestToken != feedRequestGeneration ||
                    categoryAtStart != currentCat || categoryIdAtStart != selectedServerCategoryId()) return@launch

                val seen = HashSet<String>()
                items.forEach { seen += normalizeUrl(it.link); seen += normalizeTitle(it.title) }
                fresh = result.filter { item ->
                    val urlKey = normalizeUrl(item.link)
                    val titleKey = normalizeTitle(item.title)
                    if (urlKey.isBlank() || titleKey.isBlank()) false
                    else if (urlKey in seen || titleKey in seen) false
                    else {
                        seen += urlKey
                        seen += titleKey
                        true
                    }
                }
                if (fresh.isNotEmpty()) break
                candidatePage++
                if (attempt < 4) kotlinx.coroutines.delay(450L)
            }

            if (isFinishing || isDestroyed || requestToken != feedRequestGeneration ||
                categoryAtStart != currentCat || categoryIdAtStart != selectedServerCategoryId()) return@launch
            loading = false

            if (fresh.isEmpty()) {
                exhausted = true
                adapter.hideBottomSkeleton()
                return@launch
            }

            adapter.hideBottomSkeleton()
            page = candidatePage
            items.addAll(fresh)
            adapter.append(fresh)
            NewsRepository.writeCategoryCache(this@MainActivity, categoryIdAtStart, items)
            if (categoryIdAtStart == "world") NewsRepository.writeCache(this@MainActivity, items)
            updateFeedStack()
        }
    }

    private fun updateFeedStack() {
        val available = items.filter { it.image.isNotBlank() }
        val chosen = when {
            available.size >= 2 -> available.shuffled().take(2)
            available.size == 1 -> listOf(available.first(), available.first())
            else -> emptyList()
        }
        val fallback = chosen.firstOrNull()
        loadStackImage(stackBack, chosen.getOrNull(1)?.image ?: fallback?.image)
        loadStackImage(stackFront, chosen.getOrNull(0)?.image ?: fallback?.image)
    }

    private fun loadStackImage(view: ImageView, url: String?) {
        if (url.isNullOrBlank()) {
            Glide.with(view).load(R.drawable.logo).dontAnimate().into(view)
        } else {
            Glide.with(view).load(url).dontAnimate().centerCrop().into(view)
        }
        view.clipToOutline = true
    }

    private fun sendToAi() {
        val query = biInput.text.toString().trim()
        if (query.isEmpty()) return
        biInput.clearFocus()
        startActivity(Intent(this, SearchActivity::class.java).apply {
            putExtra("query", query)
        })
        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
        biInput.setText("")
    }

    private fun showAppsPopup(anchor: View) {
        AppsPopup.show(anchor) { action ->
            when (action) {
                "file" -> {
                    startActivity(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                    )
                }
                "image" -> {
                    startActivity(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                    )
                }
                "camera" -> {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    }
                }
                "think" -> {
                    showNavProgress()
                    startActivity(Intent(this, AppViewerActivity::class.java).apply {
                        putExtra("url", "file:///android_asset/apps/ai.html?mode=think")
                    })
                }
            }
        }
    }

    private fun showNavProgress() {
        progress.visibility = View.VISIBLE
        val width = resources.displayMetrics.widthPixels
        val lp = progress.layoutParams as FrameLayout.LayoutParams
        lp.width = 0
        lp.topMargin = 0
        progress.layoutParams = lp
        progress.alpha = 1f
        ValueAnimator.ofInt(0, (width * 0.88f).toInt()).apply {
            duration = 1200L
            interpolator = Curves.PROGRESS
            addUpdateListener { a ->
                val p = progress.layoutParams as FrameLayout.LayoutParams
                p.width = a.animatedValue as Int
                progress.layoutParams = p
            }
            start()
        }
    }

    private fun finishPullRefresh() {
        if (!pullRefreshing) return
        pullRefreshing = false
        pullTargetY = 0f
        pullVelocity = -maxOf(2f, pullCurrentY * 0.05f)
        startPullLoop()
        rootHost.postDelayed({
            if (!pullRefreshing && abs(pullCurrentY) < 1f) {
                pullSpinnerAnimator?.cancel()
                pullSpinnerAnimator = null
                pullRotation = 0f
                pullIndicator.rotationDegrees = 0f
            }
        }, 700L)
    }

    private fun startPullLoop() {
        if (pullFramePosted) return
        pullFramePosted = true
        rootHost.postOnAnimation(pullFrameRunnable)
    }

    private val pullFrameRunnable = object : Runnable {
        override fun run() {
            val now = android.os.SystemClock.elapsedRealtimeNanos()
            val dt = if (pullLastTimeNs != 0L) {
                kotlin.math.min(
                    (now - pullLastTimeNs).toDouble() / 1_000_000_000.0,
                    0.033
                )
            } else {
                0.016
            }
            pullLastTimeNs = now

            if (pullDragging) {
                pullCurrentY += (pullTargetY - pullCurrentY) * 0.55f
            } else {
                val stiffness = 180.0
                val damping = 22.0
                val force = -stiffness * (pullCurrentY - pullTargetY)
                pullVelocity += (force * dt).toFloat()
                pullVelocity *= kotlin.math.exp(-damping * dt).toFloat()
                pullCurrentY += (pullVelocity * dt).toFloat()
            }

            applyPullVisual(pullCurrentY)

            val settled =
                kotlin.math.abs(pullCurrentY - pullTargetY) < 0.3f &&
                    kotlin.math.abs(pullVelocity) < 0.5f

            if (pullDragging || !settled) {
                rootHost.postOnAnimation(this)
            } else {
                pullCurrentY = pullTargetY
                pullVelocity = 0f
                applyPullVisual(pullCurrentY)
                pullFramePosted = false
                pullLastTimeNs = 0L
            }
        }
    }

    private fun applyPullVisual(y: Float) {
        val threshold = dp(72).toFloat()
        val appearT = (y / dp(55).toFloat()).coerceAtMost(1f)
        val zoom = 1f - (1f - appearT) * (1f - appearT) * (1f - appearT)
        val topY = y * 0.5f - dp(14).toFloat()

        pullIndicator.alpha = zoom
        pullIndicator.scaleX = zoom
        pullIndicator.scaleY = zoom
        pullIndicator.translationY = topY

        if (!pullRefreshing) {
            pullRotation = (y / threshold).coerceIn(0f, 1f) * 300f
            pullIndicator.rotationDegrees = pullRotation
        }

        feedPanel.translationY = y.coerceAtLeast(0f)
        feedPanel.translationX = if (feedOpen) 0f else (rootHost.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels).toFloat()
        feedHeader.translationY = -(feedHeaderHeight.takeIf { it > 0 } ?: dp(56)).toFloat() * feedBarProgress
    }

    private fun beginPullRefresh() {
        pullRefreshing = true
        pullDragging = false
        pullTargetY = dp(72).toFloat()
        pullVelocity = 0f

        pullIndicator.rotationDegrees = pullRotation
        pullSpinnerAnimator?.cancel()
        val start = pullRotation
        pullSpinnerAnimator = ValueAnimator.ofFloat(start, start + 360f).apply {
            duration = 800L
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                pullRotation = it.animatedValue as Float
                pullIndicator.rotationDegrees = pullRotation
            }
            start()
        }

        startPullLoop()

        pullFinishPosted = true
        rootHost.postDelayed({
            pullFinishPosted = false
            if (pullRefreshing && !isFinishing && !isDestroyed) {
                finishPullRefresh()
            }
        }, 1400L)

        loadNews(true)
    }

    private fun openPublish() {
        startActivity(Intent(this, PublishActivity::class.java))
        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
    }

    private fun openArticle(item: NewsItem, source: View) {
        if (item.link.isBlank() || item.title.isBlank()) return
        val transitionName = "news_container_${item.id.hashCode().toUInt().toString(36)}"
        ViewCompat.setTransitionName(source, transitionName)

        val intent = Intent(this, ArticleActivity::class.java).apply {
            putExtra("id", item.id)
            putExtra("title", item.title)
            putExtra("summary", item.summary)
            putExtra("link", item.link)
            putExtra("source", item.source)
            putExtra("date", item.date)
            putExtra("image", item.image)
            putExtra("logo", item.logo)
            putExtra("transition_name", transitionName)
            putExtra("transition_is_image", item.image.isNotBlank())
        }

        val options = android.app.ActivityOptions.makeSceneTransitionAnimation(this, source, transitionName)
        startActivity(intent, options.toBundle())
    }

    private fun openFeedAndKeepPosition() {
        openFeedPanel()
    }

    private fun deduplicate(source: List<NewsItem>): List<NewsItem> {
        val out = ArrayList<NewsItem>(source.size)
        val urls = HashSet<String>()
        val titles = HashSet<String>()
        source.forEach { item ->
            val url = normalizeUrl(item.link)
            val title = normalizeTitle(item.title)
            if (url.isBlank() || title.isBlank()) return@forEach
            if (!urls.add(url)) return@forEach
            if (!titles.add(title)) return@forEach
            out += item
        }
        return out
    }

    private fun normalizeUrl(value: String): String = value.trim().lowercase(Locale.ROOT)
        .removePrefix("https://").removePrefix("http://").removePrefix("www.")
        .substringBefore('#').trimEnd('/')

    private fun normalizeTitle(value: String): String = value.trim().lowercase(Locale.ROOT)
        .replace(Regex("[\\p{Punct}]+"), " ").replace(Regex("\\s+"), " ").trim()

    private fun restoreFeedScroll() {
        recycler.post {
            val lm = recycler.layoutManager as? LinearLayoutManager ?: return@post
            lm.scrollToPositionWithOffset(restoredFeedPosition, restoredFeedOffset)
            resetFeedBar()
        }
    }

    override fun onBackPressed() {
        when {
            feedOpen -> closeFeedPanel()
            appsOpen -> closeAppsPanel()
            else -> super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
        refreshNativeTheme()
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
        refreshNativeTheme()
        progress.animate().alpha(0f).setDuration(180L).withEndAction {
            progress.visibility = View.GONE
            (progress.layoutParams as? FrameLayout.LayoutParams)?.let { lp -> lp.width = 0; progress.layoutParams = lp }
        }.start()
        if (::feedTabsHost.isInitialized) {
            val hidden = hiddenCategories()
            val selected = categories.firstOrNull { it.label == currentCat }
            if (selected != null && selected.id in hidden) {
                currentCat = categories.firstOrNull { it.id == "world" }?.label ?: "Internacional"
                page = 1
                exhausted = false
                updateFeedCategoryAndReload(false)
            } else {
                refreshFeedTabs(false)
            }
        }
    }

    private fun refreshNativeTheme() {
        val bg = ContextCompat.getColor(this, R.color.bg)
        rootHost.setBackgroundColor(bg)
        homePage.setBackgroundColor(bg)
        feedPanel.setBackgroundColor(bg)
        appsPanel.setBackgroundColor(bg)
        appsContent.setBackgroundColor(bg)
        conversationsContent.setBackgroundColor(bg)
        homeHeader.setBackgroundColor(bg)
        feedHeader.setBackgroundColor(bg)
        biBar.setBackgroundColor(bg)
        feedHeader.elevation = 0f
        feedHeader.translationZ = 0f
        homeHeader.elevation = 0f
        homeHeader.translationZ = 0f
        val appsHeader = findViewById<View>(R.id.appsHeaderBar)
        appsHeader.elevation = 0f
        appsHeader.translationZ = 0f
        appsHeader.setBackgroundColor(bg)

        IconLoader.applySvg(findViewById(R.id.homeMenuIcon), "menu", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.feedBackIcon), "arrow_left", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.feedPublishIcon), "publish", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.feedMoreIcon), "more_vert", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.biAddIcon), "plus", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.biSliderIcon), "slider", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.biSendIcon), "arrow_up", R.color.onpri)
        IconLoader.applyPng(findViewById(R.id.appsProfileAvatar), "avatar")
        IconLoader.applySvg(findViewById(R.id.appsBackIcon), "arrow_right", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.appsSettingsIcon), "settings", R.color.iconTint)

        findViewById<TextView>(R.id.feedToolbarTitle).setTextColor(ContextCompat.getColor(this, R.color.text))
        refreshFeedTabs(false)
        findViewById<TextView>(R.id.appsTitle).setTextColor(ContextCompat.getColor(this, R.color.text))
        findViewById<TextView>(R.id.conversationsEmpty).setTextColor(ContextCompat.getColor(this, R.color.dim))
        applyComposerSurface()
        biInput.post { resizeComposerInput(false) }
        appsBottomTabs.background = ContextCompat.getDrawable(this, R.drawable.bg_auth_segment)
        appsTabIndicator.background = ContextCompat.getDrawable(this, R.drawable.bg_auth_indicator)
        findViewById<View>(R.id.appsSettings).background = ContextCompat.getDrawable(this, R.drawable.bg_circle_card)
        findViewById<View>(R.id.appsProfile).background = ContextCompat.getDrawable(this, R.drawable.bg_circle_card)
        findViewById<ImageView>(R.id.appsProfileAvatar).background = ContextCompat.getDrawable(this, R.drawable.bg_circle_card)
        selectAppsTab(appsTabIsApps, false)
        if (::adapter.isInitialized) adapter.refreshTheme(recycler)
        // Theme changes update the current surface in place; no feed request or data rebuild is made.
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is EditText) {
                val rect = Rect()
                focused.getGlobalVisibleRect(rect)
                if (!rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    focused.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(focused.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        composerHeightAnimator?.cancel()
        feedAnimator?.cancel()
        appsAnimator?.cancel()
        feedBarAnimator?.cancel()
        pullSpinnerAnimator?.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
