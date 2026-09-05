package com.quantumvault.wkqpx.security

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class GenerationCorruptionException(message: String, cause: Throwable? = null) : SecurityException(message, cause)
class GenerationPersistenceException(message: String, cause: Throwable? = null) : SecurityException(message, cause)

/**
 * Authoritative persistent Vault Generation Lifecycle Manager.
 * Prevents vault rollback attacks and binds cryptographic sentinels to explicit,
 * monotonically tracked generation epochs.
 *
 * Persisted binary record schema (52 bytes authenticated):
 * - [0..3]: MAGIC_GEN (0x5647454E = "VGEN")
 * - [4..11]: Generation ID (Long)
 * - [12..19]: Timestamp (Long)
 * - [20..23]: CRC-32 Checksum of [0..19] (Int)
 * - [24..35]: AES-GCM IV (12 bytes)
 * - [36..51]: AES-GCM Authentication Tag over [0..23] AAD (16 bytes)
 */
object VaultGenerationManager {
    private const val TAG = "VaultGenerationManager"
    private const val REAL_GEN_FILE = "vault_gen_real.bin"
    private const val DECOY_GEN_FILE = "vault_gen_decoy.bin"
    private const val REAL_INTENT_FILE = "vault_gen_real.intent"
    private const val DECOY_INTENT_FILE = "vault_gen_decoy.intent"

    private const val MAGIC_GEN_V2 = 0x56474532L // "VGE2"
    private const val MAGIC_GEN_LEGACY = 0x5647454EL // "VGEN"
    private const val SCHEMA_VERSION: Byte = 2
    private const val REALM_REAL: Byte = 1
    private const val REALM_DECOY: Byte = 2

    const val RECORD_SIZE_BYTES = 56
    const val LEGACY_RECORD_V1_SIZE_BYTES = 52
    const val LEGACY_UNAUTH_SIZE_BYTES = 24
    private const val GCM_IV_SIZE_BYTES = 12
    private const val GCM_TAG_SIZE_BYTES = 16
    private val DOMAIN_AAD_PREFIX = "QUANTUM_VAULT_GEN_AUTH_V2".toByteArray(Charsets.UTF_8)

    @Synchronized
    fun getActiveGeneration(context: Context, isDecoy: Boolean): Long {
        val fileName = if (isDecoy) DECOY_GEN_FILE else REAL_GEN_FILE
        val file = File(context.filesDir, fileName)
        if (!file.exists()) {
            if (VaultKeyManager.hasCredentialWrap(context, isDecoy)) {
                throw GenerationCorruptionException(
                    "Missing generation metadata file '$fileName' for initialized vault (credential wrap exists). Replay/Rollback protection record lost. RECOVERY_REQUIRED."
                )
            }
            // First initialization of vault generation for fresh uninitialized vault
            val initialGen = 1L
            persistGeneration(context, isDecoy, initialGen)
            return initialGen
        }

        val len = file.length()
        if (len != RECORD_SIZE_BYTES.toLong() && len != LEGACY_RECORD_V1_SIZE_BYTES.toLong() && len != LEGACY_UNAUTH_SIZE_BYTES.toLong()) {
            throw GenerationCorruptionException(
                "Corrupt generation file '$fileName': length is $len bytes, expected $RECORD_SIZE_BYTES bytes. RECOVERY_REQUIRED."
            )
        }

        return try {
            val bytes = file.readBytes()

            if (bytes.size == RECORD_SIZE_BYTES) {
                val bb = ByteBuffer.wrap(bytes)
                val magic = bb.int.toLong() and 0xFFFFFFFFL
                if (magic != MAGIC_GEN_V2) {
                    throw GenerationCorruptionException(
                        "Corrupt generation header in '$fileName': magic $magic != expected $MAGIC_GEN_V2. RECOVERY_REQUIRED."
                    )
                }
                val version = bb.get()
                if (version != SCHEMA_VERSION) {
                    throw GenerationCorruptionException(
                        "Unsupported generation schema version in '$fileName': $version != expected $SCHEMA_VERSION. RECOVERY_REQUIRED."
                    )
                }
                val realm = bb.get()
                val expectedRealm = if (isDecoy) REALM_DECOY else REALM_REAL
                if (realm != expectedRealm) {
                    throw GenerationCorruptionException(
                        "Cross-realm generation record substitution detected in '$fileName': realm $realm != expected $expectedRealm. RECOVERY_REQUIRED."
                    )
                }
                bb.getShort() // Reserved
                val gen = bb.long
                val timestamp = bb.long
                val recordedCrc = bb.int

                val crcCalculator = CRC32()
                crcCalculator.update(bytes, 0, 24)
                val computedCrc = crcCalculator.value.toInt()
                if (recordedCrc != computedCrc) {
                    throw GenerationCorruptionException(
                        "Generation integrity CRC mismatch in '$fileName': recorded $recordedCrc != computed $computedCrc. Tamper detected. RECOVERY_REQUIRED."
                    )
                }

                if (gen < 1L) {
                    throw GenerationCorruptionException(
                        "Invalid generation epoch in '$fileName': $gen < 1. RECOVERY_REQUIRED."
                    )
                }

                val iv = bytes.copyOfRange(28, 28 + GCM_IV_SIZE_BYTES)
                val tag = bytes.copyOfRange(28 + GCM_IV_SIZE_BYTES, RECORD_SIZE_BYTES)
                val aad = ByteBuffer.allocate(DOMAIN_AAD_PREFIX.size + 28)
                    .put(DOMAIN_AAD_PREFIX)
                    .put(bytes, 0, 28)
                    .array()
                verifyAuthenticationTag(context, aad, iv, tag)
                gen
            } else if (bytes.size == LEGACY_RECORD_V1_SIZE_BYTES) {
                // Legacy V1 (52 bytes) - verify V1 authentication tag
                val bb = ByteBuffer.wrap(bytes)
                val magic = bb.int.toLong() and 0xFFFFFFFFL
                if (magic != MAGIC_GEN_LEGACY) {
                    throw GenerationCorruptionException("Corrupt legacy generation header in '$fileName'. RECOVERY_REQUIRED.")
                }
                val gen = bb.long
                val timestamp = bb.long
                val recordedCrc = bb.int

                val crcCalculator = CRC32()
                crcCalculator.update(bytes, 0, 20)
                if (crcCalculator.value.toInt() != recordedCrc) {
                    throw GenerationCorruptionException("Legacy generation CRC mismatch in '$fileName'. RECOVERY_REQUIRED.")
                }
                val iv = bytes.copyOfRange(24, 36)
                val tag = bytes.copyOfRange(36, 52)
                verifyLegacyAuthenticationTag(context, bytes.copyOfRange(0, 24), iv, tag)

                // Seamlessly upgrade to hardened GEN2 format
                persistGeneration(context, isDecoy, gen)
                gen
            } else if (bytes.size == 24) {
                // Legacy 24-byte unauthenticated record
                val bb = ByteBuffer.wrap(bytes)
                val magic = bb.int.toLong() and 0xFFFFFFFFL
                if (magic != MAGIC_GEN_LEGACY) {
                    throw GenerationCorruptionException("Corrupted legacy generation file '$fileName' (invalid magic header). RECOVERY_REQUIRED.")
                }
                val gen = bb.long
                val timestamp = bb.long
                val recordedCrc = bb.int
                val crcCalculator = CRC32()
                crcCalculator.update(bytes, 0, 20)
                if (crcCalculator.value.toInt() != recordedCrc) {
                    throw GenerationCorruptionException("Corrupted legacy generation file '$fileName' (CRC mismatch). RECOVERY_REQUIRED.")
                }
                if (VaultKeyManager.hasCredentialWrap(context, isDecoy)) {
                    throw GenerationCorruptionException(
                        "Unauthenticated legacy 24-byte generation record rejected for initialized vault '$fileName'. Tamper detected. RECOVERY_REQUIRED."
                    )
                }
                val validGen = if (gen >= 1L) gen else 1L
                persistGeneration(context, isDecoy, validGen)
                validGen
            } else {
                throw GenerationCorruptionException("Corrupted generation file '$fileName' of unexpected size (${bytes.size} bytes). RECOVERY_REQUIRED.")
            }
        } catch (e: GenerationCorruptionException) {
            throw e
        } catch (e: Exception) {
            throw GenerationCorruptionException("Failed to read generation file '$fileName': ${e.message}", e)
        }
    }

    @Synchronized
    fun incrementAndGetNextGeneration(context: Context, isDecoy: Boolean): Long {
        val current = getActiveGeneration(context, isDecoy)
        val next = current + 1L
        persistGeneration(context, isDecoy, next)
        return next
    }

    /**
     * Prepares a durable, authenticated generation intent for two-phase commit during atomic restores.
     */
    @Synchronized
    fun prepareGenerationIntent(context: Context, isDecoy: Boolean, nextGen: Long): File {
        val intentFileName = if (isDecoy) DECOY_INTENT_FILE else REAL_INTENT_FILE
        val intentFile = File(context.filesDir, intentFileName)
        val bytes = buildGenerationRecord(context, isDecoy, nextGen)
        FileOutputStream(intentFile).use { fos ->
            fos.write(bytes)
            fos.flush()
            fos.fd.sync()
        }
        return intentFile
    }

    /**
     * Checks and recovers an orphaned generation intent file if no active restore journal governs it.
     */
    @Synchronized
    fun recoverPendingIntentIfAny(context: Context, isDecoy: Boolean): Boolean {
        val intentFileName = if (isDecoy) DECOY_INTENT_FILE else REAL_INTENT_FILE
        val intentFile = File(context.filesDir, intentFileName)
        if (!intentFile.exists()) return false

        // Fail-closed: If reading the journal throws JournalCorruptedException or any parse failure,
        // we MUST NOT treat the journal as absent. We must abort intent cleanup and fail closed into RECOVERY_REQUIRED.
        val journal = try {
            VaultRestoreJournal.readJournal(context)
        } catch (e: Exception) {
            Log.e(TAG, "Corrupt or unauthenticated restore journal detected while intent file '$intentFileName' exists. Aborting intent cleanup. RECOVERY_REQUIRED.", e)
            throw GenerationCorruptionException(
                "Corrupt restore journal detected while generation intent file '$intentFileName' exists. Recovery state protected. RECOVERY_REQUIRED.",
                e
            )
        }

        if (journal != null) {
            // Restore journal manages recovery
            return false
        }

        Log.w(TAG, "Cleaning verified orphaned generation intent file: $intentFileName")
        return try {
            intentFile.delete()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Atomically commits a prepared generation intent to become the authoritative active generation.
     */
    @Synchronized
    fun commitGeneration(context: Context, isDecoy: Boolean, nextGen: Long, intentFile: File? = null) {
        val fileName = if (isDecoy) DECOY_GEN_FILE else REAL_GEN_FILE
        val targetFile = File(context.filesDir, fileName)

        val sourceFile = if (intentFile != null && intentFile.exists()) {
            // Verify intent integrity & authenticity prior to commit
            val intentBytes = intentFile.readBytes()
            if (intentBytes.size == RECORD_SIZE_BYTES) {
                val iv = intentBytes.copyOfRange(28, 28 + GCM_IV_SIZE_BYTES)
                val tag = intentBytes.copyOfRange(28 + GCM_IV_SIZE_BYTES, RECORD_SIZE_BYTES)
                val aad = ByteBuffer.allocate(DOMAIN_AAD_PREFIX.size + 28)
                    .put(DOMAIN_AAD_PREFIX)
                    .put(intentBytes, 0, 28)
                    .array()
                verifyAuthenticationTag(context, aad, iv, tag)
            }
            intentFile
        } else {
            val tempFile = File(context.filesDir, "$fileName.tmp")
            val bytes = buildGenerationRecord(context, isDecoy, nextGen)
            FileOutputStream(tempFile).use { fos ->
                fos.write(bytes)
                fos.flush()
                fos.fd.sync()
            }
            tempFile
        }

        try {
            Files.move(
                sourceFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: Exception) {
            try { sourceFile.delete() } catch (_: Throwable) {}
            throw GenerationPersistenceException("Atomic generation commit failed for $fileName: ${e.message}", e)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Synchronized
    fun resetGenerationForTesting(context: Context, isDecoy: Boolean, newGen: Long = 1L) {
        if (!com.quantumvault.wkqpx.BuildConfig.DEBUG) {
            throw SecurityException("Resetting generation is strictly forbidden in production release builds.")
        }
        persistGeneration(context, isDecoy, newGen)
    }

    internal fun persistGeneration(context: Context, isDecoy: Boolean, gen: Long) {
        val fileName = if (isDecoy) DECOY_GEN_FILE else REAL_GEN_FILE
        val targetFile = File(context.filesDir, fileName)
        val tempFile = File(context.filesDir, "$fileName.tmp")
        try {
            val bytes = buildGenerationRecord(context, isDecoy, gen)
            FileOutputStream(tempFile).use { fos ->
                fos.write(bytes)
                fos.flush()
                fos.fd.sync()
            }
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: Exception) {
            try { tempFile.delete() } catch (_: Throwable) {}
            throw GenerationPersistenceException("Failed to atomically persist generation $gen for $fileName: ${e.message}", e)
        }
    }

    private fun buildGenerationRecord(context: Context, isDecoy: Boolean, gen: Long): ByteArray {
        val realmByte = if (isDecoy) REALM_DECOY else REALM_REAL
        val header = ByteBuffer.allocate(28)
        header.putInt(MAGIC_GEN_V2.toInt())
        header.put(SCHEMA_VERSION)
        header.put(realmByte)
        header.putShort(0.toShort()) // Reserved
        header.putLong(gen)
        header.putLong(System.currentTimeMillis())

        val crcCalculator = CRC32()
        crcCalculator.update(header.array(), 0, 24)
        header.putInt(crcCalculator.value.toInt())

        val aad = ByteBuffer.allocate(DOMAIN_AAD_PREFIX.size + 28)
            .put(DOMAIN_AAD_PREFIX)
            .put(header.array())
            .array()

        val (iv, authTag) = computeAuthenticationTag(context, aad)
        val fullRecord = ByteBuffer.allocate(RECORD_SIZE_BYTES)
        fullRecord.put(header.array())
        fullRecord.put(iv)
        fullRecord.put(authTag)
        return fullRecord.array()
    }

    private fun computeAuthenticationTag(context: Context, aadBytes: ByteArray): Pair<ByteArray, ByteArray> {
        val key = VaultKeyManager.getOrCreateKey(VaultKeyAliases.ALIAS_GENERATION_AUTH)
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aadBytes)
        val authTag = cipher.doFinal() // 16-byte authentication tag
        return Pair(iv, authTag)
    }

    private fun verifyAuthenticationTag(context: Context, aadBytes: ByteArray, iv: ByteArray, tag: ByteArray) {
        try {
            val key = VaultKeyManager.getOrCreateKey(VaultKeyAliases.ALIAS_GENERATION_AUTH)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(aadBytes)
            cipher.doFinal(tag)
        } catch (e: Exception) {
            throw GenerationCorruptionException(
                "Generation record cryptographic authentication failed: Tamper detected. RECOVERY_REQUIRED.",
                e
            )
        }
    }

    private fun verifyLegacyAuthenticationTag(context: Context, recordHeaderBytes: ByteArray, iv: ByteArray, tag: ByteArray) {
        try {
            val key = VaultKeyManager.getOrCreateKey(VaultKeyAliases.ALIAS_GENERATION_AUTH)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(recordHeaderBytes, 0, 24)
            cipher.doFinal(tag)
        } catch (e: Exception) {
            throw GenerationCorruptionException(
                "Legacy generation record cryptographic authentication failed: Tamper detected. RECOVERY_REQUIRED.",
                e
            )
        }
    }
}
