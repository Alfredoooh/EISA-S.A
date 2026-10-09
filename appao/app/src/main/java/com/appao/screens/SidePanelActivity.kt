package com.appao

import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.app.AppCompatActivity

class SidePanelActivity : AppCompatActivity() {

    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_side_panel)
        SystemBarHelper.sync(this)

        val topBar = findViewById<android.view.View>(R.id.sideTopBar)
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
            view.setPadding(view.paddingLeft, dp(2), view.paddingRight, view.paddingBottom)
            view.layoutParams = view.layoutParams.apply { height = dp(56) }
            insets
        }
        ViewCompat.requestApplyInsets(topBar)

        IconLoader.applySvg(
            findViewById(R.id.sideCloseIcon),
            "nav_forward",
            R.color.iconTint
        )

        findViewById<android.view.View>(R.id.sideClose).setOnClickListener {
            closePanel()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                tracking = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking) {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dy) > kotlin.math.abs(dx) && kotlin.math.abs(dy) > touchSlop) {
                        tracking = false
                    } else if (dx < -touchSlop * 2f) {
                        tracking = false
                        closePanel()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onBackPressed() {
        closePanel()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun closePanel() {
        if (isFinishing) return
        finish()
        overridePendingTransition(
            R.anim.hold,
            R.anim.slide_out_left
        )
    }
}
