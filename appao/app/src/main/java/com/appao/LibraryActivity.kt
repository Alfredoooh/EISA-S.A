package com.appao

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class LibraryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        IconLoader.applySvg(findViewById(R.id.libBackIcon), "back", R.color.iconTint)
        findViewById<View>(R.id.libBack).setOnClickListener { finish() }

        val prefs = getSharedPreferences("appao", MODE_PRIVATE)
        val json = prefs.getString("library_json", "[]") ?: "[]"
        val type = object : TypeToken<List<NewsItem>>() {}.type
        val items: List<NewsItem> = try { Gson().fromJson(json, type) } catch (e: Exception) { emptyList() }

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
