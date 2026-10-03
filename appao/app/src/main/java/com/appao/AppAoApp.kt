package com.appao

import android.app.Application
import android.content.ComponentCallbacks2
import com.bumptech.glide.Glide

class AppAoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.apply(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        IconLoader.clearMemory()
        Glide.get(this).trimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        IconLoader.clearMemory()
        Glide.get(this).trimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }
}
