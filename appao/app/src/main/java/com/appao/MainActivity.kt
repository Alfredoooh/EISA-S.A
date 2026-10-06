package com.appao

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.location.LocationManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var rootHost: DrawerHostLayout
    private lateinit var homePage: View
    private lateinit var homeHeader: View
    private lateinit var feedPanel: View
    private lateinit var feedHeader: View
    private lateinit var appsPanel: View
    private lateinit var appsBottomTabs: View
    private lateinit var appsTabIndicator: View
    private lateinit var appsContent: View
    private lateinit var conversationsContent: View

    private lateinit var adapter: NewsAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var progress: View
    private lateinit var biPill: View
    private lateinit var biInput: EditText
    private lateinit var biSend: FrameLayout
    private lateinit var pullIndicator: PullRefreshIndicatorView
    private lateinit var homeFeedStack: View
    private lateinit var stackBack: ImageView
    private lateinit var stackFront: ImageView

    private val items = mutableListOf<NewsItem>()
    private val categories = listOf(
        "Todas", "Angola", "Internacional", "Economia", "Desporto",
        "Tecnologia", "Cultura", "Política", "Saúde", "Local 📍"
    )

    private var currentCat = "Todas"
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
    private var sendButtonShown = false

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
        appsPanel = findViewById(R.id.appsPanel)
        appsBottomTabs = findViewById(R.id.appsBottomTabs)
        appsTabIndicator = findViewById(R.id.appsTabIndicator)
        appsContent = findViewById(R.id.appsContent)
        conversationsContent = findViewById(R.id.conversationsContent)
        recycler = findViewById(R.id.feedRecycler)
        progress = findViewById(R.id.appProgress)
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

        recycler.postDelayed({
            if (!isFinishing && !isDestroyed) safeStartup { initWeather() }
        }, 800L)
    }

    private inline fun safeStartup(block: () -> Unit) {
        try { block() } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Startup step failed", t)
        }
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        val state = savedInstanceState ?: return
        currentCat = state.getString("main.category", "Todas") ?: "Todas"
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
                        title = o.optString("title"),
                        summary = o.optString("summary"),
                        link = o.optString("link"),
                        source = o.optString("source"),
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
        ViewCompat.setOnApplyWindowInsetsListener(biPill) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val lp = view.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = if (ime > 0) ime + dp(14) else dp(18)
            view.layoutParams = lp
            if (view.height > 0) bottomBarHeight = view.height
            insets
        }

        feedHeaderHeight = dp(56)
        recycler.updatePadding(top = feedHeaderHeight, bottom = dp(28))
        ViewCompat.requestApplyInsets(rootHost)
    }

    private fun setupHome() {
        IconLoader.applySvg(findViewById(R.id.homeMenuIcon), "menu", R.color.iconTint)

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
            showTopCards = true
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
        IconLoader.applySvg(findViewById(R.id.feedBackIcon), "back", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.feedPublishIcon), "publish", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.feedChevron), "chevron-down", R.color.iconTint)

        findViewById<TextView>(R.id.feedCategoryLabel).text = currentCat
        findViewById<View>(R.id.feedBack).setOnClickListener { closeFeedPanel() }
        findViewById<View>(R.id.feedPublish).setOnClickListener { openPublish() }
        findViewById<View>(R.id.feedCategory).setOnClickListener { openCatSheet() }
    }

    private fun setupBottomInput() {
        IconLoader.applySvg(findViewById(R.id.biAddIcon), "add", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.biSendIcon), "arrow_up", R.color.onpri)

        biInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                setSendVisible(!s.isNullOrBlank())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        biInput.setOnFocusChangeListener { _, focused ->
            animatePillFocus(focused)
        }

        biSend.setOnClickListener { sendToAi() }
        biInput.setOnEditorActionListener { _, _, _ ->
            sendToAi()
            true
        }
        findViewById<View>(R.id.biAdd).setOnClickListener { showAppsPopup(it) }
    }

    private fun animatePillFocus(focused: Boolean) {
        val target = if (focused) 1.015f else 1f
        biPill.animate()
            .scaleX(target)
            .scaleY(target)
            .setDuration(220L)
            .setInterpolator(Curves.SMOOTH)
            .start()
    }

    private fun setSendVisible(hasText: Boolean) {
        if (hasText == sendButtonShown) return
        sendButtonShown = hasText

        if (hasText) {
            biSend.visibility = View.VISIBLE
            biSend.alpha = 0f
            biSend.scaleX = 0.8f
            biSend.scaleY = 0.8f
            biSend.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .setInterpolator(Curves.SPRING)
                .start()
        } else {
            biSend.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(160L)
                .setInterpolator(Curves.SMOOTH)
                .withEndAction {
                    if (!sendButtonShown) biSend.visibility = View.GONE
                }
                .start()
        }
    }

    private fun setupAppsPanel() {
        if (appsReady) return
        appsReady = true

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
        val target = if (apps) 0f else tabWidth.toFloat()
        appsTabIndicator.layoutParams = appsTabIndicator.layoutParams.apply {
            width = tabWidth
            height = appsBottomTabs.height - appsBottomTabs.paddingTop - appsBottomTabs.paddingBottom
        }

        appsTabIndicator.animate().cancel()
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
        val grid = findViewById<android.widget.GridLayout>(R.id.appsGrid)
        grid.removeAllViews()

        AppsPopup.APPS.forEach { app ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_news_text_card)
                isClickable = true
                isFocusable = true
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setOnClickListener {
                    val path = app.url ?: return@setOnClickListener
                    showNavProgress()
                    startActivity(Intent(this@MainActivity, AppViewerActivity::class.java).apply {
                        putExtra("url", "file:///android_asset/$path")
                    })
                }
            }

            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            IconLoader.applyPng(icon, app.png)
            card.addView(icon)

            card.addView(TextView(this).apply {
                text = app.name
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(context, R.color.text))
                setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                setPadding(0, dp(8), 0, 0)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            val lp = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = dp(112)
                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(card, lp)
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
                startPullLoop()
            }

            override fun onPullGestureProgress(distance: Float) {
                if (!pullDragging || pullRefreshing) return
                val maxPull = dp(128).toFloat()
                val k = 0.55f
                pullTargetY = (1f - kotlin.math.exp(-distance * k / maxPull))
                    .times(maxPull)
                    .coerceAtMost(maxPull)
                startPullLoop()
            }

            override fun onPullGestureEnd() {
                if (!pullDragging || pullRefreshing) return
                pullDragging = false
                if (pullCurrentY >= dp(70)) beginPullRefresh() else {
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
        homePage.scaleX = 1f - 0.025f * p
        homePage.scaleY = 1f - 0.025f * p
    }

    private fun setAppsProgress(progressValue: Float) {
        val p = progressValue.coerceIn(0f, 1f)
        val width = rootHost.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        appsPanel.translationX = -width * (1f - p)
        homePage.scaleX = 1f - 0.025f * p
        homePage.scaleY = 1f - 0.025f * p
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
                homePage.scaleX = 1f
                homePage.scaleY = 1f
            }
        }
    }

    private fun animateFeedTo(target: Float, current: Float, end: (() -> Unit)? = null) {
        feedAnimator?.cancel()
        feedAnimator = ValueAnimator.ofFloat(current, target).apply {
            duration = 420L
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
                homePage.scaleX = 1f
                homePage.scaleY = 1f
            }
        }
    }

    private fun animateAppsTo(target: Float, current: Float, end: (() -> Unit)? = null) {
        appsAnimator?.cancel()
        appsAnimator = ValueAnimator.ofFloat(current, target).apply {
            duration = 420L
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
            val cached = withContext(Dispatchers.IO) { NewsRepository.readCache(this@MainActivity) }
            if (isFinishing || isDestroyed) return@launch
            if (cached.isNotEmpty()) {
                val unique = deduplicate(cached)
                items.clear()
                items.addAll(unique)
                adapter.submit(unique)
                updateFeedStack()
            }
            loadNews(false)
        }
    }

    private fun updateFeedBarsFromScroll(dy: Int) {
        val travel = feedHeaderHeight.takeIf { it > 0 } ?: dp(56)
        val direction = if (dy > 0) 1f else -1f
        feedBarProgress = (feedBarProgress + abs(dy).toFloat() / travel * direction).coerceIn(0f, 1f)
        if (recycler.computeVerticalScrollOffset() <= 0) feedBarProgress = 0f
        feedHeader.translationY = -travel * feedBarProgress
    }

    private fun resetFeedBar() {
        feedBarAnimator?.cancel()
        feedBarProgress = 0f
        feedHeader.translationY = 0f
    }

    private fun openCatSheet() {
        val checked = categories.indexOf(currentCat).coerceAtLeast(0)
        NativeSheetDialog.showSelection(this, "Categoria", categories, checked) { index ->
            val selected = categories.getOrNull(index) ?: return@showSelection
            if (selected == currentCat) return@showSelection
            currentCat = selected
            findViewById<TextView>(R.id.feedCategoryLabel).text = selected
            page = 1
            exhausted = false
            NewsRepository.resetDedup()
            loadNews(true)
        }
    }

    private fun loadNews(force: Boolean) {
        if (loading) return
        loading = true
        exhausted = false
        page = 1
        if (force) adapter.showSkeleton(8)

        lifecycleScope.launch {
            val result = try {
                when (currentCat) {
                    "Todas" -> NewsRepository.fetchGeneral(page)
                    "Angola" -> NewsRepository.fetchByCategory("general", page, "AO")
                    "Internacional" -> NewsRepository.fetchByCategory("general", page, "")
                    "Economia" -> NewsRepository.fetchByCategory("business", page)
                    "Desporto" -> NewsRepository.fetchByCategory("sports", page)
                    "Tecnologia" -> NewsRepository.fetchByCategory("technology", page)
                    "Cultura" -> NewsRepository.fetchByCategory("entertainment", page)
                    "Política" -> NewsRepository.fetchByCategory("politics", page)
                    "Saúde" -> NewsRepository.fetchByCategory("health", page)
                    else -> NewsRepository.fetchByCategory("general", page)
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                android.util.Log.e("MainActivity", "News load failed", t)
                emptyList()
            }

            if (isFinishing || isDestroyed) return@launch
            loading = false
            val clean = deduplicate(result)

            if (clean.isNotEmpty()) {
                items.clear()
                items.addAll(clean)
                NewsRepository.writeCache(this@MainActivity, clean)
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
        if (loading) return
        loading = true
        exhausted = false
        adapter.showBottomSkeleton(3)

        lifecycleScope.launch {
            var candidatePage = page + 1
            var fresh: List<NewsItem> = emptyList()

            for (attempt in 0 until 5) {
                val result = try {
                    when (currentCat) {
                        "Todas" -> NewsRepository.fetchGeneral(candidatePage)
                        "Angola" -> NewsRepository.fetchByCategory("general", candidatePage, "AO")
                        "Internacional" -> NewsRepository.fetchByCategory("general", candidatePage, "")
                        "Economia" -> NewsRepository.fetchByCategory("business", candidatePage)
                        "Desporto" -> NewsRepository.fetchByCategory("sports", candidatePage)
                        "Tecnologia" -> NewsRepository.fetchByCategory("technology", candidatePage)
                        "Cultura" -> NewsRepository.fetchByCategory("entertainment", candidatePage)
                        "Política" -> NewsRepository.fetchByCategory("politics", candidatePage)
                        "Saúde" -> NewsRepository.fetchByCategory("health", candidatePage)
                        else -> emptyList()
                    }
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    android.util.Log.e("MainActivity", "Load more failed", t)
                    emptyList()
                }

                if (isFinishing || isDestroyed) return@launch

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

            if (isFinishing || isDestroyed) return@launch
            loading = false

            if (fresh.isEmpty()) {
                adapter.showBottomSkeleton(3)
                recycler.postDelayed({
                    if (!isFinishing && !isDestroyed && feedOpen && !recycler.canScrollVertically(1) && !loading) loadMore()
                }, 1200L)
                return@launch
            }

            adapter.hideBottomSkeleton()
            page = candidatePage
            items.addAll(fresh)
            adapter.append(fresh)
            NewsRepository.writeCache(this@MainActivity, items)
            updateFeedStack()
        }
    }

    private fun updateFeedStack() {
        val withImages = items.filter { it.image.isNotBlank() }.take(2)
        val fallback = if (withImages.isEmpty()) null else withImages.firstOrNull()
        loadStackImage(stackBack, withImages.getOrNull(1)?.image ?: fallback?.image)
        loadStackImage(stackFront, withImages.getOrNull(0)?.image ?: fallback?.image)
    }

    private fun loadStackImage(view: ImageView, url: String?) {
        if (url.isNullOrBlank()) {
            Glide.with(view).load(R.drawable.logo).dontAnimate().into(view)
        } else {
            Glide.with(view).load(url).dontAnimate().centerCrop().into(view)
        }
        view.clipToOutline = true
    }

    private fun initWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }
        loadWeatherFor(-8.84, 13.23)
    }

    private fun loadWeatherFor(lat: Double, lon: Double) {
        lifecycleScope.launch {
            try {
                val result = WeatherHelper.fetch(this@MainActivity, lat, lon) ?: return@launch
                if (!isFinishing && !isDestroyed) adapter.setWeather(result)
            } catch (t: Throwable) {
                android.util.Log.w("MainActivity", "Weather update failed", t)
            }
        }
    }

    private fun sendToAi() {
        val query = biInput.text.toString().trim()
        if (query.isEmpty()) return
        showNavProgress()
        biInput.setText("")
        biInput.clearFocus()
        biPill.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            startActivity(Intent(this, AppViewerActivity::class.java).apply {
                putExtra("url", "file:///android_asset/apps/ai.html?q=" + URLEncoder.encode(query, "UTF-8"))
            })
        }, 80L)
    }

    private fun showAppsPopup(anchor: View) {
        anchor.animate().scaleX(0.9f).scaleY(0.9f).setDuration(110L).withEndAction {
            anchor.animate().scaleX(1f).scaleY(1f).setDuration(210L).start()
        }.start()
        AppsPopup.show(anchor) { app ->
            val path = app.url ?: return@show
            showNavProgress()
            startActivity(Intent(this, AppViewerActivity::class.java).apply {
                putExtra("url", "file:///android_asset/$path")
            })
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

    private fun beginPullRefresh() {
        pullRefreshing = true
        pullDragging = false
        pullVelocity = 0f
        pullTargetY = dp(70).toFloat()
        pullCurrentY = maxOf(pullCurrentY, dp(70).toFloat())

        pullSpinnerAnimator?.cancel()
        pullSpinnerAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { pullRotation = it.animatedValue as Float; pullIndicator.rotationDegrees = pullRotation }
            start()
        }

        startPullLoop()

        pullFinishPosted = true
        rootHost.postDelayed({
            pullFinishPosted = false
            if (pullRefreshing && !isFinishing && !isDestroyed) finishPullRefresh()
        }, 1250L)

        loadNews(true)
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
        }, 600L)
    }

    private fun startPullLoop() {
        if (pullFramePosted) return
        pullFramePosted = true
        rootHost.postOnAnimation(pullFrameRunnable)
    }

    private val pullFrameRunnable = object : Runnable {
        override fun run() {
            val now = android.os.SystemClock.elapsedRealtimeNanos()
            val dt = if (pullLastTimeNs != 0L) kotlin.math.min((now - pullLastTimeNs).toDouble() / 1_000_000_000.0, 0.033) else 0.016
            pullLastTimeNs = now

            val target = pullTargetY
            if (abs(target - pullCurrentY) > 0.2f) {
                val stiffness = if (pullDragging) 18f else 28f
                pullVelocity += (target - pullCurrentY) * stiffness * dt.toFloat()
                pullVelocity *= if (pullDragging) 0.82f else 0.88f
                pullCurrentY += pullVelocity * dt.toFloat()
                applyPullVisual(pullCurrentY)
                rootHost.postOnAnimation(this)
            } else {
                pullCurrentY = target
                pullVelocity *= 0.85f
                applyPullVisual(pullCurrentY)
                pullFramePosted = false
                if (pullDragging || pullRefreshing) rootHost.postOnAnimation(this)
            }
        }
    }

    private fun applyPullVisual(y: Float) {
        val h = rootHost.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val headerTravel = dp(56).toFloat()
        val shift = y.coerceAtLeast(0f)
        pullIndicator.translationY = shift * 0.35f
        pullIndicator.alpha = (shift / dp(52).toFloat()).coerceIn(0f, 1f)
        pullIndicator.scaleX = (0.25f + shift / dp(72).toFloat()).coerceIn(0.25f, 1f)
        pullIndicator.scaleY = pullIndicator.scaleX
        if (!feedOpen) {
            pullIndicator.alpha = 0f
        }
        // The feed surface itself follows the pull instead of the home page.
        feedPanel.translationY = shift
        feedPanel.translationX = if (feedOpen) 0f else (rootHost.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels).toFloat()
        feedHeader.translationY = -headerTravel * feedBarProgress
        if (h <= 0) return
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            try {
                val manager = getSystemService(LOCATION_SERVICE) as LocationManager
                val location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (location != null) loadWeatherFor(location.latitude, location.longitude)
            } catch (_: SecurityException) { }
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
    }

    private fun refreshNativeTheme() {
        val bg = ContextCompat.getColor(this, R.color.bg)
        rootHost.setBackgroundColor(bg)
        homePage.setBackgroundColor(bg)
        feedPanel.setBackgroundColor(bg)
        appsPanel.setBackgroundColor(bg)
        homeHeader.setBackgroundColor(bg)
        feedHeader.setBackgroundColor(bg)

        IconLoader.applySvg(findViewById(R.id.homeMenuIcon), "menu", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.feedBackIcon), "back", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.feedPublishIcon), "publish", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.feedChevron), "chevron-down", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.biAddIcon), "add", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.biSendIcon), "arrow_up", R.color.onpri)
        IconLoader.applyPng(findViewById(R.id.appsProfileAvatar), "avatar")
        IconLoader.applySvg(findViewById(R.id.appsBackIcon), "arrow_right", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.appsSettingsIcon), "settings", R.color.iconTint)

        findViewById<TextView>(R.id.feedCategoryLabel).setTextColor(ContextCompat.getColor(this, R.color.text))
        findViewById<TextView>(R.id.appsTitle).setTextColor(ContextCompat.getColor(this, R.color.text))
        findViewById<TextView>(R.id.conversationsEmpty).setTextColor(ContextCompat.getColor(this, R.color.dim))
        selectAppsTab(appsTabIsApps, false)
        updateFeedStack()
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
        feedAnimator?.cancel()
        appsAnimator?.cancel()
        feedBarAnimator?.cancel()
        pullSpinnerAnimator?.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
