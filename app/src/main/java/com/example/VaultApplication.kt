package com.example

import android.app.Application
import android.content.Intent
import android.util.Log
import com.example.ui.screens.ErrorFallbackActivity
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class VaultApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        setupGlobalExceptionHandler()

        try {
            SQLiteDatabase.loadLibs(this)
        } catch (e: UnsatisfiedLinkError) {
            Log.e("VaultApplication", "SQLCipher native libraries load failure", e)
            logStartupErrorToFile(e)
        } catch (e: SecurityException) {
            Log.e("VaultApplication", "Security exception while loading SQLCipher", e)
            logStartupErrorToFile(e)
        } catch (e: Exception) {
            Log.e("VaultApplication", "Exception during SQLCipher initialization", e)
            logStartupErrorToFile(e)
        }
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("VaultApplication", "FATAL UNCAUGHT EXCEPTION on thread ${thread.name}", throwable)
                logStartupErrorToFile(throwable)

                val stringWriter = StringWriter()
                val printWriter = PrintWriter(stringWriter)
                throwable.printStackTrace(printWriter)
                val stackTrace = stringWriter.toString()

                val intent = Intent(this, ErrorFallbackActivity::class.java).apply {
                    putExtra(ErrorFallbackActivity.EXTRA_ERROR_MESSAGE, throwable.localizedMessage ?: throwable.javaClass.simpleName)
                    putExtra(ErrorFallbackActivity.EXTRA_STACK_TRACE, stackTrace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("VaultApplication", "Error in global exception handler", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun logStartupErrorToFile(throwable: Throwable) {
        try {
            val logFile = File(filesDir, "startup_error.log")
            logFile.writeText("CRASH TIME: ${System.currentTimeMillis()}\n${Log.getStackTraceString(throwable)}\n")
        } catch (_: Exception) {}
    }
}


