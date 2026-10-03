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
    private lateinit var header: View
    private lateinit var drawerPanel: View
    private lateinit var drawerScrim: View

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

    private var headerHeight = 0
    private var bottomBarHeight = 0
    private var barProgress = 0f
    private var barAnimator: ValueAnimator? = null
    private var progressAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        SystemBarHelper.sync(this)

        progress = findViewById(R.id.appProgress)
        recycler = findViewById(R.id.recycler)
        swipe = findViewById(R.id.swipe)
        biPill = findViewById(R.id.biPill)
        biInput = findViewById(R.id.biInput)
        biSend = findViewById(R.id.biSend)
        biAdd = findViewById(R.id.biAdd)
        header = findViewById(R.id.header)
        drawerPanel = findViewById(R.id.drawerPanel)
        drawerScrim = findViewById(R.id.drawerScrim)

        setupInsets()
        setupFeed()
        setupHeader()
        setupBottomInput()
        setupDrawer()
        setupCategories()
        setupInitialData()
        initWeather()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(progress) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val lp = view.layoutParams as FrameLayout.LayoutParams
            lp.topMargin = top
            view.layoutParams = lp
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(header) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top + dp(4))
            view.layoutParams = view.layoutParams.apply {
                height = dp(56) + bars.top
            }
            headerHeight = view.layoutParams.height
            applyBarTranslation(barProgress)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(biPill) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bottom = maxOf(ime, bars.bottom)
            val lp = view.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = bottom + dp(16)
            view.layoutParams = lp
            bottomBarHeight = view.height
            applyBarTranslation(barProgress)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(drawerPanel) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    private fun setupFeed() {
        adapter = NewsAdapter(items.toList()) { item, source ->
            openArticle(item, source)
        }

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

        swipe.setOnRefreshListener {
            NewsRepository.resetDedup()
            resetBarsInstantly()
            loadNews(true)
        }
    }

    private fun setupHeader() {
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
        IconLoader.applyPng(
            findViewById(R.id.wIcon),
            "sunny"
        )

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
            "arrow-up",
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
        drawerScrim.setOnClickListener {
            closeDrawer()
        }

        findViewById<View>(R.id.drProfile).setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.drLibrary).setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, LibraryActivity::class.java))
        }

        findViewById<View>(R.id.drSettings).setOnClickListener {
            closeDrawer()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupDrawerDrag()
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

                IconLoader.applyPng(
                    findViewById(R.id.wIcon),
                    result.icon
                )
                findViewById<TextView>(R.id.wTemp).text =
                    "${result.temp}°"
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

    private fun openDrawer() {
        if (drawerOpen) return

        drawerOpen = true
        drawerScrim.visibility = View.VISIBLE
        drawerPanel.translationX = dp(300).toFloat()

        drawerScrim.animate()
            .alpha(1f)
            .setDuration(450L)
            .setInterpolator(Curves.IOS)
            .start()

        drawerPanel.animate()
            .translationX(0f)
            .setDuration(480L)
            .setInterpolator(Curves.SMOOTH)
            .start()
    }

    private fun closeDrawer() {
        if (!drawerOpen) return

        drawerOpen = false

        drawerScrim.animate()
            .alpha(0f)
            .setDuration(300L)
            .setInterpolator(Curves.IOS)
            .withEndAction {
                drawerScrim.visibility = View.GONE
            }
            .start()

        drawerPanel.animate()
            .translationX(dp(300).toFloat())
            .setDuration(320L)
            .setInterpolator(Curves.SMOOTH)
            .start()
    }

    private fun setupDrawerDrag() {
        var startX = 0f
        var lastX = 0f
        var lastTime = 0L
        var velocity = 0f
        var tracking = false

        drawerPanel.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    lastX = startX
                    lastTime = System.currentTimeMillis()
                    velocity = 0f
                    tracking = true
                    drawerPanel.animate().cancel()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!tracking) return@setOnTouchListener true

                    val dx = (event.rawX - startX).coerceAtLeast(0f)
                    val now = System.currentTimeMillis()
                    val dt = now - lastTime

                    if (dt > 0) {
                        velocity = (event.rawX - lastX) / dt
                    }

                    lastX = event.rawX
                    lastTime = now
                    drawerPanel.translationX = dx
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (!tracking) return@setOnTouchListener true
                    tracking = false

                    val shouldClose =
                        drawerPanel.translationX > dp(70) ||
                            (velocity > 0.5f && drawerPanel.translationX > dp(20))

                    if (shouldClose) {
                        closeDrawer()
                    } else {
                        drawerPanel.animate()
                            .translationX(0f)
                            .setDuration(280L)
                            .setInterpolator(Curves.SMOOTH)
                            .start()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun openCatSheet() {
        val content = layoutInflater.inflate(
            R.layout.sheet_categories,
            null,
            false
        )

        val list = content.findViewById<LinearLayout>(R.id.catList)
        val dialog = NativeSheetDialog.show(this, content)

        categories.forEach { category ->
            val item = TextView(this).apply {
                text = category
                textSize = 16f
                setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        R.color.text
                    )
                )
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(
                    dp(22),
                    dp(16),
                    dp(22),
                    dp(16)
                )
                isClickable = true
                isFocusable = true
                background = ContextCompat.getDrawable(
                    this@MainActivity,
                    R.drawable.bg_sheet_item
                )
                if (category == currentCat) {
                    setTypeface(
                        android.graphics.Typeface.DEFAULT,
                        android.graphics.Typeface.BOLD
                    )
                }
            }

            item.setOnClickListener {
                currentCat = category
                findViewById<TextView>(R.id.hCatLabel).text = category
                dialog.dismiss()
                NewsRepository.resetDedup()
                resetBarsInstantly()
                loadNews(true)
            }

            list.addView(item)
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
            "arrow-up",
            R.color.onpri
        )
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
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
