package com.appao

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

/**
 * One persisted source of truth for light/dark/system.
 *
 * DayNight is applied at the application delegate level so all native
 * Activities resolve the same resource set after a theme change.
 */
object ThemeManager {

    private const val PREFS = "appao"
    private const val KEY = "theme"

    private fun normalize(
        value: String?
    ): String =
        when (value) {
            "dark",
            "light",
            "system" -> value

            else -> "system"
        }

    fun current(
        ctx: Context
    ): String {

        return normalize(
            ctx.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            ).getString(
                KEY,
                "system"
            )
        )
    }

    fun save(
        ctx: Context,
        value: String
    ) {

        val safe =
            normalize(value)

        ctx.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY,
                safe
            )
            .apply()
    }

    fun mode(
        ctx: Context
    ): Int =
        when (current(ctx)) {

            "dark" ->
                AppCompatDelegate.MODE_NIGHT_YES

            "light" ->
                AppCompatDelegate.MODE_NIGHT_NO

            else ->
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

    fun resolvedDark(
        ctx: Context
    ): Boolean {

        return when (current(ctx)) {

            "dark" ->
                true

            "light" ->
                false

            else ->
                (
                    ctx.resources
                        .configuration
                        .uiMode
                        and
                        android.content.res.Configuration
                            .UI_MODE_NIGHT_MASK
                    ) ==
                    android.content.res.Configuration
                        .UI_MODE_NIGHT_YES
        }
    }

    fun apply(
        ctx: Context
    ) {

        AppCompatDelegate.setDefaultNightMode(
            mode(ctx)
        )
    }

    /**
     * Applies and persists a new theme immediately.
     *
     * AppCompat will recreate only the affected Activity when necessary to
     * resolve values-night correctly; the application itself is not closed.
     */
    fun applyImmediately(
        activity: AppCompatActivity,
        value: String
    ) {

        val safe =
            normalize(value)

        if (
            current(activity) ==
            safe &&
            AppCompatDelegate
                .getDefaultNightMode() ==
            mode(activity)
        ) {
            SystemBarHelper.sync(
                activity
            )
            return
        }

        save(
            activity,
            safe
        )

        AppCompatDelegate.setDefaultNightMode(
            when (safe) {

                "dark" ->
                    AppCompatDelegate.MODE_NIGHT_YES

                "light" ->
                    AppCompatDelegate.MODE_NIGHT_NO

                else ->
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun applyAndRefresh(
        activity: AppCompatActivity,
        value: String
    ) {
        applyImmediately(
            activity,
            value
        )
    }

    fun label(
        ctx: Context
    ): String =
        when (current(ctx)) {

            "dark" ->
                "Escuro"

            "light" ->
                "Claro"

            else ->
                "Sistema"
        }

    fun applyFromLabel(
        ctx: Context,
        label: String
    ) {

        val value =
            when (label) {

                "Escuro" ->
                    "dark"

                "Claro" ->
                    "light"

                else ->
                    "system"
            }

        save(
            ctx,
            value
        )

        AppCompatDelegate.setDefaultNightMode(
            when (value) {

                "dark" ->
                    AppCompatDelegate.MODE_NIGHT_YES

                "light" ->
                    AppCompatDelegate.MODE_NIGHT_NO

                else ->
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
