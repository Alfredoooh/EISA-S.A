package com.appao

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

/** Placeholder collections are explicitly empty until the app has real follow/like data sources. */
class ProfileCollectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        SystemBarHelper.sync(this)
        val title = intent.getStringExtra("collection_title").orEmpty().ifBlank { "Coleção" }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ContextCompat.getColor(this@ProfileCollectionActivity, R.color.bg)) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(5), dp(14), dp(5)) }
        val back = FrameLayout(this).apply {
            background = ContextCompat.getDrawable(this@ProfileCollectionActivity, R.drawable.bg_transparent_pressed); isClickable = true; isFocusable = true
            addView(ImageView(this@ProfileCollectionActivity).apply { layoutParams = FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER); IconLoader.applyIcon(this, "arrow_left", R.color.iconTint) })
            setOnClickListener { finish(); overridePendingTransition(R.anim.publish_return_enter, R.anim.publish_return_exit) }
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(40), dp(44)))
        bar.addView(TextView(this).apply { text = title; textSize = 19f; setTextColor(ContextCompat.getColor(this@ProfileCollectionActivity, R.color.text)); setPadding(dp(8),0,0,0) }, LinearLayout.LayoutParams(-1, dp(44)))
        root.addView(bar)
        val empty = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(24), dp(30), dp(24), dp(30)) }
        empty.addView(ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(128), dp(112)).apply { gravity = Gravity.CENTER }; setImageDrawable(EmptySavedIllustrationDrawable(this@ProfileCollectionActivity)); scaleType = ImageView.ScaleType.FIT_CENTER })
        empty.addView(TextView(this).apply { text = "Ainda não há itens em $title"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(ContextCompat.getColor(this@ProfileCollectionActivity, R.color.text)); setPadding(0, dp(12), 0, dp(5)) })
        empty.addView(TextView(this).apply { text = "Esta coleção ficará disponível quando existirem dados guardados."; textSize = 13f; gravity = Gravity.CENTER; setTextColor(ContextCompat.getColor(this@ProfileCollectionActivity, R.color.dim)) })
        root.addView(empty, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
