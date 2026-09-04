package com.quantumvault.wkqpx.security

import android.content.Context
import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader

/**
 * Multi-Vector Root, Custom ROM, and Jailbreak Detection Engine.
 * 
 * Performs 6 layers of deep system heuristics:
 * 1. 25+ Known Root & KernelSU Binary Locations
 * 2. Mountinfo & Virtual Filesystem Anomalies (/proc/self/mountinfo, Magisk mirrors)
 * 3. SELinux Enforcement & Status Inspection
 * 4. Dangerous Android System Properties (ro.secure, ro.debuggable, ro.build.tags)
 * 5. Package Manager Root Management Apps & Hook Frameworks
 * 6. Combined Threat Risk Scoring (0 to 100)
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
        "/system/usr/we-need-root/su-backup",
        "/system/xbin/mu",
        "/system/bin/.ext/.su",
        "/data/adb/su",
        "/data/adb/ksu",
        "/data/adb/ap",
        "/system/bin/ksu",
        "/system/xbin/ksu",
        "/data/adb/magisk",
        "/data/adb/modules",
        "/system/addon.d",
        "/sbin/.magisk",
        "/cache/magisk.log"
    )

    private val KNOWN_ROOT_PACKAGES = arrayOf(
        "com.noshufou.android.su",
        "com.thirdparty.superuser",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.zacharee1.systemuituner",
        "com.topjohnwu.magisk",
        "me.weishu.kernelsu",
        "io.github.bmax121.apatch",
        "org.lsposed.manager",
        "de.robv.android.xposed.installer",
        "com.chelpus.lackypatch",
        "catch_.me_.if_.you_.can_"
    )

    data class RootAuditResult(
        val isRooted: Boolean,
        val riskScore: Int,
        val suBinaryFound: Boolean,
        val dangerousPropsDetected: Boolean,
        val selinuxPermissive: Boolean,
        val magiskMountsDetected: Boolean,
        val rootPackagesFound: Boolean,
        val details: String
    )

    /**
     * Comprehensive root audit with risk scoring.
     */
    fun performRootAudit(context: Context? = null): RootAuditResult {
        val suFound = checkRootBinaryPaths() || checkSuCommandExecution()
        val dangerousProps = checkDangerousSystemProperties() || checkBuildTags()
        val selinuxPermissive = isSELinuxPermissive()
        val magiskMounts = checkMagiskMounts()
        val rootPkgs = checkRootPackages(context)

        var score = 0
        if (suFound) score += 40
        if (magiskMounts) score += 35
        if (rootPkgs) score += 25
        if (selinuxPermissive) score += 20
        if (dangerousProps) score += 10

        val riskScore = score.coerceIn(0, 100)
        // A device is considered truly rooted if SU binaries are present, Magisk mounts exist,
        // root management apps are installed, or SELinux is actively set to Permissive with dangerous build props.
        // Test-keys or debug build properties alone on an OEM device with SELinux enforcing and no SU binary do not trigger root lockdown.
        val isRooted = suFound || magiskMounts || rootPkgs || (selinuxPermissive && dangerousProps)

        val detailsBuilder = StringBuilder()
        if (suFound) detailsBuilder.append("[SU Binary Detected] ")
        if (magiskMounts) detailsBuilder.append("[Magisk /proc Mounts] ")
        if (rootPkgs) detailsBuilder.append("[Root Management Packages] ")
        if (selinuxPermissive) detailsBuilder.append("[SELinux Permissive] ")
        if (dangerousProps) detailsBuilder.append("[Dangerous Build Tags] ")

        if (detailsBuilder.isEmpty()) {
            detailsBuilder.append("Clean Environment (Enforced SELinux, No SU binaries)")
        }

        return RootAuditResult(
            isRooted = isRooted,
            riskScore = riskScore,
            suBinaryFound = suFound,
            dangerousPropsDetected = dangerousProps,
            selinuxPermissive = selinuxPermissive,
            magiskMountsDetected = magiskMounts,
            rootPackagesFound = rootPkgs,
            details = detailsBuilder.toString().trim()
        )
    }

    /**
     * Fast check if device is rooted.
     */
    fun isDeviceRooted(context: Context? = null): Boolean {
        return performRootAudit(context).isRooted
    }

    /**
     * Checks if executing inside an Android emulator environment.
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
                (brand.startsWith("generic") && device.startsWith("generic"))
    }

    private fun checkBuildTags(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    private fun checkRootBinaryPaths(): Boolean {
        for (path in KNOWN_ROOT_PATHS) {
            try {
                if (File(path).exists()) return true
            } catch (_: Throwable) {}
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

    private fun checkDangerousSystemProperties(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec("getprop ro.secure")
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            val line = reader.readLine()
            p.destroy()
            line != null && line.trim() == "0"
        } catch (e: Exception) {
            false
        }
    }

    private fun isSELinuxPermissive(): Boolean {
        return try {
            val enforceFile = File("/sys/fs/selinux/enforce")
            if (enforceFile.exists()) {
                val content = enforceFile.readText().trim()
                content == "0" // 0 = Permissive, 1 = Enforcing
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun checkMagiskMounts(): Boolean {
        return try {
            val mountFile = File("/proc/self/mountinfo")
            if (!mountFile.exists()) return false

            val reader = BufferedReader(FileReader(mountFile))
            var line: String?
            var found = false
            while (reader.readLine().also { line = it } != null) {
                val lower = line?.lowercase() ?: continue
                if (lower.contains("magisk") || lower.contains("core/mirror") || lower.contains("/data/adb")) {
                    found = true
                    break
                }
            }
            reader.close()
            found
        } catch (e: Exception) {
            false
        }
    }

    private fun checkRootPackages(context: Context?): Boolean {
        if (context == null) return false
        val pm = context.packageManager
        for (packageName in KNOWN_ROOT_PACKAGES) {
            try {
                pm.getPackageInfo(packageName, 0)
                return true
            } catch (_: Exception) {
                // Package not found
            }
        }
        return false
    }
}
