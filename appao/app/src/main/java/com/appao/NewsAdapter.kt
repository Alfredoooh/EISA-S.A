package com.appao

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class NewsAdapter(
    private val list: MutableList<NewsItem>,
    private val onClick: (NewsItem) -> Unit
) : RecyclerView.Adapter<NewsAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val favicon: ImageView = v.findViewById(R.id.favicon)
        val source: TextView = v.findViewById(R.id.source)
        val time: TextView = v.findViewById(R.id.time)
        val title: TextView = v.findViewById(R.id.title)
        val image: ImageView = v.findViewById(R.id.image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_news, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val it = list[position]
        h.source.text = it.source
        h.title.text = it.title
        h.time.text = if (it.date.isBlank()) "" else "· ${ago(it.date)}"
        if (it.logo.isNotEmpty()) Glide.with(h.favicon).load(it.logo).into(h.favicon)
        else h.favicon.setImageDrawable(null)
        if (it.image.isNotEmpty()) {
            h.image.visibility = View.VISIBLE
            Glide.with(h.image).load(it.image).into(h.image)
        } else h.image.visibility = View.GONE
        h.itemView.setOnClickListener { onClick(it) }
    }

    override fun getItemCount() = list.size

    fun submit(items: List<NewsItem>) {
        list.clear()
        list.addAll(items)
        notifyDataSetChanged()
    }

    fun append(items: List<NewsItem>) {
        val start = list.size
        list.addAll(items)
        notifyItemRangeInserted(start, items.size)
    }

    private fun ago(v: String): String {
        if (v.isBlank()) return ""
        return try {
            val normalized = v.replace("Z", "")
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val t = fmt.parse(normalized.substring(0, normalized.length.coerceAtMost(19)))?.time ?: return ""
            val m = ((System.currentTimeMillis() - t) / 60000).coerceAtLeast(0)
            when {
                m < 1 -> "agora"
                m < 60 -> "$m min"
                m < 1440 -> "${m / 60} h"
                m < 10080 -> "${m / 1440} d"
                else -> SimpleDateFormat("dd MMM", Locale("pt", "PT")).format(Date(t))
            }
        } catch (e: Exception) { "" }
    }
}
