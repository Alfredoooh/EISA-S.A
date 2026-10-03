package com.appao

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        IconLoader.applySvg(findViewById(R.id.libBackIcon), "back", R.color.iconTint)
        findViewById<View>(R.id.libBack).setOnClickListener { finish() }

        val prefs = getSharedPreferences("appao", MODE_PRIVATE)
        val json = prefs.getString("library_json", "[]") ?: "[]"
        val items: List<NewsItem> = try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val link = o.optString("link")
                    val title = o.optString("title")
                    if (link.isBlank() || title.isBlank()) continue
                    add(NewsItem(
                        id = o.optString("id").ifBlank { "u_" + link.hashCode().toUInt().toString(36) },
                        title = title,
                        summary = o.optString("summary"),
                        link = link,
                        source = o.optString("source"),
                        date = o.optString("date"),
                        image = o.optString("image"),
                        logo = o.optString("logo")
                    ))
                }
            }
        } catch (_: Throwable) { emptyList() }

        val list = findViewById<RecyclerView>(R.id.libList)
        val empty = findViewById<View>(R.id.libEmpty)
        val emptyImg = findViewById<ImageView>(R.id.libEmptyImg)
        IconLoader.applyPngAt(emptyImg, "illustrations/png/emptystate_library.png")

        if (items.isEmpty()) {
            empty.visibility = View.VISIBLE
            list.visibility = View.GONE
        } else {
            empty.visibility = View.GONE
            list.visibility = View.VISIBLE
            list.layoutManager = LinearLayoutManager(this)
            list.adapter = NewsAdapter(items.toMutableList()) { openArticle(it) }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        SystemBarHelper.sync(this)
        try {
            window.decorView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.bg))
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        SystemBarHelper.sync(this)
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
