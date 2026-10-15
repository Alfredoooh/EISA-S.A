package com.appao

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
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
import com.bumptech.glide.Glide
import java.io.File

/** Two-column browser tab overview with persisted WebView snapshots and shared-element transitions. */
class BrowserTabsActivity : AppCompatActivity() {
    private lateinit var list: RecyclerView
    private lateinit var adapter: TabAdapter
    private lateinit var countLabel: TextView
    private var sourceTabId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        SystemBarHelper.sync(this)
        sourceTabId = intent.getStringExtra("source_tab_id").orEmpty()
        if (sourceTabId.isNotBlank()) postponeEnterTransition()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.bg))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58))
        }
        val back = iconButton("arrow_left", "Voltar", 42)
        back.setOnClickListener { finishWithMotion() }
        top.addView(back)
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titleBox.addView(TextView(this).apply {
            text = "Separadores"
            textSize = 20f
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))
            setTextColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.text))
        })
        countLabel = TextView(this).apply {
            textSize = 11.5f
            setTextColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.dim))
            setPadding(0, dp(1), 0, 0)
        }
        titleBox.addView(countLabel)
        top.addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        val add = iconButton("plus", "Novo separador", 42)
        add.setOnClickListener { openNewTab() }
        top.addView(add)
        root.addView(top)

        list = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@BrowserTabsActivity, 2)
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(dp(12), dp(8), dp(12), dp(12))
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = 230L
                removeDuration = 190L
                moveDuration = 250L
                changeDuration = 200L
            }
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    outRect.set(dp(5), dp(5), dp(5), dp(5))
                }
            })
        }
        adapter = TabAdapter()
        list.adapter = adapter
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60))
            setBackgroundColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.bg))
        }
        bottom.addView(iconButton("arrow_left", "Voltar", 42).apply { setOnClickListener { finishWithMotion() } })
        bottom.addView(SpaceView(this), LinearLayout.LayoutParams(0, 1, 1f))
        bottom.addView(iconButton("home", "Início", 42).apply { setOnClickListener {
            startActivity(Intent(this@BrowserTabsActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finishWithMotion()
        } })
        bottom.addView(iconButton("sparkles", "Assistente IA", 42).apply { setOnClickListener {
            startActivity(Intent(this@BrowserTabsActivity, SearchActivity::class.java))
            overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
        } })
        bottom.addView(iconButton("flame", "Limpar separadores", 42).apply { setOnClickListener { clearTabs() } })
        bottom.addView(iconButton("plus", "Novo separador", 42).apply { setOnClickListener { openNewTab() } })
        root.addView(bottom)

        setContentView(root)
        refreshTabs()
        if (sourceTabId.isNotBlank()) {
            list.post { startPostponedEnterTransition() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) refreshTabs()
    }

    private fun refreshTabs() {
        if (!::adapter.isInitialized) return
        val realTabs = BrowserTabStore.tabs(this)
        // The app home is a first-class card in the tab overview, but is not stored as a WebView tab.
        val homeTab = BrowserTab("__home__", "appao://home", "Início", "", "", Long.MAX_VALUE)
        adapter.submit(listOf(homeTab) + realTabs)
        countLabel.text = "Início + ${realTabs.size} ${if (realTabs.size == 1) "separador" else "separadores"}"
    }

    private fun openNewTab() {
        val tab = BrowserTabStore.create(this, "https://www.google.com/")
        openTab(tab, null)
    }

    private fun openTab(tab: BrowserTab, preview: View?) {
        val transitionName = preview?.transitionName?.takeIf { it.isNotBlank() }
            ?: "browser_tab_preview_${tab.id}"
        if (tab.id == "__home__") {
            val launch = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val transitioned = preview != null && runCatching {
                val options = ActivityOptions.makeSceneTransitionAnimation(this, preview, "browser_tab_preview")
                startActivity(launch, options.toBundle())
            }.isSuccess
            if (!transitioned) startActivity(launch)
            finish()
            if (!transitioned) overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit)
            return
        }
        if (tab.id == sourceTabId) {
            // Selecting the currently active tab only closes the overview; the existing page remains alive.
            finishWithMotion()
            return
        }
        val launch = Intent(this, AppViewerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("browser_mode", true)
            putExtra("browser_tab_id", tab.id)
            putExtra("url", tab.url)
            putExtra("transition_name", transitionName)
        }
        if (preview != null) {
            val launched = runCatching {
                val options = ActivityOptions.makeSceneTransitionAnimation(this, preview, transitionName)
                startActivity(launch, options.toBundle())
            }.isSuccess
            if (!launched) {
                startActivity(launch)
                overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
            }
        } else {
            startActivity(launch)
            overridePendingTransition(R.anim.publish_enter, R.anim.publish_exit)
        }
    }

    private fun closeTab(tab: BrowserTab) {
        if (tab.id == "__home__") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finishWithMotion()
            return
        }
        BrowserTabStore.remove(this, tab.id)
        BrowserWebViewPool.removeAndDestroy(tab.id)
        if (tab.id == sourceTabId) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finishWithMotion()
        } else {
            refreshTabs()
        }
    }

    private fun clearTabs() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Limpar separadores")
            .setMessage("Queres fechar todos os separadores do browser?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Limpar") { _, _ ->
                BrowserTabStore.clear(this)
                BrowserWebViewPool.clearExcept(null)
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finishWithMotion()
            }
            .show()
    }

    private fun finishWithMotion() {
        finish()
        overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit)
    }

    private fun iconButton(icon: String, description: String, size: Int): FrameLayout = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(46))
        isClickable = true
        isFocusable = true
        contentDescription = description
        foreground = ContextCompat.getDrawable(this@BrowserTabsActivity, R.drawable.bg_transparent_pressed)
        addView(ImageView(this@BrowserTabsActivity).apply {
            layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
            scaleType = ImageView.ScaleType.FIT_CENTER
            IconLoader.applyIcon(this, icon, R.color.iconTint)
        })
    }

    private inner class TabAdapter : RecyclerView.Adapter<TabHolder>() {
        private var data: List<BrowserTab> = emptyList()
        fun submit(next: List<BrowserTab>) {
            data = next
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabHolder {
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
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            val title = TextView(this@BrowserTabsActivity).apply {
                textSize = 12.5f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL))
                setTextColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.text))
                setPadding(dp(7), 0, dp(3), 0)
            }
            val close = TextView(this@BrowserTabsActivity).apply {
                text = "×"
                textSize = 25f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.dim))
                layoutParams = LinearLayout.LayoutParams(dp(27), dp(30))
                contentDescription = "Fechar separador"
            }
            header.addView(favicon)
            header.addView(title, LinearLayout.LayoutParams(0, dp(28), 1f))
            header.addView(close)
            card.addView(header)
            val preview = ImageView(this@BrowserTabsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(166))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.bg))
            }
            card.addView(preview)
            val holder = TabHolder(card, favicon, title, close, preview)
            holder.close.setOnClickListener { v ->
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) closeTab(data[pos])
            }
            card.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) openTab(data[pos], holder.preview)
            }
            return holder
        }
        override fun getItemCount(): Int = data.size
        override fun onBindViewHolder(holder: TabHolder, position: Int) {
            val tab = data[position]
            holder.preview.transitionName = if (sourceTabId == tab.id) "browser_tab_preview" else "browser_tab_preview_${tab.id}"
            holder.title.text = tab.title.ifBlank { titleFromUrl(tab.url) }
            if (tab.id == "__home__") {
                Glide.with(this@BrowserTabsActivity).clear(holder.favicon)
                IconLoader.applyIcon(holder.favicon, "home", R.color.iconTint)
                holder.preview.setImageDrawable(com.appao.EmptyNewsIllustrationDrawable(this@BrowserTabsActivity))
                holder.preview.setBackgroundColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.bg))
                holder.itemView.contentDescription = "Separador Início"
                return
            }
            Glide.with(this@BrowserTabsActivity).load(tab.favicon.ifBlank { BrowserTabStore.faviconFor(tab.url) }).circleCrop().placeholder(R.drawable.logo).into(holder.favicon)
            val imageFile = tab.snapshotPath.takeIf { it.isNotBlank() }?.let(::File)
            if (imageFile != null && imageFile.exists()) {
                Glide.with(this@BrowserTabsActivity).load(imageFile).centerCrop().placeholder(R.color.card2).into(holder.preview)
            } else {
                Glide.with(this@BrowserTabsActivity).clear(holder.preview)
                holder.preview.setImageDrawable(null)
                holder.preview.setBackgroundColor(ContextCompat.getColor(this@BrowserTabsActivity, R.color.card2))
            }
            holder.itemView.setOnLongClickListener {
                closeTab(tab)
                true
            }
        }
    }

    private class TabHolder(item: View, val favicon: ImageView, val title: TextView, val close: TextView, val preview: ImageView) : RecyclerView.ViewHolder(item)

    private fun titleFromUrl(url: String): String = runCatching { android.net.Uri.parse(url).host.orEmpty().removePrefix("www.") }.getOrDefault("").ifBlank { "Nova aba" }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private class SpaceView(context: android.content.Context) : View(context)
}
