package com.appao

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
    private lateinit var catScrim: View
    private lateinit var catSheet: View
    private lateinit var drawerScrim: View
    private lateinit var drawerPanel: View
    private lateinit var header: View

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
    private var catSheetOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(
            if (isSystemDark()) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        progress = findViewById(R.id.appProgress)
        recycler = findViewById(R.id.recycler)
        swipe = findViewById(R.id.swipe)
        biPill = findViewById(R.id.biPill)
        biInput = findViewById(R.id.biInput)
        biSend = findViewById(R.id.biSend)
        biAdd = findViewById(R.id.biAdd)
        catScrim = findViewById(R.id.catScrim)
        catSheet = findViewById(R.id.catSheet)
        drawerScrim = findViewById(R.id.drawerScrim)
        drawerPanel = findViewById(R.id.drawerPanel)
        header = findViewById(R.id.header)

        // padding top só no header (para a statusbar) — drawer NÃO recebe
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = top + (6 * resources.displayMetrics.density).toInt())
            insets
        }
        // pill recebe bottom padding de navegação
        ViewCompat.setOnApplyWindowInsetsListener(biPill) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = b + (20 * resources.displayMetrics.density).toInt()
            v.layoutParams = lp
            insets
        }
        // catSheet recebe bottom
        ViewCompat.setOnApplyWindowInsetsListener(catSheet) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, b + (12 * resources.displayMetrics.density).toInt())
            insets
        }

        adapter = NewsAdapter(items) { openArticle(it) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!rv.canScrollVertically(1) && !loading && !exhausted) loadMore()
            }
        })
        swipe.setOnRefreshListener { loadNews(true) }

        // ícones
        IconLoader.applySvg(findViewById(R.id.hMoreIcon), "menu", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.hCatChevron), "chevron-down", R.color.iconTint)
        IconLoader.applySvg(biAdd.findViewById(R.id.biAddIcon), "add", R.color.iconTint)
        IconLoader.applySvg(biSend.findViewById(R.id.biSendIcon), "arrow-up", R.color.onpri)
        IconLoader.applyPng(findViewById(R.id.wIcon), "sunny")
        IconLoader.applyPng(findViewById(R.id.drIconProfile), "profile")
        IconLoader.applyPng(findViewById(R.id.drIconLibrary), "bookmark")
        IconLoader.applyPng(findViewById(R.id.drIconSettings), "settings")
        IconLoader.applyPng(findViewById(R.id.drAvatar), "avatar")

        // input
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
        catScrim.setOnClickListener { closeCatSheet() }
        drawerScrim.setOnClickListener { closeDrawer() }

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

        buildCatList()
        setupDrawerDrag()
        setupCatSheetDrag()

        loadNews(false)
        initWeather()
    }

    private fun isSystemDark(): Boolean {
        val mode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun animatePillFocus(focused: Boolean) {
        val target = if (focused) dp(340) else dp(320)
        val lp = biPill.layoutParams
        val start = lp.width
        ValueAnimator.ofInt(start, target).apply {
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
            lp.width = dp(34); lp.marginStart = dp(2)
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
        loading = true; exhausted = false; page = 1
        if (!force) swipe.isRefreshing = true
        lifecycleScope.launch {
            val result = NewsRepository.fetchGeneral(page)
            swipe.isRefreshing = false
            loading = false
            if (result.isNotEmpty()) { items.clear(); items.addAll(result); adapter.submit(result) }
        }
    }

    private fun loadMore() {
        if (loading || exhausted) return
        loading = true
        val next = page + 1
        lifecycleScope.launch {
            val result = NewsRepository.fetchGeneral(next)
            loading = false
            if (result.isEmpty()) { exhausted = true; return@launch }
            val seen = items.map { it.id }.toHashSet()
            val newOnes = result.filter { it.id !in seen }
            if (newOnes.isEmpty()) { exhausted = true; return@launch }
            page = next
            items.addAll(newOnes)
            adapter.append(newOnes)
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
                putExtra("title", "Inteligência Artificial")
            })
        }, 100)
    }

    private fun showNavProgress() {
        progress.visibility = View.VISIBLE
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
        if (progress.alpha > 0f) {
            progress.animate().alpha(0f).setDuration(250).withEndAction {
                progress.layoutParams.width = 0
                progress.requestLayout()
            }.start()
        }
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
                        putExtra("title", app.name)
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
                android.view.MotionEvent.ACTION_DOWN -> {
                    x0 = ev.rawX; lastX = x0; lastT = System.currentTimeMillis(); vx = 0f
                    drawerPanel.animate().cancel(); true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - x0).coerceAtLeast(0f)
                    val now = System.currentTimeMillis(); val dt = now - lastT
                    if (dt > 0) vx = (ev.rawX - lastX) / dt
                    lastX = ev.rawX; lastT = now
                    drawerPanel.translationX = dx; true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
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

    private fun buildCatList() {
        val list = findViewById<LinearLayout>(R.id.catList)
        list.removeAllViews()
        for (c in categories) {
            val tv = TextView(this).apply {
                text = c
                setTextColor(getColor(R.color.text))
                textSize = 16f
                setPadding(dp(22), dp(16), dp(22), dp(16))
                isClickable = true; isFocusable = true
                background = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.list_selector_background)
                if (c == currentCat) setTypeface(null, android.graphics.Typeface.BOLD)
            }
            tv.setOnClickListener {
                currentCat = c
                findViewById<TextView>(R.id.hCatLabel).text = c
                buildCatList()
                closeCatSheet()
                loadNews(false)
            }
            list.addView(tv)
        }
    }

    private fun openCatSheet() {
        if (catSheetOpen) return
        catSheetOpen = true
        catScrim.visibility = View.VISIBLE
        catScrim.animate().alpha(1f).setDuration(300).setInterpolator(Curves.SMOOTH).start()
        catSheet.visibility = View.VISIBLE
        catSheet.post {
            val h = catSheet.height.toFloat().coerceAtLeast(400f)
            catSheet.translationY = h + 40f
            catSheet.animate().translationY(0f).setDuration(400).setInterpolator(Curves.SMOOTH).start()
        }
    }
    private fun closeCatSheet() {
        if (!catSheetOpen) return
        catSheetOpen = false
        catScrim.animate().alpha(0f).setDuration(300).setInterpolator(Curves.SMOOTH)
            .withEndAction { catScrim.visibility = View.GONE }.start()
        catSheet.animate().translationY(dp(800).toFloat()).setDuration(400).setInterpolator(Curves.SMOOTH)
            .withEndAction { catSheet.visibility = View.GONE }.start()
    }
    private fun setupCatSheetDrag() {
        var y0 = 0f; var lastY = 0f; var lastT = 0L; var vy = 0f; var h = 1f
        catSheet.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    y0 = ev.rawY; lastY = y0; lastT = System.currentTimeMillis(); vy = 0f
                    h = catSheet.height.toFloat().coerceAtLeast(1f)
                    catSheet.animate().cancel(); true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dy = (ev.rawY - y0).coerceAtLeast(0f)
                    val now = System.currentTimeMillis(); val dt = now - lastT
                    if (dt > 0) vy = (ev.rawY - lastY) / dt
                    lastY = ev.rawY; lastT = now
                    catSheet.translationY = dy
                    catScrim.alpha = (1f - dy / (h * 0.9f)).coerceIn(0f, 1f)
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    val dy = catSheet.translationY
                    val dismiss = dy > h * 0.28f || (vy > 0.6f && dy > dp(24))
                    if (dismiss) {
                        catSheet.animate().translationY(dp(800).toFloat()).setDuration(260)
                            .setInterpolator(Curves.SMOOTH)
                            .withEndAction {
                                catSheet.visibility = View.GONE
                                catSheet.translationY = 0f
                            }.start()
                        catScrim.animate().alpha(0f).setDuration(260)
                            .withEndAction { catScrim.visibility = View.GONE }.start()
                        catSheetOpen = false
                    } else {
                        catSheet.animate().translationY(0f).setDuration(280).setInterpolator(Curves.SMOOTH).start()
                        catScrim.animate().alpha(1f).setDuration(280).start()
                    }
                    true
                }
                else -> false
            }
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
