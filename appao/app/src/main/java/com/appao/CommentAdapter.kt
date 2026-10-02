package com.appao

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CommentAdapter(
    private val list: MutableList<Comment>,
    private val onClickReact: (Int, String) -> Unit
) : RecyclerView.Adapter<CommentAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: ImageView = v.findViewById(R.id.cmAvatar)
        val user: TextView = v.findViewById(R.id.cmUser)
        val time: TextView = v.findViewById(R.id.cmTime)
        val text: TextView = v.findViewById(R.id.cmText)
        val reactsRow: LinearLayout = v.findViewById(R.id.cmReactsRow)
        val picker: LinearLayout = v.findViewById(R.id.cmPicker)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val c = list[position]
        h.user.text = c.user
        h.time.text = c.date
        h.text.text = c.text

        if (c.avatar.isNotEmpty()) {
            com.bumptech.glide.Glide.with(h.avatar).load(c.avatar).into(h.avatar)
        } else {
            h.avatar.setImageDrawable(null)
        }

        h.reactsRow.removeAllViews()
        val shown = c.reactions.filterValues { it > 0 }
        if (shown.isEmpty()) {
            h.reactsRow.visibility = View.GONE
        } else {
            h.reactsRow.visibility = View.VISIBLE
            for ((k, v) in shown) {
                val tv = TextView(h.itemView.context)
                tv.text = "$k $v"
                tv.setTextColor(0xFFB0B5BC.toInt())
                tv.textSize = 11.5f
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = dp(h.itemView, 12)
                tv.layoutParams = lp
                h.reactsRow.addView(tv)
            }
        }

        h.picker.removeAllViews()
        val reacts = listOf("like", "love", "haha", "wow", "sad", "angry")
        for (r in reacts) {
            val iv = ImageView(h.itemView.context)
            val lp = LinearLayout.LayoutParams(dp(h.itemView, 40), dp(h.itemView, 40))
            lp.marginEnd = dp(h.itemView, 4)
            iv.layoutParams = lp
            IconLoader.applyPng(iv, r)
            iv.setOnClickListener { onClickReact(position, r) }
            h.picker.addView(iv)
        }

        h.itemView.setOnClickListener {
            val expanded = h.picker.visibility == View.VISIBLE
            h.picker.visibility = if (expanded) View.GONE else View.VISIBLE
        }
        h.picker.visibility = View.GONE
    }

    override fun getItemCount() = list.size

    private fun dp(v: View, value: Int): Int =
        (value * v.resources.displayMetrics.density).toInt()
}
