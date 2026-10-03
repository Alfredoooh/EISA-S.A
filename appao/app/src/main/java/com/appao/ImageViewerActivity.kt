package com.appao

import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.ChangeClipBounds
import android.transition.ChangeImageTransform
import android.transition.ChangeTransform
import android.transition.TransitionSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import kotlin.math.abs

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var image: ImageView

    private var downY = 0f
    private var downX = 0f
    private var dragging = false
    private var moved = false
    private var velocityTracker: VelocityTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.sharedElementEnterTransition = imageTransition()
        window.sharedElementReturnTransition = imageTransition()
        setContentView(R.layout.activity_image_viewer)
        SystemBarHelper.sync(
            this,
            darkOverride = true
        )

        image = findViewById(R.id.fullImage)

        val url = intent.getStringExtra("image")
            ?: run {
                finish()
                return
            }

        ViewCompat.setTransitionName(
            image,
            intent.getStringExtra("transition") ?: "hero"
        )

        Glide.with(this)
            .load(url)
            .dontAnimate()
            .into(image)

        image.setOnTouchListener { _, event ->
            handleTouch(event)
        }
    }

    private fun imageTransition(): TransitionSet =
        TransitionSet().apply {
            addTransition(ChangeBounds())
            addTransition(ChangeTransform())
            addTransition(ChangeClipBounds())
            addTransition(ChangeImageTransform())
            duration = 450L
            interpolator = Curves.SMOOTH
        }

    private fun handleTouch(
        event: MotionEvent
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = true
                moved = false

                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)

                image.animate().cancel()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)

                val dx = event.rawX - downX
                val dy = event.rawY - downY

                if (
                    abs(dx) > 5f ||
                    abs(dy) > 5f
                ) {
                    moved = true
                }

                if (!dragging) return true

                val distance = abs(dy)
                val maxDistance =
                    resources.displayMetrics.heightPixels.toFloat()

                val progress = (
                    distance / maxDistance
                ).coerceIn(0f, 1f)

                val scale =
                    1f - progress * 0.25f

                val alpha =
                    1f - progress * 0.65f

                image.translationX = dx * 0.18f
                image.translationY = dy
                image.scaleX = scale.coerceAtLeast(0.72f)
                image.scaleY = scale.coerceAtLeast(0.72f)
                image.alpha = alpha.coerceAtLeast(0.25f)

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)

                val dy = event.rawY - downY
                val velocityY =
                    velocityTracker?.yVelocity ?: 0f

                val dismiss =
                    abs(dy) > resources.displayMetrics.heightPixels * 0.20f ||
                        abs(velocityY) > 1200f

                dragging = false

                velocityTracker?.recycle()
                velocityTracker = null

                if (dismiss) {
                    closeWithTransition()
                } else {
                    restorePosition()
                }

                return true
            }
        }

        return true
    }

    private fun restorePosition() {
        image.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(360L)
            .setInterpolator(Curves.SPRING)
            .start()
    }

    private fun closeWithTransition() {
        image.animate()
            .alpha(0f)
            .setDuration(120L)
            .withEndAction {
                finishAfterTransition()
            }
            .start()
    }

    override fun onBackPressed() {
        finishAfterTransition()
    }

    override fun onDestroy() {
        velocityTracker?.recycle()
        velocityTracker = null
        super.onDestroy()
    }
}
