package com.quantumvault.wkqpx.security

import android.content.Context
import android.util.Log
import com.quantumvault.wkqpx.data.AppDatabase
import com.quantumvault.wkqpx.util.VaultLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class SelfDestructStatus {
    COMPLETE,
    PARTIAL,
    FAILED
}

data class SelfDestructResult(
    val status: SelfDestructStatus,
    val keyDestructionResults: Map<String, Boolean>,
    val databaseDestroyed: Boolean,
    val storageWiped: Boolean,
    val error: Throwable? = null
)

/**
 * Self-Destruct Nuclear Engine.
 * Securely shreds and wipes all internal storage, databases, cache, and Keystore entries.
 */
object SelfDestructManager {
    private const val TAG = "SelfDestructManager"

    suspend fun executeNuclearSelfDestruct(context: Context): SelfDestructResult = withContext(Dispatchers.IO) {
        var dbDestroyed = false
        var storageWiped = false
        var keyResults = emptyMap<String, Boolean>()

        try {
            // 1. Close and delete Room databases (both real and decoy) with explicit verification
            try {
                AppDatabase.closeDatabases()
                val deletedReal = context.deleteDatabase("secure_vault_db")
                val deletedDecoy = context.deleteDatabase("secure_vault_decoy_db")
                DatabaseKeyManager.destroyKeys(context)

                val realDbFile = context.getDatabasePath("secure_vault_db")
                val decoyDbFile = context.getDatabasePath("secure_vault_decoy_db")
                val realStillExists = realDbFile != null && realDbFile.exists()
                val decoyStillExists = decoyDbFile != null && decoyDbFile.exists()

                dbDestroyed = !realStillExists && !decoyStillExists
            } catch (e: Exception) {
                Log.e(TAG, "Error closing/deleting databases during self-destruct", e)
                dbDestroyed = false
            }

            // 2. Wipe / shred files in Context.filesDir and datastore recursively with authoritative verification
            try {
                val filesDirWiped = shredDirectory(context.filesDir)
                val datastoreDir = File(context.filesDir.parent, "datastore")
                val datastoreWiped = if (datastoreDir.exists()) shredDirectory(datastoreDir) else true
                val sharedPrefsDir = File(context.filesDir.parent, "shared_prefs")
                val sharedPrefsWiped = if (sharedPrefsDir.exists()) shredDirectory(sharedPrefsDir) else true
                val cacheDirWiped = shredDirectory(context.cacheDir)
                
                storageWiped = filesDirWiped && datastoreWiped && sharedPrefsWiped && cacheDirWiped
            } catch (e: Exception) {
                Log.e(TAG, "Error wiping storage directories during self-destruct", e)
                storageWiped = false
            }

            // 3. Authoritatively destroy ALL Keystore keys via central VaultKeyManager
            keyResults = VaultKeyManager.destroyAllKeys()
            val allKeysDestroyed = keyResults.isNotEmpty() && keyResults.values.all { it }

            // 4. Status determination
            val status = if (allKeysDestroyed && dbDestroyed && storageWiped) {
                SelfDestructStatus.COMPLETE
            } else if (keyResults.values.any { it } || dbDestroyed || storageWiped) {
                SelfDestructStatus.PARTIAL
            } else {
                SelfDestructStatus.FAILED
            }

            // 5. Ultimate wipe using OS ActivityManager (terminates app)
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                activityManager.clearApplicationUserData()
            } catch (e: Exception) {
                Log.w(TAG, "clearApplicationUserData skipped or unsupported in test runtime")
            }

            SelfDestructResult(
                status = status,
                keyDestructionResults = keyResults,
                databaseDestroyed = dbDestroyed,
                storageWiped = storageWiped
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error during nuclear self-destruct", e)
            SelfDestructResult(
                status = SelfDestructStatus.FAILED,
                keyDestructionResults = keyResults,
                databaseDestroyed = dbDestroyed,
                storageWiped = storageWiped,
                error = e
            )
        }
    }

    private fun shredDirectory(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return true
        var allSuccess = true
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (!shredDirectory(file)) allSuccess = false
            } else {
                if (!shredFile(file)) allSuccess = false
            }
        }
        val dirDeleted = dir.delete() || !dir.exists()
        return allSuccess && dirDeleted
    }

    fun shredFile(file: File): Boolean {
        return try {
            if (file.exists() && file.canWrite()) {
                val length = file.length()
                if (length > 0) {
                    file.outputStream().use { fos ->
                        val zeroes = ByteArray(8192)
                        var remaining = length
                        while (remaining > 0) {
                            val writeLen = minOf(remaining, zeroes.size.toLong()).toInt()
                            fos.write(zeroes, 0, writeLen)
                            remaining -= writeLen
                        }
                        fos.flush()
                        fos.fd.sync()
                    }
                }
            }
            file.delete() || !file.exists()
        } catch (e: Exception) {
            file.delete() || !file.exists()
        }
    }
}
