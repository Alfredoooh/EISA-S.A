package com.appao

import android.app.Activity
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Keeps Android system bars aligned with the application's own colors. */
object SystemBarHelper {

    fun syncColor(
        activity: Activity,
        color: Int
    ) {

        val dark =
            relativeLuminance(
                color
            ) < 0.50

        WindowCompat.setDecorFitsSystemWindows(
            activity.window,
            false
        )

        activity.window.statusBarColor =
            color

        activity.window.navigationBarColor =
            color

        WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView
        ).apply {

            isAppearanceLightStatusBars =
                !dark

            isAppearanceLightNavigationBars =
                !dark
        }
    }

    fun sync(
        activity: Activity,
        darkOverride: Boolean? = null
    ) {

        WindowCompat.setDecorFitsSystemWindows(
            activity.window,
            false
        )

        val dark =
            darkOverride
                ?: ThemeManager.resolvedDark(
                    activity
                )

        /*
         * Always resolve colors from the app resources.
         * This makes values/ and values-night/ the single visual source of
         * truth instead of hard-coding generic black/white system colors.
         */
        val bg =
            ContextCompat.getColor(
                activity,
                R.color.bg
            )

        activity.window.statusBarColor =
            bg

        activity.window.navigationBarColor =
            bg

        WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView
        ).apply {

            isAppearanceLightStatusBars =
                !dark

            isAppearanceLightNavigationBars =
                !dark
        }
    }

    private fun relativeLuminance(
        color: Int
    ): Double {

        fun channel(
            value: Int
        ): Double {

            val c =
                value / 255.0

            return if (
                c <= 0.03928
            ) {

                c / 12.92

            } else {

                Math.pow(
                    (c + 0.055) / 1.055,
                    2.4
                )
            }
        }

        val r =
            channel(
                Color.red(color)
            )

        val g =
            channel(
                Color.green(color)
            )

        val b =
            channel(
                Color.blue(color)
            )

        return (
            0.2126 * r +
                0.7152 * g +
                0.0722 * b
            )
    }
}
