package com.example.security

import android.content.Context
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore

/**
 * Self-Destruct Nuclear Engine.
 * Securely shreds and wipes all internal storage, databases, cache, and Keystore entries.
 */
object SelfDestructManager {

    suspend fun executeNuclearSelfDestruct(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Close and delete Room database
            try {
                val db = AppDatabase.getDatabase(context)
                db.close()
                context.deleteDatabase("secure_vault_db")
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Wipe /shred files in Context.filesDir recursively
            shredDirectory(context.filesDir)

            // 3. Wipe /shred files in Context.cacheDir recursively
            shredDirectory(context.cacheDir)

            // 4. Reset Keystore keys
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (keyStore.containsAlias("VaultMasterKey")) {
                    keyStore.deleteEntry("VaultMasterKey")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun shredDirectory(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                shredDirectory(file)
            } else {
                shredFile(file)
            }
        }
        dir.delete()
    }

    private fun shredFile(file: File) {
        try {
            if (file.exists() && file.canWrite()) {
                val length = file.length()
                if (length > 0) {
                    file.outputStream().use { fos ->
                        // Zero overwrite
                        val zeroes = ByteArray(8192)
                        var remaining = length
                        while (remaining > 0) {
                            val writeLen = minOf(remaining, zeroes.size.toLong()).toInt()
                            fos.write(zeroes, 0, writeLen)
                            remaining -= writeLen
                        }
                        fos.flush()
                    }
                }
            }
            file.delete()
        } catch (e: Exception) {
            file.delete()
        }
    }
}
