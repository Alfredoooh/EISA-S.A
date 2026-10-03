package com.appao

import android.app.Activity
import android.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

object SystemBarHelper {
    fun syncColor(activity: Activity, color: Int) {
        val dark = relativeLuminance(color) < 0.5
        activity.window.statusBarColor = color
        activity.window.navigationBarColor = color
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val r = channel(android.graphics.Color.red(color))
        val g = channel(android.graphics.Color.green(color))
        val b = channel(android.graphics.Color.blue(color))
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun sync(activity: Activity, darkOverride: Boolean? = null) {
        val dark = darkOverride ?: when (ThemeManager.current(activity)) {
            "dark" -> true
            "light" -> false
            else -> (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val bg = if (dark) Color.rgb(13, 15, 18) else Color.WHITE
        activity.window.statusBarColor = bg
        activity.window.navigationBarColor = bg
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
