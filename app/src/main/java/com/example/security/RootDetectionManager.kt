package com.example.security

import android.content.Context
import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Root, Custom ROM, and Emulator Detection Utility.
 * Performs deep environment inspections to detect rooted devices, hooked runtimes, and emulator environments.
 */
object RootDetectionManager {

    private val KNOWN_ROOT_PATHS = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su",
        "/system/usr/we-need-root/su-backup"
    )

    private val KNOWN_ROOT_APPS = arrayOf(
        "com.noshufou.android.su",
        "com.thirdparty.superuser",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.zacharee1.systemuituner",
        "com.topjohnwu.magisk"
    )

    /**
     * Checks if the current Android device is rooted using multiple verification vectors.
     */
    fun isDeviceRooted(context: Context? = null): Boolean {
        return checkBuildTags() || checkRootBinaryPaths() || checkSuCommandExecution() || checkRootPackages(context)
    }

    /**
     * Checks if the app is executing inside an Android emulator environment.
     */
    fun isEmulator(): Boolean {
        val fingerPrint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()

        return fingerPrint.startsWith("generic") ||
                fingerPrint.startsWith("unknown") ||
                model.contains("google_sdk") ||
                model.contains("emulator") ||
                model.contains("android sdk built for x86") ||
                model.contains("sdk_gphone") ||
                manufacturer.contains("genymotion") ||
                hardware.contains("goldish") ||
                hardware.contains("ranchu") ||
                hardware.contains("vbox86") ||
                product.contains("sdk") ||
                product.contains("google_sdk") ||
                product.contains("sdk_x86") ||
                product.contains("vbox86p") ||
                brand.startsWith("generic") && device.startsWith("generic")
    }

    /**
     * Returns true if the environment is compromised (Rooted, Emulator, or Test-Keys build).
     */
    fun isTamperedOrCompromised(context: Context? = null): Boolean {
        return isDeviceRooted(context) || isEmulator()
    }

    private fun checkBuildTags(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    private fun checkRootBinaryPaths(): Boolean {
        for (path in KNOWN_ROOT_PATHS) {
            if (File(path).exists()) {
                return true
            }
        }
        return false
    }

    private fun checkSuCommandExecution(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine() != null
        } catch (t: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }

    private fun checkRootPackages(context: Context?): Boolean {
        if (context == null) return false
        val pm = context.packageManager
        for (packageName in KNOWN_ROOT_APPS) {
            try {
                pm.getPackageInfo(packageName, 0)
                return true
            } catch (e: Exception) {
                // Package not found
            }
        }
        return false
    }
}
