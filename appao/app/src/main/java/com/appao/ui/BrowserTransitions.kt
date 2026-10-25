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
    // Shared element used by the "Abas" square+count button on both MainActivity (bottombar) and
    // AppViewerActivity (browser top bar): the tab overview now always morphs out of this fixed,
    // always-measured button instead of a dynamically captured page snapshot.
    const val TABS_BUTTON_TRANSITION_NAME = "tabs_button_transform"

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
    fun configure(activity: Activity, fadeSurface: Boolean = true, sharedTransitionName: String? = null) {
        activity.window.allowEnterTransitionOverlap = true
        activity.window.allowReturnTransitionOverlap = true

        // Fade the non-shared content on browser Activities only. MainActivity arms the shared
        // element just before a browser navigation so other app transitions remain untouched.
        // The shared element itself MUST be excluded from this Fade by transitionName: without
        // this, both the Fade and the MaterialContainerTransform try to animate the very same
        // View at once, which crashes inside the platform's TransitionSet on several OEMs/API
        // levels the moment the shared element starts moving. The two known browser shared
        // element names are always excluded here regardless of the caller's parameter, because
        // an Activity may enter via one (e.g. address pill) and exit via the other (tabs button),
        // and both paths run through this same Fade.
        if (fadeSurface) {
            fun fade(mode: Int, dur: Long) = Fade(mode).apply {
                duration = dur
                excludeTarget(android.R.id.statusBarBackground, true)
                excludeTarget(android.R.id.navigationBarBackground, true)
                excludeTarget(ADDRESS_TRANSITION_NAME, true)
                excludeTarget(TABS_BUTTON_TRANSITION_NAME, true)
                if (!sharedTransitionName.isNullOrBlank() && sharedTransitionName != ADDRESS_TRANSITION_NAME &&
                    sharedTransitionName != TABS_BUTTON_TRANSITION_NAME
                ) {
                    excludeTarget(sharedTransitionName, true)
                }
            }
            activity.window.enterTransition = fade(Fade.IN, 180L)
            activity.window.exitTransition = fade(Fade.OUT, 140L)
            activity.window.reenterTransition = fade(Fade.IN, 180L)
            activity.window.returnTransition = fade(Fade.OUT, 140L)
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