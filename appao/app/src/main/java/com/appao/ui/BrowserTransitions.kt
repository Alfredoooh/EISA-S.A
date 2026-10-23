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
 */
object BrowserTransitions {
    fun configure(activity: Activity, fadeSurface: Boolean = true) {
        runCatching { activity.window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS) }
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
