package com.appao

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.caverock.androidsvg.SVG

/**
 * Loads native packaged PNGs first, then optional HTML assets, then the
 * lightweight native fallback. This keeps drawer and settings icons visible
 * even when the web asset bundle is intentionally small.
 */
object IconLoader {

    private val cache =
        LruCache<String, Bitmap>(32)

    fun clearMemory() {
        cache.evictAll()
    }

    fun svgBitmap(
        context: Context,
        name: String,
        sizePx: Int
    ): Bitmap? {

        val key =
            "svg:$name:$sizePx"

        cache.get(key)?.let {
            return it
        }

        return try {

            openSvg(
                context,
                name
            ).use { stream ->

                val svg =
                    SVG.getFromInputStream(
                        stream
                    )

                svg.setDocumentWidth(
                    sizePx.toFloat()
                )

                svg.setDocumentHeight(
                    sizePx.toFloat()
                )

                val bmp =
                    Bitmap.createBitmap(
                        sizePx,
                        sizePx,
                        Bitmap.Config.ARGB_8888
                    )

                Canvas(bmp).drawPicture(
                    svg.renderToPicture()
                )

                cache.put(
                    key,
                    bmp
                )

                bmp
            }

        } catch (_: Throwable) {
            null
        }
    }

    private fun openSvg(
        context: Context,
        name: String
    ): java.io.InputStream {

        return try {

            context.assets.open(
                "icons/svg/$name.svg"
            )

        } catch (_: Throwable) {

            context.assets.open(
                "icons/svg/${
                    name.replace(
                        "-",
                        "_"
                    )
                }.svg"
            )
        }
    }

    private fun assetExists(
        context: Context,
        path: String
    ): Boolean {

        return try {

            context.assets
                .open(path)
                .use { }

            true

        } catch (_: Throwable) {
            false
        }
    }

    private fun resourceId(
        context: Context,
        name: String
    ): Int {

        return context.resources
            .getIdentifier(
                name
                    .substringAfterLast("/")
                    .substringBeforeLast("."),
                "drawable",
                context.packageName
            )
    }

    private fun tintColor(
        view: ImageView,
        tintRes: Int
    ): Int {

        return if (
            tintRes != 0
        ) {

            ContextCompat.getColor(
                view.context,
                tintRes
            )

        } else {

            ContextCompat.getColor(
                view.context,
                R.color.iconTint
            )
        }
    }

    private fun applyResourcePng(
        view: ImageView,
        name: String,
        tintRes: Int
    ): Boolean {

        val id =
            resourceId(
                view.context,
                name
            )

        if (
            id == 0
        ) {
            return false
        }

        try {

            view.setImageResource(
                id
            )

            if (
                tintRes != 0
            ) {

                view.setColorFilter(
                    ContextCompat.getColor(
                        view.context,
                        tintRes
                    ),
                    PorterDuff.Mode.SRC_IN
                )

            } else {

                view.clearColorFilter()
            }

            return true

        } catch (_: Throwable) {

            return false
        }
    }

    fun applySvg(
        view: ImageView,
        name: String,
        tintRes: Int = 0
    ) {

        /*
         * A packaged PNG is the preferred native representation when
         * available. This is also how the original drawer PNGs are restored.
         */
        if (
            applyResourcePng(
                view,
                name,
                tintRes
            )
        ) {
            return
        }

        val size =
            resolveSize(view)

        val bmp =
            svgBitmap(
                view.context,
                name,
                size
            )

        if (
            bmp != null
        ) {

            view.setImageBitmap(
                bmp
            )

            if (
                tintRes != 0
            ) {

                view.setColorFilter(
                    ContextCompat.getColor(
                        view.context,
                        tintRes
                    ),
                    PorterDuff.Mode.SRC_IN
                )

            } else {

                view.clearColorFilter()
            }

        } else {

            view.clearColorFilter()

            view.setImageDrawable(
                FallbackIconDrawable(
                    name,
                    tintColor(
                        view,
                        tintRes
                    )
                )
            )
        }
    }

    fun applyPng(
        view: ImageView,
        name: String,
        tintRes: Int = 0
    ) {

        if (
            applyResourcePng(
                view,
                name,
                tintRes
            )
        ) {
            return
        }

        view.clearColorFilter()

        val path =
            "icons/png/$name.png"

        if (
            !assetExists(
                view.context,
                path
            )
        ) {

            view.setImageDrawable(
                FallbackIconDrawable(
                    name,
                    tintColor(
                        view,
                        tintRes
                    )
                )
            )

            return
        }

        try {

            Glide.with(view)
                .load(
                    "file:///android_asset/$path"
                )
                .dontAnimate()
                .into(view)

            if (
                tintRes != 0
            ) {

                view.setColorFilter(
                    ContextCompat.getColor(
                        view.context,
                        tintRes
                    ),
                    PorterDuff.Mode.SRC_IN
                )
            }

        } catch (_: Throwable) {

            view.setImageDrawable(
                FallbackIconDrawable(
                    name,
                    tintColor(
                        view,
                        tintRes
                    )
                )
            )
        }
    }

    fun applyPngAt(
        view: ImageView,
        path: String,
        tintRes: Int = 0
    ) {

        val normalized =
            path.removePrefix("/")

        /*
         * Try a resource based on the final filename first.
         */
        val name =
            normalized
                .substringAfterLast("/")
                .substringBeforeLast(".")

        if (
            applyResourcePng(
                view,
                name,
                tintRes
            )
        ) {
            return
        }

        if (
            !assetExists(
                view.context,
                normalized
            )
        ) {

            view.setImageDrawable(
                FallbackIconDrawable(
                    name,
                    tintColor(
                        view,
                        tintRes
                    )
                )
            )

            return
        }

        try {

            Glide.with(view)
                .load(
                    "file:///android_asset/$normalized"
                )
                .dontAnimate()
                .into(view)

            if (
                tintRes != 0
            ) {

                view.setColorFilter(
                    ContextCompat.getColor(
                        view.context,
                        tintRes
                    ),
                    PorterDuff.Mode.SRC_IN
                )
            }

        } catch (_: Throwable) {

            view.setImageDrawable(
                FallbackIconDrawable(
                    name,
                    tintColor(
                        view,
                        tintRes
                    )
                )
            )
        }
    }

    fun drawable(
        context: Context,
        name: String,
        sizePx: Int,
        tintColor: Int = 0
    ): Drawable? {

        val resource =
            resourceId(
                context,
                name
            )

        if (
            resource != 0
        ) {

            return ContextCompat
                .getDrawable(
                    context,
                    resource
                )
                ?.mutate()
                ?.apply {

                    setBounds(
                        0,
                        0,
                        sizePx,
                        sizePx
                    )

                    if (
                        tintColor != 0
                    ) {
                        setTint(
                            tintColor
                        )
                    }
                }
        }

        val bmp =
            svgBitmap(
                context,
                name,
                sizePx
            )

        if (
            bmp != null
        ) {

            return BitmapDrawable(
                context.resources,
                bmp
            ).apply {

                setBounds(
                    0,
                    0,
                    sizePx,
                    sizePx
                )

                if (
                    tintColor != 0
                ) {
                    setTint(
                        tintColor
                    )
                }
            }
        }

        return FallbackIconDrawable(
            name,
            tintColor.takeIf {
                it != 0
            } ?: ContextCompat.getColor(
                context,
                R.color.iconTint
            )
        ).apply {

            setBounds(
                0,
                0,
                sizePx,
                sizePx
            )
        }
    }

    fun loadPngDrawable(
        context: Context,
        name: String,
        sizePx: Int
    ): Drawable? {

        val resource =
            resourceId(
                context,
                name
            )

        if (
            resource != 0
        ) {

            return ContextCompat
                .getDrawable(
                    context,
                    resource
                )
                ?.mutate()
                ?.apply {

                    setBounds(
                        0,
                        0,
                        sizePx,
                        sizePx
                    )
                }
        }

        val path =
            "icons/png/$name.png"

        if (
            !assetExists(
                context,
                path
            )
        ) {

            return FallbackIconDrawable(
                name,
                ContextCompat.getColor(
                    context,
                    R.color.iconTint
                )
            ).apply {

                setBounds(
                    0,
                    0,
                    sizePx,
                    sizePx
                )
            }
        }

        return try {

            context.assets
                .open(path)
                .use { stream ->

                    val bmp =
                        android.graphics.BitmapFactory
                            .decodeStream(stream)
                            ?: return@use null

                    BitmapDrawable(
                        context.resources,
                        bmp
                    ).apply {

                        setBounds(
                            0,
                            0,
                            sizePx,
                            sizePx
                        )
                    }
                }

        } catch (_: Throwable) {

            FallbackIconDrawable(
                name,
                ContextCompat.getColor(
                    context,
                    R.color.iconTint
                )
            ).apply {

                setBounds(
                    0,
                    0,
                    sizePx,
                    sizePx
                )
            }
        }
    }

    private fun resolveSize(
        view: ImageView
    ): Int {

        val lp =
            view.layoutParams

        val width =
            if (
                lp != null &&
                lp.width > 0
            ) {
                lp.width
            } else {
                view.width
            }

        return if (
            width > 0
        ) {
            width
        } else {
            96
        }
    }
}
