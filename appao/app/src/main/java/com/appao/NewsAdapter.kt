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

/** Native feed renderer with image cards plus grouped, square no-image sections. */
class NewsAdapter(
    initial: List<NewsItem>,
    private val onClick: (NewsItem, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_IMAGE = 1
        private const val TYPE_TEXT_SINGLE = 2
        private const val TYPE_TEXT_GROUP = 3
        private const val TYPE_SKELETON = 4
        private const val MAX_TEXT_GROUP = 6
        private const val GRID_GAP_DP = 8
        private const val GROUP_CARD_MAX_DP = 136
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
        replaceArticles(initial)
        rebuildRows()
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

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
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
                        dp(parent.context, 16),
                        dp(parent.context, 8),
                        dp(parent.context, 16),
                        dp(parent.context, 8)
                    )
                    clipChildren = false
                    clipToPadding = false
                }
            )
            else -> error("Unknown news view type: $viewType")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        when (val row = rows[position]) {
            is RenderRow.Image -> bindImage(holder as ImageVH, row.item)
            is RenderRow.TextSingle -> bindTextSingle(holder as DynamicTextVH, row.item)
            is RenderRow.TextGroup -> bindTextGroup(holder as DynamicTextVH, row.items)
            RenderRow.Skeleton -> bindSkeleton(holder as SkeletonVH, position)
        }
    }

    private fun bindImage(holder: ImageVH, item: NewsItem) {
        holder.itemView.transitionName = null
        holder.imageCard.transitionName = transitionName(item)

        holder.source.text = item.source
        holder.time.text = ago(item.date)
        holder.title.text = item.title

        Glide.with(holder.favicon).clear(holder.favicon)
        if (item.logo.isNotBlank()) {
            Glide.with(holder.favicon)
                .load(item.logo)
                .override(dp(holder.itemView.context, 34), dp(holder.itemView.context, 34))
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

    private fun bindTextSingle(
        holder: DynamicTextVH,
        item: NewsItem
    ) {
        val root = holder.itemView as FrameLayout
        val context = root.context
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
                dp(context, 158)
            )
        )
    }

    private fun bindTextGroup(
        holder: DynamicTextVH,
        items: List<NewsItem>
    ) {
        val root = holder.itemView as FrameLayout
        val context = root.context
        root.removeAllViews()
        root.transitionName = null

        val columns = when {
            items.size == 2 -> 2
            items.size == 4 -> 2
            else -> 3
        }

        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        root.addView(
            section,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        items.chunked(columns).forEachIndexed { rowIndex, chunk ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
            }

            if (rowIndex > 0) {
                section.addView(
                    SpaceView(context),
                    LinearLayout.LayoutParams(
                        1,
                        dp(context, GRID_GAP_DP)
                    )
                )
            }

            section.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, GROUP_CARD_MAX_DP)
                )
            )

            chunk.forEachIndexed { index, item ->
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
                    dp(context, GROUP_CARD_MAX_DP),
                    1f
                )

                if (index > 0) {
                    lp.marginStart = dp(context, GRID_GAP_DP)
                }

                if (chunk.size < columns && index == chunk.lastIndex) {
                    lp.weight = 1f
                }

                row.addView(card, lp)
            }

            // Keep every group card visually square after the real row width is known.
            row.post {
                val available = (
                    row.width -
                        dp(context, GRID_GAP_DP) * (chunk.size - 1)
                    ).coerceAtLeast(dp(context, 80))

                val size = (
                    available.toFloat() / chunk.size
                ).toInt()
                    .coerceAtMost(dp(context, GROUP_CARD_MAX_DP))
                    .coerceAtLeast(dp(context, 92))

                for (i in 0 until row.childCount) {
                    val child = row.getChildAt(i)
                    val lp = child.layoutParams as LinearLayout.LayoutParams
                    lp.width = size
                    lp.height = size
                    lp.weight = 0f
                    child.layoutParams = lp
                }
            }
        }
    }

    private fun createTextCard(
        context: android.content.Context,
        item: NewsItem,
        square: Boolean
    ): FrameLayout {

        val card = FrameLayout(context).apply {
            background = ContextCompat.getDrawable(
                context,
                R.drawable.bg_card_pressed
            )
            clipToOutline = true
            isClickable = true
            isFocusable = true
            elevation = dp(context, 1).toFloat()
        }

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(context, if (square) 12 else 16),
                dp(context, if (square) 12 else 16),
                dp(context, if (square) 12 else 16),
                dp(context, if (square) 12 else 16)
            )
        }

        card.addView(
            body,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val metaRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val faviconFrame = FrameLayout(context).apply {
            background = rounded(
                ContextCompat.getColor(
                    context,
                    R.color.bgElevated
                ),
                dp(context, 999).toFloat()
            )
            clipToOutline = true
        }

        val favicon = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(
                dp(context, 2),
                dp(context, 2),
                dp(context, 2),
                dp(context, 2)
            )
        }

        faviconFrame.addView(
            favicon,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        metaRow.addView(
            faviconFrame,
            LinearLayout.LayoutParams(
                dp(context, if (square) 36 else 42),
                dp(context, if (square) 36 else 42)
            )
        )

        val source = TextView(context).apply {
            text = item.source.ifBlank { "Fonte" }
            textSize = if (square) 13.5f else 14f
            setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.dim
                )
            )
            setTypeface(
                Typeface.create(
                    "sans-serif-medium",
                    Typeface.NORMAL
                )
            )
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        metaRow.addView(
            source,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dp(context, 10)
            }
        )

        body.addView(metaRow)

        val title = TextView(context).apply {
            text = item.title
            textSize = if (square) 16f else 19f
            setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.text
                )
            )
            setTypeface(
                Typeface.create(
                    "sans-serif",
                    Typeface.BOLD
                )
            )
            setLineSpacing(0f, 1.25f)
            maxLines = if (square) 4 else 5
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        body.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, if (square) 12 else 16)
            }
        )

        if (!square) {
            val meta = TextView(context).apply {
                text = ago(item.date)
                textSize = 13f
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.dim
                    )
                )
            }

            body.addView(
                meta,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(context, 10)
                }
            )
        }

        Glide.with(favicon).clear(favicon)
        if (item.logo.isNotBlank()) {
            val size = if (square) 32 else 38
            Glide.with(favicon)
                .load(item.logo)
                .override(
                    dp(context, size),
                    dp(context, size)
                )
                .dontAnimate()
                .into(favicon)
        } else {
            favicon.setImageDrawable(null)
        }

        return card
    }

    private fun bindSkeleton(
        holder: SkeletonVH,
        position: Int
    ) {
        holder.image.visibility =
            if (position % 3 == 0) View.VISIBLE else View.GONE

        for (view in holder.skeletonViews) {
            val old = view.background as? ShimmerDrawable
            if (old != null) {
                removeShimmer(old)
            }

            val drawable = ShimmerDrawable(
                holder.itemView.context
            )

            view.background = drawable

            activeShimmers.add(
                WeakReference(drawable)
            )
        }

        startShimmerIfNeeded()
    }

    override fun onViewRecycled(
        holder: RecyclerView.ViewHolder
    ) {
        when (holder) {
            is SkeletonVH -> {
                holder.skeletonViews.forEach { view ->
                    val drawable =
                        view.background as? ShimmerDrawable
                    if (drawable != null) {
                        removeShimmer(drawable)
                    }
                }
            }

            is ImageVH -> {
                Glide.with(holder.favicon)
                    .clear(holder.favicon)
                Glide.with(holder.image)
                    .clear(holder.image)
            }
        }

        super.onViewRecycled(holder)
    }

    override fun onDetachedFromRecyclerView(
        recyclerView: RecyclerView
    ) {
        shimmerAnim?.cancel()
        shimmerAnim = null
        activeShimmers.clear()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun getItemCount(): Int = rows.size

    fun showSkeleton(
        count: Int = 8
    ) {
        articles.clear()
        rows.clear()
        activeShimmers.clear()
        shimmerAnim?.cancel()
        shimmerAnim = null

        repeat(
            count.coerceIn(1, 12)
        ) {
            rows += RenderRow.Skeleton
        }

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
        val seenUrls = HashSet<String>()
        val seenTitles = HashSet<String>()

        articles.forEach { item ->
            seenUrls += normalizeUrl(item.link)
            seenTitles += titleKey(item.title)
        }

        val fresh = items.filter { item ->
            val url = normalizeUrl(item.link)
            val title = titleKey(item.title)

            if (url.isBlank() || title.isBlank()) {
                false
            } else if (
                !seenUrls.add(url) ||
                !seenTitles.add(title)
            ) {
                false
            } else {
                true
            }
        }

        if (fresh.isEmpty()) return

        articles += fresh
        rebuildRows()
        notifyDataSetChanged()
        stopShimmerIfNoSkeleton()
    }

    private fun replaceArticles(
        items: List<NewsItem>
    ) {
        articles.clear()

        val seenUrls = HashSet<String>()
        val seenTitles = HashSet<String>()

        items.forEach { item ->
            val url = normalizeUrl(item.link)
            val title = titleKey(item.title)

            if (url.isBlank() || title.isBlank()) {
                return@forEach
            }

            if (!seenUrls.add(url)) return@forEach
            if (!seenTitles.add(title)) return@forEach

            articles += item
        }
    }

    /**
     * Image articles stay independent. Consecutive no-image articles are kept
     * together in their own section. A single no-image article remains a long
     * card; 2/4 use two columns; other groups use three columns.
     */
    private fun rebuildRows() {
        rows.clear()

        var index = 0

        while (index < articles.size) {
            val item = articles[index]

            if (item.image.isNotBlank()) {
                rows += RenderRow.Image(item)
                index++
                continue
            }

            val group = ArrayList<NewsItem>(MAX_TEXT_GROUP)

            while (
                index < articles.size &&
                articles[index].image.isBlank() &&
                group.size < MAX_TEXT_GROUP
            ) {
                group += articles[index]
                index++
            }

            if (group.size == 1) {
                rows += RenderRow.TextSingle(group[0])
            } else {
                rows += RenderRow.TextGroup(group.toList())
            }
        }
    }

    private fun startShimmerIfNeeded() {
        if (shimmerAnim?.isRunning == true) return
        if (rows.none { it === RenderRow.Skeleton }) return

        shimmerAnim = ValueAnimator.ofFloat(
            -1f,
            2f
        ).apply {
            duration = 1400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()

            addUpdateListener { animator ->
                val progress =
                    animator.animatedValue as Float

                val dead = ArrayList<WeakReference<ShimmerDrawable>>()

                for (ref in activeShimmers) {
                    val drawable = ref.get()
                    if (drawable == null) {
                        dead += ref
                    } else {
                        drawable.setProgress(progress)
                    }
                }

                if (dead.isNotEmpty()) {
                    activeShimmers.removeAll(dead.toSet())
                }
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

    private fun removeShimmer(
        drawable: ShimmerDrawable
    ) {
        val remove = ArrayList<WeakReference<ShimmerDrawable>>()

        for (ref in activeShimmers) {
            if (ref.get() === drawable) {
                remove += ref
            }
        }

        if (remove.isNotEmpty()) {
            activeShimmers.removeAll(remove.toSet())
        }
    }

    private fun transitionName(
        item: NewsItem
    ): String =
        "news_container_${item.id.hashCode().toUInt().toString(36)}"

    private fun titleKey(
        value: String
    ): String = value.trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[\\p{Punct}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizeUrl(
        value: String
    ): String = value.trim()
        .lowercase(Locale.ROOT)
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore('#')
        .substringBefore("?ref=")
        .trimEnd('/')

    private fun ago(
        value: String
    ): String {
        if (value.isBlank()) return ""

        return try {
            val raw = value
                .replace("Z", "")
                .replace(Regex("[+-]\\d{2}:?\\d{2}$"), "")

            val format = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss",
                Locale.US
            ).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val date = format.parse(
                raw.substring(
                    0,
                    raw.length.coerceAtMost(19)
                )
            ) ?: return ""

            val minutes = (
                (System.currentTimeMillis() - date.time) / 60000
            ).coerceAtLeast(0)

            when {
                minutes < 1 -> "agora"
                minutes < 60 -> "$minutes min"
                minutes < 1440 -> "${minutes / 60} h"
                minutes < 10080 -> "${minutes / 1440} d"
                else -> SimpleDateFormat(
                    "dd MMM",
                    Locale("pt", "PT")
                ).format(
                    Date(date.time)
                )
            }
        } catch (_: Exception) {
            ""
        }
    }

    private class SpaceView(
        context: android.content.Context
    ) : View(context)

    private fun rounded(
        color: Int,
        radius: Float
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(
        context: android.content.Context,
        value: Int
    ): Int = (
        value *
            context.resources.displayMetrics.density +
            0.5f
    ).toInt()

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
                    ) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            ) {
                0x24FFFFFF
            } else {
                0x18000000
            }

        private val paint = Paint(
            Paint.ANTI_ALIAS_FLAG
        )

        private var progress = -1f

        private val radius =
            10f *
                context.resources.displayMetrics.density

        fun setProgress(
            value: Float
        ) {
            progress = value
            invalidateSelf()
        }

        override fun draw(
            canvas: Canvas
        ) {
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

        override fun setColorFilter(
            colorFilter: ColorFilter?
        ) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int =
            PixelFormat.TRANSLUCENT
    }
}
