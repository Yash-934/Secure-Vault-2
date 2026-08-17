package com.example.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Ephemeral Clipboard Manager with Auto-Purge & Memory Hygiene.
 * Automatically shreds clipboard contents after a configurable timeout (default: 15 seconds)
 * to prevent background malware or clipboard snooping apps from harvesting credentials.
 */
object ClipboardProtectionHelper {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingClearRunnable: Runnable? = null

    /**
     * Copies sensitive text to clipboard and schedules automatic destruction after timeoutMs.
     */
    fun copyWithAutoClear(
        context: Context,
        label: String,
        sensitiveText: String,
        timeoutMs: Long = 15_000L,
        onAutoCleared: (() -> Unit)? = null
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return

        // Cancel previous pending clear
        pendingClearRunnable?.let { handler.removeCallbacks(it) }

        val clip = ClipData.newPlainText(label, sensitiveText)
        clipboard.setPrimaryClip(clip)

        val runnable = Runnable {
            try {
                // Clear the primary clip safely
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
                onAutoCleared?.invoke()
            } catch (_: Exception) {}
        }

        pendingClearRunnable = runnable
        handler.postDelayed(runnable, timeoutMs)
    }

    /**
     * Manually clear clipboard immediately.
     */
    fun clearImmediate(context: Context) {
        pendingClearRunnable?.let { handler.removeCallbacks(it) }
        pendingClearRunnable = null
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (_: Exception) {}
    }
}
