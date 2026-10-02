package com.appao

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide

class ImageViewerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_image_viewer)

        val img = findViewById<ImageView>(R.id.fullImage)
        val url = intent.getStringExtra("image") ?: run { finish(); return }
        img.transitionName = intent.getStringExtra("transition") ?: "hero"
        Glide.with(this).load(url).into(img)

        img.setOnClickListener { finishAfterTransition() }
    }
}
