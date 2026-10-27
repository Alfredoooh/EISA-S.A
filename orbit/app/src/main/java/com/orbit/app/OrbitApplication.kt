package com.orbit.app

import android.app.Application
import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/** Records uncaught crashes so MainActivity can display the report on the next launch. */
class OrbitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stack = StringWriter().also { writer ->
                    throwable.printStackTrace(PrintWriter(writer))
                }.toString()
                val timestamp = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss Z",
                    Locale.ROOT,
                ).format(Date())
                val report = buildString {
                    appendLine("Orbit startup/runtime crash report")
                    appendLine("Time: $timestamp")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(stack)
                }
                openFileOutput(CRASH_FILE, Context.MODE_PRIVATE).use { output ->
                    output.write(report.toByteArray(Charsets.UTF_8))
                }
            } catch (_: Throwable) {
                // Never prevent Android's default uncaught-exception handler from running.
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    companion object {
        const val CRASH_FILE = "orbit-last-crash.txt"

        fun readLastCrash(context: Context): String? = runCatching {
            context.openFileInput(CRASH_FILE).bufferedReader(Charsets.UTF_8).use {
                it.readText().take(120_000)
            }
        }.getOrNull()

        fun clearLastCrash(context: Context) {
            runCatching { context.deleteFile(CRASH_FILE) }
        }
    }
}
