package com.appao

import android.animation.ValueAnimator
import android.content.res.ColorStateList
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
    private val list: MutableList<Any>,
    private val onClick: (NewsItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object { private const val T_NEWS = 1; private const val T_SKELETON = 2 }
    object SkeletonMarker

    private var shimmerAnim: ValueAnimator? = null
    private val shimmerDrawables = LinkedHashSet<ShimmerDrawable>()

    class NewsVH(v: View) : RecyclerView.ViewHolder(v) {
        val favicon: ImageView = v.findViewById(R.id.favicon)
        val source: TextView = v.findViewById(R.id.source)
        val time: TextView = v.findViewById(R.id.time)
        val title: TextView = v.findViewById(R.id.title)
        val imageCard: View = v.findViewById(R.id.imageCard)
        val image: ImageView = v.findViewById(R.id.image)
    }

    class SkeletonVH(v: View) : RecyclerView.ViewHolder(v) {
        val line1: View = v.findViewById(R.id.skLine1)
        val line2: View = v.findViewById(R.id.skLine2)
        val line3: View = v.findViewById(R.id.skLine3)
        val image: View = v.findViewById(R.id.skImage)
        val bars: List<View> get() = listOf(line1, line2, line3, image).filter { it.visibility != View.GONE }
    }

    override fun getItemViewType(position: Int): Int = if (list[position] is SkeletonMarker) T_SKELETON else T_NEWS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == T_SKELETON) SkeletonVH(inf.inflate(R.layout.item_news_skeleton, parent, false))
        else NewsVH(inf.inflate(R.layout.item_news, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is SkeletonVH) {
            holder.image.visibility = if (position % 3 == 0) View.VISIBLE else View.GONE
            holder.bars.forEach { v ->
                val d = ShimmerDrawable(v.context)
                v.background = d
                shimmerDrawables.add(d)
            }
            ensureShimmerRunning()
            return
        }

        val item = list[position] as NewsItem
        val vh = holder as NewsVH
        vh.source.text = item.source
        vh.title.text = item.title
        vh.time.text = ago(item.date)
        if (item.logo.isNotEmpty()) Glide.with(vh.favicon).load(item.logo).into(vh.favicon) else vh.favicon.setImageDrawable(null)
        if (item.image.isNotEmpty()) {
            vh.imageCard.visibility = View.VISIBLE
            Glide.with(vh.image).load(item.image).into(vh.image)
        } else vh.imageCard.visibility = View.GONE
        vh.itemView.setOnClickListener { onClick(item) }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is SkeletonVH) {
            listOf(holder.line1, holder.line2, holder.line3, holder.image).forEach { v ->
                (v.background as? ShimmerDrawable)?.let { shimmerDrawables.remove(it) }
                v.background = null
            }
        }
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = list.size

    private fun ensureShimmerRunning() {
        if (shimmerAnim?.isRunning == true) return
        shimmerAnim = ValueAnimator.ofFloat(-0.65f, 1.65f).apply {
            duration = 1450L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val phase = animator.animatedValue as Float
                shimmerDrawables.toList().forEach { it.phase = phase; it.invalidateSelf() }
            }
            start()
        }
    }

    private fun stopShimmer() {
        shimmerAnim?.cancel()
        shimmerAnim = null
        shimmerDrawables.clear()
    }

    private fun stopShimmerIfNoSkeleton() {
        if (list.none { it is SkeletonMarker }) stopShimmer()
    }

    fun showSkeleton(count: Int = 8) {
        stopShimmer()
        list.clear()
        repeat(count) { list.add(SkeletonMarker) }
        notifyDataSetChanged()
        ensureShimmerRunning()
    }

    fun submit(items: List<NewsItem>) {
        val old = list.toList()
        val new = items as List<Any>
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
            override fun areContentsTheSame(o: Int, n: Int): Boolean = old[o] == new[n]
        }, true)
        list.clear(); list.addAll(new)
        diff.dispatchUpdatesTo(this)
        stopShimmerIfNoSkeleton()
    }

    fun append(items: List<NewsItem>) {
        val start = list.size
        list.removeAll { it is SkeletonMarker }
        val removed = start - list.size
        if (removed > 0) notifyItemRangeRemoved(list.size, removed)
        val insert = list.size
        list.addAll(items)
        notifyItemRangeInserted(insert, items.size)
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
        } catch (_: Exception) { "" }
    }

    private class ShimmerDrawable(private val context: android.content.Context) : Drawable() {
        var phase: Float = -0.65f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat().coerceAtLeast(1f)
            val h = bounds.height().toFloat().coerceAtLeast(1f)
            val base = ContextCompat.getColor(context, R.color.skeleton)
            val highlight = if ((context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                0x2AFFFFFF else 0x55FFFFFF
            canvas.drawColor(base)
            val start = (phase * w * 1.5f) - w
            val end = start + w * 0.9f
            paint.shader = LinearGradient(start, 0f, end, 0f,
                intArrayOf(base, highlight, base), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
