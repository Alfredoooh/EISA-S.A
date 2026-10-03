package com.appao

import android.app.Application

class AppAoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.apply(this)
    }
}
