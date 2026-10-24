package com.appao.ui

import android.app.Activity
import android.graphics.Color
import android.transition.Fade
import android.view.Window
import androidx.core.content.ContextCompat
import com.appao.R
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback

/**
 * Activity shared-element transitions for the browser and tab overview.
 * Uses Material's real Container Transform in both directions; no ChangeBounds/ChangeTransform
 * imitation is used for these navigation paths.
 *
 * IMPORTANT: prepare() MUST be called once per Activity, in its onCreate, strictly before
 * setContentView(). It must never be called again later (see configure() below) — the platform
 * forbids requestFeature(FEATURE_ACTIVITY_TRANSITIONS) once content has been added.
 */
object BrowserTransitions {
    const val ADDRESS_TRANSITION_NAME = "browser_address_transform"

    /**
     * Requests the platform transition feature. Legal only before the Activity installs its
     * content view for the first time. Call this from onCreate, before setContentView().
     */
    fun prepare(activity: Activity) {
        runCatching { activity.window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS) }
    }

    /**
     * Configures transitions, callbacks and shared-element transitions. Safe to call after
     * setContentView() has already run — it only touches Window transition properties.
     *
     * Do NOT call requestFeature()/prepare() here. In the previous revision configure() called
     * prepare() as its first line, and configure() is invoked again in some flows (e.g. right
     * before a browser navigation in MainActivity.openMainBrowserSearch) long after
     * setContentView. That produced "requestFeature() must be called before adding content"
     * on Android, corrupting the window and crashing the browser navigation.
     */
    fun configure(activity: Activity, fadeSurface: Boolean = true) {
        activity.window.allowEnterTransitionOverlap = true
        activity.window.allowReturnTransitionOverlap = true

        // Fade the non-shared content on browser Activities only. MainActivity arms the shared
        // element just before a browser navigation so other app transitions remain untouched.
        if (fadeSurface) {
            activity.window.enterTransition = Fade(Fade.IN).apply { duration = 180L }
            activity.window.exitTransition = Fade(Fade.OUT).apply { duration = 140L }
            activity.window.reenterTransition = Fade(Fade.IN).apply { duration = 180L }
            activity.window.returnTransition = Fade(Fade.OUT).apply { duration = 140L }
        }

        activity.setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        activity.setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())

        val surface = ContextCompat.getColor(activity, R.color.bg)
        // Forward navigation uses the ENTER configuration on both sides of the shared element:
        // the source's exit and the destination's enter are one forward Container Transform.
        // Returning uses the RETURN configuration on both sides, so the morph reverses instead
        // of replaying the forward easing/direction when Back closes the destination.
        activity.window.sharedElementEnterTransition = transform(activity, surface, entering = true)
        activity.window.sharedElementExitTransition = transform(activity, surface, entering = true)
        activity.window.sharedElementReturnTransition = transform(activity, surface, entering = false)
        activity.window.sharedElementReenterTransition = transform(activity, surface, entering = false)
    }

    fun clearSharedElementTransitions(activity: Activity) {
        activity.setEnterSharedElementCallback(null)
        activity.setExitSharedElementCallback(null)
        activity.window.sharedElementEnterTransition = null
        activity.window.sharedElementExitTransition = null
        activity.window.sharedElementReturnTransition = null
        activity.window.sharedElementReenterTransition = null
        activity.window.allowEnterTransitionOverlap = false
        activity.window.allowReturnTransitionOverlap = false
    }

    private fun transform(
        activity: Activity,
        surface: Int,
        entering: Boolean
    ): MaterialContainerTransform = MaterialContainerTransform(activity, entering).apply {
        duration = 380L
        interpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
        setTransitionDirection(
            if (entering) MaterialContainerTransform.TRANSITION_DIRECTION_ENTER
            else MaterialContainerTransform.TRANSITION_DIRECTION_RETURN
        )
        setFadeMode(MaterialContainerTransform.FADE_MODE_CROSS)
        setFitMode(MaterialContainerTransform.FIT_MODE_AUTO)
        setScrimColor(Color.TRANSPARENT)
        setContainerColor(surface)
        setStartContainerColor(surface)
        setEndContainerColor(surface)
        setElevationShadowEnabled(true)
    }
}