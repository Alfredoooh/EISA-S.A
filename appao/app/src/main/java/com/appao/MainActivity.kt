package com.appao

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.location.LocationManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: NewsAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: View
    private lateinit var biPill: View
    private lateinit var biInput: EditText
    private lateinit var biSend: FrameLayout
    private lateinit var biAdd: FrameLayout
    private lateinit var mainContent: View
    private lateinit var header: View
    private lateinit var drawerPanel: View
    private lateinit var drawerHeader: View
    private lateinit var sidePanel: View
    private lateinit var pullIndicator: PullRefreshIndicatorView

    private val items = mutableListOf<NewsItem>()
    private val categories = listOf(
        "Todas", "Angola", "Internacional", "Economia", "Desporto",
        "Tecnologia", "Cultura", "Política", "Saúde", "Local 📍"
    )

    private var currentCat = "Todas"
    private var page = 1
    private var loading = false
    private var exhausted = false
    private var drawerOpen = false
    private var sidePanelOpen = false

    private var drawerAnimator: ValueAnimator? = null
    private var sidePanelAnimator: ValueAnimator? = null

    // Instagram-style pull-to-refresh state. The values mirror the supplied
    // reference: 72dp trigger, 130dp maximum, exponential resistance and a
    // damped spring for the return/hold motion.
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

    private var headerHeight = 0
    private var bottomBarHeight = 0
    private var barProgress = 0f
    private var barAnimator: ValueAnimator? = null
    private var progressAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)
        try { SystemBarHelper.sync(this) } catch (t: Throwable) { android.util.Log.e("MainActivity", "System bars init failed", t) }

        mainContent = findViewById(R.id.mainContent)
        progress = findViewById(R.id.appProgress)
        recycler = findViewById(R.id.recycler)
        swipe = findViewById(R.id.swipe)
        biPill = findViewById(R.id.biPill)
        biInput = findViewById(R.id.biInput)
        biSend = findViewById(R.id.biSend)
        biAdd = findViewById(R.id.biAdd)
        header = findViewById(R.id.header)
        drawerPanel = findViewById(R.id.drawerPanel)
        drawerHeader = findViewById(R.id.drawerHeader)
        sidePanel = findViewById(R.id.sidePanel)
        pullIndicator = findViewById(R.id.pullIndicator)

        safeStartup("insets") { setupInsets() }
        safeStartup("feed") { setupFeed() }
        safeStartup("header") { setupHeader() }
        safeStartup("bottom-input") { setupBottomInput() }
        safeStartup("drawer") { setupDrawer() }
        safeStartup("side-panel") { setupSidePanel() }
        safeStartup("categories") { setupCategories() }

        // Render a stable first frame before starting the adapter animation,
        // cache read and network activity. This prevents startup work from
        // competing with layout creation on lower-end devices.
        recycler.post {
            if (isFinishing || isDestroyed) return@post
            safeStartup("initial-data") { setupInitialData() }
        }

        recycler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            safeStartup("weather") { initWeather() }
        }, 800L)
    }

    private inline fun safeStartup(
        stage: String,
        block: () -> Unit
    ) {
        try {
            block()
        } catch (t: Throwable) {
            android.util.Log.e(
                "MainActivity",
                "Startup stage failed: $stage",
                t
            )
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(biPill) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val lp = view.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = if (ime > 0) ime + dp(16) else dp(20)
            view.layoutParams = lp
            if (view.height > 0) bottomBarHeight = view.height
            applyBarTranslation(barProgress)
            insets
        }

        headerHeight = dp(56)
        recycler.updatePadding(top = headerHeight)
        applyBarTranslation(barProgress)
        ViewCompat.requestApplyInsets(mainContent)
    }

    private fun setupFeed() {
        adapter = NewsAdapter(
            initial = items.toList(),
            onClick = { item, source -> openArticle(item, source) },
            showTopCards = true
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        recycler.setHasFixedSize(false)
        recycler.itemAnimator = null
        recycler.setItemViewCacheSize(4)

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0) {
                    updateBarsFromScroll(dy)
                }

                if (!rv.canScrollVertically(1) && !loading && !exhausted) {
                    loadMore()
                }
            }
        })

        // The stock SwipeRefreshLayout animation is disabled. Pull-to-refresh
        // is implemented by the root gesture host so the entire home container
        // follows the finger exactly like the supplied HTML reference.
        swipe.isEnabled = false
        pullIndicator.alpha = 0f
        pullIndicator.scaleX = 0f
        pullIndicator.scaleY = 0f
    }

    private fun setupHeader() {
        IconLoader.applySvg(
            findViewById(R.id.hAddIcon),
            "apps",
            R.color.iconTint
        )
        IconLoader.applySvg(
            findViewById(R.id.hMoreIcon),
            "menu",
            R.color.iconTint
        )
        IconLoader.applySvg(
            findViewById(R.id.hCatChevron),
            "chevron-down",
            R.color.iconTint
        )

        findViewById<View>(R.id.hAdd).setOnClickListener {
            openSidePanel()
        }
        findViewById<View>(R.id.hMore).setOnClickListener {
            openDrawer()
        }
        findViewById<View>(R.id.hCat).setOnClickListener {
            openCatSheet()
        }
    }

    private fun setupBottomInput() {
        IconLoader.applySvg(
            biAdd.findViewById(R.id.biAddIcon),
            "add",
            R.color.iconTint
        )
        IconLoader.applySvg(
            biSend.findViewById(R.id.biSendIcon),
            "arrow_up",
            R.color.onpri
        )

        biInput.addTextChangedListener(object : TextWatcher {
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
                setSendVisible(!s.isNullOrBlank())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        biInput.setOnFocusChangeListener { _, focused ->
            if (focused) {
                animateBarProgressTo(0f, 220L)
            }
            animatePillFocus(focused)
        }

        biSend.setOnClickListener {
            sendToAi()
        }

        biInput.setOnEditorActionListener { _, _, _ ->
            sendToAi()
            true
        }

        biAdd.setOnClickListener {
            showAppsPopup(it)
        }
    }

    private fun setupDrawer() {
        drawerOpen = false
        drawerPanel.visibility = View.GONE

        drawerPanel.post {
            if (isFinishing || isDestroyed) return@post

            val targetWidth = rootMainWidth()

            drawerPanel.layoutParams =
                drawerPanel.layoutParams.apply {
                    width = targetWidth
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }

            drawerPanel.requestLayout()
            drawerPanel.translationX = targetWidth.toFloat()
        }

        IconLoader.applyPng(findViewById(R.id.drAvatar), "avatar")
        IconLoader.applyPng(findViewById(R.id.drIconVerify), "verified")
        IconLoader.applyPng(findViewById(R.id.drIconSaved), "bookmark")
        IconLoader.applyPng(findViewById(R.id.drIconSettings), "settings")
        IconLoader.applyPng(findViewById(R.id.drIconFriends), "profile")
        IconLoader.applyPng(findViewById(R.id.drIconReport), "alert")
        IconLoader.applyPng(findViewById(R.id.drIconAi), "magic_wand")

        IconLoader.applySvg(
            findViewById(R.id.drBackIcon),
            "arrow_left",
            R.color.iconTint
        )

        findViewById<View>(R.id.drBack).setOnClickListener {
            closeDrawer()
        }

        findViewById<View>(R.id.drVerify).setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, AuthActivity::class.java))
        }

        findViewById<View>(R.id.drSaved).setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, LibraryActivity::class.java))
        }

        findViewById<View>(R.id.drSettings).setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.drFriends).setOnClickListener {
            closeDrawer()
            AppAoToast.show(this, "Amigos — em breve")
        }

        findViewById<View>(R.id.drReport).setOnClickListener {
            closeDrawer()
            AppAoToast.show(this, "Comunicar um problema — em breve")
        }

        findViewById<View>(R.id.drAiSettings).setOnClickListener {
            closeDrawer()
            AppAoToast.show(this, "Configurações de IA — em breve")
        }

        val host = findViewById<DrawerHostLayout>(R.id.rootMain)
        host.listener = object : DrawerHostLayout.Listener {
            override fun isDrawerOpen(): Boolean = drawerOpen

            override fun isSidePanelOpen(): Boolean = sidePanelOpen

            override fun canStartPull(x: Float, y: Float): Boolean {
                if (drawerOpen || sidePanelOpen || pullRefreshing || loading) return false
                if (!::recycler.isInitialized || !recycler.isShown) return false
                if (recycler.canScrollVertically(-1)) return false

                val reservedBottom = if (bottomBarHeight > 0) {
                    bottomBarHeight + dp(12)
                } else {
                    dp(70)
                }
                return y < rootMainHeight() - reservedBottom
            }

            override fun onPullGestureStart() {
                pullDragging = true
                pullVelocity = 0f
                pullTargetY = pullCurrentY
                pullLastTimeNs = 0L
                recycler.stopScroll()
                mainContent.animate().cancel()
                startPullLoop()
            }

            override fun onPullGestureProgress(distance: Float) {
                if (!pullDragging || pullRefreshing) return
                val maxPull = dp(130).toFloat()
                val threshold = dp(72).toFloat()
                val k = 0.55f
                pullTargetY = (
                    (1f - kotlin.math.exp(-distance * k / maxPull)) * maxPull
                ).coerceAtMost(maxPull)
                if (pullCurrentY > threshold && pullTargetY < threshold) {
                    // Keep the indicator state continuous when crossing the
                    // trigger boundary in the opposite direction.
                }
                startPullLoop()
            }

            override fun onPullGestureEnd() {
                if (!pullDragging || pullRefreshing) return
                pullDragging = false

                val threshold = dp(72).toFloat()
                if (pullCurrentY >= threshold) {
                    beginPullRefresh()
                } else {
                    pullTargetY = 0f
                    pullVelocity = 0f
                    startPullLoop()
                }
            }

            override fun onDrawerGestureStart(opening: Boolean) {
                drawerAnimator?.cancel()
                drawerPanel.animate().cancel()
                mainContent.animate().cancel()
                if (opening) {
                    beginDrawerOpenGesture()
                } else {
                    val width = drawerWidth()
                    val current = ((width - drawerPanel.translationX) / width)
                        .coerceIn(0f, 1f)
                    drawerOpen = true
                    drawerPanel.visibility = View.VISIBLE
                    setDrawerProgress(current)
                }
            }

            override fun onDrawerGestureProgress(progress: Float) {
                if (drawerOpen || progress > 0f) {
                    setDrawerProgress(progress)
                }
            }

            override fun onDrawerGestureEnd(
                opening: Boolean,
                progress: Float,
                velocity: Float
            ) {
                finishDrawerGesture(opening, progress, velocity)
            }

            override fun onSidePanelGestureStart(opening: Boolean) {
                sidePanelAnimator?.cancel()
                sidePanel.animate().cancel()
                mainContent.animate().cancel()
                if (opening) {
                    beginSidePanelOpenGesture()
                } else {
                    val width = sidePanelWidth()
                    val current = ((width + sidePanel.translationX) / width)
                        .coerceIn(0f, 1f)
                    sidePanelOpen = true
                    sidePanel.visibility = View.VISIBLE
                    setSidePanelProgress(current)
                }
            }

            override fun onSidePanelGestureProgress(progress: Float) {
                if (sidePanelOpen || progress > 0f) {
                    setSidePanelProgress(progress)
                }
            }

            override fun onSidePanelGestureEnd(
                opening: Boolean,
                progress: Float,
                velocity: Float
            ) {
                finishSidePanelGesture(opening, progress, velocity)
            }
        }
    }

    private fun setupSidePanel() {
        sidePanelOpen = false
        sidePanel.visibility = View.GONE

        sidePanel.post {
            if (isFinishing || isDestroyed) return@post
            sidePanel.layoutParams = sidePanel.layoutParams.apply {
                width = rootMainWidth()
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            sidePanel.requestLayout()
            sidePanel.translationX = -rootMainWidth().toFloat()
        }

        findViewById<View>(R.id.sideHeader).apply {
            layoutParams = layoutParams.apply { height = dp(56) }
            setPadding(paddingLeft, 0, paddingRight, paddingBottom)
        }

        IconLoader.applySvg(
            findViewById(R.id.sideCloseIcon),
            "arrow_right",
            R.color.iconTint
        )

        findViewById<View>(R.id.sideClose).setOnClickListener {
            closeSidePanel()
        }
    }

    private fun setupCategories() {
        // The sheet itself is populated when opened so theme changes are reflected immediately.
    }

    private fun setupInitialData() {
        adapter.showSkeleton(8)

        lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) {
                NewsRepository.readCache(this@MainActivity)
            }

            if (isFinishing || isDestroyed) return@launch

            if (cached.isNotEmpty()) {
                val unique = deduplicate(cached)
                items.clear()
                items.addAll(unique)
                adapter.submit(unique)
            }

            loadNews(false)
        }
    }

    private fun updateBarsFromScroll(dy: Int) {
        val headerTravel = (
            if (headerHeight > 0) headerHeight
            else dp(56)
        ).toFloat().coerceAtLeast(1f)

        val bottomTravel = (
            if (bottomBarHeight > 0) bottomBarHeight + dp(40)
            else dp(86)
        ).toFloat().coerceAtLeast(1f)

        val direction = if (dy > 0) 1f else -1f
        val delta = kotlin.math.abs(dy).toFloat() / headerTravel
        barProgress = (
            barProgress + direction * delta
        ).coerceIn(0f, 1f)

        if (recycler.computeVerticalScrollOffset() <= 0) {
            barProgress = 0f
        }

        val target = barProgress
        header.translationY = -headerTravel * target
        biPill.translationY = bottomTravel * target
    }

    private fun applyBarTranslation(progress: Float) {
        val headerTravel = (
            if (headerHeight > 0) headerHeight
            else dp(56)
        ).toFloat()

        val bottomTravel = (
            if (bottomBarHeight > 0) bottomBarHeight + dp(40)
            else dp(86)
        ).toFloat()

        header.translationY = -headerTravel * progress
        biPill.translationY = bottomTravel * progress
    }

    private fun animateBarProgressTo(
        target: Float,
        duration: Long = 260L
    ) {
        barAnimator?.cancel()
        val start = barProgress
        barAnimator = ValueAnimator.ofFloat(start, target.coerceIn(0f, 1f)).apply {
            this.duration = duration
            interpolator = Curves.IOS
            addUpdateListener {
                barProgress = it.animatedValue as Float
                applyBarTranslation(barProgress)
            }
            start()
        }
    }

    private fun resetBarsInstantly() {
        barAnimator?.cancel()
        barProgress = 0f
        header.translationY = 0f
        biPill.translationY = 0f
    }

    private fun animatePillFocus(focused: Boolean) {
        val target = if (focused) dp(340) else dp(320)
        val lp = biPill.layoutParams

        ValueAnimator.ofInt(lp.width, target).apply {
            duration = 400L
            interpolator = Curves.IOS
            addUpdateListener {
                lp.width = it.animatedValue as Int
                biPill.layoutParams = lp
            }
            start()
        }
    }

    private fun setSendVisible(hasText: Boolean) {
        if (hasText) {
            val lp = biSend.layoutParams as LinearLayout.LayoutParams
            lp.width = dp(36)
            lp.marginStart = dp(2)
            biSend.layoutParams = lp
            biSend.visibility = View.VISIBLE
            biSend.scaleX = 0.4f
            biSend.scaleY = 0.4f
            biSend.alpha = 0f
            biSend.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(300L)
                .setInterpolator(Curves.SPRING)
                .start()
        } else {
            biSend.animate()
                .alpha(0f)
                .scaleX(0.4f)
                .scaleY(0.4f)
                .setDuration(250L)
                .withEndAction {
                    val lp = biSend.layoutParams as LinearLayout.LayoutParams
                    lp.width = 0
                    lp.marginStart = 0
                    biSend.layoutParams = lp
                    biSend.visibility = View.GONE
                }
                .start()
        }
    }

    private fun loadNews(force: Boolean) {
        if (loading) return

        loading = true
        exhausted = false
        page = 1

        if (force && items.isEmpty()) {
            adapter.showSkeleton(8)
        }

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
            swipe.isRefreshing = false

            val clean = deduplicate(result)

            if (clean.isNotEmpty()) {
                items.clear()
                items.addAll(clean)
                NewsRepository.writeCache(this@MainActivity, clean)
                adapter.submit(clean)
            } else if (items.isEmpty()) {
                adapter.submit(emptyList())
                exhausted = true
            }
        }
    }

    private fun loadMore() {
        if (loading || exhausted) return

        loading = true
        val nextPage = page + 1

        lifecycleScope.launch {
            val result = try {
                when (currentCat) {
                    "Todas" -> NewsRepository.fetchGeneral(nextPage)
                    "Angola" -> NewsRepository.fetchByCategory("general", nextPage, "AO")
                    "Internacional" -> NewsRepository.fetchByCategory("general", nextPage, "")
                    "Economia" -> NewsRepository.fetchByCategory("business", nextPage)
                    "Desporto" -> NewsRepository.fetchByCategory("sports", nextPage)
                    "Tecnologia" -> NewsRepository.fetchByCategory("technology", nextPage)
                    "Cultura" -> NewsRepository.fetchByCategory("entertainment", nextPage)
                    "Política" -> NewsRepository.fetchByCategory("politics", nextPage)
                    "Saúde" -> NewsRepository.fetchByCategory("health", nextPage)
                    else -> emptyList()
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                android.util.Log.e("MainActivity", "Load more failed", t)
                emptyList()
            }

            if (isFinishing || isDestroyed) return@launch

            loading = false
            val seen = HashSet<String>()
            items.forEach {
                seen += normalizeUrl(it.link)
                seen += normalizeTitle(it.title)
            }

            val fresh = result.filter { item ->
                val urlKey = normalizeUrl(item.link)
                val titleKey = normalizeTitle(item.title)
                if (urlKey.isBlank() || titleKey.isBlank()) {
                    false
                } else if (urlKey in seen || titleKey in seen) {
                    false
                } else {
                    seen += urlKey
                    seen += titleKey
                    true
                }
            }

            if (fresh.isEmpty()) {
                exhausted = true
                return@launch
            }

            page = nextPage
            items.addAll(fresh)
            adapter.append(fresh)
            NewsRepository.writeCache(this@MainActivity, items)
        }
    }

    private fun initWeather() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
        }
        loadWeatherFor(-8.84, 13.23)
    }

    private fun loadWeatherFor(lat: Double, lon: Double) {
        lifecycleScope.launch {
            try {
                val result = WeatherHelper.fetch(
                    this@MainActivity,
                    lat,
                    lon
                ) ?: return@launch

                if (isFinishing || isDestroyed) return@launch

                adapter.setWeather(result)
            } catch (t: Throwable) {
                android.util.Log.w(
                    "MainActivity",
                    "Weather update failed",
                    t
                )
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

            startActivity(
                Intent(this, AppViewerActivity::class.java).apply {
                    putExtra(
                        "url",
                        "file:///android_asset/apps/ai.html?q=" +
                            URLEncoder.encode(query, "UTF-8")
                    )
                }
            )
        }, 80L)
    }

    private fun showNavProgress() {
        progress.visibility = View.VISIBLE
        progressAnimator?.cancel()

        val width = resources.displayMetrics.widthPixels
        val lp = progress.layoutParams as FrameLayout.LayoutParams
        lp.width = 0
        val top = ViewCompat.getRootWindowInsets(progress)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top ?: 0
        lp.topMargin = top
        progress.layoutParams = lp
        progress.alpha = 1f

        progressAnimator = ValueAnimator.ofInt(
            0,
            (width * 0.88f).toInt()
        ).apply {
            duration = 1400L
            interpolator = Curves.PROGRESS
            addUpdateListener {
                val params = progress.layoutParams as FrameLayout.LayoutParams
                params.width = it.animatedValue as Int
                progress.layoutParams = params
            }
            start()
        }
    }

    private fun showAppsPopup(anchor: View) {
        anchor.animate()
            .scaleX(0.88f)
            .scaleY(0.88f)
            .setDuration(120L)
            .withEndAction {
                anchor.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220L)
                    .start()
            }
            .start()

        AppsPopup.show(anchor) { app ->
            val path = app.url ?: return@show
            showNavProgress()
            startActivity(
                Intent(this, AppViewerActivity::class.java).apply {
                    putExtra(
                        "url",
                        "file:///android_asset/$path"
                    )
                }
            )
        }
    }

    private fun drawerWidth(): Float =
        drawerPanel.width.toFloat().takeIf { it > 0f }
            ?: (rootMainWidth() * 0.82f).coerceAtLeast(1f)

    private fun sidePanelWidth(): Float =
        sidePanel.width.toFloat().takeIf { it > 0f }
            ?: rootMainWidth().toFloat().coerceAtLeast(1f)

    private fun rootMainWidth(): Int =
        if (::mainContent.isInitialized && mainContent.width > 0) {
            mainContent.width
        } else {
            resources.displayMetrics.widthPixels
        }

    private fun setDrawerProgress(progressValue: Float) {
        val p = progressValue.coerceIn(0f, 1f)
        val width = drawerWidth()

        drawerPanel.translationX = width * (1f - p)

        // Pixel-perfect horizontal relationship from the supplied reference:
        // the drawer follows the finger 1:1 and the current screen follows at
        // 34% parallax, producing the requested delayed/pushed feel.
        mainContent.translationX = -rootMainWidth() * 0.34f * p

        findViewById<View>(R.id.gestureScrim).alpha = p * 0.35f
    }

    private fun beginDrawerOpenGesture() {
        drawerOpen = true
        sidePanelOpen = false
        drawerPanel.visibility = View.VISIBLE
        drawerPanel.animate().cancel()
        mainContent.animate().cancel()

        val width = drawerWidth()
        val current = ((width - drawerPanel.translationX) / width)
            .coerceIn(0f, 1f)
        setDrawerProgress(current)
    }

    private fun finishDrawerGesture(
        opening: Boolean,
        progress: Float,
        velocity: Float
    ) {
        if (opening) {
            val shouldOpen = progress > 0.18f || velocity < -0.4f
            if (shouldOpen) openDrawer(progress) else closeDrawer()
        } else {
            val shouldClose = progress < 0.78f || velocity > 0.4f
            if (shouldClose) closeDrawer() else openDrawer(progress)
        }
    }

    private fun animateDrawerTo(target: Float, current: Float) {
        drawerAnimator?.cancel()
        val distance = kotlin.math.abs(target - current)
        val duration = 500L

        drawerAnimator = ValueAnimator.ofFloat(current, target).apply {
            this.duration = duration
            interpolator = Curves.IOS
            addUpdateListener {
                setDrawerProgress(it.animatedValue as Float)
            }
            start()
        }
    }

    private fun openDrawer(gestureProgress: Float? = null) {
        drawerOpen = true
        sidePanelOpen = false
        drawerPanel.visibility = View.VISIBLE
        drawerAnimator?.cancel()
        drawerPanel.animate().cancel()
        mainContent.animate().cancel()

        val width = drawerWidth()
        val current = (gestureProgress
            ?: ((width - drawerPanel.translationX) / width))
            .coerceIn(0f, 1f)
        setDrawerProgress(current)
        animateDrawerTo(1f, current)
    }

    private fun closeDrawer() {
        if (!drawerOpen && drawerPanel.visibility != View.VISIBLE) return

        val width = drawerWidth()
        val current = ((width - drawerPanel.translationX) / width)
            .coerceIn(0f, 1f)
        drawerOpen = false
        drawerAnimator?.cancel()
        setDrawerProgress(current)

        drawerAnimator = ValueAnimator.ofFloat(current, 0f).apply {
            duration = 500L
            interpolator = Curves.IOS
            addUpdateListener {
                setDrawerProgress(it.animatedValue as Float)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (drawerOpen) return
                    drawerPanel.visibility = View.GONE
                    drawerPanel.translationX = width
                    mainContent.translationX = 0f
                    findViewById<View>(R.id.gestureScrim).alpha = 0f
                    SystemBarHelper.sync(this@MainActivity)
                }
            })
            start()
        }
    }

    private fun setSidePanelProgress(progressValue: Float) {
        val p = progressValue.coerceIn(0f, 1f)
        val width = sidePanelWidth()

        sidePanel.translationX = -width * (1f - p)

        // Same 34% parallax in the opposite direction.
        mainContent.translationX = rootMainWidth() * 0.34f * p

        findViewById<View>(R.id.gestureScrim).alpha = p * 0.35f
    }

    private fun beginSidePanelOpenGesture() {
        sidePanelOpen = true
        drawerOpen = false
        sidePanel.visibility = View.VISIBLE
        sidePanelAnimator?.cancel()
        mainContent.animate().cancel()

        val width = sidePanelWidth()
        val current = ((width + sidePanel.translationX) / width)
            .coerceIn(0f, 1f)
        setSidePanelProgress(current)
    }

    private fun finishSidePanelGesture(
        opening: Boolean,
        progress: Float,
        velocity: Float
    ) {
        if (opening) {
            val shouldOpen = progress > 0.18f || velocity > 0.4f
            if (shouldOpen) openSidePanel(progress) else closeSidePanel()
        } else {
            val shouldClose = progress < 0.78f || velocity < -0.4f
            if (shouldClose) closeSidePanel() else openSidePanel(progress)
        }
    }

    private fun animateSidePanelTo(target: Float, current: Float) {
        sidePanelAnimator?.cancel()
        val distance = kotlin.math.abs(target - current)
        val duration = 500L
        sidePanelAnimator = ValueAnimator.ofFloat(current, target).apply {
            this.duration = duration
            interpolator = Curves.IOS
            addUpdateListener {
                setSidePanelProgress(it.animatedValue as Float)
            }
            start()
        }
    }

    private fun openSidePanel(gestureProgress: Float? = null) {
        if (drawerOpen || isFinishing || isDestroyed) return

        sidePanelOpen = true
        sidePanel.visibility = View.VISIBLE
        sidePanelAnimator?.cancel()
        val width = sidePanelWidth()
        val current = (gestureProgress
            ?: ((width + sidePanel.translationX) / width))
            .coerceIn(0f, 1f)
        setSidePanelProgress(current)
        animateSidePanelTo(1f, current)
    }

    private fun closeSidePanel() {
        if (!sidePanelOpen && sidePanel.visibility != View.VISIBLE) return

        val width = sidePanelWidth()
        val current = ((width + sidePanel.translationX) / width)
            .coerceIn(0f, 1f)
        sidePanelOpen = false
        sidePanelAnimator?.cancel()

        sidePanelAnimator = ValueAnimator.ofFloat(current, 0f).apply {
            duration = 500L
            interpolator = Curves.IOS
            addUpdateListener {
                setSidePanelProgress(it.animatedValue as Float)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (sidePanelOpen) return
                    sidePanel.visibility = View.GONE
                    sidePanel.translationX = -width
                    mainContent.translationX = 0f
                    findViewById<View>(R.id.gestureScrim).alpha = 0f
                    SystemBarHelper.sync(this@MainActivity)
                }
            })
            start()
        }
    }

    private fun rootMainHeight(): Int =
        if (findViewById<View>(R.id.rootMain).height > 0) {
            findViewById<View>(R.id.rootMain).height
        } else {
            resources.displayMetrics.heightPixels
        }

    private fun startPullLoop() {
        if (pullFramePosted) return
        pullFramePosted = true
        findViewById<View>(R.id.rootMain).postOnAnimation(pullFrameRunnable)
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
                findViewById<View>(R.id.rootMain).postOnAnimation(this)
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
        mainContent.translationY = y

        val threshold = dp(72).toFloat()
        val appearT = (y / dp(55).toFloat()).coerceAtMost(1f)
        val zoom = 1f - (1f - appearT) * (1f - appearT) * (1f - appearT)
        val topY = y * 0.5f - dp(14).toFloat()

        pullIndicator.alpha = zoom
        pullIndicator.scaleX = zoom
        pullIndicator.scaleY = zoom
        pullIndicator.translationY = topY

        if (!pullRefreshing) {
            pullRotation = (
                (y / threshold).coerceIn(0f, 1f) * 300f
            )
            pullIndicator.rotationDegrees = pullRotation
        }
    }

    private fun beginPullRefresh() {
        pullRefreshing = true
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

        // Match the supplied reference timing: the refresh phase lasts
        // 1400 ms independently of network latency. The fetched content may
        // arrive earlier and is rendered immediately, but the physical pull
        // animation remains on the same timing curve.
        pullFinishPosted = true
        findViewById<View>(R.id.rootMain).postDelayed({
            pullFinishPosted = false
            if (pullRefreshing && !isFinishing && !isDestroyed) {
                finishPullRefresh()
            }
        }, 1400L)

        loadNews(true)
    }

    private fun finishPullRefresh() {
        if (!pullRefreshing) return
        pullRefreshing = false
        pullTargetY = 0f
        pullVelocity = -kotlin.math.max(2f, pullCurrentY * 0.05f)
        startPullLoop()

        findViewById<View>(R.id.rootMain).postDelayed({
            if (!pullRefreshing && kotlin.math.abs(pullCurrentY) < 1f) {
                pullSpinnerAnimator?.cancel()
                pullSpinnerAnimator = null
                pullRotation = 0f
                pullIndicator.rotationDegrees = 0f
            }
        }, 700L)
    }

    private fun openCatSheet() {
        val checked = categories.indexOf(currentCat).coerceAtLeast(0)

        NativeSheetDialog.showSelection(
            this,
            "Categoria",
            categories,
            checked
        ) { index ->
            val selected = categories.getOrNull(index) ?: return@showSelection
            if (selected == currentCat) return@showSelection

            currentCat = selected
            findViewById<TextView>(R.id.hCatLabel).text = selected
            NewsRepository.resetDedup()
            resetBarsInstantly()
            loadNews(true)
        }
    }

    private fun openArticle(
        item: NewsItem,
        source: View
    ) {
        val transitionName =
            "news_container_${item.id.hashCode().toUInt().toString(36)}"

        ViewCompat.setTransitionName(
            source,
            transitionName
        )

        val intent = Intent(
            this,
            ArticleActivity::class.java
        ).apply {
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

        val options =
            android.app.ActivityOptions.makeSceneTransitionAnimation(
                this,
                source,
                transitionName
            )

        startActivity(
            intent,
            options.toBundle()
        )
    }

    private fun deduplicate(
        source: List<NewsItem>
    ): List<NewsItem> {
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

    private fun normalizeUrl(value: String): String = value.trim()
        .lowercase(Locale.ROOT)
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('#')
        .trimEnd('/')

    private fun normalizeTitle(value: String): String = value.trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\p{Punct}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val manager = getSystemService(LOCATION_SERVICE) as LocationManager
                val location = manager.getLastKnownLocation(
                    LocationManager.NETWORK_PROVIDER
                )
                if (location != null) {
                    loadWeatherFor(
                        location.latitude,
                        location.longitude
                    )
                }
            } catch (_: SecurityException) {
            }
        }
    }

    override fun onBackPressed() {
        if (sidePanelOpen) {
            closeSidePanel()
            return
        }
        if (drawerOpen) {
            closeDrawer()
            return
        }
        super.onBackPressed()
    }

    override fun onConfigurationChanged(
        newConfig: android.content.res.Configuration
    ) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
        refreshNativeTheme()
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
        refreshNativeTheme()

        if (progress.alpha > 0f) {
            progress.animate()
                .alpha(0f)
                .setDuration(250L)
                .withEndAction {
                    progressAnimator?.cancel()
                    val lp = progress.layoutParams as FrameLayout.LayoutParams
                    lp.width = 0
                    progress.layoutParams = lp
                }
                .start()
        }
    }

    private fun refreshNativeTheme() {
        val bg = ContextCompat.getColor(this, R.color.bg)
        findViewById<View>(R.id.rootMain).setBackgroundColor(bg)
        recycler.setBackgroundColor(bg)
        header.setBackgroundColor(bg)
        biPill.background = ContextCompat.getDrawable(
            this,
            R.drawable.bg_pill_card
        )

        IconLoader.applySvg(
            findViewById(R.id.hAddIcon),
            "apps",
            R.color.iconTint
        )
        IconLoader.applySvg(
            findViewById(R.id.hMoreIcon),
            "menu",
            R.color.iconTint
        )
        IconLoader.applySvg(
            findViewById(R.id.hCatChevron),
            "chevron-down",
            R.color.iconTint
        )
        IconLoader.applySvg(
            biAdd.findViewById(R.id.biAddIcon),
            "add",
            R.color.iconTint
        )
        IconLoader.applySvg(
            biSend.findViewById(R.id.biSendIcon),
            "arrow_up",
            R.color.onpri
        )

        drawerPanel.setBackgroundColor(
            ContextCompat.getColor(this, R.color.bgElevated)
        )
        findViewById<View>(R.id.drawerHeader).setBackgroundColor(
            ContextCompat.getColor(this, R.color.bgElevated)
        )
        findViewById<TextView>(R.id.drName).setTextColor(
            ContextCompat.getColor(this, R.color.text)
        )
        findViewById<TextView>(R.id.drUser).setTextColor(
            ContextCompat.getColor(this, R.color.dim)
        )
        findViewById<TextView>(R.id.sideTitle)?.setTextColor(
            ContextCompat.getColor(this, R.color.text)
        )
        IconLoader.applyPng(findViewById(R.id.drAvatar), "avatar")
        IconLoader.applyPng(findViewById(R.id.drIconVerify), "verified")
        IconLoader.applyPng(findViewById(R.id.drIconSaved), "bookmark")
        IconLoader.applyPng(findViewById(R.id.drIconSettings), "settings")
        IconLoader.applyPng(findViewById(R.id.drIconFriends), "profile")
        IconLoader.applyPng(findViewById(R.id.drIconReport), "alert")
        IconLoader.applyPng(findViewById(R.id.drIconAi), "magic_wand")
        IconLoader.applySvg(
            findViewById(R.id.drBackIcon),
            "arrow_left",
            R.color.iconTint
        )
        adapter.refreshTopCards()
    }

    override fun dispatchTouchEvent(
        event: MotionEvent
    ): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is EditText) {
                val rect = Rect()
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

    override fun onDestroy() {
        progressAnimator?.cancel()
        barAnimator?.cancel()
        pullSpinnerAnimator?.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
