package com.appao

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

object Curves {

    /** cubic-bezier(.32,.72,0,1) — iOS-like, usado no drawer e pill */
    val IOS: Interpolator = PathInterpolator(0.32f, 0.72f, 0f, 1f)

    /** cubic-bezier(.22,1,.36,1) — saida suave, usado em sheets e progress */
    val SMOOTH: Interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)

    /** cubic-bezier(.1,.7,.3,1) — usado no progressbar de largura */
    val PROGRESS: Interpolator = PathInterpolator(0.1f, 0.7f, 0.3f, 1f)

    /** cubic-bezier(.34,1.45,.64,1) — spring com overshoot, usado em scale de botões */
    val SPRING: Interpolator = PathInterpolator(0.34f, 1.45f, 0.64f, 1f)

    /** cubic-bezier(.2,0,0,1) — usado em crossfades entre views */
    val EMPHASIZED: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
}
