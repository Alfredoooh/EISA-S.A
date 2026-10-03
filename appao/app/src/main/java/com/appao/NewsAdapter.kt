package com.appao

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class NewsAdapter(
    private val list: MutableList<Any>,   // NewsItem OR SkeletonMarker
    private val onClick: (NewsItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val T_NEWS = 1
        private const val T_SKELETON = 2
    }

    object SkeletonMarker

    private var shimmerAnim: ValueAnimator? = null
    private var shimmerPos: Float = 0f

    class NewsVH(v: View) : RecyclerView.ViewHolder(v) {
        val favicon: ImageView = v.findViewById(R.id.favicon)
        val source: TextView = v.findViewById(R.id.source)
        val time: TextView = v.findViewById(R.id.time)
        val title: TextView = v.findViewById(R.id.title)
        val image: ImageView = v.findViewById(R.id.image)
    }

    class SkeletonVH(v: View) : RecyclerView.ViewHolder(v) {
        val line1: View = v.findViewById(R.id.skLine1)
        val line2: View = v.findViewById(R.id.skLine2)
        val line3: View = v.findViewById(R.id.skLine3)
        val image: View = v.findViewById(R.id.skImage)
        val bars: List<View> get() = listOf(line1, line2, line3, image)
    }

    override fun getItemViewType(position: Int): Int =
        if (list[position] is SkeletonMarker) T_SKELETON else T_NEWS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == T_SKELETON) {
            SkeletonVH(inf.inflate(R.layout.item_news_skeleton, parent, false))
        } else {
            NewsVH(inf.inflate(R.layout.item_news, parent, false))
        }
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        if (h is SkeletonVH) {
            h.image.visibility = if (position % 3 == 0) View.VISIBLE else View.GONE
            val shimmer = ShimmerDrawable(h.itemView.context)
            h.bars.forEach { it.background = shimmer.duplicate() }
            bindShimmerDrawables(h.bars)
            startShimmerIfNeeded()
            return
        }

        val item = list[position] as NewsItem
        val vh = h as NewsVH
        vh.source.text = item.source
        vh.title.text = item.title
        vh.time.text = ago(item.date)

        Glide.with(vh.favicon).clear(vh.favicon)
        if (item.logo.isNotEmpty()) {
            Glide.with(vh.favicon)
                .load(item.logo)
                .into(vh.favicon)
        } else vh.favicon.setImageDrawable(null)

        Glide.with(vh.image).clear(vh.image)
        if (item.image.isNotEmpty()) {
            vh.image.visibility = View.VISIBLE
            Glide.with(vh.image).load(item.image).into(vh.image)
        } else vh.image.visibility = View.GONE

        vh.itemView.setOnClickListener { onClick(item) }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty() && payloads.all { it == "shimmer" } && holder is SkeletonVH) {
            bindShimmerDrawables(holder.bars)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount() = list.size

    private fun bindShimmerDrawables(views: List<View>) {
        for (view in views) {
            (view.background as? ShimmerDrawable)?.bind(shimmerAnim, shimmerPos)
            view.invalidate()
        }
    }

    // ====== shimmer loop ======
    private fun startShimmerIfNeeded() {
        if (shimmerAnim != null && shimmerAnim!!.isRunning) return
        shimmerAnim = ValueAnimator.ofFloat(-1f, 2f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                shimmerPos = a.animatedValue as Float
                for (i in list.indices) {
                    if (list[i] is SkeletonMarker) notifyItemChanged(i, "shimmer")
                }
            }
            start()
        }
    }

    private fun stopShimmerIfNoSkeleton() {
        if (list.none { it is SkeletonMarker }) {
            shimmerAnim?.cancel()
            shimmerAnim = null
        }
    }

    // ====== API pública ======

    fun showSkeleton(count: Int = 8) {
        stopShimmerIfNoSkeleton()
        list.clear()
        repeat(count) { list.add(SkeletonMarker) }
        notifyDataSetChanged()
        startShimmerIfNeeded()
    }

    fun submit(items: List<NewsItem>) {
        val old = list.toList()
        val new = items.map<NewsItem, Any> { it }

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = new.size
            override fun areItemsTheSame(o: Int, n: Int): Boolean {
                val a = old[o]; val b = new[n]
                return when {
                    a is NewsItem && b is NewsItem -> a.id == b.id
                    a is SkeletonMarker && b is SkeletonMarker -> true
                    else -> false
                }
            }
            override fun areContentsTheSame(o: Int, n: Int): Boolean {
                val a = old[o]; val b = new[n]
                return when {
                    a is NewsItem && b is NewsItem -> a == b
                    a is SkeletonMarker && b is SkeletonMarker -> true
                    else -> false
                }
            }
        }, true)
        list.clear()
        list.addAll(new)
        diff.dispatchUpdatesTo(this)
        stopShimmerIfNoSkeleton()
    }

    fun append(items: List<NewsItem>) {
        val oldSize = list.size
        val skeletonCount = list.count { it is SkeletonMarker }
        if (skeletonCount > 0) {
            list.removeAll { it is SkeletonMarker }
            if (skeletonCount <= oldSize) {
                notifyItemRangeRemoved(oldSize - skeletonCount, skeletonCount)
            }
        }
        val start = list.size
        list.addAll(items)
        if (items.isNotEmpty()) notifyItemRangeInserted(start, items.size)
        stopShimmerIfNoSkeleton()
    }

    private fun ago(v: String): String {
        if (v.isBlank()) return ""
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val t = fmt.parse(v.substring(0, 19))?.time ?: return ""
            val m = ((System.currentTimeMillis() - t) / 60000).coerceAtLeast(0)
            when {
                m < 1 -> "agora"
                m < 60 -> "$m min"
                m < 1440 -> "${m / 60} h"
                m < 10080 -> "${m / 1440} d"
                else -> SimpleDateFormat("dd MMM", Locale("pt")).format(Date(t))
            }
        } catch (e: Exception) { "" }
    }

    // ====== Shimmer Drawable ======
    private class ShimmerDrawable(private val ctx: Context) : Drawable() {
        private val base = ContextCompat.getColor(ctx, R.color.skeleton)
        private val shimmerColor = 0x22FFFFFF
        private var anim: ValueAnimator? = null
        private var pos: Float = 0f

        fun duplicate(): ShimmerDrawable = ShimmerDrawable(ctx).also {
            it.anim = anim
            it.pos = pos
        }

        fun bind(anim: ValueAnimator?, pos: Float) {
            this.anim = anim
            this.pos = pos
        }

        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            if (w <= 0f || h <= 0f) return

            val cx = pos * w
            val left = cx - w * 0.4f
            val right = cx + w * 0.4f
            val shader = LinearGradient(
                left, 0f, right, 0f,
                intArrayOf(base, shimmerColor, base),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
