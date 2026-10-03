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

        // Apply the stored mode before the first Activity is created.
        try {
            ThemeManager.apply(this)
        } catch (t: Throwable) {
            Log.e("AppAoApp", "Theme initialization failed", t)
        }

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
        Glide.get(this).trimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        IconLoader.clearMemory()
        Glide.get(this).trimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }
}
