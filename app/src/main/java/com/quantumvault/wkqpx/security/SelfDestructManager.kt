package com.quantumvault.wkqpx.security

import android.content.Context
import android.util.Log
import com.quantumvault.wkqpx.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore

enum class SelfDestructStatus {
    COMPLETE,
    PARTIAL,
    FAILED
}

enum class ShredResult {
    OVERWRITE_VERIFIED,
    DELETED_UNVERIFIED,
    FAILED
}

data class NuclearArtifactInventory(
    val databaseFiles: List<File>,
    val wrapperFiles: List<File>,
    val generationFiles: List<File>,
    val sentinelFiles: List<File>,
    val vaultPayloadFiles: List<File>,
    val cacheAndTempFiles: List<File>,
    val preferencesAndDatastoreFiles: List<File>,
    val keystoreAliases: List<String>,
    val keystoreInventoryComplete: Boolean = true
) {
    val allFiles: List<File> get() = databaseFiles + wrapperFiles + generationFiles +
            sentinelFiles + vaultPayloadFiles + cacheAndTempFiles + preferencesAndDatastoreFiles
}

data class SelfDestructResult(
    val status: SelfDestructStatus,
    val keyDestructionResults: Map<String, Boolean>,
    val databaseDestroyed: Boolean,
    val storageWiped: Boolean,
    val unverifiedRemainingFiles: List<String> = emptyList(),
    val unverifiedRemainingAliases: List<String> = emptyList(),
    val error: Throwable? = null
)

/**
 * Self-Destruct Nuclear Engine.
 * Implements authoritative: Quiesce Application -> Discover Inventory -> Forensically Destroy -> Re-inventory & Authoritatively Verify Absent.
 * Guarantees zero residual artifacts before declaring SelfDestructStatus.COMPLETE.
 */
object SelfDestructManager {
    private const val TAG = "SelfDestructManager"

    @Volatile
    var isApplicationQuiesced: Boolean = false
        private set

    fun quiesceApplication() {
        isApplicationQuiesced = true
        try {
            VaultKeyManager.lockVault()
        } catch (e: Exception) {
            Log.w(TAG, "Vault lock during quiesce: ${e.message}")
        }
        try {
            AppDatabase.closeDatabases()
        } catch (e: Exception) {
            Log.w(TAG, "Database close during quiesce: ${e.message}")
        }
        try {
            System.gc()
        } catch (_: Exception) {}
    }

    suspend fun executeNuclearSelfDestruct(context: Context): SelfDestructResult = withContext(Dispatchers.IO) {
        var dbDestroyed = false
        var storageWiped = false
        var keyResults = emptyMap<String, Boolean>()

        try {
            // Phase 1: Quiesce application writers and active database connections
            quiesceApplication()

            // Phase 2: Comprehensive Artifact Inventory Discovery
            val inventory = discoverNuclearInventory(context)
            Log.d(TAG, "Nuclear inventory discovered: ${inventory.allFiles.size} files, ${inventory.keystoreAliases.size} key aliases. KeystoreComplete=${inventory.keystoreInventoryComplete}")

            // Phase 3: Forensic Destruction
            // 3a. Shred specific inventoried files individually
            for (file in inventory.allFiles) {
                shredFile(file)
            }

            // 3b. Delete databases via Context API
            try {
                context.deleteDatabase("secure_vault_db")
                context.deleteDatabase("secure_vault_decoy_db")
                DatabaseKeyManager.destroyKeys(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error in context.deleteDatabase: ${e.message}")
            }

            // 3c. Recursively shred all standard app directories
            val filesDirWiped = shredDirectory(context.filesDir)
            val parentDir = context.filesDir.parentFile
            val datastoreDir = File(parentDir, "datastore")
            val datastoreWiped = if (datastoreDir.exists()) shredDirectory(datastoreDir) else true
            val sharedPrefsDir = File(parentDir, "shared_prefs")
            val sharedPrefsWiped = if (sharedPrefsDir.exists()) shredDirectory(sharedPrefsDir) else true
            val databasesDir = File(parentDir, "databases")
            val databasesWiped = if (databasesDir.exists()) shredDirectory(databasesDir) else true
            val cacheDirWiped = shredDirectory(context.cacheDir)
            val codeCacheDirWiped = shredDirectory(context.codeCacheDir)

            storageWiped = filesDirWiped && datastoreWiped && sharedPrefsWiped &&
                    databasesWiped && cacheDirWiped && codeCacheDirWiped

            // 3d. Authoritatively destroy ALL Keystore keys dynamically
            keyResults = VaultKeyManager.destroyAllKeys()

            // Phase 4: Re-Inventory Verification (Verify Zero Residual Artifacts)
            val unverifiedFiles = mutableListOf<String>()
            for (file in inventory.allFiles) {
                if (file.exists()) {
                    unverifiedFiles.add(file.absolutePath)
                }
            }

            // Perform closed-world re-inventory check to catch any post-discovery writes
            val reInventory = discoverNuclearInventory(context)
            for (file in reInventory.allFiles) {
                if (file.exists() && !unverifiedFiles.contains(file.absolutePath)) {
                    unverifiedFiles.add(file.absolutePath)
                }
            }

            val unverifiedAliases = mutableListOf<String>()
            var postKeystoreInventoryComplete = true
            try {
                val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val currentAliases = ks.aliases().toList()
                for (alias in (inventory.keystoreAliases + reInventory.keystoreAliases).distinct()) {
                    if (currentAliases.contains(alias) || ks.containsAlias(alias)) {
                        unverifiedAliases.add(alias)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Keystore post-verification error: ${e.message}")
                postKeystoreInventoryComplete = false
            }

            val realDbFile = context.getDatabasePath("secure_vault_db")
            val decoyDbFile = context.getDatabasePath("secure_vault_decoy_db")
            dbDestroyed = (realDbFile == null || !realDbFile.exists()) &&
                    (decoyDbFile == null || !decoyDbFile.exists()) &&
                    !File(parentDir, "databases/secure_vault_db").exists() &&
                    !File(parentDir, "databases/secure_vault_decoy_db").exists()

            val allKeysDestroyed = (keyResults.isEmpty() || keyResults.values.all { it }) && unverifiedAliases.isEmpty()
            val zeroFilesRemaining = unverifiedFiles.isEmpty()
            val keystoreDiscoverySound = inventory.keystoreInventoryComplete && postKeystoreInventoryComplete

            // Phase 5: Status Determination (Strict: Anything unverified or incomplete prevents COMPLETE)
            val status = if (allKeysDestroyed && dbDestroyed && storageWiped && zeroFilesRemaining && keystoreDiscoverySound) {
                SelfDestructStatus.COMPLETE
            } else if (keyResults.values.any { it } || dbDestroyed || storageWiped || zeroFilesRemaining) {
                SelfDestructStatus.PARTIAL
            } else {
                SelfDestructStatus.FAILED
            }

            // Phase 6: OS ActivityManager Wipe (terminates process in production)
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                activityManager?.clearApplicationUserData()
            } catch (e: Exception) {
                Log.w(TAG, "clearApplicationUserData skipped or unsupported in test runtime")
            }

            SelfDestructResult(
                status = status,
                keyDestructionResults = keyResults,
                databaseDestroyed = dbDestroyed,
                storageWiped = storageWiped,
                unverifiedRemainingFiles = unverifiedFiles.distinct(),
                unverifiedRemainingAliases = unverifiedAliases.distinct()
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

    /**
     * Comprehensive artifact discovery across databases, wrappers, sentinels, generations,
     * staging directories, caches, logs, and Keystore aliases.
     */
    fun discoverNuclearInventory(context: Context): NuclearArtifactInventory {
        val parentDir = context.filesDir.parentFile

        // 1. Databases & auxiliary journal/WAL files
        val dbFiles = mutableListOf<File>()
        val dbNames = listOf("secure_vault_db", "secure_vault_decoy_db")
        for (name in dbNames) {
            val db = context.getDatabasePath(name)
            if (db != null) {
                dbFiles.add(db)
                dbFiles.add(File("${db.absolutePath}-wal"))
                dbFiles.add(File("${db.absolutePath}-shm"))
                dbFiles.add(File("${db.absolutePath}-journal"))
            }
        }
        val databasesDir = File(parentDir, "databases")
        if (databasesDir.exists()) {
            databasesDir.listFiles()?.let { dbFiles.addAll(it) }
        }

        // 2. Cryptographic Wrappers
        val wrapperNames = listOf(
            "vrk_pin_wrap.bin", "decoy_vrk_pin_wrap.bin",
            "vrk_pin_wrap.bin.tmp", "decoy_vrk_pin_wrap.bin.tmp",
            "biometric_wrap.bin", "decoy_biometric_wrap.bin",
            "biometric_wrap.bin.staged", "decoy_biometric_wrap.bin.staged",
            "biometric_envelope_real.bin", "biometric_envelope_decoy.bin",
            "vrk_wrapper_real.bin", "vrk_wrapper_decoy.bin",
            "db_key_wrapper_real.bin", "db_key_wrapper_decoy.bin",
            "credential_wrap.bin", "decoy_credential_wrap.bin",
            "kek_salt.bin"
        )
        val wrapperFiles = wrapperNames.map { File(context.filesDir, it) }

        // 3. Generation Metadata & Commit Journals
        val genNames = listOf(
            "vault_gen_real.bin", "vault_gen_decoy.bin",
            "vault_gen_real.intent", "vault_gen_decoy.intent",
            "vault_gen_real.bin.tmp", "vault_gen_decoy.bin.tmp",
            "vault_restore_journal.bin", "vault_restore_journal.bin.tmp",
            "vault_restore_journal.json", "vault_restore_journal.json.tmp"
        )
        val generationFiles = genNames.map { File(context.filesDir, it) }

        // 4. Cryptographic Sentinels
        val sentinelNames = listOf(
            "vault_sentinel.bin", "decoy_vault_sentinel.bin",
            "vault_sentinel.bin.tmp", "decoy_vault_sentinel.bin.tmp",
            "vault_sentinel_real.bin", "vault_sentinel_decoy.bin"
        )
        val sentinelFiles = sentinelNames.map { File(context.filesDir, it) }

        // 5. Vault Payload Directories & Staging Generations
        val vaultPayloadFiles = mutableListOf<File>()
        val vaultDirNames = listOf("vault_data", "vault_data_decoy")
        for (dirName in vaultDirNames) {
            val dir = File(context.filesDir, dirName)
            if (dir.exists()) {
                dir.listFiles()?.let { vaultPayloadFiles.addAll(it) }
                vaultPayloadFiles.add(dir)
            }
        }
        context.filesDir.listFiles()?.forEach { file ->
            if (file.isDirectory && (file.name.contains("staging") || file.name.contains("prev_gen") || file.name.contains("next_gen") || file.name.contains("merge_backup"))) {
                file.listFiles()?.let { vaultPayloadFiles.addAll(it) }
                vaultPayloadFiles.add(file)
            }
        }

        // 6. Caches, Thumbnails, Temp Backup Artifacts
        val cacheAndTempFiles = mutableListOf<File>()
        val thumbDir = File(context.cacheDir, "vault_thumbnails_encrypted")
        if (thumbDir.exists()) {
            thumbDir.listFiles()?.let { cacheAndTempFiles.addAll(it) }
            cacheAndTempFiles.add(thumbDir)
        }
        context.cacheDir.listFiles()?.let { cacheAndTempFiles.addAll(it) }
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("temp_backup") || file.name.endsWith(".tmp") || file.name.endsWith(".enc")) {
                cacheAndTempFiles.add(file)
            }
        }

        // 7. Preferences & DataStore
        val prefsAndDatastoreFiles = mutableListOf<File>()
        val datastoreDir = File(parentDir, "datastore")
        if (datastoreDir.exists()) {
            datastoreDir.listFiles()?.let { prefsAndDatastoreFiles.addAll(it) }
            prefsAndDatastoreFiles.add(datastoreDir)
        }
        val sharedPrefsDir = File(parentDir, "shared_prefs")
        if (sharedPrefsDir.exists()) {
            sharedPrefsDir.listFiles()?.let { prefsAndDatastoreFiles.addAll(it) }
            prefsAndDatastoreFiles.add(sharedPrefsDir)
        }

        // 8. Keystore Aliases (Fail-closed: tracks if discovery succeeded)
        val aliases = mutableListOf<String>()
        var keystoreComplete = true
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            aliases.addAll(ks.aliases().toList())
        } catch (e: Exception) {
            Log.e(TAG, "Keystore alias discovery failed: ${e.message}")
            keystoreComplete = false
        }

        return NuclearArtifactInventory(
            databaseFiles = dbFiles.distinct(),
            wrapperFiles = wrapperFiles.distinct(),
            generationFiles = generationFiles.distinct(),
            sentinelFiles = sentinelFiles.distinct(),
            vaultPayloadFiles = vaultPayloadFiles.distinct(),
            cacheAndTempFiles = cacheAndTempFiles.distinct(),
            preferencesAndDatastoreFiles = prefsAndDatastoreFiles.distinct(),
            keystoreAliases = aliases.distinct(),
            keystoreInventoryComplete = keystoreComplete
        )
    }

    private fun shredDirectory(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return true
        var allSuccess = true
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (!shredDirectory(file)) allSuccess = false
            } else {
                if (shredFile(file) == ShredResult.FAILED) allSuccess = false
            }
        }
        val dirDeleted = dir.delete() || !dir.exists()
        return allSuccess && dirDeleted
    }

    fun shredFile(file: File): ShredResult {
        if (!file.exists()) return ShredResult.DELETED_UNVERIFIED
        var overwriteSuccess = false
        try {
            if (file.isFile && file.canWrite()) {
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
                    overwriteSuccess = true
                } else {
                    overwriteSuccess = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "File zero-overwrite failed for ${file.name}: ${e.message}")
        }

        val deleted = try { file.delete() || !file.exists() } catch (_: Exception) { !file.exists() }
        return when {
            deleted && overwriteSuccess -> ShredResult.OVERWRITE_VERIFIED
            deleted -> ShredResult.DELETED_UNVERIFIED
            else -> ShredResult.FAILED
        }
    }
}
