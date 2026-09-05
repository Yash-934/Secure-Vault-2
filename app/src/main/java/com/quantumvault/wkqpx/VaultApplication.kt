package com.quantumvault.wkqpx

import android.app.Application
import android.content.Intent
import android.util.Log
import com.quantumvault.wkqpx.security.VaultBackupManager
import com.quantumvault.wkqpx.security.VaultGenerationManager
import com.quantumvault.wkqpx.ui.screens.ErrorFallbackActivity
import com.quantumvault.wkqpx.util.VaultLogger
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class VaultApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        setupGlobalExceptionHandler()
        VaultLogger.log(this, "VaultApplication", "Quantum Vault Application starting up")

        try {
            VaultBackupManager.recoverPendingRestoreIfAny(this)
            VaultGenerationManager.recoverPendingIntentIfAny(this, isDecoy = false)
            VaultGenerationManager.recoverPendingIntentIfAny(this, isDecoy = true)
        } catch (e: Exception) {
            Log.e("VaultApplication", "Failed during crash recovery checks on startup", e)
        }

        try {
            SQLiteDatabase.loadLibs(this)
            VaultLogger.log(this, "VaultApplication", "SQLCipher native libraries initialized successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("VaultApplication", "SQLCipher native libraries load failure", e)
            VaultLogger.logError(this, "VaultApplication", "SQLCipher native libraries load failure", e)
            logStartupErrorToFile(e)
        } catch (e: SecurityException) {
            Log.e("VaultApplication", "Security exception while loading SQLCipher", e)
            VaultLogger.logError(this, "VaultApplication", "Security exception while loading SQLCipher", e)
            logStartupErrorToFile(e)
        } catch (e: Exception) {
            Log.e("VaultApplication", "Exception during SQLCipher initialization", e)
            VaultLogger.logError(this, "VaultApplication", "Exception during SQLCipher initialization", e)
            logStartupErrorToFile(e)
        }
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("VaultApplication", "FATAL UNCAUGHT EXCEPTION on thread ${thread.name}", throwable)
                VaultLogger.logError(this, "VaultApplication", "FATAL UNCAUGHT EXCEPTION on thread ${thread.name}", throwable)
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


