package com.appao.ui

import com.appao.R
import android.app.Activity
import android.graphics.Color
import android.view.Window
import android.transition.Fade
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import androidx.core.content.ContextCompat

/** Shared-element transitions used when moving between a live page and the tab overview. */
object BrowserTransitions {
    fun configure(activity: Activity) {
        runCatching { activity.window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS) }
        activity.window.allowEnterTransitionOverlap = true
        activity.window.allowReturnTransitionOverlap = true
        // Fade the surrounding Activity surface while the selected page/card uses container transform.
        activity.window.enterTransition = Fade(Fade.IN).apply { duration = 180L }
        activity.window.exitTransition = Fade(Fade.OUT).apply { duration = 140L }
        activity.window.reenterTransition = Fade(Fade.IN).apply { duration = 180L }
        activity.window.returnTransition = Fade(Fade.OUT).apply { duration = 140L }
        activity.setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        activity.setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        val surface = ContextCompat.getColor(activity, R.color.bg)
        activity.window.sharedElementEnterTransition = transform(surface)
        activity.window.sharedElementExitTransition = transform(surface)
        activity.window.sharedElementReturnTransition = transform(surface)
        activity.window.sharedElementReenterTransition = transform(surface)
    }

    private fun transform(surface: Int): MaterialContainerTransform = MaterialContainerTransform().apply {
        duration = 240L
        setFadeMode(MaterialContainerTransform.FADE_MODE_CROSS)
        setFitMode(MaterialContainerTransform.FIT_MODE_AUTO)
        setScrimColor(Color.TRANSPARENT)
        setContainerColor(surface)
        setStartContainerColor(surface)
        setEndContainerColor(surface)
        setElevationShadowEnabled(true)
    }
}