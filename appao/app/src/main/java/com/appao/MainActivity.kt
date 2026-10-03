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
import kotlinx.coroutines.launch

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
        "Todas","Angola","Internacional","Economia","Desporto",
        "Tecnologia","Cultura","Política","Saúde","Local 📍"
    )
    private var currentCat = "Todas"
    private var page = 1
    private var loading = false
    private var exhausted = false
    private var drawerOpen = false
    private var lastScrollY = 0
    private var headerHidden = false
    private var headerHeight = 0
    private var headerAnimator: android.view.ViewPropertyAnimator? = null
    private var pillAnimator: android.view.ViewPropertyAnimator? = null

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

        val root = findViewById<View>(R.id.rootMain)

        ViewCompat.setOnApplyWindowInsetsListener(progress) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.topMargin = sys.top
            v.layoutParams = lp
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = sys.top + dp(4))
            val lp = v.layoutParams
            lp.height = dp(56) + sys.top
            v.layoutParams = lp
            headerHeight = lp.height
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(biPill) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bottom = maxOf(ime, sys.bottom)
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = bottom + dp(16)
            v.layoutParams = lp
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(drawerPanel) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = sys.top, bottom = sys.bottom)
            insets
        }

        adapter = NewsAdapter(items.toMutableList<Any>()) { openArticle(it) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        recycler.setHasFixedSize(true)
        recycler.itemAnimator = null
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val y = rv.computeVerticalScrollOffset()
                val delta = y - lastScrollY
                if (delta > 6 && y > dp(40)) hideAppBar()
                else if (delta < -6) showAppBar()
                lastScrollY = y
                if (!rv.canScrollVertically(1) && !loading && !exhausted) loadMore()
            }
        })
        swipe.setOnRefreshListener {
            NewsRepository.resetDedup()
            loadNews(true)
        }

        IconLoader.applySvg(findViewById(R.id.hMoreIcon), "menu", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.hCatChevron), "chevron-down", R.color.iconTint)
        IconLoader.applySvg(biAdd.findViewById(R.id.biAddIcon), "add", R.color.iconTint)
        IconLoader.applySvg(biSend.findViewById(R.id.biSendIcon), "arrow-up", R.color.onpri)
        IconLoader.applyPng(findViewById(R.id.wIcon), "sunny")
        IconLoader.applyPng(findViewById(R.id.drIconProfile), "profile")
        IconLoader.applyPng(findViewById(R.id.drIconLibrary), "bookmark")
        IconLoader.applyPng(findViewById(R.id.drIconSettings), "settings")
        IconLoader.applyPng(findViewById(R.id.drAvatar), "avatar")

        biInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { setSendVisible(!s.isNullOrBlank()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        biInput.setOnFocusChangeListener { _, focused -> animatePillFocus(focused) }
        biSend.setOnClickListener { sendToAi() }
        biInput.setOnEditorActionListener { _, _, _ -> sendToAi(); true }
        biAdd.setOnClickListener { v -> showAppsPopup(v) }

        findViewById<View>(R.id.hMore).setOnClickListener { openDrawer() }
        findViewById<View>(R.id.hCat).setOnClickListener { openCatSheet() }
        drawerScrim.setOnClickListener { closeDrawer() }

        findViewById<View>(R.id.drProfile).setOnClickListener {
            closeDrawer(); startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.drLibrary).setOnClickListener {
            closeDrawer(); startActivity(Intent(this, LibraryActivity::class.java))
        }
        findViewById<View>(R.id.drSettings).setOnClickListener {
            closeDrawer(); startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupDrawerDrag()

        adapter.showSkeleton(8)
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val cached = NewsRepository.readCache(this@MainActivity)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                if (cached.isNotEmpty() && items.isEmpty()) {
                    items.clear()
                    items.addAll(cached)
                    adapter.submit(cached)
                }
                loadNews(false)
            }
        }
        initWeather()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun hideAppBar() {
        if (headerHidden) return
        headerHidden = true
        headerAnimator?.cancel()
        pillAnimator?.cancel()
        val headerDistance = if (headerHeight > 0) headerHeight.toFloat() else header.height.toFloat()
        headerAnimator = header.animate().translationY(-headerDistance)
            .setDuration(260).setInterpolator(Curves.SMOOTH)
        headerAnimator?.start()
        val pillDistance = biPill.height.toFloat() + dp(120)
        pillAnimator = biPill.animate().translationY(pillDistance)
            .setDuration(260).setInterpolator(Curves.SMOOTH)
        pillAnimator?.start()
    }

    private fun showAppBar() {
        if (!headerHidden) return
        headerHidden = false
        headerAnimator?.cancel()
        pillAnimator?.cancel()
        headerAnimator = header.animate().translationY(0f)
            .setDuration(260).setInterpolator(Curves.SMOOTH)
        pillAnimator = biPill.animate().translationY(0f)
            .setDuration(260).setInterpolator(Curves.SMOOTH)
        headerAnimator?.start(); pillAnimator?.start()
    }

    private fun animatePillFocus(focused: Boolean) {
        val target = if (focused) dp(340) else dp(320)
        val lp = biPill.layoutParams
        ValueAnimator.ofInt(lp.width, target).apply {
            duration = 400
            interpolator = Curves.IOS
            addUpdateListener {
                lp.width = it.animatedValue as Int
                biPill.layoutParams = lp
            }
            start()
        }
    }

    private fun setSendVisible(has: Boolean) {
        if (has) {
            val lp = biSend.layoutParams as LinearLayout.LayoutParams
            lp.width = dp(36); lp.marginStart = dp(2)
            biSend.layoutParams = lp
            biSend.visibility = View.VISIBLE
            biSend.scaleX = 0.4f; biSend.scaleY = 0.4f; biSend.alpha = 0f
            biSend.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(300).setInterpolator(Curves.SPRING).start()
        } else {
            biSend.animate().alpha(0f).scaleX(0.4f).scaleY(0.4f)
                .setDuration(250).withEndAction {
                    val lp = biSend.layoutParams as LinearLayout.LayoutParams
                    lp.width = 0; lp.marginStart = 0
                    biSend.layoutParams = lp
                    biSend.visibility = View.GONE
                }.start()
        }
    }

    private fun loadNews(force: Boolean) {
        if (loading) return
        loading = true
        exhausted = false
        page = 1

        if (force || items.isEmpty()) adapter.showSkeleton(8)

        lifecycleScope.launch {
            val result = try {
                when (currentCat) {
                    "Todas" -> NewsRepository.fetchGeneral(page)
                    "Angola" -> NewsRepository.fetchByCategory("general", page, country = "AO")
                    "Internacional" -> NewsRepository.fetchByCategory("general", page, country = "")
                    "Economia" -> NewsRepository.fetchByCategory("business", page)
                    "Desporto" -> NewsRepository.fetchByCategory("sports", page)
                    "Tecnologia" -> NewsRepository.fetchByCategory("technology", page)
                    "Cultura" -> NewsRepository.fetchByCategory("entertainment", page)
                    "Política" -> NewsRepository.fetchByCategory("politics", page)
                    "Saúde" -> NewsRepository.fetchByCategory("health", page)
                    else -> NewsRepository.fetchGeneral(page)
                }
            } catch (_: Exception) {
                emptyList()
            }

            if (isFinishing || isDestroyed) return@launch
            swipe.isRefreshing = false
            loading = false
            if (result.isNotEmpty()) {
                items.clear()
                items.addAll(result)
                NewsRepository.writeCache(this@MainActivity, result)
                adapter.submit(result)
            } else if (items.isEmpty()) {
                adapter.submit(emptyList())
            }
        }
    }

    private fun loadMore() {
        if (loading || exhausted) return
        loading = true
        val next = page + 1
        lifecycleScope.launch {
            val result = try {
                when (currentCat) {
                    "Todas" -> NewsRepository.fetchGeneral(next)
                    "Angola" -> NewsRepository.fetchByCategory("general", next, country = "AO")
                    "Internacional" -> NewsRepository.fetchByCategory("general", next, country = "")
                    "Economia" -> NewsRepository.fetchByCategory("business", next)
                    "Desporto" -> NewsRepository.fetchByCategory("sports", next)
                    "Tecnologia" -> NewsRepository.fetchByCategory("technology", next)
                    "Cultura" -> NewsRepository.fetchByCategory("entertainment", next)
                    "Política" -> NewsRepository.fetchByCategory("politics", next)
                    "Saúde" -> NewsRepository.fetchByCategory("health", next)
                    else -> NewsRepository.fetchGeneral(next)
                }
            } catch (_: Exception) {
                emptyList()
            }
            if (isFinishing || isDestroyed) return@launch
            loading = false
            if (result.isEmpty()) {
                exhausted = true
                return@launch
            }
            val seen = items.map { it.id }.toHashSet()
            val newOnes = result.filter { it.id !in seen }
            if (newOnes.isEmpty()) {
                exhausted = true
                return@launch
            }
            page = next
            items.addAll(newOnes)
            adapter.append(newOnes)
            NewsRepository.writeCache(this@MainActivity, items)
        }
    }

    private fun initWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }
        loadWeatherFor(-8.84, 13.23)
    }

    private fun loadWeatherFor(lat: Double, lon: Double) {
        lifecycleScope.launch {
            val r = WeatherHelper.fetch(this@MainActivity, lat, lon) ?: return@launch
            IconLoader.applyPng(findViewById(R.id.wIcon), r.icon)
            findViewById<TextView>(R.id.wTemp).text = "${r.temp}°"
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
        refreshNativeThemeViews()
    }

    private fun refreshNativeThemeViews() {
        recycler.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        findViewById<View>(R.id.rootMain).setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        header.setBackgroundColor(ContextCompat.getColor(this, R.color.bg))
        biPill.background = ContextCompat.getDrawable(this, R.drawable.bg_pill_card)
        IconLoader.applySvg(findViewById(R.id.hMoreIcon), "menu", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.hCatChevron), "chevron-down", R.color.iconTint)
        IconLoader.applySvg(biAdd.findViewById(R.id.biAddIcon), "add", R.color.iconTint)
        IconLoader.applySvg(biSend.findViewById(R.id.biSendIcon), "arrow-up", R.color.onpri)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 100 && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
            try {
                val lm = getSystemService(LOCATION_SERVICE) as LocationManager
                val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (loc != null) loadWeatherFor(loc.latitude, loc.longitude)
            } catch (_: SecurityException) {}
        }
    }

    private fun sendToAi() {
        val q = biInput.text.toString().trim()
        if (q.isEmpty()) return
        showNavProgress()
        biInput.setText("")
        recycler.postDelayed({
            startActivity(Intent(this, AppViewerActivity::class.java).apply {
                putExtra("url", "file:///android_asset/apps/ai.html?q=${java.net.URLEncoder.encode(q, "UTF-8")}")
            })
        }, 100)
    }

    private fun showNavProgress() {
        progress.visibility = View.VISIBLE
        val insetTop = ViewCompat.getRootWindowInsets(progress)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        val currentLp = progress.layoutParams as FrameLayout.LayoutParams
        currentLp.topMargin = insetTop
        progress.layoutParams = currentLp
        progress.alpha = 1f
        progress.layoutParams.width = 0
        progress.requestLayout()
        progress.post {
            val target = (resources.displayMetrics.widthPixels * 0.88f).toInt()
            ValueAnimator.ofInt(0, target).apply {
                duration = 1400
                interpolator = Curves.PROGRESS
                addUpdateListener {
                    progress.layoutParams.width = it.animatedValue as Int
                    progress.requestLayout()
                }
                start()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
        if (progress.alpha > 0f) {
            progress.animate().alpha(0f).setDuration(250).withEndAction {
                progress.layoutParams.width = 0
                progress.requestLayout()
            }.start()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is EditText) {
                val r = Rect()
                focused.getGlobalVisibleRect(r)
                if (!r.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    focused.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(focused.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun showAppsPopup(anchor: View) {
        anchor.animate().scaleX(0.88f).scaleY(0.88f).setDuration(120)
            .withEndAction { anchor.animate().scaleX(1f).scaleY(1f).setDuration(220).start() }.start()
        AppsPopup.show(anchor) { app ->
            if (app.url != null) {
                showNavProgress()
                recycler.postDelayed({
                    startActivity(Intent(this, AppViewerActivity::class.java).apply {
                        putExtra("url", "file:///android_asset/${app.url}")
                    })
                }, 100)
            }
        }
    }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        drawerScrim.visibility = View.VISIBLE
        drawerScrim.animate().alpha(1f).setDuration(450).setInterpolator(Curves.IOS).start()
        drawerPanel.translationX = dp(300).toFloat()
        drawerPanel.animate().translationX(0f).setDuration(480).setInterpolator(Curves.SMOOTH).start()
    }
    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        drawerScrim.animate().alpha(0f).setDuration(450).setInterpolator(Curves.IOS)
            .withEndAction { drawerScrim.visibility = View.GONE }.start()
        drawerPanel.animate().translationX(dp(300).toFloat()).setDuration(480).setInterpolator(Curves.SMOOTH).start()
    }
    private fun setupDrawerDrag() {
        var x0 = 0f; var lastX = 0f; var lastT = 0L; var vx = 0f
        drawerPanel.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = ev.rawX; lastX = x0; lastT = System.currentTimeMillis(); vx = 0f
                    drawerPanel.animate().cancel(); true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - x0).coerceAtLeast(0f)
                    val now = System.currentTimeMillis(); val dt = now - lastT
                    if (dt > 0) vx = (ev.rawX - lastX) / dt
                    lastX = ev.rawX; lastT = now
                    drawerPanel.translationX = dx; true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dismiss = drawerPanel.translationX > dp(70) || (vx > 0.5f && drawerPanel.translationX > dp(20))
                    if (dismiss) {
                        drawerScrim.animate().alpha(0f).setDuration(300).start()
                        drawerPanel.animate().translationX(dp(300).toFloat()).setDuration(320)
                            .setInterpolator(Curves.SMOOTH)
                            .withEndAction { drawerScrim.visibility = View.GONE }.start()
                        drawerOpen = false
                    } else {
                        drawerPanel.animate().translationX(0f).setDuration(280)
                            .setInterpolator(Curves.SMOOTH).start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun openCatSheet() {
        val view = layoutInflater.inflate(R.layout.sheet_categories, null)
        val sheet = NativeSheetDialog.show(this, view)
        val list = view.findViewById<LinearLayout>(R.id.catList)
        for (c in categories) {
            val tv = TextView(this).apply {
                text = c
                setTextColor(getColor(R.color.text))
                textSize = 16f
                setPadding(dp(22), dp(18), dp(22), dp(18))
                isClickable = true; isFocusable = true
                background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.list_selector_background)
                if (c == currentCat) setTypeface(null, android.graphics.Typeface.BOLD)
            }
            tv.setOnClickListener {
                currentCat = c
                findViewById<TextView>(R.id.hCatLabel).text = c
                sheet.dismiss()
                NewsRepository.resetDedup()
                adapter.showSkeleton(8)
                loadNews(true)
            }
            list.addView(tv)
        }
    }

    private fun openArticle(item: NewsItem) {
        startActivity(Intent(this, ArticleActivity::class.java).apply {
            putExtra("id", item.id); putExtra("title", item.title)
            putExtra("summary", item.summary); putExtra("link", item.link)
            putExtra("source", item.source); putExtra("date", item.date)
            putExtra("image", item.image); putExtra("logo", item.logo)
        })
        overridePendingTransition(R.anim.slide_in_right_fast, R.anim.slide_out_left_fast)
    }
}
