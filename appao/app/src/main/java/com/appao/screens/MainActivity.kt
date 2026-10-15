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
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.ProgressBar
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
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
    private lateinit var homeContentScroll: View
    private lateinit var homeQuickSites: GridLayout
    private lateinit var homeNewsSections: LinearLayout
    private lateinit var homeEmptyNews: View
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
    private lateinit var appsGrid: GridLayout
    private var homeNewsJob: Job? = null
    private lateinit var pullIndicator: PullRefreshIndicatorView
    private lateinit var homeFeedStack: View
    private lateinit var stackBack: ImageView
    private lateinit var stackFront: ImageView

    private val items = mutableListOf<NewsItem>()
    private val categories = mutableListOf(
        NewsCategory("Geral", "Geral"),
        NewsCategory("Economia", "Economia"),
        NewsCategory("Política", "Política"),
        NewsCategory("Desporto", "Desporto"),
        NewsCategory("Saúde", "Saúde"),
        NewsCategory("Ambiente", "Ambiente"),
        NewsCategory("Tecnologia", "Tecnologia"),
        NewsCategory("Energia", "Energia"),
        NewsCategory("Segurança", "Segurança"),
        NewsCategory("Justiça", "Justiça"),
        NewsCategory("Educação", "Educação"),
        NewsCategory("Protestos", "Protestos"),
        NewsCategory("Direitos Humanos", "Direitos Humanos"),
        NewsCategory("Negócios", "Negócios"),
        NewsCategory("Entretenimento", "Entretenimento")
    )

    private var currentCat = "Geral"
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
    private var feedStatusBarInset = 0
    private var feedAppsMode = false
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
        homeContentScroll = findViewById(R.id.homeContentScroll)
        homeQuickSites = findViewById(R.id.homeQuickSites)
        homeNewsSections = findViewById(R.id.homeNewsSections)
        homeEmptyNews = findViewById(R.id.homeEmptyNews)
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
        appsGrid = findViewById(R.id.appsGrid)
        pullIndicator = findViewById(R.id.pullIndicator)
        homeFeedStack = findViewById(R.id.homeFeedStack)
        stackBack = findViewById(R.id.stackBack)
        stackFront = findViewById(R.id.stackFront)

        restoreState(savedInstanceState)

        safeStartup { setupInsets() }
        safeStartup { setupHome() }
        safeStartup { populateHomeQuickSites() }
        safeStartup { setupFeed() }
        safeStartup { setupFeedHeader() }
        safeStartup { populateFeedApps() }
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
        currentCat = (state.getString("main.category", "Geral") ?: "Geral").let { saved ->
            if (saved == "Todas" || saved == "Para você" || saved == "Seguindo" || saved == "Internacional") "Geral" else saved
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
                        logo = o.optString("logo"),
                        category = o.optString("category").ifBlank { "Geral" }
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
                put("category", item.category)
            })
        }
    }.toString()

    private fun setupInsets() {
        val homeBottomBar = findViewById<View>(R.id.homeBottomBar)
        homeBottomBar.visibility = View.GONE
        ViewCompat.setOnApplyWindowInsetsListener(homeBottomBar) { view, insets ->
            view.visibility = View.GONE
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(biBar) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val keyboardVisible = imeBottom > systemBottom + dp(80)
            val params = view.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                params.bottomMargin = if (keyboardVisible) dp(2) else dp(8) + systemBottom
                view.layoutParams = params
            }
            (view as ViewGroup).setPadding(dp(8), dp(6), dp(8), dp(6))
            bottomBarHeight = view.measuredHeight
            insets
        }
        biBar.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> bottomBarHeight = v.height }
        ViewCompat.setOnApplyWindowInsetsListener(homeContentScroll) { view, insets ->
            val systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val params = view.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                params.bottomMargin = dp(76) + systemBottom
                view.layoutParams = params
            }
            insets
        }

        val appsBottomBar = findViewById<View>(R.id.appsBottomBar)
        val appsBottomBarBaseMargin = dp(16)
        ViewCompat.setOnApplyWindowInsetsListener(appsBottomBar) { view, insets ->
            val systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = appsBottomBarBaseMargin + systemBottom
            view.layoutParams = params
            insets
        }

        feedHeaderHeight = dp(104)
        recycler.updatePadding(top = feedHeaderHeight, bottom = dp(84))
        val feedBottom = findViewById<View>(R.id.feedBottomBar)
        ViewCompat.setOnApplyWindowInsetsListener(feedBottom) { view, insets ->
            val systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBottom)
            view.layoutParams = view.layoutParams.apply { height = dp(64) + systemBottom }
            recycler.updatePadding(bottom = dp(84) + systemBottom)
            insets
        }
        ViewCompat.requestApplyInsets(rootHost)
    }

    private fun setupHome() {
        homeHeader.visibility = View.GONE
        homeHeader.elevation = 0f
        homeHeader.translationZ = 0f
        homeFeedStack.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        homeFeedStack.foreground = null
        homeFeedStack.stateListAnimator = null
        homeFeedStack.isFocusable = false
        homeFeedStack.setOnClickListener { openFeedPanel() }
        findViewById<View>(R.id.homeMenu).setOnClickListener { openAppsPanel() }
        homeEmptyNews.visibility = View.GONE
        findViewById<ImageView>(R.id.homeEmptyNewsIcon).setImageDrawable(EmptyNewsIllustrationDrawable(this))

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
            showTopCards = false,
            onArticleMenu = { item, anchor -> showArticleCardMenu(item, anchor) },
            isSaved = { item -> isArticleSaved(item) },
            onToggleSaved = { item ->
                toggleArticleSaved(item)
                if (::adapter.isInitialized) adapter.notifyDataSetChanged()
            }
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
        recycler.translationY = 0f
    }

    private fun setupFeedHeader() {
        feedHeader.elevation = 0f
        feedHeader.translationZ = 0f
        IconLoader.applyIcon(findViewById(R.id.feedBackIcon), "arrow_left", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.feedPublishIcon), "write", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.feedMoreIcon), "more_vert", R.color.iconTint)

        findViewById<TextView>(R.id.feedToolbarTitle).apply {
            text = "Descobrir"
            visibility = View.VISIBLE
        }
        findViewById<View>(R.id.feedBack).setOnClickListener { closeFeedPanel() }
        findViewById<View>(R.id.feedPublish).setOnClickListener { openPublish() }
        findViewById<View>(R.id.feedMore).setOnClickListener { showFeedMorePopup(it) }
        setFeedBottomSelection(discover = true)
        findViewById<View>(R.id.feedNavDiscover).setOnClickListener {
            setFeedBottomSelection(discover = true)
            showFeedAppsTab(false)
            recycler.smoothScrollToPosition(0)
            openFeedPanelImmediate()
        }
        findViewById<View>(R.id.feedNavApps).setOnClickListener {
            setFeedBottomSelection(discover = false)
            showFeedAppsTab(true)
        }

        // feedToolbar previously had no status-bar inset handling, so
        // feedBack ("voltar" on the Descobrir panel) sat flush under the
        // clock/battery icons. Push the toolbar down by the real status
        // bar inset and grow the header + recycler top padding to match,
        // same pattern as aTopBar in ArticleActivity.
        val feedToolbar = findViewById<View>(R.id.feedToolbar)
        val feedToolbarContentHeight = dp(56)
        val feedHeaderBaseHeight = dp(104)
        ViewCompat.setOnApplyWindowInsetsListener(feedHeader) { view, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            feedToolbar.updatePadding(top = statusBarTop)
            feedToolbar.layoutParams = feedToolbar.layoutParams.apply {
                height = feedToolbarContentHeight + statusBarTop
            }
            feedStatusBarInset = statusBarTop
            feedHeaderHeight = feedHeaderBaseHeight + statusBarTop
            view.layoutParams = view.layoutParams.apply {
                height = feedHeaderHeight
            }
            recycler.updatePadding(top = feedHeaderHeight)
            insets
        }

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
            val indicatorTrack = FrameLayout(this).apply {
                clipChildren = false
                clipToPadding = false
            }
            val indicator = View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dp(99).toFloat()
                    setColor(AccentColorManager.resolvedOr(this@MainActivity, R.color.pri))
                }
                alpha = if (label == currentCat) 1f else 0f
                scaleX = if (label == currentCat) 1f else 0.72f
            }
            indicatorTrack.addView(indicator, FrameLayout.LayoutParams(dp(1), dp(3), Gravity.CENTER))
            tab.addView(indicatorTrack, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(5)))
            tv.post {
                // Measure the actual label, not the tab container. This prevents
                // zero/undersized underlines when tabs are rebuilt during a swipe.
                val labelWidth = tv.paint.measureText(tv.text.toString()).toInt().coerceAtLeast(dp(24))
                indicatorTrack.layoutParams = (indicatorTrack.layoutParams as LinearLayout.LayoutParams).apply {
                    width = labelWidth
                    height = dp(5)
                }
                indicator.layoutParams = (indicator.layoutParams as FrameLayout.LayoutParams).apply {
                    width = labelWidth
                    height = dp(3)
                }
                if (label == currentCat) {
                    indicator.animate().cancel()
                    indicator.scaleX = 0.78f
                    indicator.alpha = 0f
                    indicator.animate().scaleX(1f).alpha(1f).setDuration(340L).setInterpolator(Curves.SMOOTH).start()
                } else {
                    indicator.animate().cancel()
                    indicator.alpha = 0f
                }
            }

            tab.setOnClickListener {
                if (label != currentCat) {
                    currentCat = label
                    updateFeedCategoryAndReload(true)
                }
            }

            feedTabsHost.addView(tab, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        // The category control is an uncontained pencil, not a plus/circle button.
        val plus = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginStart = dp(6) }
            background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            contentDescription = "Editar categorias"
        }
        val plusIcon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        IconLoader.applyIcon(plusIcon, "write", R.color.iconTint)
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
        categories.firstOrNull { it.label == currentCat }?.label ?: "Geral"

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

    private data class HomeSite(val label: String, val url: String = "", val internal: String = "")

    private fun populateHomeQuickSites() {
        if (!::homeQuickSites.isInitialized) return
        homeQuickSites.removeAllViews()
        homeQuickSites.columnCount = 5
        val sites = listOf(
            HomeSite("Google", "https://www.google.com"),
            HomeSite("YouTube", "https://m.youtube.com"),
            HomeSite("Facebook", "https://m.facebook.com"),
            HomeSite("Wikipedia", "https://www.wikipedia.org"),
            HomeSite("X", "https://x.com"),
            HomeSite("GitHub", "https://github.com"),
            HomeSite("LinkedIn", "https://www.linkedin.com"),
            HomeSite("Google News", "https://news.google.com"),
            HomeSite("Downloads", internal = "downloads"),
            HomeSite("Mais", internal = "more")
        )
        sites.forEachIndexed { index, site ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                isFocusable = true
                setPadding(dp(2), dp(6), dp(2), dp(7))
                setBackgroundResource(R.drawable.bg_transparent_pressed)
                setOnClickListener {
                    when (site.internal) {
                        "downloads" -> startActivity(Intent(this@MainActivity, BrowserDownloadsActivity::class.java))
                        "more" -> showMoreQuickSites(this)
                        else -> openNewBrowserTab(site.url)
                    }
                }
            }
            val iconBox = FrameLayout(this).apply {
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_circle_card)
                clipToOutline = true
            }
            val icon = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            if (site.url.isNotBlank()) {
                val favicon = BrowserTabStore.faviconFor(site.url)
                Glide.with(this).load(favicon).circleCrop().placeholder(R.drawable.logo).error(R.drawable.logo).into(icon)
            } else {
                IconLoader.applyIcon(icon, if (site.internal == "downloads") "download" else "plus", R.color.iconTint)
            }
            iconBox.addView(icon, FrameLayout.LayoutParams(-1, -1))
            tile.addView(iconBox, LinearLayout.LayoutParams(dp(44), dp(44)))
            tile.addView(TextView(this).apply {
                text = site.label
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text))
                setPadding(0, dp(5), 0, 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / 5, 1, 1f),
                GridLayout.spec(index % 5, 1, 1f)
            ).apply { width = 0; height = dp(82); setMargins(dp(1), dp(1), dp(1), dp(1)) }
            homeQuickSites.addView(tile, params)
        }
    }

    private fun showMoreQuickSites(anchor: View) {
        NativePopupMenu.show(anchor, listOf(
            NativePopupMenu.Item(1, "Reddit", iconName = "chat"),
            NativePopupMenu.Item(2, "BBC News", iconName = "newspaper"),
            NativePopupMenu.Item(3, "Reuters", iconName = "newspaper"),
            NativePopupMenu.Item(4, "Downloads", iconName = "download")
        ), alignLeft = false, placeAbove = true) { item ->
            when (item.id) {
                1 -> openNewBrowserTab("https://www.reddit.com")
                2 -> openNewBrowserTab("https://www.bbc.com/news")
                3 -> openNewBrowserTab("https://www.reuters.com")
                4 -> startActivity(Intent(this, BrowserDownloadsActivity::class.java))
            }
        }
    }

    private fun loadHomeNewsSections(sourceCategories: List<NewsCategory>) {
        homeNewsJob?.cancel()
        if (!::homeNewsSections.isInitialized) return
        homeEmptyNews.visibility = View.GONE
        homeNewsSections.removeAllViews()
        homeNewsSections.addView(TextView(this).apply {
            text = "A carregar notícias…"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.dim))
            setPadding(0, dp(14), 0, dp(20))
        })

        // Fetch by exact category, rather than grouping only the first few global pages.
        // This guarantees a fair category sample and caps every section at 20 articles.
        val chosen = sourceCategories
            .distinctBy { it.label.lowercase(Locale.ROOT) }
            .ifEmpty { NewsRepository.categoriesSnapshot() }

        homeNewsJob = lifecycleScope.launch {
            val categoryResults = runCatching {
                supervisorScope {
                    chosen.map { category ->
                        async(Dispatchers.IO) {
                            val articles = if (category.label.equals("Geral", ignoreCase = true)) {
                                NewsRepository.fetchGeneral(page = 1, limit = 20)
                            } else {
                                NewsRepository.fetchByCategory(category.label, page = 1, country = "", limit = 20)
                            }
                            category to articles.distinctBy { it.id.ifBlank { normalizeUrl(it.link) } }.take(20)
                        }
                    }.awaitAll()
                }
            }.getOrDefault(emptyList())

            if (isFinishing || isDestroyed) return@launch
            homeNewsSections.removeAllViews()
            var displayed = 0

            categoryResults.forEach { (category, categoryItems) ->
                if (categoryItems.isEmpty()) return@forEach
                displayed += categoryItems.size

                val header = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(1), dp(9), dp(1), dp(5))
                }
                header.addView(TextView(this@MainActivity).apply {
                    text = category.label
                    textSize = 18f
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                homeNewsSections.addView(header)

                val horizontal = HorizontalScrollView(this@MainActivity).apply {
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    clipToPadding = false
                }
                val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; clipToPadding = false }
                categoryItems.forEach { item -> row.addView(createHomeNewsCard(item)) }
                horizontal.addView(row)
                homeNewsSections.addView(horizontal, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

                val continueButton = TextView(this@MainActivity).apply {
                    text = "Continuar a ver ${category.label}  ›"
                    textSize = 13f
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    setTextColor(AccentColorManager.resolvedOr(this@MainActivity, R.color.pri))
                    setPadding(dp(2), dp(10), dp(2), dp(12))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        currentCat = categories.firstOrNull { it.label.equals(category.label, true) }?.label ?: category.label
                        page = 1
                        exhausted = false
                        openFeedPanel()
                        updateFeedCategoryAndReload(true)
                    }
                }
                homeNewsSections.addView(continueButton)
            }

            if (displayed == 0) {
                homeEmptyNews.visibility = View.VISIBLE
                homeNewsSections.removeAllViews()
            } else {
                homeEmptyNews.visibility = View.GONE
            }
        }
    }

    private fun createHomeNewsCard(item: NewsItem): View {
        val density = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_news_text_card)
            setPadding(dp(8), dp(8), dp(8), dp(10))
            isClickable = true; isFocusable = true
            setOnClickListener { openArticle(item, this) }
        }
        val imageHost = FrameLayout(this).apply {
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_news_image_card)
            clipToOutline = true
        }
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true }
        if (item.image.isNotBlank()) {
            Glide.with(this).load(item.image).centerCrop().placeholder(R.drawable.logo).error(R.drawable.logo).into(image)
        } else {
            IconLoader.applyIcon(image, "newspaper", R.color.iconTint)
            image.scaleType = ImageView.ScaleType.CENTER_INSIDE
            image.setPadding(dp(38), dp(28), dp(38), dp(28))
            image.setBackgroundColor(ContextCompat.getColor(this, R.color.card2))
        }
        imageHost.addView(image, FrameLayout.LayoutParams(-1, -1))
        card.addView(imageHost, LinearLayout.LayoutParams(dp(226), dp(118)))
        card.addView(TextView(this).apply {
            text = item.title
            textSize = 14f
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text))
            setPadding(dp(1), dp(8), dp(1), dp(4))
        }, LinearLayout.LayoutParams(dp(226), ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(TextView(this).apply {
            text = item.source
            textSize = 11.5f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.dim))
            setPadding(dp(1), dp(2), dp(1), 0)
        }, LinearLayout.LayoutParams(dp(226), ViewGroup.LayoutParams.WRAP_CONTENT))
        return card.also { it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10); bottomMargin = dp(6) } }
    }

    private fun showFeedMorePopup(anchor: View) {
        NativePopupMenu.show(
            anchor,
            listOf(
                NativePopupMenu.Item(1, "Recarregar", iconName = "refresh"),
                NativePopupMenu.Item(2, "Editar categorias", iconName = "write"),
                NativePopupMenu.Item(3, "Guardados", iconName = "bookmark")
            )
        ) { item ->
            when (item.id) {
                1 -> loadNews(true)
                2 -> showCategoryManager()
                3 -> startActivity(Intent(this, LibraryActivity::class.java).putExtra("source", "saved"))
            }
        }
    }

    private fun showCategoryManager() {
        startActivity(Intent(this, CategoryManagerActivity::class.java))
        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
    }

    private fun setupBottomInput() {
        // Always keep one blank browser tab available on first launch, like a real browser.
        if (BrowserTabStore.tabs(this).isEmpty()) BrowserTabStore.create(this, "https://duckduckgo.com/")
        // The home bottom bar intentionally reuses the same geometry and controls as the browser address bar.
        IconLoader.applyIcon(findViewById(R.id.biAddIcon), "plus", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.biFaviconIcon), "global", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.biSendIcon), "sparkles", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.biFireIcon), "flame", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.biMoreIcon), "menu", R.color.iconTint)
        biInput.setSingleLine(true)
        biInput.maxLines = 1
        biInput.setHorizontallyScrolling(true)
        biInput.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        biInput.includeFontPadding = true
        biInput.setPadding(0, 0, 0, 0)
        biInput.isVerticalScrollBarEnabled = false
        biInput.overScrollMode = View.OVER_SCROLL_NEVER
        biInput.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
        biInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        biInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateComposerSendState()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        biInput.setOnFocusChangeListener { _, focused ->
            val lp = biBar.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null) {
                val target = dp(if (focused) 0 else 5)
                lp.marginStart = target
                lp.marginEnd = target
                biBar.layoutParams = lp
            }
            biPill.animate().cancel()
            biPill.animate().scaleX(if (focused) 1.01f else 1f)
                .setDuration(320L).setInterpolator(Curves.SMOOTH).start()
        }
        // Address submit always opens the in-app browser. AI is a separate sparkle action.
        biInput.setOnEditorActionListener { _, _, _ -> openBrowserFromInput(); true }
        biPill.setOnClickListener { biInput.requestFocus() }
        biSend.setOnClickListener { openAiFromHome() }
        findViewById<View>(R.id.biAdd).setOnClickListener { showAppsPopup(it) }
        findViewById<View>(R.id.biFire).setOnClickListener { showClearBrowsingFromHome(it) }
        findViewById<View>(R.id.biTabs).setOnClickListener { openBrowserTabs() }
        findViewById<View>(R.id.biMore).setOnClickListener { showHomeBrowserMenu(it) }
        applyComposerSurface()
        updateComposerExpanded(false)
        updateBrowserTabCount()
    }

    private fun updateComposerExpanded(focused: Boolean) {
        if (!::biInput.isInitialized) return
        biInput.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        biPill.minimumHeight = dp(46)
    }

    private fun updateComposerSendState() {
        biSend.background = ContextCompat.getDrawable(this, R.drawable.bg_transparent_pressed)
        IconLoader.applyIcon(findViewById(R.id.biSendIcon), "sparkles", R.color.iconTint)
    }

    private fun resizeComposerInput(animate: Boolean) {
        if (!::biInput.isInitialized) return
        val lp = biInput.layoutParams
        if (lp.height != dp(46)) { lp.height = dp(46); biInput.layoutParams = lp }
        biInput.isVerticalScrollBarEnabled = false
        biInput.overScrollMode = View.OVER_SCROLL_NEVER
    }

    private fun applyComposerSurface() {
        biBar.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        biPill.background = ContextCompat.getDrawable(this, R.drawable.bg_toolbar_actions)
        biInput.setTextColor(ContextCompat.getColor(this, R.color.text))
        biInput.setHintTextColor(ContextCompat.getColor(this, R.color.dim))
        updateComposerSendState()
    }

    private fun setupAppsPanel() {
        if (appsReady) return
        appsReady = true
        val appsHeader = findViewById<View>(R.id.appsHeaderBar)
        appsHeader.elevation = 0f
        appsHeader.translationZ = 0f
        findViewById<ImageView>(R.id.appsProfileAvatar).setImageResource(R.mipmap.ic_launcher_round)
        IconLoader.applyIcon(findViewById(R.id.appsHistoryIcon), "history", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.appsBackIcon), "arrow_right", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.appsSettingsIcon), "settings", R.color.iconTint)
        findViewById<View>(R.id.appsProfile).visibility = View.GONE
        findViewById<View>(R.id.appsSearch).visibility = View.GONE
        findViewById<View>(R.id.appsNewChat).visibility = View.GONE
        findViewById<View>(R.id.appsBack).setOnClickListener { closeAppsPanel() }
        findViewById<View>(R.id.appsSettings).setOnClickListener {
            startActivity(Intent(this, HistorySettingsActivity::class.java))
            overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
        }
        val pagesTab = findViewById<TextView>(R.id.appsTab)
        val conversationsTab = findViewById<TextView>(R.id.conversationsTab)
        pagesTab.text = "Páginas"
        conversationsTab.text = "Conversas"
        pagesTab.setOnClickListener { selectAppsTab(true, true) }
        conversationsTab.setOnClickListener { selectAppsTab(false, true) }
        populateAppsGrid()
        refreshConversationHistory()
        appsBottomTabs.post { selectAppsTab(appsTabIsApps, false) }
    }

    private fun selectAppsTab(apps: Boolean, animated: Boolean) {
        appsTabIsApps = apps
        appsContent.visibility = if (apps) View.VISIBLE else View.GONE
        conversationsContent.visibility = if (apps) View.GONE else View.VISIBLE
        val pagesTab = findViewById<TextView>(R.id.appsTab)
        val conversationsTab = findViewById<TextView>(R.id.conversationsTab)
        val active = AccentColorManager.onPrimaryColor(this)
        val inactive = ContextCompat.getColor(this, R.color.dim)
        pagesTab.setTextColor(if (apps) active else inactive)
        conversationsTab.setTextColor(if (!apps) active else inactive)
        appsTabIndicator.background = AccentColorManager.primaryShape(this, android.graphics.drawable.GradientDrawable.RECTANGLE, 999f)
        val available = appsBottomTabs.width - appsBottomTabs.paddingLeft - appsBottomTabs.paddingRight
        val tabHeight = appsBottomTabs.height - appsBottomTabs.paddingTop - appsBottomTabs.paddingBottom
        if (appsBottomTabs.width <= 0 || appsBottomTabs.height <= 0 || available <= dp(40) || tabHeight <= dp(16)) return
        val tabWidth = available / 2
        appsTabIndicator.layoutParams = (appsTabIndicator.layoutParams as FrameLayout.LayoutParams).apply { width = tabWidth; height = tabHeight; leftMargin = 0; topMargin = 0 }
        pagesTab.layoutParams = (pagesTab.layoutParams as FrameLayout.LayoutParams).apply { width = tabWidth; height = tabHeight; leftMargin = 0; topMargin = 0 }
        conversationsTab.layoutParams = (conversationsTab.layoutParams as FrameLayout.LayoutParams).apply { width = tabWidth; height = tabHeight; leftMargin = tabWidth; topMargin = 0 }
        appsTabIndicator.visibility = View.VISIBLE
        appsTabIndicator.alpha = 1f
        appsTabIndicator.animate().cancel()
        val target = if (apps) 0f else tabWidth.toFloat()
        if (animated) appsTabIndicator.animate().translationX(target).setDuration(320L).setInterpolator(Curves.SMOOTH).start()
        else appsTabIndicator.translationX = target
        if (apps) populateAppsGrid() else refreshConversationHistory()
    }

    private fun refreshAppsTabsAfterLayout() {
        val bottomBar = findViewById<View>(R.id.appsBottomBar)
        bottomBar.bringToFront()
        appsBottomTabs.post {
            if (appsPanel.visibility == View.VISIBLE && appsBottomTabs.width > dp(80) && appsBottomTabs.height > dp(20)) {
                selectAppsTab(appsTabIsApps, false)
            }
        }
    }

    private fun populateAppsGrid() {
        if (!::appsGrid.isInitialized) return
        appsGrid.removeAllViews()
        val history = BrowsingHistoryStore.all(this)
        if (history.isEmpty()) {
            val empty = TextView(this).apply {
                text = "As páginas visitadas aparecerão aqui.\n\nAbre uma página no browser para começar o histórico."
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.dim))
                setPadding(dp(20), dp(44), dp(20), dp(44))
            }
            appsGrid.addView(empty, GridLayout.LayoutParams().apply { width = ViewGroup.LayoutParams.MATCH_PARENT; height = ViewGroup.LayoutParams.WRAP_CONTENT; columnSpec = GridLayout.spec(0, 1f) })
            return
        }
        history.take(300).forEachIndexed { index, entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_settings_single)
                setPadding(dp(12), dp(10), dp(10), dp(10))
                isClickable = true; isFocusable = true
                setOnClickListener { openNewBrowserTab(entry.url); closeAppsPanel() }
            }
            val icon = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
            val faviconUrl = entry.favicon.ifBlank { BrowserTabStore.faviconFor(entry.url) }
            if (faviconUrl.isNotBlank()) Glide.with(this).load(faviconUrl).circleCrop().placeholder(R.drawable.logo).into(icon)
            else IconLoader.applyIcon(icon, "global", R.color.iconTint)
            row.addView(icon, LinearLayout.LayoutParams(dp(34), dp(34)))
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
            labels.addView(TextView(this).apply {
                text = entry.title; textSize = 14f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text))
            })
            labels.addView(TextView(this).apply {
                text = "${android.net.Uri.parse(entry.url).host.orEmpty().removePrefix("www.")} · ${historyTime(entry.visitedAt)}"
                textSize = 12f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.dim))
                setPadding(0, dp(3), 0, 0)
            })
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val params = GridLayout.LayoutParams().apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT; height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(0, 1f); setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            appsGrid.addView(row, params)
        }
    }

    private fun refreshConversationHistory() {
        val column = findViewById<LinearLayout>(R.id.conversationsHistoryColumn)
        val empty = findViewById<TextView>(R.id.conversationsEmpty)
        for (i in column.childCount - 1 downTo 1) column.removeViewAt(i)
        val entries = ConversationHistoryStore.all(this)
        empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        entries.take(200).forEach { entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_settings_single)
                setPadding(dp(13), dp(12), dp(13), dp(12))
                isClickable = true; isFocusable = true
                setOnClickListener { startActivity(Intent(this@MainActivity, SearchActivity::class.java).putExtra("query", entry.query)); overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit) }
            }
            row.addView(ImageView(this).apply { IconLoader.applyIcon(this, "chat_history", R.color.iconTint) }, LinearLayout.LayoutParams(dp(22), dp(22)))
            row.addView(TextView(this).apply {
                text = entry.title; textSize = 14f; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text)); setPadding(dp(11),0,0,0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            column.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(3) })
        }
    }

    private fun historyTime(value: Long): String {
        if (value <= 0L) return ""
        val elapsed = (System.currentTimeMillis() - value).coerceAtLeast(0L)
        return when {
            elapsed < 60_000L -> "agora"
            elapsed < 3_600_000L -> "há ${elapsed / 60_000L} min"
            elapsed < 86_400_000L -> "há ${elapsed / 3_600_000L} h"
            else -> java.text.SimpleDateFormat("dd/MM", Locale.getDefault()).format(java.util.Date(value))
        }
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
        setFeedBottomSelection(discover = !feedAppsMode)
        showFeedAppsTab(feedAppsMode)
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
        setFeedBottomSelection(discover = false)
        feedPanel.visibility = View.GONE
        appsPanel.visibility = View.VISIBLE
        refreshAppsTabsAfterLayout()
        setAppsProgress(1f)
    }

    private fun openFeedPanel(gestureProgress: Float? = null) {
        if (isFinishing || isDestroyed) return
        feedAppsMode = false
        showFeedAppsTab(false)
        setFeedBottomSelection(discover = true)
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
            duration = 520L
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
        setFeedBottomSelection(discover = false)
        appsOpen = true
        feedOpen = false
        feedPanel.visibility = View.GONE
        appsPanel.visibility = View.VISIBLE
        refreshAppsTabsAfterLayout()
        populateAppsGrid()
        refreshConversationHistory()
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
            duration = 520L
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
            homeEmptyNews.visibility = View.GONE
            homeNewsSections.removeAllViews()
            homeNewsSections.addView(TextView(this@MainActivity).apply {
                text = "A carregar notícias…"
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.dim))
                setPadding(0, dp(14), 0, dp(20))
            })
            val cached = withContext(Dispatchers.IO) {
                NewsRepository.readCategoryCache(this@MainActivity, "Geral")
                    .ifEmpty { NewsRepository.readCategoryCache(this@MainActivity, "world") }
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
                    currentCat = categories.firstOrNull { it.label == "Geral" }?.label ?: categories.firstOrNull()?.label ?: "Geral"
                    page = 1
                    exhausted = false
                    loadNews(false)
                }
                refreshFeedTabs(false)
                loadHomeNewsSections(liveCategories)
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
        recycler.updatePadding(top = feedHeaderHeight.takeIf { it > 0 } ?: dp(104))
        recycler.translationY = 0f
    }

    private suspend fun fetchFeedCategory(category: String, pageNumber: Int): List<NewsItem> {
        val serverCategory = categories.firstOrNull { it.label == category }
        return NewsRepository.fetchByCategory(serverCategory?.label ?: category, pageNumber)
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
                if (categoryId == "Geral") NewsRepository.writeCache(this@MainActivity, clean)
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
            if (categoryIdAtStart == "Geral") NewsRepository.writeCache(this@MainActivity, items)
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

    private fun openBrowserFromInput() {
        val query = biInput.text.toString().trim()
        if (query.isEmpty()) {
            biInput.requestFocus()
            biInput.post { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(biInput, InputMethodManager.SHOW_IMPLICIT) }
            return
        }
        val target = normalizeBrowserTarget(query)
        biInput.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(biInput.windowToken, 0)
        biInput.setText("")
        openNewBrowserTab(target)
    }

    private fun normalizeBrowserTarget(value: String): String {
        val input = value.trim()
        if (input.isBlank()) return "https://duckduckgo.com/"
        if (input.startsWith("http://", true) || input.startsWith("https://", true)) return input
        val looksLikeAddress = !input.contains(' ') && input.contains('.') && !input.startsWith(".") && input.substringAfterLast('.', "").length in 2..20
        return if (looksLikeAddress) "https://$input" else "https://duckduckgo.com/?q=" + android.net.Uri.encode(input)
    }

    private fun openAiFromHome() {
        val query = biInput.text.toString().trim()
        if (query.isNotBlank()) ConversationHistoryStore.record(this, query)
        startActivity(Intent(this, SearchActivity::class.java).putExtra("query", query))
        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
    }

    private fun updateBrowserTabCount() {
        val count = BrowserTabStore.tabs(this).size.coerceAtMost(99).toString()
        findViewById<TextView>(R.id.biTabsCount).text = count
    }

    private fun showHomeBrowserMenu(anchor: View) {
        val adBlock = BrowserPreferences.adBlockEnabled(this)
        val forcedDark = BrowserPreferences.forceDarkEnabled(this)
        NativePopupMenu.show(anchor, listOf(
            NativePopupMenu.Item(1, "Novo separador", iconName = "plus"),
            NativePopupMenu.Item(2, "Todos os separadores", iconName = "apps"),
            NativePopupMenu.Item(3, "Downloads", iconName = "download"),
            NativePopupMenu.Item(4, "Histórico", iconName = "history"),
            NativePopupMenu.Item(5, "Nova conversa", iconName = "edit_square"),
            NativePopupMenu.Item(6, "Perfil", iconName = "profile"),
            NativePopupMenu.Item(7, "Definições", iconName = "settings"),
            NativePopupMenu.Item(8, if (adBlock) "Bloqueador de anúncios: ligado" else "Bloqueador de anúncios: desligado", iconName = "shield_check", checked = adBlock),
            NativePopupMenu.Item(9, if (forcedDark) "Forçar tema escuro: ligado" else "Forçar tema escuro: desligado", iconName = "moon", checked = forcedDark),
            NativePopupMenu.Item(10, "Limpar dados de navegação", iconName = "delete_bin")
        ), alignLeft = false, placeAbove = true) { item ->
            when (item.id) {
                1 -> openNewBrowserTab()
                2 -> openBrowserTabs()
                3 -> startActivity(Intent(this, BrowserDownloadsActivity::class.java))
                4 -> openAppsPanel()
                5 -> { ConversationHistoryStore.record(this, "Nova conversa"); startActivity(Intent(this, SearchActivity::class.java)); overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit) }
                6 -> startActivity(Intent(this, AuthActivity::class.java))
                7 -> startActivity(Intent(this, SettingsActivity::class.java))
                8 -> { BrowserPreferences.setAdBlockEnabled(this, !adBlock); AppAoToast.show(this, if (!adBlock) "Bloqueador de anúncios ativado" else "Bloqueador de anúncios desativado") }
                9 -> { BrowserPreferences.setForceDarkEnabled(this, !forcedDark); AppAoToast.show(this, if (!forcedDark) "Tema escuro de sites ativado" else "Tema escuro de sites desativado") }
                10 -> showClearBrowsingFromHome(anchor)
            }
        }
    }

    private fun showClearBrowsingFromHome(anchor: View) {
        NativeM3Dialog.confirm(this, "Limpar navegação", "Limpar páginas visitadas e fechar separadores guardados?") { accepted ->
            if (accepted) {
                BrowsingHistoryStore.clear(this)
                BrowserTabStore.clear(this)
                BrowserTabStore.create(this, "https://duckduckgo.com/")
                updateBrowserTabCount()
                populateAppsGrid()
                AppAoToast.show(this, "Histórico limpo; uma nova página está pronta")
            }
        }
    }

    private fun openBrowserTabs() {
        startActivity(Intent(this, BrowserTabsActivity::class.java))
        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
    }

    private fun openNewBrowserTab(url: String = "https://duckduckgo.com/") {
        val tab = BrowserTabStore.create(this, url)
        startActivity(Intent(this, AppViewerActivity::class.java).apply {
            putExtra("browser_mode", true)
            putExtra("browser_tab_id", tab.id)
            putExtra("url", tab.url)
        })
        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
    }

    private fun clearBrowserSessions() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Limpar separadores")
            .setMessage("Queres fechar todos os separadores guardados do browser?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Limpar") { _, _ ->
                BrowserTabStore.clear(this)
                updateBrowserTabCount()
                AppAoToast.show(this, "Separadores fechados")
            }
            .show()
    }

    private fun showFeedAppsTab(showApps: Boolean) {
        feedAppsMode = showApps
        findViewById<View>(R.id.feedAppsContent).apply {
            visibility = if (showApps) View.VISIBLE else View.GONE
            (layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.topMargin = (if (showApps) dp(56) else dp(104)) + feedStatusBarInset
                layoutParams = lp
            }
        }
        recycler.visibility = if (showApps) View.GONE else View.VISIBLE
        feedTabsScroll.visibility = if (showApps) View.GONE else View.VISIBLE
        feedHeader.layoutParams = feedHeader.layoutParams.apply {
            height = (if (showApps) dp(56) else dp(104)) + feedStatusBarInset
        }
        feedHeaderHeight = (if (showApps) dp(56) else dp(104)) + feedStatusBarInset
        recycler.updatePadding(top = feedHeaderHeight)
        findViewById<TextView>(R.id.feedToolbarTitle).text = if (showApps) "Apps" else "Descobrir"
    }

    private fun populateFeedApps() {
        val list = findViewById<LinearLayout>(R.id.feedAppsList)
        list.removeAllViews()
        data class Shortcut(val title: String, val subtitle: String, val icon: String, val action: () -> Unit)
        val shortcuts = listOf(
            Shortcut("Assistente", "Pesquisar na Web", "sparkles") {
                closeFeedPanel()
                biInput.requestFocus()
                biInput.post { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(biInput, InputMethodManager.SHOW_IMPLICIT) }
            },
            Shortcut("Navegador", "Abrir uma página", "search") { openNewBrowserTab() },
            Shortcut("Guardados", "Artigos guardados", "bookmark") { startActivity(Intent(this, LibraryActivity::class.java)) },
            Shortcut("Atividade", "Histórico de utilização", "chart_bars") { startActivity(Intent(this, UsageActivity::class.java)) },
            Shortcut("Definições", "Personalizar a aplicação", "settings") { startActivity(Intent(this, SettingsActivity::class.java)) }
        )
        shortcuts.forEach { shortcut ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(13), dp(14), dp(13))
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_drawer_item)
                isClickable = true
                isFocusable = true
                setOnClickListener { shortcut.action() }
            }
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                IconLoader.applyIcon(this, shortcut.icon, R.color.iconTint)
            }
            row.addView(icon)
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(TextView(this).apply {
                text = shortcut.title
                textSize = 15.5f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text))
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            })
            labels.addView(TextView(this).apply {
                text = shortcut.subtitle
                textSize = 12.5f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.dim))
            })
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
        }
    }

    private fun setFeedBottomSelection(discover: Boolean) {
        if (!::feedPanel.isInitialized) return
        runCatching {
            IconLoader.applyIcon(findViewById(R.id.feedNavDiscoverIcon), if (discover) "compass_filled" else "compass", R.color.iconTint)
            IconLoader.applyIcon(findViewById(R.id.feedNavAppsIcon), if (discover) "apps" else "apps_filled", R.color.iconTint)
            findViewById<TextView>(R.id.feedNavDiscoverLabel).setTextColor(ContextCompat.getColor(this, if (discover) R.color.text else R.color.dim))
            findViewById<TextView>(R.id.feedNavAppsLabel).setTextColor(ContextCompat.getColor(this, if (discover) R.color.dim else R.color.text))
        }
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
                    startActivityForResult(
                        Intent(this, SearchActivity::class.java).putExtra("gallery_mode", true),
                        902
                    )
                    overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
                }
                "camera" -> {
                    startActivityForResult(
                        Intent(this, SearchActivity::class.java).putExtra("camera_mode", true),
                        903
                    )
                    overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
                }
                "think" -> {
                    val active = AppsPopup.toggleThink(this)
                    AppAoToast.show(this, if (active) "Pensar mais ativado" else "Pensar mais desativado")
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
        // Instagram-style refresh: the toolbar and category bar stay pinned.
        // Only the news content is elastically pushed down, revealing a gap for the loader.
        val headerHeight = (feedHeaderHeight.takeIf { it > 0 } ?: dp(104)).toFloat()
        val topY = headerHeight + (y * 0.34f) - dp(14).toFloat()

        pullIndicator.alpha = zoom
        pullIndicator.scaleX = zoom
        pullIndicator.scaleY = zoom
        pullIndicator.translationY = topY

        if (!pullRefreshing) {
            pullRotation = (y / threshold).coerceIn(0f, 1f) * 300f
            pullIndicator.rotationDegrees = pullRotation
        }

        recycler.translationY = y.coerceAtLeast(0f)
        feedPanel.translationX = if (feedOpen) 0f else (rootHost.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels).toFloat()
        // AppBar remains in place while pulling; normal scrolling still hides it via updateFeedBarsFromScroll().
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

    @Deprecated("Uses legacy result callbacks to remain compatible with the existing Activity structure")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == 902 || requestCode == 903) && resultCode == RESULT_OK) {
            val selected = data?.getStringArrayListExtra("image_uris").orEmpty()
            val single = data?.getStringExtra("image_uri") ?: data?.data?.toString()
            val all = selected.ifEmpty { listOfNotNull(single) }
            if (all.isNotEmpty()) {
                getSharedPreferences("appao", MODE_PRIVATE).edit()
                    .putString("pending_media_uri", all.first())
                    .putString("pending_media_uris_json", JSONArray(all).toString())
                    .apply()
                AppAoToast.show(this, if (requestCode == 903) "Imagem da câmara selecionada" else if (all.size > 1) "${all.size} imagens selecionadas" else "Imagem selecionada")
            }
        }
    }

    private fun openPublish() {
        startActivity(Intent(this, PublishActivity::class.java))
        overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
    }

    private fun isArticleSaved(item: NewsItem): Boolean {
        if (item.link.isBlank()) return false
        val raw = getSharedPreferences("appao", MODE_PRIVATE).getString("library_json", "[]") ?: "[]"
        return runCatching {
            val saved = JSONArray(raw)
            (0 until saved.length()).any { i ->
                normalizeUrl(saved.optJSONObject(i)?.optString("link").orEmpty()) == normalizeUrl(item.link)
            }
        }.getOrDefault(false)
    }

    private fun toggleArticleSaved(item: NewsItem) {
        val prefs = getSharedPreferences("appao", MODE_PRIVATE)
        val original = runCatching { JSONArray(prefs.getString("library_json", "[]") ?: "[]") }.getOrElse { JSONArray() }
        val target = normalizeUrl(item.link)
        val wasSaved = isArticleSaved(item)
        val updated = JSONArray()
        for (i in 0 until original.length()) {
            val obj = original.optJSONObject(i) ?: continue
            if (wasSaved && normalizeUrl(obj.optString("link")) == target) continue
            updated.put(obj)
        }
        if (!wasSaved) updated.put(JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("summary", item.summary)
            put("link", item.link)
            put("source", item.source)
            put("date", item.date)
            put("image", item.image)
            put("logo", item.logo)
            put("category", item.category)
        })
        prefs.edit().putString("library_json", updated.toString()).apply()
        AppAoToast.show(this, if (wasSaved) "Removido dos guardados" else "Notícia guardada")
    }

    private fun showArticleCardMenu(item: NewsItem, anchor: View) {
        NativePopupMenu.show(
            anchor,
            listOf(
                NativePopupMenu.Item(1, "Partilhar", iconName = "share"),
                NativePopupMenu.Item(2, if (isArticleSaved(item)) "Remover dos guardados" else "Guardar", iconName = "bookmark"),
                NativePopupMenu.Item(3, "Abrir no navegador", iconName = "arrow_top_right2")
            ),
            alignLeft = false,
            placeAbove = true
        ) { choice ->
            when (choice.id) {
                1 -> runCatching {
                    startActivity(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${item.title}\n${item.link}")
                    }.let { Intent.createChooser(it, "Partilhar notícia") })
                }.onFailure { AppAoToast.show(this, "Não foi possível partilhar") }
                2 -> {
                    toggleArticleSaved(item)
                    adapter.notifyDataSetChanged()
                }
                3 -> openNewBrowserTab(item.link)
            }
        }
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
        if (::biBar.isInitialized) updateBrowserTabCount()
        if (::appsGrid.isInitialized) populateAppsGrid()
        if (::appsPanel.isInitialized) refreshConversationHistory()
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
                currentCat = categories.firstOrNull { it.label == "Geral" }?.label ?: categories.firstOrNull()?.label ?: "Geral"
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
        progress.background = AccentColorManager.primaryShape(
            this,
            android.graphics.drawable.GradientDrawable.RECTANGLE,
            4f
        )
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

        IconLoader.applyIcon(findViewById(R.id.homeMenuIcon), "menu", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.biFaviconIcon), "global", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.biFireIcon), "flame", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.biMoreIcon), "menu", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.feedBackIcon), "arrow_left", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.feedPublishIcon), "write", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.feedMoreIcon), "more_vert", R.color.iconTint)
        // Preserve the correct Discover/Apps glyph pair and selected state after
        // theme changes or returning from another Activity.
        setFeedBottomSelection(discover = !feedAppsMode)
        IconLoader.applyIcon(findViewById(R.id.biAddIcon), "plus", R.color.iconTint)
        
        IconLoader.applyIcon(findViewById(R.id.biSendIcon), "sparkles", R.color.iconTint)
        findViewById<ImageView>(R.id.appsProfileAvatar).setImageResource(R.mipmap.ic_launcher_round)
        IconLoader.applyIcon(findViewById(R.id.appsSearchIcon), "search", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.appsNewChatIcon), "edit_square", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.appsBackIcon), "arrow_right", R.color.iconTint)
        IconLoader.applyIcon(findViewById(R.id.appsSettingsIcon), "settings", R.color.iconTint)
        findViewById<TextView>(R.id.feedToolbarTitle).setTextColor(ContextCompat.getColor(this, R.color.text))
        refreshFeedTabs(false)
        findViewById<TextView>(R.id.appsTitle).setTextColor(ContextCompat.getColor(this, R.color.text))
        findViewById<TextView>(R.id.conversationsEmpty).setTextColor(ContextCompat.getColor(this, R.color.dim))
        applyComposerSurface()
        biInput.post { resizeComposerInput(false) }
        appsBottomTabs.background = ContextCompat.getDrawable(this, R.drawable.bg_auth_segment)
        appsTabIndicator.background = AccentColorManager.primaryShape(
            this,
            android.graphics.drawable.GradientDrawable.RECTANGLE,
            999f
        )
        findViewById<View>(R.id.appsSettings).background = ContextCompat.getDrawable(this, R.drawable.bg_transparent_pressed)
        findViewById<View>(R.id.appsProfile).background = ContextCompat.getDrawable(this, R.drawable.bg_circle_card)
        findViewById<ImageView>(R.id.appsProfileAvatar).background = ContextCompat.getDrawable(this, R.drawable.bg_circle_card)
        findViewById<ImageView>(R.id.appsProfileAvatar).setImageResource(R.mipmap.ic_launcher_round)
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