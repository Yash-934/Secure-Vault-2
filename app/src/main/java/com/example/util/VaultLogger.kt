package com.example.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent file-based logger for security and data operations.
 * Records operational logs into internal storage so that state persistence,
 * database lifecycle, and crypto operations can be audited across app restarts.
 */
object VaultLogger {
    private const val TAG = "VaultLogger"
    private const val LOG_FILE_NAME = "vault_events.log"
    private const val MAX_LOG_SIZE_BYTES = 512 * 1024 // 512 KB rotate limit
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(context: Context, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedEntry = "[$timestamp] [$tag] $message"
        Log.i(tag, message)
        appendToFile(context, formattedEntry)
    }

    @Synchronized
    fun logError(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val stackTrace = throwable?.let { "\n" + Log.getStackTraceString(it) } ?: ""
        val formattedEntry = "[$timestamp] [ERROR] [$tag] $message$stackTrace"
        Log.e(tag, message, throwable)
        appendToFile(context, formattedEntry)
    }

    private fun appendToFile(context: Context, text: String) {
        try {
            val logFile = getLogFile(context)
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
                // Rotate old log
                val backupFile = File(context.filesDir, "vault_events_old.log")
                if (backupFile.exists()) backupFile.delete()
                logFile.renameTo(backupFile)
            }

            FileWriter(logFile, true).use { fw ->
                PrintWriter(fw).use { pw ->
                    pw.println(text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to persistent log file", e)
        }
    }

    fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    fun readLogs(context: Context): String {
        return try {
            val logFile = getLogFile(context)
            if (logFile.exists()) {
                logFile.readText()
            } else {
                "No persistent logs recorded yet."
            }
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    fun clearLogs(context: Context) {
        try {
            val logFile = getLogFile(context)
            if (logFile.exists()) {
                logFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs", e)
        }
    }
}
