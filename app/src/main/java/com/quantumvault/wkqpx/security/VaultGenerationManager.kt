package com.quantumvault.wkqpx.security

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Authoritative persistent Vault Generation Lifecycle Manager.
 * Prevents vault rollback attacks and binds cryptographic sentinels to explicit,
 * monotonically tracked generation epochs.
 */
object VaultGenerationManager {
    private const val TAG = "VaultGenerationManager"
    private const val REAL_GEN_FILE = "vault_gen_real.bin"
    private const val DECOY_GEN_FILE = "vault_gen_decoy.bin"
    private const val MAGIC_GEN = 0x5647454EL // "VGEN"

    @Synchronized
    fun getActiveGeneration(context: Context, isDecoy: Boolean): Long {
        val fileName = if (isDecoy) DECOY_GEN_FILE else REAL_GEN_FILE
        val file = File(context.filesDir, fileName)
        if (!file.exists() || file.length() < 16) {
            // First initialization of vault generation
            val initialGen = 1L
            persistGeneration(context, isDecoy, initialGen)
            return initialGen
        }
        return try {
            val bytes = file.readBytes()
            val bb = ByteBuffer.wrap(bytes)
            val magic = bb.int.toLong() and 0xFFFFFFFFL
            if (magic != MAGIC_GEN) {
                Log.w(TAG, "Corrupt generation header, resetting to 1L")
                1L
            } else {
                bb.long
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading generation ID: ${e.message}", e)
            1L
        }
    }

    @Synchronized
    fun incrementAndGetNextGeneration(context: Context, isDecoy: Boolean): Long {
        val current = getActiveGeneration(context, isDecoy)
        val next = current + 1L
        persistGeneration(context, isDecoy, next)
        return next
    }

    @Synchronized
    fun resetGeneration(context: Context, isDecoy: Boolean, newGen: Long = 1L) {
        persistGeneration(context, isDecoy, newGen)
    }

    private fun persistGeneration(context: Context, isDecoy: Boolean, gen: Long) {
        val fileName = if (isDecoy) DECOY_GEN_FILE else REAL_GEN_FILE
        val targetFile = File(context.filesDir, fileName)
        val tempFile = File(context.filesDir, "$fileName.tmp")
        try {
            val bb = ByteBuffer.allocate(16)
            bb.putInt(MAGIC_GEN.toInt())
            bb.putLong(gen)
            bb.putInt(0) // Reserved padding

            FileOutputStream(tempFile).use { fos ->
                fos.write(bb.array())
                fos.flush()
                fos.fd.sync()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist generation $gen: ${e.message}", e)
        }
    }
}
