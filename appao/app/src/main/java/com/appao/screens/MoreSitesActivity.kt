package com.appao

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide

/** Popular web sites grouped by category, opened in AppAo's persistent browser tabs. */
class MoreSitesActivity : AppCompatActivity() {
    private data class Site(val name: String, val url: String, val icon: Int? = null)
    private data class SiteCategory(val name: String, val sites: List<Site>)

    private val siteCategories = listOf(
        SiteCategory("Mecanismos de pesquisa", listOf(
            Site("Google", "https://www.google.com/", R.drawable.asset_search_google),
            Site("Bing", "https://www.bing.com/", R.drawable.asset_search_bing),
            Site("DuckDuckGo", "https://duckduckgo.com/", R.drawable.asset_search_duckduckgo),
            Site("Brave Search", "https://search.brave.com/", R.drawable.asset_search_brave)
        )),
        SiteCategory("Redes sociais", listOf(
            Site("Facebook", "https://www.facebook.com/", R.drawable.asset_social_facebook),
            Site("Instagram", "https://www.instagram.com/", R.drawable.asset_social_instagram),
            Site("X", "https://x.com/", R.drawable.asset_social_x),
            Site("WhatsApp", "https://www.whatsapp.com/", R.drawable.asset_social_whatsapp),
            Site("TikTok", "https://www.tiktok.com/", R.drawable.asset_social_tiktok),
            Site("Reddit", "https://www.reddit.com/", R.drawable.asset_social_reddit),
            Site("LinkedIn", "https://www.linkedin.com/", R.drawable.asset_social_linkedin)
        )),
        SiteCategory("Vídeos e streaming", listOf(
            Site("YouTube", "https://www.youtube.com/", R.drawable.asset_social_youtube),
            Site("Netflix", "https://www.netflix.com/")
        )),
        SiteCategory("Compras", listOf(
            Site("Amazon", "https://www.amazon.com/"),
            Site("eBay", "https://www.ebay.com/")
        )),
        SiteCategory("Notícias e media", listOf(
            Site("Yahoo", "https://www.yahoo.com/"),
            Site("Globo", "https://www.globo.com/"),
            Site("CNN", "https://www.cnn.com/")
        )),
        SiteCategory("Desporto", listOf(
            Site("BeSoccer", "https://www.besoccer.com/")
        )),
        SiteCategory("Referência", listOf(
            Site("Wikipedia", "https://www.wikipedia.org/"),
            Site("Quora", "https://www.quora.com/")
        )),
        SiteCategory("Ferramentas de IA", listOf(
            Site("OpenAI", "https://openai.com/")
        )),
        SiteCategory("Educação", listOf(
            Site("Khan Academy", "https://www.khanacademy.org/"),
            Site("Coursera", "https://www.coursera.org/")
        )),
        SiteCategory("Finanças", listOf(
            Site("PayPal", "https://www.paypal.com/")
        )),
        SiteCategory("Jogos", listOf(
            Site("Roblox", "https://www.roblox.com/"),
            Site("Poki", "https://poki.com/"),
            Site("CrazyGames", "https://www.crazygames.com/")
        )),
        SiteCategory("Tecnologia", listOf(
            Site("Microsoft", "https://www.microsoft.com/"),
            Site("GitHub", "https://github.com/", R.drawable.asset_social_github)
        )),
        SiteCategory("Comunicação e ferramentas", listOf(
            Site("Zoom", "https://zoom.us/")
        ))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        SystemBarHelper.sync(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@MoreSitesActivity, R.color.bg))
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(16), dp(4))
        }
        val back = FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(this@MoreSitesActivity, R.drawable.bg_transparent_pressed)
            contentDescription = "Voltar"
            setOnClickListener { finish() }
        }
        val backIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            IconLoader.applyIcon(this, "arrow_left", R.color.iconTint)
        }
        back.addView(backIcon, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))
        toolbar.addView(back, LinearLayout.LayoutParams(dp(40), dp(44)))
        toolbar.addView(TextView(this).apply {
            text = "Mais sites"
            textSize = 20f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(this@MoreSitesActivity, R.color.text))
            setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(toolbar)
        root.addView(View(this).apply { setBackgroundColor(ContextCompat.getColor(this@MoreSitesActivity, R.color.line)) }, LinearLayout.LayoutParams(-1, dp(1)))

        val scroll = ScrollView(this).apply {
            fillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(28))
        }
        siteCategories.forEachIndexed { categoryIndex, category ->
            content.addView(TextView(this).apply {
                text = category.name
                textSize = 13f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(ContextCompat.getColor(this@MoreSitesActivity, R.color.dim))
                setPadding(dp(2), dp(if (categoryIndex == 0) 14 else 20), dp(2), dp(8))
            }, LinearLayout.LayoutParams(-1, -2))

            val grid = GridLayout(this).apply {
                columnCount = 3
                useDefaultMargins = false
                alignmentMode = GridLayout.ALIGN_BOUNDS
            }
            category.sites.forEachIndexed { index, entry ->
                grid.addView(createSiteTile(entry), GridLayout.LayoutParams(
                    GridLayout.spec(index / 3, 1, 1f),
                    GridLayout.spec(index % 3, 1, 1f)
                ).apply {
                    width = 0
                    height = dp(88)
                    setMargins(dp(1), dp(1), dp(1), dp(1))
                })
            }
            content.addView(grid, LinearLayout.LayoutParams(-1, -2))
            if (categoryIndex != siteCategories.lastIndex) {
                content.addView(View(this).apply {
                    setBackgroundColor(ContextCompat.getColor(this@MoreSitesActivity, R.color.line))
                }, LinearLayout.LayoutParams(-1, dp(1)).apply {
                    topMargin = dp(8)
                })
            }
        }
        scroll.addView(content, ScrollView.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun createSiteTile(entry: Site): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(7), dp(3), dp(5))
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(this@MoreSitesActivity, R.drawable.bg_transparent_pressed)
            contentDescription = entry.name
            setOnClickListener {
                val tab = BrowserTabStore.create(this@MoreSitesActivity, entry.url)
                startActivity(Intent(this@MoreSitesActivity, AppViewerActivity::class.java).apply {
                    putExtra("browser_mode", true)
                    putExtra("browser_tab_id", tab.id)
                    putExtra("url", tab.url)
                })
            }
        }
        val icon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Ícone de ${entry.name}"
            if (entry.icon != null) {
                setImageResource(entry.icon)
            } else {
                IconLoader.applyIcon(this, "globe", R.color.iconTint)
                Glide.with(this@MoreSitesActivity)
                    .load(BrowserTabStore.faviconFor(entry.url))
                    .placeholder(R.drawable.ic_search_outline)
                    .error(R.drawable.ic_search_outline)
                    .into(this)
            }
        }
        tile.addView(icon, LinearLayout.LayoutParams(dp(30), dp(30)))
        tile.addView(TextView(this).apply {
            text = entry.name
            textSize = 11.5f
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(ContextCompat.getColor(this@MoreSitesActivity, R.color.text))
            setPadding(0, dp(5), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        return tile
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
