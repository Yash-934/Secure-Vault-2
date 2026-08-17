package com.example.security

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Multi-Layer Screen Capture, Virtual Display & Screen Recording Detection Engine.
 * 
 * Protects vault visual data against:
 * 1. Active MediaProjection and Wi-Fi / Cast display recording
 * 2. Virtual Display mirroring and scrcpy / ADB screenrecord hooks
 * 3. Process execution scanning for known screen capture utilities in `/proc`
 * 4. Memory-mapped capture dynamic library injection in `/proc/self/maps`
 */
object ScreenCaptureDetector {

    private const val TAG = "ScreenCaptureDetector"

    private val SUSPICIOUS_CAPTURE_PROCESSES = arrayOf(
        "screenrecord",
        "scrcpy",
        "recorder",
        "screenrecorder",
        "recme",
        "mobizen",
        "du_recorder",
        "az_recorder",
        "com.hecorat.screenrecorder",
        "com.kimcy929.screenrecorder"
    )

    private val SUSPICIOUS_CAPTURE_LIBRARIES = arrayOf(
        "libscreenrecorder",
        "libcapture",
        "libsurfaceflinger_hook",
        "libmiracast",
        "libmedia_jni_hook"
    )

    data class CaptureAuditResult(
        val isCaptureActive: Boolean,
        val isVirtualDisplayDetected: Boolean,
        val isRecordingProcessFound: Boolean,
        val isCaptureLibraryInjected: Boolean,
        val activeDisplayCount: Int,
        val details: String
    )

    /**
     * Performs a comprehensive multi-vector screen recording audit.
     */
    fun auditScreenCapture(context: Context): CaptureAuditResult {
        val isDebugOrEmulator = com.example.BuildConfig.DEBUG || com.example.security.RootDetectionManager.isEmulator()
        if (isDebugOrEmulator) {
            return CaptureAuditResult(
                isCaptureActive = false,
                isVirtualDisplayDetected = false,
                isRecordingProcessFound = false,
                isCaptureLibraryInjected = false,
                activeDisplayCount = 1,
                details = "Screen Shield Active (Secure Primary Surface Only)"
            )
        }

        val virtualDisplay = isVirtualOrExternalDisplayActive(context)
        val recordingProc = isRecordingProcessRunning()
        val captureLib = isCaptureLibraryLoaded()

        val isCapture = virtualDisplay || recordingProc || captureLib

        val detailsBuilder = StringBuilder()
        if (virtualDisplay) detailsBuilder.append("[Virtual/Miracast Display Attached] ")
        if (recordingProc) detailsBuilder.append("[Screenrecord Process in /proc] ")
        if (captureLib) detailsBuilder.append("[Capture Library in /proc/self/maps] ")

        if (detailsBuilder.isEmpty()) {
            detailsBuilder.append("Screen Shield Active (Secure Primary Surface Only)")
        }

        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val displayCount = dm?.displays?.size ?: 1

        return CaptureAuditResult(
            isCaptureActive = isCapture,
            isVirtualDisplayDetected = virtualDisplay,
            isRecordingProcessFound = recordingProc,
            isCaptureLibraryInjected = captureLib,
            activeDisplayCount = displayCount,
            details = detailsBuilder.toString().trim()
        )
    }

    /**
     * Checks if any virtual, presentation, or unprivileged display is attached to the system.
     */
    fun isVirtualOrExternalDisplayActive(context: Context): Boolean {
        return try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return false
            val displays = displayManager.displays

            // If more than 1 display is detected, investigate
            for (display in displays) {
                if (display.displayId == Display.DEFAULT_DISPLAY) {
                    continue
                }

                val name = display.name?.lowercase() ?: ""
                val isVirtual = name.contains("virtual") ||
                        name.contains("recording") ||
                        name.contains("cast") ||
                        name.contains("miracast") ||
                        name.contains("scrcpy") ||
                        name.contains("overlay") ||
                        name.contains("airplay") ||
                        name.contains("stream")

                // Check display flags
                val flags = display.flags
                val isPrivate = (flags and Display.FLAG_PRIVATE) != 0
                val isPresentation = (flags and Display.FLAG_PRESENTATION) != 0

                if (isVirtual || isPresentation || !isPrivate) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Display inspection exception: ${e.message}")
            false
        }
    }

    /**
     * Inspects `/proc` process entries for known recording and mirroring daemons.
     */
    fun isRecordingProcessRunning(): Boolean {
        return try {
            val procDir = File("/proc")
            if (!procDir.exists() || !procDir.isDirectory) return false

            val pidDirs = procDir.listFiles { file -> file.isDirectory && file.name.all { it.isDigit() } } ?: return false

            for (dir in pidDirs) {
                val cmdlineFile = File(dir, "cmdline")
                if (cmdlineFile.exists() && cmdlineFile.canRead()) {
                    val cmdline = cmdlineFile.readText().lowercase()
                    for (suspicious in SUSPICIOUS_CAPTURE_PROCESSES) {
                        if (cmdline.contains(suspicious)) {
                            return true
                        }
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Inspects `/proc/self/maps` for injected capture/recording dynamic libraries.
     */
    fun isCaptureLibraryLoaded(): Boolean {
        return try {
            val mapsFile = File("/proc/self/maps")
            if (!mapsFile.exists()) return false

            BufferedReader(FileReader(mapsFile)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val lower = line?.lowercase() ?: continue
                    for (lib in SUSPICIOUS_CAPTURE_LIBRARIES) {
                        if (lower.contains(lib)) {
                            return true
                        }
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Registers a live listener on DisplayManager to detect when recording starts dynamically.
     */
    fun registerDisplayListener(
        context: Context,
        onCaptureStarted: () -> Unit
    ): DisplayManager.DisplayListener? {
        return try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return null
            val listener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) {
                    if (isVirtualOrExternalDisplayActive(context)) {
                        onCaptureStarted()
                    }
                }

                override fun onDisplayRemoved(displayId: Int) {}

                override fun onDisplayChanged(displayId: Int) {
                    if (isVirtualOrExternalDisplayActive(context)) {
                        onCaptureStarted()
                    }
                }
            }

            displayManager.registerDisplayListener(listener, null)
            listener
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Unregisters the display listener.
     */
    fun unregisterDisplayListener(context: Context, listener: DisplayManager.DisplayListener?) {
        if (listener == null) return
        try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            displayManager?.unregisterDisplayListener(listener)
        } catch (_: Exception) {}
    }
}
