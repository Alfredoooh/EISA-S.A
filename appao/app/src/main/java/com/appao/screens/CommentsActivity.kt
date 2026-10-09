package com.appao

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject

/** Dedicated comments screen. Comments are currently kept locally for the open article session. */
class CommentsActivity : AppCompatActivity() {
    private val comments = mutableListOf<Comment>()
    private lateinit var adapter: CommentAdapter
    private lateinit var input: EditText
    private lateinit var list: RecyclerView
    private var resultReturned = false
    private var articleLink = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_comments)
        SystemBarHelper.sync(this)

        input = findViewById(R.id.commentInput)
        list = findViewById(R.id.commentsList)
        articleLink = intent.getStringExtra("article_link").orEmpty()
        findViewById<TextView>(R.id.commentsArticleTitle).text = intent.getStringExtra("article_title").orEmpty()
        IconLoader.applySvg(findViewById(R.id.commentsBackIcon), "arrow_left", R.color.iconTint)
        IconLoader.applySvg(findViewById(R.id.commentSendIcon), "arrow_up", R.color.onpri)

        parseComments(intent.getStringExtra("comments_json").orEmpty()).forEach { comments.add(it) }
        adapter = CommentAdapter(comments) { index, reaction ->
            if (index !in comments.indices) return@CommentAdapter
            val item = comments[index]
            item.reactions[reaction] = (item.reactions[reaction] ?: 0) + 1
            adapter.notifyItemChanged(index)
            persistComments()
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.itemAnimator = null
        list.overScrollMode = View.OVER_SCROLL_NEVER
        list.setHasFixedSize(false)

        findViewById<View>(R.id.commentsBack).setOnClickListener { returnComments() }
        findViewById<View>(R.id.commentSend).setOnClickListener { submitComment() }
        input.setOnEditorActionListener { _, _, _ -> submitComment(); true }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.commentsRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.updatePadding(bottom = maxOf(bars, ime))
            insets
        }
        if (savedInstanceState != null) {
            comments.clear()
            comments.addAll(parseComments(savedInstanceState.getString("comments_json").orEmpty()))
            adapter.notifyDataSetChanged()
        }
        if (intent.getBooleanExtra("focus_composer", true)) {
            input.requestFocus()
            input.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                }
            }, 220L)
        }
    }

    private fun submitComment() {
        val value = input.text?.toString()?.trim().orEmpty()
        if (value.isBlank()) return
        comments.add(Comment(user = "Convidado", text = value, avatar = "", date = "agora"))
        adapter.notifyItemInserted(comments.lastIndex)
        persistComments()
        input.setText("")
        list.post { list.scrollToPosition(comments.lastIndex) }
    }

    private fun parseComments(raw: String): List<Comment> = try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val reactionsJson = o.optJSONObject("reactions") ?: JSONObject()
                val reactions = mutableMapOf<String, Int>()
                val keys = reactionsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    reactions[key] = reactionsJson.optInt(key, 0)
                }
                add(Comment(
                    user = o.optString("user", "Convidado"),
                    text = o.optString("text"),
                    avatar = o.optString("avatar"),
                    date = o.optString("date", "agora"),
                    reactions = reactions
                ))
            }
        }
    } catch (_: Throwable) { emptyList() }

    private fun encodeComments(): String = JSONArray().apply {
        comments.forEach { comment ->
            put(JSONObject().apply {
                put("user", comment.user)
                put("text", comment.text)
                put("avatar", comment.avatar)
                put("date", comment.date)
                put("reactions", JSONObject(comment.reactions as Map<*, *>))
            })
        }
    }.toString()

    private fun persistComments() {
        if (articleLink.isBlank()) return
        val normalized = articleLink.trim().lowercase()
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .substringBefore('#').trimEnd('/')
        val key = "article_comments_${normalized.hashCode().toUInt().toString(36)}"
        getSharedPreferences("appao", MODE_PRIVATE).edit()
            .putString(key, encodeComments())
            .apply()
    }

    private fun returnComments() {
        if (resultReturned) return
        resultReturned = true
        persistComments()
        setResult(RESULT_OK, intent.putExtra("comments_json", encodeComments()))
        finish()
        overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("comments_json", encodeComments())
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with the app's navigation")
    override fun onBackPressed() = returnComments()

    override fun onDestroy() {
        if (!resultReturned && !isChangingConfigurations) {
            setResult(RESULT_CANCELED)
        }
        super.onDestroy()
    }
}
