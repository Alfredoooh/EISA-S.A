package com.appao.screens

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appao.EmptyNewsIllustrationDrawable
import com.appao.MainActivity
import com.appao.R
import com.appao.SystemBarHelper
import com.appao.ui.IconLoader
import com.appao.ui.NativePopupMenu
import com.bumptech.glide.Glide
import java.io.File


/** Browser overview. The original WebView activity remains underneath, preserving the live page. */
class BrowserTabsActivity : AppCompatActivity() {
    private lateinit var list: RecyclerView
    private lateinit var adapter: TabAdapter
    private lateinit var emptyState: TextView
    private lateinit var normalModeTab: TextView
    private lateinit var privateModeTab: TextView
    private var sourceTabId: String = ""
    private var sourcePrivateMode: Boolean = false
    private var privateMode: Boolean = false
    private var homeSnapshotPath: String = ""
    private var firstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        SystemBarHelper.sync(this)
        sourceTabId = intent.getStringExtra("source_tab_id").orEmpty()
        sourcePrivateMode = intent.getBooleanExtra("browser_private_mode", false)
        privateMode = sourcePrivateMode
        homeSnapshotPath = intent.getStringExtra("home_snapshot_path").orEmpty().ifBlank {
            getSharedPreferences("appao", MODE_PRIVATE).getString("home_tab_snapshot_path", "").orEmpty()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.bg))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(5), dp(12), dp(5))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66))
        }
        top.addView(iconButton("close", "Fechar vista de separadores").apply {
            setOnClickListener { finishWithMotion() }
        }, LinearLayout.LayoutParams(dp(40), dp(44)))

        val segment = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                setColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.card))
                setStroke(dp(1), ContextCompat.getColor(this@BrowserTabsActivity, R.color.border))
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        normalModeTab = TextView(this).apply {
            text = "Separadores"; gravity = Gravity.CENTER; textSize = 13.5f
            setPadding(dp(10), 0, dp(10), 0); isClickable = true; isFocusable = true
            setOnClickListener { switchMode(false) }
        }
        privateModeTab = TextView(this).apply {
            text = "Privados"; gravity = Gravity.CENTER; textSize = 13.5f
            setPadding(dp(10), 0, dp(10), 0); isClickable = true; isFocusable = true
            setOnClickListener { switchMode(true) }
        }
        segment.addView(normalModeTab, LinearLayout.LayoutParams(0, dp(44), 1f))
        segment.addView(privateModeTab, LinearLayout.LayoutParams(0, dp(44), 1f))
        top.addView(segment, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginStart = dp(8); marginEnd = dp(8) })
        top.addView(iconButton("home", "Ir para o início").apply {
            setOnClickListener {
                val launch = Intent(this@BrowserTabsActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("appao_main_tab", "home")
                startActivity(launch)
                finish()
                overridePendingTransition(R.anim.browser_fade_in, R.anim.browser_fade_out)
            }
        }, LinearLayout.LayoutParams(dp(40), dp(44)))
        root.addView(top)

        val content = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f) }
        list = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@BrowserTabsActivity, 2)
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(dp(12), dp(8), dp(12), dp(18))
            // Full-list refreshes while Glide thumbnails are loading should not animate stale holders.
            itemAnimator = null
            setItemViewCacheSize(4)
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    outRect.set(dp(5), dp(5), dp(5), dp(5))
                }
            })
        }
        adapter = TabAdapter()
        list.adapter = adapter
        content.addView(list, FrameLayout.LayoutParams(-1, -1))
        emptyState = TextView(this).apply {
            text = "Ainda não há separadores privados.\n\nToca aqui para abrir um separador privado."
            textSize = 14f; gravity = Gravity.CENTER; setPadding(dp(28), dp(28), dp(28), dp(28))
            setTextColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.dim))
            visibility = View.GONE; isClickable = true; isFocusable = true
            setOnClickListener { openNewTab() }
        }
        content.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        root.addView(content)
        setContentView(root)
        refreshSegment()
        refreshTabs()
    }

    override fun onResume() {
        super.onResume()
        if (firstResume) {
            firstResume = false
            return
        }
        if (::adapter.isInitialized) refreshTabs()
    }

    private fun switchMode(isPrivateMode: Boolean) {
        if (privateMode == isPrivateMode) return
        privateMode = isPrivateMode
        refreshSegment()
        refreshTabs()
    }

    private fun refreshSegment() {
        val selectedText = ContextCompat.getColor(this, R.color.text)
        val inactiveText = ContextCompat.getColor(this, R.color.dim)
        val selectedSurface = ContextCompat.getColor(this, R.color.bg)
        val border = ContextCompat.getColor(this, R.color.border)
        fun selectedBackground(selected: Boolean) = if (selected) GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(999).toFloat()
            setColor(selectedSurface)
            setStroke(dp(1), border)
        } else null
        normalModeTab.background = selectedBackground(!privateMode)
        privateModeTab.background = selectedBackground(privateMode)
        normalModeTab.elevation = if (!privateMode && !com.appao.ThemeManager.resolvedDark(this)) dp(1).toFloat() else 0f
        privateModeTab.elevation = if (privateMode && !com.appao.ThemeManager.resolvedDark(this)) dp(1).toFloat() else 0f
        normalModeTab.setTextColor(if (!privateMode) selectedText else inactiveText)
        normalModeTab.typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            if (!privateMode) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        )
        privateModeTab.setTextColor(if (privateMode) selectedText else inactiveText)
        privateModeTab.typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            if (privateMode) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        )
    }

    private fun refreshTabs() {
        if (!::adapter.isInitialized) return
        val tabs = if (privateMode) PrivateBrowserTabStore.tabs() else BrowserTabStore.tabs(this)
        val display = if (privateMode) tabs else listOf(BrowserTab("__home__", "appao://home", "Início", "", "", Long.MAX_VALUE)) + tabs
        adapter.submit(display)
        emptyState.visibility = View.GONE
        list.visibility = View.VISIBLE
        emptyState.setTextColor(ContextCompat.getColor(this, R.color.dim))
    }

    private fun openNewTab() {
        val tab = if (privateMode) PrivateBrowserTabStore.create(this, "https://www.google.com/") else BrowserTabStore.create(this, "https://www.google.com/")
        val launch = Intent(this, AppViewerActivity::class.java).apply {
            putExtra("browser_mode", true)
            putExtra("browser_private_mode", privateMode)
            putExtra("browser_tab_id", tab.id)
            putExtra("url", tab.url)
        }
        startActivity(launch)
        finish()
        overridePendingTransition(R.anim.browser_fade_in, R.anim.browser_fade_out)
    }

    private fun openTab(tab: BrowserTab, preview: View?, targetPrivateMode: Boolean = privateMode) {
        if (tab.id == "__home__") {
            val launch = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("appao_main_tab", "home")
            startActivity(launch)
            finish()
            overridePendingTransition(R.anim.browser_fade_in, R.anim.browser_fade_out)
            return
        }
        if (tab.id == sourceTabId && targetPrivateMode == sourcePrivateMode) {
            // Returning to the already-live page reverses the shared container transform.
            finishWithMotion()
            return
        }

        val launch = Intent(this, AppViewerActivity::class.java).apply {
            putExtra("browser_mode", true)
            putExtra("browser_private_mode", targetPrivateMode)
            putExtra("browser_tab_id", tab.id)
            putExtra("url", tab.url)
        }
        startActivity(launch)
        finish()
        overridePendingTransition(R.anim.browser_fade_in, R.anim.browser_fade_out)
    }

    private fun showTabOptions(anchor: View, tab: BrowserTab, preview: View) {
        val items = if (tab.id == "__home__") listOf(
            NativePopupMenu.Item(1, "Abrir Início", iconName = "home"),
            NativePopupMenu.Item(2, "Novo separador", iconName = "plus")
        ) else listOf(
            NativePopupMenu.Item(1, "Abrir separador", iconName = "open-outline"),
            NativePopupMenu.Item(2, "Duplicar separador", iconName = "copy"),
            NativePopupMenu.Item(3, if (privateMode) "Tornar normal" else "Tornar privado", iconName = if (privateMode) "globe" else "eye_off"),
            NativePopupMenu.Item(4, "Eliminar separador", iconName = "delete_bin")
        )
        NativePopupMenu.show(anchor, items, alignLeft = false, placeAbove = false) { item ->
            when (item.id) {
                1 -> openTab(tab, preview)
                2 -> if (tab.id == "__home__") openNewTab() else {
                    val duplicate = if (privateMode) PrivateBrowserTabStore.create(this, tab.url) else BrowserTabStore.create(this, tab.url)
                    if (privateMode) PrivateBrowserTabStore.updatePage(this, duplicate.id, tab.url, tab.title)
                    else BrowserTabStore.updatePage(this, duplicate.id, tab.url, tab.title)
                    openTab(duplicate, preview, privateMode)
                }
                3 -> {
                    val targetPrivate = !privateMode
                    val moved = if (targetPrivate) PrivateBrowserTabStore.create(this, tab.url) else BrowserTabStore.create(this, tab.url)
                    if (targetPrivate) PrivateBrowserTabStore.updatePage(this, moved.id, tab.url, tab.title)
                    else BrowserTabStore.updatePage(this, moved.id, tab.url, tab.title)
                    if (privateMode) PrivateBrowserTabStore.remove(this, tab.id) else BrowserTabStore.remove(this, tab.id)
                    BrowserWebViewPool.removeAndDestroy(tab.id)
                    openTab(moved, preview, targetPrivate)
                }
                4 -> closeTab(tab)
            }
        }
    }

    private fun closeTab(tab: BrowserTab) {
        if (tab.id == "__home__") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("appao_main_tab", "home"))
            finishWithMotion()
            return
        }
        if (privateMode) PrivateBrowserTabStore.remove(this, tab.id) else BrowserTabStore.remove(this, tab.id)
        BrowserWebViewPool.removeAndDestroy(tab.id)
        if (tab.id == sourceTabId && privateMode == sourcePrivateMode) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("appao_main_tab", "home"))
            finishWithMotion()
        } else refreshTabs()
    }

    private fun finishWithMotion() {
        finish()
        overridePendingTransition(R.anim.browser_fade_in, R.anim.browser_fade_out)
    }

    private fun iconButton(icon: String, description: String): FrameLayout = FrameLayout(this).apply {
        isClickable = true; isFocusable = true; contentDescription = description
        foreground = ContextCompat.getDrawable(this@BrowserTabsActivity, R.drawable.bg_transparent_pressed)
        addView(ImageView(this@BrowserTabsActivity).apply {
            layoutParams = FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER)
            scaleType = ImageView.ScaleType.FIT_CENTER
            IconLoader.applyIcon(this, icon, R.color.iconTint)
        })
    }

    private inner class TabAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val TYPE_TAB = 0
        private val TYPE_ADD_TAB = 1
        private var data: List<BrowserTab> = emptyList()
        fun submit(next: List<BrowserTab>) {
            data = next
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = data.size + 1 // Permanent new-tab card.
        override fun getItemViewType(position: Int): Int = if (position == data.size) TYPE_ADD_TAB else TYPE_TAB

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == TYPE_ADD_TAB) {
                val card = FrameLayout(this@BrowserTabsActivity).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(18).toFloat()
                        setColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.card))
                        setStroke(dp(1), ContextCompat.getColor(this@BrowserTabsActivity, R.color.border))
                    }
                    clipToOutline = true
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Novo separador"
                    setOnClickListener { openNewTab() }
                }
                val addCircle = FrameLayout(this@BrowserTabsActivity).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.bg))
                        setStroke(dp(1), ContextCompat.getColor(this@BrowserTabsActivity, R.color.border))
                    }
                }
                val plus = ImageView(this@BrowserTabsActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    IconLoader.applyIcon(this, "plus", R.color.iconTint)
                }
                addCircle.addView(plus)
                card.addView(addCircle, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER))
                card.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160))
                return AddTabHolder(card)
            }

            val card = LinearLayout(this@BrowserTabsActivity).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.card))
                    setStroke(dp(1), ContextCompat.getColor(this@BrowserTabsActivity, R.color.border))
                }
                clipToOutline = true
                isClickable = true
                isFocusable = true
                elevation = dp(1).toFloat()
            }
            val header = LinearLayout(this@BrowserTabsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(9), dp(8), dp(7), dp(8))
            }
            val favicon = ImageView(this@BrowserTabsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            val title = TextView(this@BrowserTabsActivity).apply {
                textSize = 12.5f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))
                setTextColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.text))
                setPadding(dp(8), 0, dp(3), 0)
            }
            header.addView(favicon)
            header.addView(title, LinearLayout.LayoutParams(0, dp(28), 1f))
            card.addView(header)
            val preview = ImageView(this@BrowserTabsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(124))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.bg))
            }
            card.addView(preview)
            val holder = TabHolder(card, favicon, title, preview)
            card.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos in data.indices) openTab(data[pos], holder.preview)
            }
            return holder
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (getItemViewType(position) == TYPE_ADD_TAB) return
            val tabHolder = holder as TabHolder
            val tab = data[position]
            tabHolder.itemView.setOnLongClickListener {
                showTabOptions(tabHolder.itemView, tab, tabHolder.preview)
                true
            }
            tabHolder.title.text = tab.title.ifBlank { titleFromUrl(tab.url) }
            Glide.with(this@BrowserTabsActivity).clear(tabHolder.favicon)
            Glide.with(this@BrowserTabsActivity).clear(tabHolder.preview)
            if (tab.id == "__home__") {
                tabHolder.favicon.setImageResource(R.drawable.logo)
                val homeImage = homeSnapshotPath.takeIf { it.isNotBlank() }?.let(::File)
                if (homeImage != null && homeImage.exists()) {
                    Glide.with(this@BrowserTabsActivity).load(homeImage).centerCrop().into(tabHolder.preview)
                } else {
                    Glide.with(this@BrowserTabsActivity).clear(tabHolder.preview)
                    tabHolder.preview.setImageDrawable(com.appao.EmptyNewsIllustrationDrawable(this@BrowserTabsActivity))
                    tabHolder.preview.setBackgroundColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.bg))
                }
                tabHolder.itemView.contentDescription = "Separador Início"
                return
            }
            Glide.with(this@BrowserTabsActivity).load(tab.favicon.ifBlank { BrowserTabStore.faviconFor(tab.url) })
                .circleCrop().placeholder(R.drawable.logo).into(tabHolder.favicon)
            val imageFile = tab.snapshotPath.takeIf { it.isNotBlank() }?.let(::File)
            if (imageFile != null && imageFile.exists()) {
                Glide.with(this@BrowserTabsActivity).load(imageFile).centerCrop().placeholder(R.color.card2).into(tabHolder.preview)
            } else {
                Glide.with(this@BrowserTabsActivity).clear(tabHolder.preview)
                tabHolder.preview.setImageDrawable(null)
                tabHolder.preview.setBackgroundColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.card2))
            }
        }

    }

    private class AddTabHolder(item: View) : RecyclerView.ViewHolder(item)

    private class TabHolder(item: View, val favicon: ImageView, val title: TextView, val preview: ImageView) : RecyclerView.ViewHolder(item)

    private fun titleFromUrl(url: String): String = runCatching { android.net.Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("").ifBlank { "Nova aba" }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private class SpaceView(context: android.content.Context) : View(context)
}