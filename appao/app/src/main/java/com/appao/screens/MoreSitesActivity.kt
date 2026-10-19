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
import com.appao.screens.AppViewerActivity
import com.appao.screens.BrowserTabStore
import com.appao.ui.IconLoader

/** Popular web sites grouped by category, opened in AppAo's persistent browser tabs. */
class MoreSitesActivity : AppCompatActivity() {
    private data class Site(val name: String, val url: String, val icon: Int? = null)
    private data class SiteCategory(val name: String, val sites: List<Site>)

    private val siteCategories = listOf(
        SiteCategory("Pesquisa e navegadores", listOf(
            Site("Google", "https://www.google.com/", R.drawable.site_icon_google),
            Site("Bing", "https://www.bing.com/", R.drawable.site_icon_bing),
            Site("DuckDuckGo", "https://duckduckgo.com/", R.drawable.site_icon_duckduckgo),
            Site("Brave Search", "https://search.brave.com/", R.drawable.asset_search_brave),
            Site("Arc Browser", "https://arc.net/", R.drawable.site_icon_arc_browser),
            Site("Opera", "https://www.opera.com/", R.drawable.site_icon_opera_browser)
        )),
        SiteCategory("Redes sociais e comunidades", listOf(
            Site("Facebook", "https://www.facebook.com/", R.drawable.asset_social_facebook),
            Site("Instagram", "https://www.instagram.com/", R.drawable.site_icon_instagram),
            Site("X", "https://x.com/", R.drawable.site_icon_x),
            Site("WhatsApp", "https://www.whatsapp.com/", R.drawable.asset_social_whatsapp),
            Site("TikTok", "https://www.tiktok.com/", R.drawable.site_icon_tiktok),
            Site("Reddit", "https://www.reddit.com/", R.drawable.site_icon_reddit),
            Site("LinkedIn", "https://www.linkedin.com/", R.drawable.asset_social_linkedin)
        )),
        SiteCategory("Vídeo, áudio e streaming", listOf(
            Site("YouTube", "https://www.youtube.com/", R.drawable.site_icon_youtube),
            Site("Netflix", "https://www.netflix.com/", R.drawable.site_icon_netflix),
            Site("Spotify", "https://open.spotify.com/", R.drawable.site_icon_spotify)
        )),
        SiteCategory("Compras, viagens e serviços", listOf(
            Site("Amazon", "https://www.amazon.com/", R.drawable.site_icon_amazon),
            Site("Alibaba", "https://www.alibaba.com/", R.drawable.site_icon_alibaba),
            Site("Flipkart", "https://www.flipkart.com/", R.drawable.site_icon_flipkart),
            Site("Airbnb", "https://www.airbnb.com/", R.drawable.site_icon_airbnb),
            Site("eBay", "https://www.ebay.com/")
        )),
        SiteCategory("Notícias e publicação", listOf(
            Site("BBC", "https://www.bbc.com/", R.drawable.site_icon_bbc),
            Site("Medium", "https://medium.com/", R.drawable.site_icon_medium),
            Site("Yahoo", "https://www.yahoo.com/"),
            Site("Globo", "https://www.globo.com/"),
            Site("CNN", "https://www.cnn.com/")
        )),
        SiteCategory("Desporto", listOf(
            Site("BeSoccer", "https://www.besoccer.com/", R.drawable.site_icon_besoccer),
            Site("FC Barcelona", "https://www.fcbarcelona.com/", R.drawable.site_icon_fc_barcelona),
            Site("Real Madrid", "https://www.realmadrid.com/", R.drawable.site_icon_real_madrid)
        )),
        SiteCategory("Referência e conhecimento", listOf(
            Site("Wikipedia", "https://www.wikipedia.org/", R.drawable.site_icon_wikipedia),
            Site("Quora", "https://www.quora.com/")
        )),
        SiteCategory("Inteligência artificial", listOf(
            Site("ChatGPT", "https://chatgpt.com/", R.drawable.site_icon_openai_chatgpt),
            Site("Mistral AI", "https://mistral.ai/", R.drawable.site_icon_mistral_ai),
            Site("Perplexity", "https://www.perplexity.ai/", R.drawable.site_icon_perplexity),
            Site("Suno", "https://suno.com/", R.drawable.site_icon_suno)
        )),
        SiteCategory("Design e produtividade", listOf(
            Site("Adobe", "https://www.adobe.com/", R.drawable.site_icon_adobe),
            Site("Canva", "https://www.canva.com/", R.drawable.site_icon_canva),
            Site("Dropbox", "https://www.dropbox.com/", R.drawable.site_icon_dropbox),
            Site("Figma", "https://www.figma.com/", R.drawable.site_icon_figma),
            Site("Gmail", "https://mail.google.com/", R.drawable.site_icon_gmail),
            Site("HubSpot", "https://www.hubspot.com/", R.drawable.site_icon_hubspot),
            Site("Make", "https://www.make.com/", R.drawable.site_icon_make),
            Site("Miro", "https://miro.com/", R.drawable.site_icon_miro),
            Site("Notion", "https://www.notion.so/", R.drawable.site_icon_notion),
            Site("Zapier", "https://zapier.com/", R.drawable.site_icon_zapier),
            Site("Zoom", "https://zoom.us/", R.drawable.site_icon_zoom)
        )),
        SiteCategory("Educação", listOf(
            Site("Khan Academy", "https://www.khanacademy.org/"),
            Site("Coursera", "https://www.coursera.org/")
        )),
        SiteCategory("Finanças", listOf(
            Site("PayPal", "https://www.paypal.com/")
        )),
        SiteCategory("Jogos", listOf(
            Site("Roblox", "https://www.roblox.com/", R.drawable.site_icon_roblox),
            Site("Poki", "https://poki.com/", R.drawable.site_icon_poki),
            Site("CrazyGames", "https://www.crazygames.com/"),
            Site("Xbox", "https://www.xbox.com/", R.drawable.site_icon_xbox)
        )),
        SiteCategory("Tecnologia e desenvolvimento", listOf(
            Site("Apple", "https://www.apple.com/", R.drawable.site_icon_apple),
            Site("Atlassian", "https://www.atlassian.com/", R.drawable.site_icon_atlassian),
            Site("Cloudflare", "https://www.cloudflare.com/", R.drawable.site_icon_cloudflare),
            Site("GitHub", "https://github.com/", R.drawable.site_icon_github),
            Site("GitLab", "https://gitlab.com/", R.drawable.site_icon_gitlab),
            Site("Microsoft", "https://www.microsoft.com/", R.drawable.site_icon_microsoft)
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
            isFillViewport = true
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
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
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
        val iconBox = FrameLayout(this).apply {
            background = ContextCompat.getDrawable(this@MoreSitesActivity, R.drawable.bg_circle_card)
            clipToOutline = true
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
        iconBox.addView(icon, FrameLayout.LayoutParams(-1, -1))
        tile.addView(iconBox, LinearLayout.LayoutParams(dp(44), dp(44)))
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
