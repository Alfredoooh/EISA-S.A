package com.appao.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.appao.AccentColorManager
import com.appao.R
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Shared Material 3 switch styling. Uses the native MaterialSwitch widget (the same one
 * WhatsApp and most M3 apps use) — not a custom-drawn component — with:
 *  - a check icon on the thumb when ON (native thumbIconDrawable),
 *  - a track tinted from the app's current primary/accent color instead of Material's default,
 *  - a darker/tonally correct track that reads as "Material 3" in both light and dark.
 *
 * Call this once per MaterialSwitch, ideally right after construction and before setting
 * the initial `isChecked` value, so the color state list resolves correctly on first paint.
 */
object M3SwitchStyle {
    fun apply(context: Context, switch: MaterialSwitch) {
        val checkedState = intArrayOf(android.R.attr.state_checked)
        val defaultState = intArrayOf()
        val accent = AccentColorManager.resolvedOr(context, R.color.pri)
        val onAccent = AccentColorManager.onPrimaryColor(context)
        val offThumb = ContextCompat.getColor(context, R.color.dim)
        val offTrack = ContextCompat.getColor(context, R.color.card2)

        // ON: thumb takes the on-primary color (contrast with the tinted track);
        // OFF: thumb uses the dimmed foreground so it reads as an empty, inactive control.
        switch.thumbTintList = ColorStateList(
            arrayOf(checkedState, defaultState),
            intArrayOf(onAccent, offThumb)
        )
        // ON: track uses the full primary/accent color (the "darker, more Material" tint);
        // OFF: track uses the surface container tone so the switch sits on any surface.
        switch.trackTintList = ColorStateList(
            arrayOf(checkedState, defaultState),
            intArrayOf(accent, offTrack)
        )
        // Native thumb icon (check) that appears only when ON. OFF has no icon (transparent
        // tint), matching M3 Expressive's default "tick when selected" behaviour.
        switch.setThumbIconResource(R.drawable.ic_switch_check)
        switch.thumbIconTintList = ColorStateList(
            arrayOf(checkedState, defaultState),
            intArrayOf(accent, Color.TRANSPARENT)
        )
    }
}