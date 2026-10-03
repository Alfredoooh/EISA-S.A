package com.appao

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray

class LibraryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_library)
        SystemBarHelper.sync(this)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(android.R.id.content)
        ) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            view.updatePadding(
                top = bars.top,
                bottom = bars.bottom
            )
            insets
        }

        IconLoader.applySvg(
            findViewById(R.id.libBackIcon),
            "back",
            R.color.iconTint
        )

        findViewById<View>(R.id.libBack)
            .setOnClickListener { finish() }

        val prefs = getSharedPreferences(
            "appao",
            MODE_PRIVATE
        )

        val raw = prefs.getString(
            "library_json",
            "[]"
        ) ?: "[]"

        val items = try {
            val array = JSONArray(raw)
            val result = ArrayList<NewsItem>(array.length())
            val urls = HashSet<String>()
            val titles = HashSet<String>()

            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val link = obj.optString("link").trim()
                val title = obj.optString("title").trim()
                if (link.isBlank() || title.isBlank()) continue

                val urlKey = normalizeUrl(link)
                val titleKey = normalizeTitle(title)
                if (!urls.add(urlKey)) continue
                if (!titles.add(titleKey)) continue

                result += NewsItem(
                    id = obj.optString("id")
                        .ifBlank {
                            link.hashCode().toUInt().toString(36)
                        },
                    title = title,
                    summary = obj.optString("summary"),
                    link = link,
                    source = obj.optString("source"),
                    date = obj.optString("date"),
                    image = obj.optString("image"),
                    logo = obj.optString("logo")
                )
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }

        val list = findViewById<RecyclerView>(R.id.libList)
        val empty = findViewById<View>(R.id.libEmpty)
        val emptyImage = findViewById<ImageView>(R.id.libEmptyImg)

        IconLoader.applyPngAt(
            emptyImage,
            "illustrations/png/emptystate_library.png"
        )

        if (items.isEmpty()) {
            empty.visibility = View.VISIBLE
            list.visibility = View.GONE
            return
        }

        empty.visibility = View.GONE
        list.visibility = View.VISIBLE
        list.layoutManager = LinearLayoutManager(this)
        list.itemAnimator = null
        list.adapter = NewsAdapter(items.toMutableList()) { item, source ->
            openArticle(item, source)
        }
    }

    private fun openArticle(
        item: NewsItem,
        source: View
    ) {
        val transitionName =
            "news_container_${item.id.hashCode().toUInt().toString(36)}"

        ViewCompat.setTransitionName(source, transitionName)

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
            ActivityOptions.makeSceneTransitionAnimation(
                this,
                source,
                transitionName
            )

        startActivity(
            intent,
            options.toBundle()
        )
    }

    override fun onConfigurationChanged(
        newConfig: android.content.res.Configuration
    ) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
        window.decorView.setBackgroundColor(
            ContextCompat.getColor(this, R.color.bg)
        )
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
    }

    private fun normalizeUrl(value: String): String = value.trim()
        .lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('#')
        .trimEnd('/')

    private fun normalizeTitle(value: String): String = value.trim()
        .lowercase()
        .replace(Regex("[\\p{Punct}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
