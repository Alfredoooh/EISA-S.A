package com.orbit.app.ui

import android.content.Context
import android.content.res.Configuration

internal enum class BrowserThemeMode { SYSTEM, LIGHT, DARK }

internal data class BrowserPalette(
    val isDark: Boolean,
    val background: Int,
    val surface: Int,
    val pill: Int,
    val softSurface: Int,
    val foreground: Int,
    val secondary: Int,
    val line: Int,
    val blue: Int,
    val blueSoft: Int,
    val pressed: Int,
    val scrim: Int,
    val safe: Int,
    val unsafe: Int,
) {
    companion object {
        fun from(context: Context, mode: BrowserThemeMode): BrowserPalette {
            val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val dark = when (mode) {
                BrowserThemeMode.SYSTEM -> systemDark
                BrowserThemeMode.LIGHT -> false
                BrowserThemeMode.DARK -> true
            }
            return if (dark) {
                BrowserPalette(
                    isDark = true,
                    background = 0xFF111318.toInt(),
                    surface = 0xFF17191F.toInt(),
                    pill = 0xFF292C33.toInt(),
                    softSurface = 0xFF1D2026.toInt(),
                    foreground = 0xFFE3E5EA.toInt(),
                    secondary = 0xFFB6BBC4.toInt(),
                    line = 0xFF353942.toInt(),
                    blue = 0xFF8AB4F8.toInt(),
                    blueSoft = 0x333F7DDC,
                    pressed = 0x335F6368,
                    scrim = 0x99000000.toInt(),
                    safe = 0xFF81C995.toInt(),
                    unsafe = 0xFFF28B82.toInt(),
                )
            } else {
                BrowserPalette(
                    isDark = false,
                    background = Color.WHITE,
                    surface = Color.WHITE,
                    pill = 0xFFF0F1F3.toInt(),
                    softSurface = 0xFFF8F9FA.toInt(),
                    foreground = 0xFF202124.toInt(),
                    secondary = 0xFF5F6368.toInt(),
                    line = 0xFFE8EAED.toInt(),
                    blue = 0xFF1A73E8.toInt(),
                    blueSoft = 0x221A73E8,
                    pressed = 0x1F5F6368,
                    scrim = 0x66000000,
                    safe = 0xFF188038.toInt(),
                    unsafe = 0xFFB3261E.toInt(),
                )
            }
        }
    }
}
