package com.appao

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import kotlin.math.pow

/**
 * Accent color picker: a fixed set of ten solid colors, the same hex value
 * in both light and dark theme (unlike the old FluentColorPalette, which
 * had a separate light/dark pair per color — that approach was replaced
 * when the default primary became black/white per theme).
 *
 * "None" (index -1, the default) means the primary stays black/white as
 * driven by colors.xml / values-night/colors.xml. Picking one of the ten
 * entries below overrides that at the view level — see applyToComposerSend
 * and the other per-screen apply functions below, which activities call
 * from onResume()/refreshValues() the same way they already read
 * ThemeManager.label().
 *
 * One persisted source of truth, same SharedPreferences file ThemeManager
 * already uses, so a device backup/restore keeps both settings together.
 */
object AccentColorManager {

    private const val PREFS = "appao"
    private const val KEY = "accent_color_index"
    private const val KEY_CUSTOM = "accent_color_custom_hex"
    const val NONE = -1
    const val CUSTOM = -2

    data class AccentOption(val id: Int, val label: String, val color: Int)

    val options = listOf(
        AccentOption(0, "Coral", Color.parseColor("#FF6F61")),
        AccentOption(1, "Azul", Color.parseColor("#0F6CBD")),
        AccentOption(2, "Roxo", Color.parseColor("#8764B8")),
        AccentOption(3, "Magenta", Color.parseColor("#C239B3")),
        AccentOption(4, "Vermelho", Color.parseColor("#D13438")),
        AccentOption(5, "Laranja", Color.parseColor("#CA5010")),
        AccentOption(6, "Dourado", Color.parseColor("#986F0B")),
        AccentOption(7, "Verde", Color.parseColor("#0B6A0B")),
        AccentOption(8, "Turquesa", Color.parseColor("#00767A")),
        AccentOption(9, "Ciano", Color.parseColor("#038387"))
    )

    private fun normalize(value: Int): Int =
        if (value in options.indices) value else NONE

    fun current(ctx: Context): Int {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getString(KEY_CUSTOM, null).isNullOrBlank()) return CUSTOM
        return normalize(prefs.getInt(KEY, NONE))
    }

    fun save(ctx: Context, value: Int) {
        val safe = normalize(value)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CUSTOM)
            .putInt(KEY, safe)
            .apply()
    }

    fun saveCustom(ctx: Context, color: Int) {
        val hex = String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and color)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM, hex)
            .putInt(KEY, NONE)
            .apply()
    }

    /**
     * Resolved color for the current selection, or null when the
     * selection is NONE and callers should fall back to R.color.pri as
     * read from resources (black in light, white in dark).
     */
    fun resolvedColor(ctx: Context): Int? {
        val index = current(ctx)
        if (index == NONE) return null
        if (index == CUSTOM) {
            val hex = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CUSTOM, null) ?: return null
            return runCatching { Color.parseColor(hex) }.getOrNull()
        }
        return options.getOrNull(index)?.color
    }

    fun label(ctx: Context): String {
        val index = current(ctx)
        return when {
            index == NONE -> "Padrão"
            index == CUSTOM -> ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CUSTOM, "Cor personalizada") ?: "Cor personalizada"
            else -> options.getOrNull(index)?.label ?: "Padrão"
        }
    }

    /**
     * Resolves the color to actually paint with: the user's chosen accent
     * if one is set, otherwise whatever [fallbackColorRes] resolves to
     * (normally R.color.pri, i.e. black in light / white in dark).
     *
     * This is the one call sites in code should use instead of
     * ContextCompat.getColor(ctx, R.color.pri) directly, so a chosen
     * accent is honored everywhere that reads the primary color in
     * Kotlin. Pure-XML usages of @color/pri (drawables, layouts) cannot
     * be overridden this way — Android resources are resolved at
     * compile/inflate time, not from SharedPreferences — so those keep
     * using the black/white default unless a view is also re-tinted in
     * code at the call site, the same way updateComposerSendState()
     * re-tints the send button's icon and background in MainActivity /
     * SearchActivity / ArticleActivity / CommentsActivity.
     */
    fun resolvedOr(ctx: Context, fallbackColorRes: Int): Int =
        resolvedColor(ctx) ?: androidx.core.content.ContextCompat.getColor(ctx, fallbackColorRes)

    /** Returns the foreground color with readable contrast over the active primary. */
    fun onPrimaryColor(ctx: Context): Int {
        val selected = resolvedColor(ctx)
            ?: return androidx.core.content.ContextCompat.getColor(ctx, R.color.onpri)

        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }

        val luminance = 0.2126 * linear(Color.red(selected)) +
            0.7152 * linear(Color.green(selected)) +
            0.0722 * linear(Color.blue(selected))
        return if (luminance > 0.179) Color.BLACK else Color.WHITE
    }

    /** A fresh shape drawable using the selected primary or the theme's black/white default. */
    fun primaryShape(
        ctx: Context,
        shape: Int = GradientDrawable.RECTANGLE,
        cornerRadiusDp: Float = 999f
    ): GradientDrawable = GradientDrawable().apply {
        this.shape = shape
        cornerRadius = cornerRadiusDp * ctx.resources.displayMetrics.density
        setColor(resolvedOr(ctx, R.color.pri))
    }

    /** Applies the user's primary to a view whose color is expected to track the app accent. */
    fun applyPrimaryBackground(
        view: View,
        ctx: Context,
        cornerRadiusDp: Float = 999f
    ) {
        view.background = primaryShape(ctx, GradientDrawable.RECTANGLE, cornerRadiusDp)
    }

}
