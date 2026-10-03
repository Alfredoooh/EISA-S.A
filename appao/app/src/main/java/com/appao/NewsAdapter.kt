package com.appao

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.max

class NewsAdapter(
    initial: List<NewsItem>,
    private val onClick: (NewsItem, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_IMAGE = 1
        private const val TYPE_TEXT_SINGLE = 2
        private const val TYPE_TEXT_GROUP = 3
        private const val TYPE_SKELETON = 4
        private const val MAX_TEXT_GROUP = 3
    }

    private sealed class RenderRow {
        data class Image(val item: NewsItem) : RenderRow()
        data class TextSingle(val item: NewsItem) : RenderRow()
        data class TextGroup(val items: List<NewsItem>) : RenderRow()
        data object Skeleton : RenderRow()
    }

    private val articles = mutableListOf<NewsItem>()
    private val rows = mutableListOf<RenderRow>()
    private val activeShimmers = CopyOnWriteArraySet<WeakReference<ShimmerDrawable>>()
    private var shimmerAnim: ValueAnimator? = null

    init {
        replaceArticles(initial.filterIsInstance<NewsItem>())
    }

    class ImageVH(v: View) : RecyclerView.ViewHolder(v) {
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
            get() = listOf(avatar, line1, line2, line3, image)
    }

    class DynamicTextVH(v: View) : RecyclerView.ViewHolder(v)

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is RenderRow.Image -> TYPE_IMAGE
        is RenderRow.TextSingle -> TYPE_TEXT_SINGLE
        is RenderRow.TextGroup -> TYPE_TEXT_GROUP
        RenderRow.Skeleton -> TYPE_SKELETON
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_IMAGE -> ImageVH(
                inflater.inflate(R.layout.item_news, parent, false)
            )
            TYPE_SKELETON -> SkeletonVH(
                inflater.inflate(R.layout.item_news_skeleton, parent, false)
            )
            TYPE_TEXT_SINGLE, TYPE_TEXT_GROUP -> DynamicTextVH(
                FrameLayout(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(
                        dp(parent, 16),
                        dp(parent, 8),
                        dp(parent, 16),
                        dp(parent, 8)
                    )
                }
            )
            else -> error("Unknown news view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is RenderRow.Image -> bindImage(holder as ImageVH, row.item)
            is RenderRow.TextSingle -> bindTextSingle(holder as DynamicTextVH, row.item)
            is RenderRow.TextGroup -> bindTextGroup(holder as DynamicTextVH, row.items)
            RenderRow.Skeleton -> bindSkeleton(holder as SkeletonVH, position)
        }
    }

    private fun bindImage(holder: ImageVH, item: NewsItem) {
        val context = holder.itemView.context
        holder.itemView.transitionName = null
        holder.imageCard.transitionName = transitionName(item)

        holder.source.text = item.source
        holder.time.text = ago(item.date)
        holder.title.text = item.title

        Glide.with(holder.favicon).clear(holder.favicon)
        if (item.logo.isNotBlank()) {
            Glide.with(holder.favicon)
                .load(item.logo)
                .dontAnimate()
                .into(holder.favicon)
        } else {
            holder.favicon.setImageDrawable(null)
        }

        Glide.with(holder.image).clear(holder.image)
        if (item.image.isNotBlank()) {
            holder.imageCard.visibility = View.VISIBLE
            Glide.with(holder.image)
                .load(item.image)
                .centerCrop()
                .dontAnimate()
                .into(holder.image)
        } else {
            holder.imageCard.visibility = View.GONE
            holder.image.setImageDrawable(null)
        }

        holder.itemView.setOnClickListener {
            try {
                onClick(item, holder.imageCard)
            } catch (t: Throwable) {
                android.util.Log.e("NewsAdapter", "Image article click failed", t)
            }
        }
    }

    private fun bindTextSingle(holder: DynamicTextVH, item: NewsItem) {
        val context = holder.itemView.context
        val root = holder.itemView as FrameLayout
        root.removeAllViews()
        root.transitionName = null

        val card = createTextCard(
            context = context,
            item = item,
            square = false
        ).apply {
            transitionName = transitionName(item)
            setOnClickListener {
                try {
                    onClick(item, this)
                } catch (t: Throwable) {
                    android.util.Log.e("NewsAdapter", "Text article click failed", t)
                }
            }
        }

        root.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 156)
            )
        )
    }

    private fun bindTextGroup(holder: DynamicTextVH, items: List<NewsItem>) {
        val context = holder.itemView.context
        val root = holder.itemView as FrameLayout
        root.removeAllViews()
        root.transitionName = null

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        root.addView(
            row,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        items.forEachIndexed { index, item ->
            val card = createTextCard(
                context = context,
                item = item,
                square = true
            ).apply {
                transitionName = transitionName(item)
                setOnClickListener {
                    try {
                        onClick(item, this)
                    } catch (t: Throwable) {
                        android.util.Log.e("NewsAdapter", "Grouped article click failed", t)
                    }
                }
            }

            val lp = LinearLayout.LayoutParams(
                0,
                dp(context, 112),
                1f
            ).apply {
                if (index > 0) marginStart = dp(context, 8)
            }
            row.addView(card, lp)
        }

        root.post {
            val count = items.size.coerceIn(1, MAX_TEXT_GROUP)
            val available = row.width - dp(context, 8) * (count - 1)
            val cardSize = ((available.toFloat() / count).toInt()).coerceAtLeast(dp(context, 72))
            for (i in 0 until row.childCount) {
                val child = row.getChildAt(i)
                val lp = child.layoutParams as LinearLayout.LayoutParams
                lp.width = cardSize
                lp.height = cardSize
                child.layoutParams = lp
            }
        }
    }

    private fun createTextCard(
        context: android.content.Context,
        item: NewsItem,
        square: Boolean
    ): FrameLayout {
        val card = FrameLayout(context).apply {
            background = rounded(
                ContextCompat.getColor(context, R.color.card),
                dp(context, 18).toFloat()
            )
            clipToOutline = true
            isClickable = true
            isFocusable = true
            elevation = dp(context, 1).toFloat()
        }

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(context, if (square) 10 else 14),
                dp(context, if (square) 10 else 14),
                dp(context, if (square) 10 else 14),
                dp(context, if (square) 10 else 14)
            )
        }
        card.addView(
            body,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        body.addView(
            top,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val favicon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(
                ContextCompat.getColor(context, R.color.bgElevated),
                dp(context, 99).toFloat()
            )
            clipToOutline = true
            setPadding(
                dp(context, 2),
                dp(context, 2),
                dp(context, 2),
                dp(context, 2)
            )
        }
        top.addView(
            favicon,
            LinearLayout.LayoutParams(
                dp(context, if (square) 20 else 24),
                dp(context, if (square) 20 else 24)
            )
        )

        val source = TextView(context).apply {
            text = item.source.ifBlank { "Fonte" }
            textSize = if (square) 10.5f else 12f
            setTextColor(ContextCompat.getColor(context, R.color.dim))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        top.addView(
            source,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(context, 7)
            }
        )

        val title = TextView(context).apply {
            text = item.title
            textSize = if (square) 12.5f else 15f
            setTextColor(ContextCompat.getColor(context, R.color.text))
            setTypeface(
                Typeface.create(
                    "sans-serif",
                    Typeface.BOLD
                )
            )
            setLineSpacing(0f, 1.25f)
            maxLines = if (square) 5 else 5
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        body.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 9)
            }
        )

        if (!square) {
            val meta = TextView(context).apply {
                text = ago(item.date)
                textSize = 11.5f
                setTextColor(ContextCompat.getColor(context, R.color.dim))
                maxLines = 1
            }
            body.addView(
                meta,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 8)
                }
            )
        }

        Glide.with(favicon).clear(favicon)
        if (item.logo.isNotBlank()) {
            Glide.with(favicon)
                .load(item.logo)
                .override(
                    if (square) dp(context, 20) else dp(context, 24),
                    if (square) dp(context, 20) else dp(context, 24)
                )
                .dontAnimate()
                .into(favicon)
        }

        return card
    }

    private fun bindSkeleton(holder: SkeletonVH, position: Int) {
        holder.avatar.background = rounded(
            ContextCompat.getColor(holder.itemView.context, R.color.skeleton),
            999f
        )
        holder.avatar.clipToOutline = true
        holder.image.visibility = if (position % 3 == 0) View.VISIBLE else View.GONE

        holder.skeletonViews.forEach { view ->
            (view.background as? ShimmerDrawable)?.let { old ->
                activeShimmers.removeIf { it.get() === old }
            }
            val drawable = ShimmerDrawable(holder.itemView.context)
            view.background = drawable
            activeShimmers.add(WeakReference(drawable))
        }

        startShimmerIfNeeded()
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is SkeletonVH -> holder.skeletonViews.forEach { view ->
                (view.background as? ShimmerDrawable)?.let { drawable ->
                    activeShimmers.removeIf { it.get() === drawable }
                }
            }
            is ImageVH -> {
                Glide.with(holder.favicon).clear(holder.favicon)
                Glide.with(holder.image).clear(holder.image)
            }
        }
        super.onViewRecycled(holder)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        shimmerAnim?.cancel()
        shimmerAnim = null
        activeShimmers.clear()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun getItemCount(): Int = rows.size

    fun showSkeleton(count: Int = 8) {
        articles.clear()
        rows.clear()
        repeat(count.coerceIn(1, 12)) {
            rows += RenderRow.Skeleton
        }
        activeShimmers.clear()
        shimmerAnim?.cancel()
        shimmerAnim = null
        notifyDataSetChanged()
        startShimmerIfNeeded()
    }

    fun submit(items: List<NewsItem>) {
        replaceArticles(items)
        rebuildRows()
        notifyDataSetChanged()
        stopShimmerIfNoSkeleton()
    }

    fun append(items: List<NewsItem>) {
        val seen = articles.flatMap { listOf(normalizeUrl(it.link), titleKey(it.title)) }.toHashSet()
        val fresh = items.filter { item ->
            val u = normalizeUrl(item.link)
            val t = titleKey(item.title)
            if (u.isBlank() || t.isBlank()) {
                false
            } else if (u in seen || t in seen) {
                false
            } else {
                seen += u
                seen += t
                true
            }
        }
        if (fresh.isEmpty()) return
        articles += fresh
        rebuildRows()
        notifyDataSetChanged()
        stopShimmerIfNoSkeleton()
    }

    private fun replaceArticles(items: List<NewsItem>) {
        articles.clear()
        val seenUrl = HashSet<String>()
        val seenTitle = HashSet<String>()
        for (item in items) {
            val u = normalizeUrl(item.link)
            val t = titleKey(item.title)
            if (u.isBlank() || t.isBlank()) continue
            if (!seenUrl.add(u)) continue
            if (!seenTitle.add(t)) continue
            articles += item
        }
    }

    private fun rebuildRows() {
        rows.clear()
        var i = 0
        while (i < articles.size) {
            val item = articles[i]
            if (item.image.isNotBlank()) {
                rows += RenderRow.Image(item)
                i++
                continue
            }

            val group = mutableListOf<NewsItem>()
            while (i < articles.size && articles[i].image.isBlank() && group.size < MAX_TEXT_GROUP) {
                group += articles[i]
                i++
            }

            if (group.size == 1) {
                rows += RenderRow.TextSingle(group.first())
            } else {
                rows += RenderRow.TextGroup(group.toList())
            }
        }
    }

    private fun startShimmerIfNeeded() {
        if (shimmerAnim?.isRunning == true) return
        if (rows.none { it === RenderRow.Skeleton }) return

        shimmerAnim = ValueAnimator.ofFloat(-1f, 2f).apply {
            duration = 1400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { animator ->
                activeShimmers.removeIf { it.get() == null }
                val progress = animator.animatedValue as Float
                activeShimmers.forEach { it.get()?.setProgress(progress) }
            }
            start()
        }
    }

    private fun stopShimmerIfNoSkeleton() {
        if (rows.none { it === RenderRow.Skeleton }) {
            shimmerAnim?.cancel()
            shimmerAnim = null
            activeShimmers.clear()
        }
    }

    private fun transitionName(item: NewsItem): String =
        "news_container_${item.id.hashCode().toUInt().toString(36)}"

    private fun titleKey(value: String): String = value.trim().lowercase(Locale.ROOT)
        .replace(Regex("[\\p{Punct}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizeUrl(value: String): String = value.trim()
        .lowercase(Locale.ROOT)
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('#')
        .trimEnd('/')

    private fun ago(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val raw = value.replace("Z", "")
            val format = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss",
                Locale.getDefault()
            ).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = format.parse(
                raw.substring(0, raw.length.coerceAtMost(19))
            ) ?: return ""
            val minutes = ((System.currentTimeMillis() - date.time) / 60000)
                .coerceAtLeast(0)
            when {
                minutes < 1 -> "agora"
                minutes < 60 -> "$minutes min"
                minutes < 1440 -> "${minutes / 60} h"
                minutes < 10080 -> "${minutes / 1440} d"
                else -> SimpleDateFormat(
                    "dd MMM",
                    Locale("pt", "PT")
                ).format(Date(date.time))
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun rounded(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
        }

    private fun dp(context: android.content.Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun dp(view: View, value: Int): Int = dp(view.context, value)

    private class ShimmerDrawable(
        context: android.content.Context
    ) : Drawable() {

        private val baseColor = ContextCompat.getColor(
            context,
            R.color.skeleton
        )

        private val highlightColor =
            if (
                (
                    context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                ) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            ) {
                0x28FFFFFF
            } else {
                0x22000000
            }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var progress = -1f
        private val radius =
            10f * context.resources.displayMetrics.density

        fun setProgress(value: Float) {
            progress = value
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val width = bounds.width().toFloat()
            val height = bounds.height().toFloat()
            if (width <= 0f || height <= 0f) return

            paint.shader = LinearGradient(
                progress * width - width * 0.55f,
                0f,
                progress * width + width * 0.55f,
                0f,
                intArrayOf(
                    baseColor,
                    Color.argb(
                        Color.alpha(highlightColor),
                        Color.red(highlightColor),
                        Color.green(highlightColor),
                        Color.blue(highlightColor)
                    ),
                    baseColor
                ),
                floatArrayOf(0f, 0.5f, 1f),
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

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
