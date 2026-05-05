package com.piashmsu.tvapk.util

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Opt-in local crash reporter. When enabled the previous default uncaught
 * exception handler is wrapped — fatal exceptions are appended to a log
 * file under `<filesDir>/crash-logs/` before re-throwing to the original
 * handler so the OS still terminates the process normally.
 */
object CrashReporter {

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(app, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
        installed = true
    }

    fun crashDir(context: Context): File =
        File(context.filesDir, "crash-logs").apply { if (!exists()) mkdirs() }

    fun listCrashes(context: Context): List<File> =
        crashDir(context).listFiles()?.sortedDescending().orEmpty()

    fun clearCrashes(context: Context) {
        crashDir(context).listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val dir = crashDir(context)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(dir, "crash-$stamp.txt")
        val sw = StringWriter()
        PrintWriter(sw).use { throwable.printStackTrace(it) }
        out.writeText(
            buildString {
                append("Time: ").append(Date().toString()).append('\n')
                append("Thread: ").append(thread.name).append('\n')
                append("Throwable: ").append(throwable::class.qualifiedName).append('\n')
                append("Message: ").append(throwable.message).append('\n')
                append("---\n")
                append(sw.toString())
            }
        )
    }
}
