package com.appao

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import com.bumptech.glide.Glide
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter

class AppAoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Apply the persisted mode before the first Activity is constructed.
        // This prevents the light-theme preference from starting with the
        // system's dark resource set.
        ThemeManager.apply(this)

        installCrashRecorder()
    }

    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = File(filesDir, "last_crash.txt")
                FileOutputStream(file, false).use { output ->
                    PrintWriter(output.writer(Charsets.UTF_8)).use { writer ->
                        writer.println("thread=${thread.name}")
                        throwable.printStackTrace(writer)
                    }
                }
            } catch (_: Throwable) {
            }

            previous?.uncaughtException(thread, throwable)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        IconLoader.clearMemory()
        try {
            Glide.get(this).trimMemory(level)
        } catch (t: Throwable) {
            Log.w("AppAoApp", "Glide trimMemory failed", t)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        IconLoader.clearMemory()
        try {
            Glide.get(this).trimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        } catch (t: Throwable) {
            Log.w("AppAoApp", "Glide trimMemory failed", t)
        }
    }
}
