package com.appao

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class ArticleActivity : AppCompatActivity() {

    private lateinit var ambient: ImageView
    private lateinit var hero: ImageView
    private lateinit var title: TextView
    private lateinit var summary: TextView
    private lateinit var body: TextView
    private lateinit var source: TextView
    private lateinit var time: TextView
    private lateinit var favicon: ImageView
    private lateinit var scroll: ScrollView
    private lateinit var articleBar: LinearLayout
    private lateinit var acInput: EditText
    private lateinit var acSend: FrameLayout
    private lateinit var commentsAdapter: CommentAdapter

    private val comments = mutableListOf<Comment>()
    private var itemId: String = ""
    private var itemLink: String = ""
    private var itemTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article)

        itemId = intent.getStringExtra("id") ?: ""
        itemTitle = intent.getStringExtra("title") ?: ""
        val itemSummary = intent.getStringExtra("summary") ?: ""
        itemLink = intent.getStringExtra("link") ?: ""
        val itemSource = intent.getStringExtra("source") ?: ""
        val itemDate = intent.getStringExtra("date") ?: ""
        val itemImage = intent.getStringExtra("image") ?: ""
        val itemLogo = intent.getStringExtra("logo") ?: ""

        ambient = findViewById(R.id.ambient)
        hero = findViewById(R.id.aHero)
        title = findViewById(R.id.aTitle)
        summary = findViewById(R.id.aSummary)
        body = findViewById(R.id.aBody)
        source = findViewById(R.id.aSource)
        time = findViewById(R.id.aTime)
        favicon = findViewById(R.id.aFavicon)
        scroll = findViewById(R.id.aScroll)
        articleBar = findViewById(R.id.articleBar)
        acInput = findViewById(R.id.acInput)
        acSend = findViewById(R.id.acSend)

        IconLoader.applySvg(findViewById<ImageView>(R.id.aBackIcon), "close", R.color.text)
        IconLoader.applySvg(findViewById<ImageView>(R.id.acSendIcon), "send", R.color.onpri)
        IconLoader.applySvg(findViewById<ImageView>(R.id.acMoreIcon), "chevron-right", R.color.dim)
        IconLoader.applyPng(findViewById<ImageView>(R.id.acLikeIcon), "like")
        IconLoader.applyPng(findViewById<ImageView>(R.id.acLoveIcon), "love")
        IconLoader.applyPng(findViewById<ImageView>(R.id.acHahaIcon), "haha")
        IconLoader.applySvg(findViewById<ImageView>(R.id.cIcon), "chat", R.color.text)
        IconLoader.applyPng(findViewById<ImageView>(R.id.aCopyIcon), "link")
        IconLoader.applyPng(findViewById<ImageView>(R.id.aShareIcon), "share")
        IconLoader.applySvg(findViewById<ImageView>(R.id.aSaveIcon), "bookmark", R.color.text)

        title.text = itemTitle
        findViewById<TextView>(R.id.aTopTitle).text = "Notícia"
        if (itemSummary.isNotBlank()) {
            summary.visibility = View.VISIBLE
            summary.text = itemSummary
        }
        source.text = itemSource
        time.text = if (itemDate.isBlank()) "" else "· ${prettyTime(itemDate)}"
        if (itemImage.isNotBlank()) {
            Glide.with(this).load(itemImage).into(hero)
            Glide.with(this).load(itemImage).into(ambient)
            ambient.visibility = View.VISIBLE
        }
        if (itemLogo.isNotBlank()) Glide.with(this).load(itemLogo).into(favicon)

        // === Barra começa escondida em baixo e entra com slide (igual ao HTML) ===
        articleBar.post {
            val h = articleBar.height.toFloat().coerceAtLeast(120f)
            articleBar.translationY = h + 40f
            articleBar.animate()
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(Curves.SMOOTH)
                .start()
        }

        commentsAdapter = CommentAdapter(comments) { idx, r ->
            if (idx in comments.indices) {
                val c = comments[idx]
                c.reactions[r] = (c.reactions[r] ?: 0) + 1
                commentsAdapter.notifyItemChanged(idx)
            }
        }
        findViewById<RecyclerView>(R.id.cList).apply {
            layoutManager = LinearLayoutManager(this@ArticleActivity)
            adapter = commentsAdapter
        }

        acInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                acSend.visibility = if (!s.isNullOrBlank()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        acSend.setOnClickListener { sendComment() }
        acInput.setOnEditorActionListener { _, _, _ -> sendComment(); true }

        findViewById<View>(R.id.aBack).setOnClickListener { finish() }

        findViewById<View>(R.id.aCopy).setOnClickListener {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("link", itemLink))
            Toast.makeText(this, "Link copiado", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.aShare).setOnClickListener {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$itemTitle $itemLink")
            }
            startActivity(Intent.createChooser(i, "Partilhar"))
        }
        findViewById<View>(R.id.aSave).setOnClickListener {
            Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.acLike).setOnClickListener { react("like") }
        findViewById<View>(R.id.acLove).setOnClickListener { react("love") }
        findViewById<View>(R.id.acHaha).setOnClickListener { react("haha") }

        lifecycleScope.launch {
            val full = NewsRepository.fetchArticleFull(itemLink)
            if (!full.isNullOrBlank()) {
                body.text = full
            } else {
                body.text = itemSummary.ifBlank { "Não foi possível carregar o artigo completo." }
            }
        }
    }

    private fun sendComment() {
        val v = acInput.text.toString().trim()
        if (v.isEmpty()) return
        comments.add(Comment(
            user = "Convidado",
            text = v,
            avatar = "",
            date = "agora"
        ))
        commentsAdapter.notifyItemInserted(comments.size - 1)
        findViewById<TextView>(R.id.cCount).text = "(${comments.size})"
        acInput.setText("")
        scroll.postDelayed({ scroll.fullScroll(View.FOCUS_DOWN) }, 100)
    }

    private fun react(kind: String) {
        Toast.makeText(this, "Reagiste: $kind", Toast.LENGTH_SHORT).show()
    }

    private fun prettyTime(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val raw = value.replace("Z", "")
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = format.parse(raw.substring(0, raw.length.coerceAtMost(19))) ?: return ""
            val minutes = ((System.currentTimeMillis() - date.time) / 60000).coerceAtLeast(0)
            when {
                minutes < 1 -> "agora"
                minutes < 60 -> "$minutes min"
                minutes < 1440 -> "${minutes / 60} h"
                minutes < 10080 -> "${minutes / 1440} d"
                else -> java.text.SimpleDateFormat("dd MMM", java.util.Locale("pt", "PT")).format(date)
            }
        } catch (_: Exception) { "" }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_from_bg, R.anim.slide_out_right)
    }
}