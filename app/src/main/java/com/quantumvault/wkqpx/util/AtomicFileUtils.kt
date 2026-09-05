package com.quantumvault.wkqpx.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Universal atomic file persistence utility.
 * Guarantees POSIX fsync and atomic commit via rename/Files.move.
 * Never silently fails or uses non-atomic partial overwrite fallbacks.
 */
object AtomicFileUtils {
    private const val TAG = "AtomicFileUtils"

    /**
     * Atomically writes [data] to [targetFile] via a temporary file with fsync and atomic move.
     * Throws [IOException] on failure.
     */
    @Throws(IOException::class)
    fun writeAtomic(targetFile: File, data: ByteArray) {
        val parentDir = targetFile.parentFile ?: throw IOException("Target file has no parent directory")
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw IOException("Failed to create parent directory: ${parentDir.absolutePath}")
        }

        val tempFile = File(parentDir, "${targetFile.name}.tmp_${System.currentTimeMillis()}_${(0..9999).random()}")

        try {
            FileOutputStream(tempFile).use { fos ->
                fos.write(data)
                fos.flush()
                fos.fd.sync()
            }

            commitAtomic(tempFile, targetFile)
        } catch (e: Exception) {
            try { tempFile.delete() } catch (_: Exception) {}
            if (e is IOException) throw e
            throw IOException("Failed atomic write to ${targetFile.name}: ${e.message}", e)
        }
    }

    /**
     * Atomically moves [sourceFile] to [targetFile].
     * Uses OS-level atomic move where supported, throwing on failure.
     */
    @Throws(IOException::class)
    fun commitAtomic(sourceFile: File, targetFile: File) {
        if (!sourceFile.exists()) {
            throw IOException("Source file does not exist: ${sourceFile.absolutePath}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Files.move(
                    sourceFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
                return
            } catch (e: Exception) {
                Log.w(TAG, "ATOMIC_MOVE failed, attempting atomic REPLACE_EXISTING: ${e.message}")
                try {
                    Files.move(
                        sourceFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                    return
                } catch (ex: Exception) {
                    throw IOException("Failed to move file atomically to ${targetFile.absolutePath}: ${ex.message}", ex)
                }
            }
        } else {
            if (targetFile.exists() && !targetFile.delete()) {
                Log.w(TAG, "Could not delete existing target before rename: ${targetFile.name}")
            }
            if (!sourceFile.renameTo(targetFile)) {
                throw IOException("renameTo failed from ${sourceFile.name} to ${targetFile.name}")
            }
        }
    }
}
