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

    private val activeShimmers =
        CopyOnWriteArraySet<WeakReference<ShimmerDrawable>>()

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

        val skeletonViews: List<View>
            get() = listOf(
                avatar,
                line1,
                line2,
                line3,
                image
            )
    }

    override fun getItemViewType(position: Int): Int {
        return if (list[position] is SkeletonMarker) {
            T_SKELETON
        } else {
            T_NEWS
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {

        val inflater = LayoutInflater.from(parent.context)

        return if (viewType == T_SKELETON) {
            SkeletonVH(
                inflater.inflate(
                    R.layout.item_news_skeleton,
                    parent,
                    false
                )
            )
        } else {
            NewsVH(
                inflater.inflate(
                    R.layout.item_news,
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {

        if (holder is SkeletonVH) {

            holder.image.visibility =
                if (position % 3 == 0) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            holder.skeletonViews.forEach { view ->

                val oldDrawable =
                    view.background as? ShimmerDrawable

                if (oldDrawable != null) {
                    removeShimmer(oldDrawable)
                }

                val drawable =
                    ShimmerDrawable(holder.itemView.context)

                view.background = drawable

                activeShimmers.add(
                    WeakReference(drawable)
                )
            }

            startShimmerIfNeeded()
            return
        }

        val item = list[position] as? NewsItem
            ?: return

        val vh = holder as NewsVH

        try {

            vh.source.text = item.source
            vh.title.text = item.title
            vh.time.text = ago(item.date)

            Glide.with(vh.favicon)
                .clear(vh.favicon)

            if (item.logo.isNotBlank()) {
                Glide.with(vh.favicon)
                    .load(item.logo)
                    .into(vh.favicon)
            } else {
                vh.favicon.setImageDrawable(null)
            }

            Glide.with(vh.image)
                .clear(vh.image)

            if (item.image.isNotBlank()) {

                vh.imageCard.visibility =
                    View.VISIBLE

                Glide.with(vh.image)
                    .load(item.image)
                    .into(vh.image)

            } else {

                vh.imageCard.visibility =
                    View.GONE

                vh.image.setImageDrawable(null)
            }

            vh.itemView.setOnClickListener {
                try {
                    onClick(item)
                } catch (t: Throwable) {
                    android.util.Log.e(
                        "NewsAdapter",
                        "News click failed",
                        t
                    )
                }
            }

        } catch (t: Throwable) {

            android.util.Log.e(
                "NewsAdapter",
                "Bind failed at position=$position",
                t
            )

            vh.source.text = item.source
            vh.time.text = ""
            vh.title.text =
                item.title.take(180)

            vh.imageCard.visibility =
                View.GONE

            vh.image.setImageDrawable(null)

            vh.itemView.setOnClickListener {
                try {
                    onClick(item)
                } catch (_: Throwable) {
                }
            }
        }
    }

    override fun onViewRecycled(
        holder: RecyclerView.ViewHolder
    ) {

        if (holder is SkeletonVH) {

            holder.skeletonViews.forEach { view ->

                val drawable =
                    view.background as? ShimmerDrawable

                if (drawable != null) {
                    removeShimmer(drawable)
                }
            }

        } else if (holder is NewsVH) {

            Glide.with(holder.favicon)
                .clear(holder.favicon)

            Glide.with(holder.image)
                .clear(holder.image)
        }

        super.onViewRecycled(holder)
    }

    override fun onDetachedFromRecyclerView(
        recyclerView: RecyclerView
    ) {

        shimmerAnim?.cancel()
        shimmerAnim = null

        activeShimmers.clear()

        super.onDetachedFromRecyclerView(
            recyclerView
        )
    }

    override fun getItemCount(): Int {
        return list.size
    }

    private fun removeShimmer(
        drawable: ShimmerDrawable
    ) {
        val toRemove =
            mutableListOf<WeakReference<ShimmerDrawable>>()

        for (ref in activeShimmers) {
            if (ref.get() === drawable) {
                toRemove += ref
            }
        }

        if (toRemove.isNotEmpty()) {
            activeShimmers.removeAll(toRemove)
        }
    }

    private fun removeDeadShimmers() {

        val dead =
            mutableListOf<WeakReference<ShimmerDrawable>>()

        for (ref in activeShimmers) {
            if (ref.get() == null) {
                dead += ref
            }
        }

        if (dead.isNotEmpty()) {
            activeShimmers.removeAll(dead)
        }
    }

    private fun startShimmerIfNeeded() {

        if (shimmerAnim?.isRunning == true) {
            return
        }

        shimmerAnim =
            ValueAnimator.ofFloat(
                -1f,
                2f
            ).apply {

                duration = 1400L

                repeatCount =
                    ValueAnimator.INFINITE

                interpolator =
                    LinearInterpolator()

                addUpdateListener { animator ->

                    val progress =
                        animator.animatedValue as Float

                    removeDeadShimmers()

                    for (ref in activeShimmers) {
                        ref.get()?.setProgress(
                            progress
                        )
                    }
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

    fun showSkeleton(
        count: Int = 8
    ) {

        shimmerAnim?.cancel()
        shimmerAnim = null

        activeShimmers.clear()

        list.clear()

        repeat(
            count.coerceIn(1, 12)
        ) {
            list.add(SkeletonMarker)
        }

        notifyDataSetChanged()

        startShimmerIfNeeded()
    }

    fun submit(
        items: List<NewsItem>
    ) {

        val old =
            list.toList()

        val newItems: List<Any> =
            items.map { it }

        val diff =
            DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {

                    override fun getOldListSize(): Int {
                        return old.size
                    }

                    override fun getNewListSize(): Int {
                        return newItems.size
                    }

                    override fun areItemsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int
                    ): Boolean {

                        val a =
                            old[oldItemPosition]

                        val b =
                            newItems[newItemPosition]

                        return a is NewsItem &&
                                b is NewsItem &&
                                a.id == b.id
                    }

                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int
                    ): Boolean {

                        return old[oldItemPosition] ==
                                newItems[newItemPosition]
                    }
                },
                true
            )

        list.clear()
        list.addAll(newItems)

        diff.dispatchUpdatesTo(this)

        stopShimmerIfNoSkeleton()
    }

    fun append(
        items: List<NewsItem>
    ) {

        val hasSkeleton =
            list.any { it is SkeletonMarker }

        if (hasSkeleton) {

            list.clear()

            notifyDataSetChanged()
        }

        val start =
            list.size

        list.addAll(items)

        if (items.isNotEmpty()) {

            notifyItemRangeInserted(
                start,
                items.size
            )
        }

        stopShimmerIfNoSkeleton()
    }

    private fun ago(
        value: String
    ): String {

        if (value.isBlank()) {
            return ""
        }

        return try {

            val clean =
                value.replace(
                    "Z",
                    ""
                )

            val format =
                SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    Locale.getDefault()
                ).apply {
                    timeZone =
                        TimeZone.getTimeZone("UTC")
                }

            val parsed =
                clean.substring(
                    0,
                    clean.length.coerceAtMost(19)
                )

            val time =
                format.parse(parsed)?.time
                    ?: return ""

            val minutes =
                (
                    (
                        System.currentTimeMillis() -
                                time
                    ) / 60000
                ).coerceAtLeast(0)

            when {

                minutes < 1 ->
                    "agora"

                minutes < 60 ->
                    "$minutes min"

                minutes < 1440 ->
                    "${minutes / 60} h"

                minutes < 10080 ->
                    "${minutes / 1440} d"

                else ->
                    SimpleDateFormat(
                        "dd MMM",
                        Locale("pt", "PT")
                    ).format(
                        Date(time)
                    )
            }

        } catch (_: Exception) {
            ""
        }
    }

    private class ShimmerDrawable(
        context: android.content.Context
    ) : Drawable() {

        private val baseColor =
            ContextCompat.getColor(
                context,
                R.color.skeleton
            )

        private val highlightColor =
            if (
                (
                    context.resources
                        .configuration
                        .uiMode
                        and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                ) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            ) {
                0x28FFFFFF
            } else {
                0x22000000
            }

        private val paint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            )

        private var progress =
            -1f

        private val radius =
            10f *
                    context.resources
                        .displayMetrics
                        .density

        fun setProgress(
            value: Float
        ) {

            progress = value

            invalidateSelf()
        }

        override fun draw(
            canvas: Canvas
        ) {

            val width =
                bounds.width().toFloat()

            val height =
                bounds.height().toFloat()

            if (
                width <= 0f ||
                height <= 0f
            ) {
                return
            }

            paint.shader =
                LinearGradient(
                    progress * width -
                            width * 0.55f,
                    0f,
                    progress * width +
                            width * 0.55f,
                    0f,
                    intArrayOf(
                        baseColor,
                        highlightColor,
                        baseColor
                    ),
                    floatArrayOf(
                        0f,
                        0.5f,
                        1f
                    ),
                    Shader.TileMode.CLAMP
                )

            canvas.drawRoundRect(
                0f,
                0f,
                width,
                height,
                radius,
                radius,
                paint
            )
        }

        override fun setAlpha(
            alpha: Int
        ) {
            paint.alpha = alpha
        }

        override fun setColorFilter(
            colorFilter: ColorFilter?
        ) {
            paint.colorFilter =
                colorFilter
        }

        @Deprecated(
            "Deprecated in Java"
        )
        override fun getOpacity(): Int {
            return PixelFormat.TRANSLUCENT
        }
    }
}