package com.quantumvault.wkqpx.security

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages authenticated sentinels to cryptographically verify unwrapped Vault Root Keys (VRK).
 * Sentinel binds VRK + realm + vault generation.
 * STRICT INVARIANT: Sentinel is created ONLY during initial vault creation.
 * If missing subsequently, system fails closed (RECOVERY_REQUIRED), never silently regenerated!
 */
object VaultSentinelManager {
    private const val TAG = "VaultSentinelManager"

    private const val REAL_SENTINEL_FILE = "vault_sentinel.bin"
    private const val DECOY_SENTINEL_FILE = "decoy_vault_sentinel.bin"

    private val MAGIC_QSEN = byteArrayOf(0x51, 0x53, 0x45, 0x4E) // "QSEN"
    private const val SENTINEL_VERSION: Byte = 2
    private const val REALM_REAL: Byte = 1
    private const val REALM_DECOY: Byte = 2

    private val REAL_SENTINEL_PLAINTEXT = "QUANTUM_VAULT_REAL_SENTINEL_OK_V2".toByteArray(Charsets.UTF_8)
    private val REAL_SENTINEL_AAD_PREFIX = "QUANTUM_VAULT_REAL_SENTINEL_AAD_V2".toByteArray(Charsets.UTF_8)

    private val DECOY_SENTINEL_PLAINTEXT = "QUANTUM_VAULT_DECOY_SENTINEL_OK_V2".toByteArray(Charsets.UTF_8)
    private val DECOY_SENTINEL_AAD_PREFIX = "QUANTUM_VAULT_DECOY_SENTINEL_AAD_V2".toByteArray(Charsets.UTF_8)

    // Legacy Plaintexts for backward compatibility with existing vaults
    private val LEGACY_REAL_PLAINTEXT = "QUANTUM_VAULT_REAL_SENTINEL_OK_V1".toByteArray(Charsets.UTF_8)
    private val LEGACY_REAL_AAD = "QUANTUM_VAULT_REAL_SENTINEL_AAD".toByteArray(Charsets.UTF_8)
    private val LEGACY_DECOY_PLAINTEXT = "QUANTUM_VAULT_DECOY_SENTINEL_OK_V1".toByteArray(Charsets.UTF_8)
    private val LEGACY_DECOY_AAD = "QUANTUM_VAULT_DECOY_SENTINEL_AAD".toByteArray(Charsets.UTF_8)

    private const val REAL_CONTEXT = "quantum_vault_real_sentinel_context"
    private const val DECOY_CONTEXT = "quantum_vault_decoy_sentinel_context"

    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_SIZE_BYTES = 12

    private fun deriveSentinelKey(vrk: ByteArray, isDecoy: Boolean, generationId: Long = 1L): SecretKeySpec {
        val md = MessageDigest.getInstance("SHA-256")
        val context = if (isDecoy) DECOY_CONTEXT else REAL_CONTEXT
        md.update(context.toByteArray(Charsets.UTF_8))
        md.update(ByteBuffer.allocate(8).putLong(generationId).array())
        md.update(vrk)
        return SecretKeySpec(md.digest(), "AES")
    }

    private fun deriveLegacySentinelKey(vrk: ByteArray, isDecoy: Boolean): SecretKeySpec {
        val md = MessageDigest.getInstance("SHA-256")
        val context = if (isDecoy) DECOY_CONTEXT else REAL_CONTEXT
        md.update(context.toByteArray(Charsets.UTF_8))
        md.update(vrk)
        return SecretKeySpec(md.digest(), "AES")
    }

    /**
     * Creates and atomically writes the authenticated sentinel for the given VRK.
     * Allowed ONLY during initial vault creation.
     */
    fun createSentinel(
        context: Context,
        vrk: ByteArray,
        isDecoy: Boolean = false,
        generationId: Long = VaultGenerationManager.getActiveGeneration(context, isDecoy)
    ): Boolean {
        return try {
            val key = deriveSentinelKey(vrk, isDecoy, generationId)
            val iv = ByteArray(IV_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            val aadPrefix = if (isDecoy) DECOY_SENTINEL_AAD_PREFIX else REAL_SENTINEL_AAD_PREFIX
            val aad = ByteBuffer.allocate(aadPrefix.size + 8).put(aadPrefix).putLong(generationId).array()
            cipher.updateAAD(aad)

            val plaintext = if (isDecoy) DECOY_SENTINEL_PLAINTEXT else REAL_SENTINEL_PLAINTEXT
            val ciphertext = cipher.doFinal(plaintext)
            val realm = if (isDecoy) REALM_DECOY else REALM_REAL

            val buffer = ByteBuffer.allocate(4 + 1 + 1 + 8 + 1 + IV_SIZE_BYTES + 2 + ciphertext.size)
            buffer.put(MAGIC_QSEN)
            buffer.put(SENTINEL_VERSION)
            buffer.put(realm)
            buffer.putLong(generationId)
            buffer.put(IV_SIZE_BYTES.toByte())
            buffer.put(iv)
            buffer.putShort(ciphertext.size.toShort())
            buffer.put(ciphertext)

            val fileName = if (isDecoy) DECOY_SENTINEL_FILE else REAL_SENTINEL_FILE
            val targetFile = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")

            FileOutputStream(tempFile).use { fos ->
                fos.write(buffer.array())
                fos.flush()
                fos.fd.sync()
            }

            try {
                java.nio.file.Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (e: Exception) {
                java.nio.file.Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
            if (!targetFile.exists() || targetFile.length() == 0L) {
                throw java.io.IOException("Failed to atomically commit sentinel file")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create sentinel (isDecoy=$isDecoy)", e)
            false
        }
    }

    /**
     * Cryptographically verifies the given VRK against the stored sentinel.
     * Returns true ONLY if the key successfully authenticates and decrypts the sentinel payload.
     * INVARIANT: NEVER silently regenerates a missing sentinel on an initialized vault!
     */
    fun verifyVrk(context: Context, vrk: ByteArray, isDecoy: Boolean = false): Boolean {
        if (vrk.size != 32) return false
        val fileName = if (isDecoy) DECOY_SENTINEL_FILE else REAL_SENTINEL_FILE
        val sentinelFile = File(context.filesDir, fileName)

        // If sentinel is missing, fail closed! Never regenerate!
        if (!sentinelFile.exists() || sentinelFile.length() < (1 + IV_SIZE_BYTES + 16)) {
            Log.e(TAG, "Sentinel missing or corrupted for vault (isDecoy=$isDecoy). Refusing silent recreation.")
            return false
        }

        return try {
            val bytes = sentinelFile.readBytes()

            // 1. Format Check: QSEN V2 format
            if (bytes.size >= 16 && bytes.copyOfRange(0, 4).contentEquals(MAGIC_QSEN)) {
                val bb = ByteBuffer.wrap(bytes)
                val magic = ByteArray(4)
                bb.get(magic)
                val version = bb.get()
                val realm = bb.get()
                val expectedRealm = if (isDecoy) REALM_DECOY else REALM_REAL
                if (realm != expectedRealm) {
                    Log.e(TAG, "Sentinel realm mismatch: expected $expectedRealm, got $realm")
                    return false
                }
                val generationId = bb.long
                val activeGen = VaultGenerationManager.getActiveGeneration(context, isDecoy)
                if (generationId != activeGen) {
                    Log.e(TAG, "Sentinel generation mismatch: expected $activeGen, got $generationId. Rollback attempt detected.")
                    return false
                }
                val ivLen = bb.get().toInt() and 0xFF
                if (ivLen != IV_SIZE_BYTES) return false
                val iv = ByteArray(IV_SIZE_BYTES)
                bb.get(iv)
                val cipherLen = bb.short.toInt() and 0xFFFF
                val ciphertext = ByteArray(cipherLen)
                bb.get(ciphertext)

                val key = deriveSentinelKey(vrk, isDecoy, generationId)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

                val aadPrefix = if (isDecoy) DECOY_SENTINEL_AAD_PREFIX else REAL_SENTINEL_AAD_PREFIX
                val aad = ByteBuffer.allocate(aadPrefix.size + 8).put(aadPrefix).putLong(generationId).array()
                cipher.updateAAD(aad)

                val decrypted = cipher.doFinal(ciphertext)
                val expectedPlaintext = if (isDecoy) DECOY_SENTINEL_PLAINTEXT else REAL_SENTINEL_PLAINTEXT
                val matches = decrypted.contentEquals(expectedPlaintext)
                decrypted.fill(0)
                return matches
            }

            // 2. Legacy V1 Sentinel Format Fallback
            val ivLen = bytes[0].toInt() and 0xFF
            if (ivLen != IV_SIZE_BYTES || bytes.size <= 1 + ivLen) return false

            val iv = bytes.copyOfRange(1, 1 + ivLen)
            val ciphertext = bytes.copyOfRange(1 + ivLen, bytes.size)

            val legacyKey = deriveLegacySentinelKey(vrk, isDecoy)
            val legacyCipher = Cipher.getInstance("AES/GCM/NoPadding")
            legacyCipher.init(Cipher.DECRYPT_MODE, legacyKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            legacyCipher.updateAAD(if (isDecoy) LEGACY_DECOY_AAD else LEGACY_REAL_AAD)

            val decrypted = legacyCipher.doFinal(ciphertext)
            val expectedPlaintext = if (isDecoy) LEGACY_DECOY_PLAINTEXT else LEGACY_REAL_PLAINTEXT
            val matches = decrypted.contentEquals(expectedPlaintext)
            decrypted.fill(0)
            matches
        } catch (e: Exception) {
            Log.w(TAG, "Sentinel verification failed (isDecoy=$isDecoy): ${e.message}")
            false
        }
    }

    /**
     * Removes the decoy sentinel when the decoy PIN is disabled.
     */
    fun removeDecoySentinel(context: Context) {
        try {
            File(context.filesDir, DECOY_SENTINEL_FILE).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing decoy sentinel", e)
        }
    }

    /**
     * Deletes all sentinels during self-destruct.
     */
    fun destroySentinels(context: Context): Boolean {
        var success = true
        try {
            val f1 = File(context.filesDir, REAL_SENTINEL_FILE)
            if (f1.exists()) success = success && f1.delete()
            val f2 = File(context.filesDir, DECOY_SENTINEL_FILE)
            if (f2.exists()) success = success && f2.delete()
        } catch (e: Exception) {
            success = false
        }
        return success
    }
}
