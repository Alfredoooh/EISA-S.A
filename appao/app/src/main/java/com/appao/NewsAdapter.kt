package com.appao

import android.animation.ValueAnimator
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
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArraySet

class NewsAdapter(
    private val list: MutableList<Any>,
    private val onClick: (NewsItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val T_NEWS = 1
        private const val T_SKELETON = 2
    }

    object SkeletonMarker

    private var shimmerAnim: ValueAnimator? = null
    private val activeShimmers = CopyOnWriteArraySet<WeakReference<ShimmerDrawable>>()

    class NewsVH(v: View) : RecyclerView.ViewHolder(v) {
        val favicon: ImageView = v.findViewById(R.id.favicon)
        val source: TextView = v.findViewById(R.id.source)
        val time: TextView = v.findViewById(R.id.time)
        val title: TextView = v.findViewById(R.id.title)
        val imageCard: View = v.findViewById(R.id.imageCard)
        val image: ImageView = v.findViewById(R.id.image)
    }

    class SkeletonVH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: View = v.findViewById(R.id.skAvatar)
        val line1: View = v.findViewById(R.id.skLine1)
        val line2: View = v.findViewById(R.id.skLine2)
        val line3: View = v.findViewById(R.id.skLine3)
        val image: View = v.findViewById(R.id.skImage)
        val skeletonViews get() = listOf(avatar, line1, line2, line3, image)
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
            holder.skeletonViews.forEach { view ->
                (view.background as? ShimmerDrawable)?.let(activeShimmers::removeIfDrawable)
                val d = ShimmerDrawable(holder.itemView.context)
                view.background = d
                activeShimmers += WeakReference(d)
            }
            startShimmerIfNeeded()
            return
        }

        val item = list[position] as? NewsItem ?: return
        val vh = holder as NewsVH
        try {
            vh.source.text = item.source
        vh.title.text = item.title
        vh.time.text = ago(item.date)

        Glide.with(vh.favicon).clear(vh.favicon)
        if (item.logo.isNotEmpty()) Glide.with(vh.favicon).load(item.logo).into(vh.favicon)
        else vh.favicon.setImageDrawable(null)

        Glide.with(vh.image).clear(vh.image)
        if (item.image.isNotEmpty()) {
            vh.imageCard.visibility = View.VISIBLE
            Glide.with(vh.image).load(item.image).into(vh.image)
        } else {
            vh.imageCard.visibility = View.GONE
            vh.image.setImageDrawable(null)
        }

            vh.itemView.setOnClickListener { onClick(item) }
        } catch (_: Exception) {
            vh.source.text = item.source
            vh.time.text = ""
            vh.title.text = item.title.take(180)
            vh.imageCard.visibility = View.GONE
            vh.image.setImageDrawable(null)
            vh.itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is SkeletonVH) {
            holder.skeletonViews.forEach { view ->
                (view.background as? ShimmerDrawable)?.let { drawable ->
                    activeShimmers.removeIf { it.get() === drawable }
                }
            }
        } else if (holder is NewsVH) {
            Glide.with(holder.favicon).clear(holder.favicon)
            Glide.with(holder.image).clear(holder.image)
        }
        super.onViewRecycled(holder)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        shimmerAnim?.cancel()
        shimmerAnim = null
        activeShimmers.clear()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun getItemCount() = list.size

    private fun startShimmerIfNeeded() {
        if (shimmerAnim?.isRunning == true) return
        shimmerAnim = ValueAnimator.ofFloat(-1f, 2f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val p = animator.animatedValue as Float
                activeShimmers.removeIf { it.get() == null }
                activeShimmers.forEach { it.get()?.setProgress(p) }
            }
            start()
        }
    }

    private fun stopShimmerIfNoSkeleton() {
        if (list.none { it is SkeletonMarker }) {
            shimmerAnim?.cancel()
            shimmerAnim = null
            activeShimmers.clear()
        }
    }

    fun showSkeleton(count: Int = 8) {
        shimmerAnim?.cancel()
        shimmerAnim = null
        activeShimmers.clear()
        list.clear()
        repeat(count.coerceIn(1, 12)) { list.add(SkeletonMarker) }
        notifyDataSetChanged()
        startShimmerIfNeeded()
    }

    fun submit(items: List<NewsItem>) {
        val old = list.toList()
        val new = items.map { it as Any }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = new.size
            override fun areItemsTheSame(o: Int, n: Int): Boolean {
                val a = old[o]; val b = new[n]
                return a is NewsItem && b is NewsItem && a.id == b.id
            }
            override fun areContentsTheSame(o: Int, n: Int): Boolean = old[o] == new[n]
        }, true)
        list.clear()
        list.addAll(new)
        diff.dispatchUpdatesTo(this)
        stopShimmerIfNoSkeleton()
    }

    fun append(items: List<NewsItem>) {
        val skeletonCount = list.count { it is SkeletonMarker }
        if (skeletonCount > 0) {
            list.clear()
            notifyDataSetChanged()
        }
        val start = list.size
        list.addAll(items)
        if (items.isNotEmpty()) notifyItemRangeInserted(start, items.size)
        stopShimmerIfNoSkeleton()
    }

    private fun ago(v: String): String {
        if (v.isBlank()) return ""
        return try {
            val clean = v.replace("Z", "")
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val t = fmt.parse(clean.substring(0, clean.length.coerceAtMost(19)))?.time ?: return ""
            val m = ((System.currentTimeMillis() - t) / 60000).coerceAtLeast(0)
            when {
                m < 1 -> "agora"
                m < 60 -> "$m min"
                m < 1440 -> "${m / 60} h"
                m < 10080 -> "${m / 1440} d"
                else -> SimpleDateFormat("dd MMM", Locale("pt", "PT")).format(Date(t))
            }
        } catch (_: Exception) { "" }
    }

    private class ShimmerDrawable(ctx: android.content.Context) : Drawable() {
        private val base = ContextCompat.getColor(ctx, R.color.skeleton)
        private val highlight = if ((ctx.resources.configuration.uiMode and 0x30) == 0x20) 0x28FFFFFF else 0x22000000
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var progress = -1f
        private val radius = 10f * ctx.resources.displayMetrics.density

        fun setProgress(value: Float) {
            progress = value
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            if (w <= 0f || h <= 0f) return
            paint.shader = LinearGradient(
                progress * w - w * 0.55f, 0f,
                progress * w + w * 0.55f, 0f,
                intArrayOf(base, highlight, base),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun <T> CopyOnWriteArraySet<WeakReference<T>>.removeIfDrawable(drawable: T) {
        removeIf { it.get() === drawable }
    }
}
