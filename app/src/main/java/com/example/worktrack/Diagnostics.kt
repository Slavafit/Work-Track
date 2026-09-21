package com.example.worktrack

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

object Diagnostics {
    private const val DIRECTORY = "diagnostics"
    private const val LOG_FILE = "events.log"
    private const val EXPORT_FILE = "worktrack-diagnostics.txt"
    private const val MAX_EVENTS = 50
    private const val MAX_BYTES = 128 * 1024
    private const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_FRAMES_PER_THROWABLE = 24
    private const val MAX_CAUSES = 4

    private val lock = Any()
    @Volatile private var diagnosticsDirectory: File? = null
    @Volatile private var installed = false

    fun install(context: Context) {
        diagnosticsDirectory = File(context.applicationContext.filesDir, DIRECTORY)
        synchronized(lock) { prune(logFile(requireNotNull(diagnosticsDirectory))) }
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { record("uncaught", throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun record(category: String, throwable: Throwable) {
        val directory = diagnosticsDirectory ?: return
        val event = safeEvent(category, throwable, System.currentTimeMillis())
        synchronized(lock) {
            val file = logFile(directory)
            file.parentFile?.mkdirs()
            file.appendText(event + "\n", Charsets.UTF_8)
            prune(file)
        }
    }

    fun share(context: Context) {
        val file = synchronized(lock) {
            val log = logFile(File(context.filesDir, DIRECTORY))
            prune(log)
            File(log.parentFile, EXPORT_FILE).apply {
                writeText(exportText(log.readTextOrEmpty()), Charsets.UTF_8)
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "diagnostics", uri)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.diagnostics_send)))
    }

    internal fun safeEvent(category: String, throwable: Throwable, timestamp: Long): String = buildString {
        append("BEGIN ").append(timestamp).append(' ').append(safeCategory(category)).appendLine()
        var current: Throwable? = throwable
        var causeIndex = 0
        while (current != null && causeIndex < MAX_CAUSES) {
            append(if (causeIndex == 0) "TYPE " else "CAUSE ")
            append(current::class.java.name).appendLine()
            current.stackTrace.take(MAX_FRAMES_PER_THROWABLE).forEach { frame ->
                append("AT ").append(frame.className).append('.').append(frame.methodName)
                append('(').append(frame.fileName ?: "Unknown").append(':').append(frame.lineNumber).appendLine(")")
            }
            current = current.cause
            causeIndex++
        }
        append("END")
    }

    internal fun retainedEvents(text: String, now: Long): String {
        val cutoff = now - RETENTION_MILLIS
        val events = text.split("\nBEGIN ").mapIndexed { index, block ->
            if (index == 0) block else "BEGIN $block"
        }.filter { it.startsWith("BEGIN ") && it.trimEnd().endsWith("END") }
            .filter { eventTimestamp(it) >= cutoff }
            .takeLast(MAX_EVENTS)
            .toMutableList()
        while (events.joinToString("\n").toByteArray(Charsets.UTF_8).size > MAX_BYTES && events.isNotEmpty()) {
            events.removeAt(0)
        }
        return events.joinToString("\n").trim()
    }

    private fun exportText(events: String): String = buildString {
        appendLine("WorkTrack diagnostics")
        appendLine("Generated: ${utcDate(System.currentTimeMillis())}")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Log policy: exception types and stack frames only; no exception messages or application data")
        appendLine()
        if (events.isBlank()) appendLine("No recorded errors.") else appendLine(events)
    }

    private fun prune(file: File) {
        if (!file.exists()) return
        val retained = retainedEvents(file.readTextOrEmpty(), System.currentTimeMillis())
        if (retained.isBlank()) file.delete() else file.writeText(retained + "\n", Charsets.UTF_8)
    }

    private fun logFile(directory: File): File = File(directory, LOG_FILE)
    private fun File.readTextOrEmpty(): String = runCatching { if (exists()) readText(Charsets.UTF_8) else "" }.getOrDefault("")
    private fun eventTimestamp(event: String): Long = event.lineSequence().firstOrNull()
        ?.substringAfter("BEGIN ")?.substringBefore(' ')?.toLongOrNull() ?: 0L
    private fun safeCategory(value: String): String = value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_.-]"), "_").take(40)
    private fun utcDate(value: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(Date(value))
}
