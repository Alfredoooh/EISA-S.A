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
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import kotlin.math.abs

/** Full-screen image viewer whose image container follows the user's gesture. */
class ImageViewerActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var backdrop: View
    private lateinit var container: FrameLayout
    private lateinit var image: ImageView

    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var moved = false
    private var velocityTracker: VelocityTracker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.sharedElementEnterTransition = imageTransition()
        window.sharedElementReturnTransition = imageTransition()
        window.exitTransition = null
        window.reenterTransition = null

        setContentView(R.layout.activity_image_viewer)
        SystemBarHelper.sync(
            this,
            darkOverride = true
        )

        root = findViewById(R.id.imageViewerRoot)
        backdrop = findViewById(R.id.imageBackdrop)
        container = findViewById(R.id.fullImageContainer)
        image = findViewById(R.id.fullImage)

        val url = intent.getStringExtra("image")
            ?.takeIf { it.isNotBlank() }
            ?: run {
                finish()
                return
            }

        val transition =
            intent.getStringExtra("transition")
                ?.takeIf { it.isNotBlank() }
                ?: "hero"

        ViewCompat.setTransitionName(
            container,
            transition
        )

        Glide.with(this)
            .load(url)
            .dontAnimate()
            .into(image)

        backdrop.alpha = 1f

        container.setOnTouchListener { _, event ->
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
                tracking = true
                moved = false

                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)

                container.animate().cancel()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return true

                velocityTracker?.addMovement(event)

                val dx = event.rawX - downX
                val dy = event.rawY - downY

                if (abs(dx) > 6f || abs(dy) > 6f) {
                    moved = true
                }

                val height =
                    resources.displayMetrics.heightPixels
                        .toFloat()
                        .coerceAtLeast(1f)

                val progress =
                    (abs(dy) / (height * .82f))
                        .coerceIn(0f, 1f)

                val scale =
                    1f - progress * .22f

                container.translationX =
                    dx * .14f

                container.translationY =
                    dy

                container.scaleX =
                    scale.coerceAtLeast(.78f)

                container.scaleY =
                    scale.coerceAtLeast(.78f)

                backdrop.alpha =
                    (1f - progress * .70f)
                        .coerceAtLeast(.20f)

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)

                val dy = event.rawY - downY
                val velocityY =
                    velocityTracker?.yVelocity ?: 0f

                tracking = false

                velocityTracker?.recycle()
                velocityTracker = null

                val height =
                    resources.displayMetrics.heightPixels
                        .toFloat()

                val dismiss =
                    abs(dy) > height * .20f ||
                        abs(velocityY) > 1200f

                if (dismiss) {
                    closeWithTransition(dy)
                } else {
                    restorePosition()
                }

                return true
            }
        }

        return true
    }

    private fun restorePosition() {
        container.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(360L)
            .setInterpolator(Curves.SPRING)
            .start()

        backdrop.animate()
            .alpha(1f)
            .setDuration(280L)
            .setInterpolator(Curves.IOS)
            .start()
    }

    private fun closeWithTransition(
        direction: Float
    ) {
        // Keep the image exactly where the finger left it. The shared-element
        // return transition then interpolates only this image container back
        // to the source hero/card; the article bars and other UI never enter
        // the transform.
        container.animate().cancel()
        backdrop.animate().cancel()

        backdrop.animate()
            .alpha(0f)
            .setDuration(180L)
            .setInterpolator(Curves.IOS)
            .withEndAction {
                if (!isFinishing && !isDestroyed) {
                    finishAfterTransition()
                }
            }
            .start()
    }

    override fun onBackPressed() {
        finishAfterTransition()
    }

    override fun onDestroy() {
        velocityTracker?.recycle()
        velocityTracker = null
        if (::container.isInitialized) {
            container.animate().cancel()
        }
        super.onDestroy()
    }
}
