package com.quantumvault.wkqpx.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.quantumvault.wkqpx.util.VaultLogger
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class DatabaseCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

object DatabaseKeyManager {
    private const val TAG = "DatabaseKeyManager"
    private const val PREF_NAME = "DBKeyPrefs"

    // Legacy Preference Keys
    private const val PREF_KEY_ENCRYPTED_REAL = "encrypted_db_passphrase_b64"
    private const val PREF_KEY_IV_REAL = "db_passphrase_iv_b64"
    private const val PREF_KEY_ENCRYPTED_DECOY = "encrypted_decoy_db_passphrase_b64"
    private const val PREF_KEY_IV_DECOY = "decoy_db_passphrase_iv_b64"
    private const val PREF_KEY_VERSION_REAL = "db_passphrase_version_real"
    private const val PREF_KEY_VERSION_DECOY = "db_passphrase_version_decoy"

    // DBW2 Binary Wrapper Files
    const val DBW2_FILE_REAL = "db_wrapper_real.bin"
    const val DBW2_FILE_DECOY = "db_wrapper_decoy.bin"

    private val MAGIC_DBW2 = byteArrayOf(0x44, 0x42, 0x57, 0x32) // "DBW2"
    private const val DBW2_VERSION: Byte = 2
    private const val REALM_REAL: Byte = 1
    private const val REALM_DECOY: Byte = 2
    private const val EXPECTED_WRAPPER_SIZE = 77 // 4(magic) + 1(ver) + 1(realm) + 8(gen) + 1(ivLen) + 12(iv) + 2(cipherLen) + 48(cipher+tag)

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_SIZE_BYTES = 12
    private const val PASSPHRASE_LENGTH = 32

    @Volatile
    private var cachedRealPassphrase: ByteArray? = null
    @Volatile
    private var cachedDecoyPassphrase: ByteArray? = null

    fun buildAad(isDecoy: Boolean, generationId: Long): ByteArray {
        val realmAad = if (isDecoy) {
            "QUANTUM_VAULT_DB_DECOY_V2".toByteArray(Charsets.UTF_8)
        } else {
            "QUANTUM_VAULT_DB_REAL_V2".toByteArray(Charsets.UTF_8)
        }
        val bb = ByteBuffer.allocate(realmAad.size + 8)
        bb.put(realmAad)
        bb.putLong(generationId)
        return bb.array()
    }

    /**
     * Checks if security artifacts exist indicating an existing vault.
     */
    fun isExistingVault(context: Context, isDecoy: Boolean): Boolean {
        val dbName = if (isDecoy) "secure_vault_decoy_db" else "secure_vault_db"
        val dbFile = context.getDatabasePath(dbName)
        val wrapperFile = File(context.filesDir, if (isDecoy) DBW2_FILE_DECOY else DBW2_FILE_REAL)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val prefKey = if (isDecoy) PREF_KEY_ENCRYPTED_DECOY else PREF_KEY_ENCRYPTED_REAL
        return (dbFile.exists() && dbFile.length() > 0) || wrapperFile.exists() || prefs.contains(prefKey)
    }

    @Synchronized
    fun verifyPassphraseAvailability(context: Context, isDecoy: Boolean = false): Boolean {
        return try {
            getDatabasePassphrase(context, isDecoy)
            true
        } catch (e: DatabaseCryptoException) {
            false
        }
    }

    @Synchronized
    fun getDatabasePassphrase(context: Context, isDecoy: Boolean = false): ByteArray {
        // Enforce DB authorization isolation
        if (isDecoy) {
            if (!VaultKeyManager.isDecoyVaultAuthorized()) {
                throw DatabaseCryptoException("Access denied: Decoy vault session is not authorized.")
            }
        } else {
            if (!VaultKeyManager.isRealVaultAuthorized()) {
                throw DatabaseCryptoException("Access denied: Real vault session is not authorized.")
            }
        }

        val cached = if (isDecoy) cachedDecoyPassphrase else cachedRealPassphrase
        if (cached != null) {
            return cached.clone()
        }

        val wrapperFile = File(context.filesDir, if (isDecoy) DBW2_FILE_DECOY else DBW2_FILE_REAL)

        // 1. Attempt DBW2 binary format unwrap
        if (wrapperFile.exists()) {
            try {
                val pass = unwrapDbw2File(context, wrapperFile, isDecoy)
                if (isDecoy) {
                    cachedDecoyPassphrase = pass.clone()
                } else {
                    cachedRealPassphrase = pass.clone()
                }
                VaultLogger.log(context, TAG, "Successfully unwrapped DBW2 database passphrase (decoy=$isDecoy)")
                return pass
            } catch (e: Exception) {
                VaultLogger.logError(context, TAG, "DBW2 unwrap failed (decoy=$isDecoy)", e)
                throw DatabaseCryptoException("RECOVERY_REQUIRED: Corrupted DBW2 database wrapper. Refusing to regenerate DB secret.", e)
            }
        }

        // 2. Attempt legacy Shared Preferences migration to DBW2
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val prefKeyEncrypted = if (isDecoy) PREF_KEY_ENCRYPTED_DECOY else PREF_KEY_ENCRYPTED_REAL
        val prefKeyIv = if (isDecoy) PREF_KEY_IV_DECOY else PREF_KEY_IV_REAL
        val prefKeyVersion = if (isDecoy) PREF_KEY_VERSION_DECOY else PREF_KEY_VERSION_REAL

        val encryptedB64 = prefs.getString(prefKeyEncrypted, null)
        val ivB64 = prefs.getString(prefKeyIv, null)
        val version = prefs.getInt(prefKeyVersion, 1)

        if (!encryptedB64.isNullOrEmpty() && !ivB64.isNullOrEmpty()) {
            try {
                val encryptedBytes = Base64.decode(encryptedB64, Base64.NO_WRAP)
                val ivBytes = Base64.decode(ivB64, Base64.NO_WRAP)
                val secretKey = VaultKeyManager.getDatabaseWrapKey(isDecoy)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                if (version >= 2) {
                    val legacyAad = if (isDecoy) "QUANTUM_VAULT_DB_DECOY_V2".toByteArray() else "QUANTUM_VAULT_DB_REAL_V2".toByteArray()
                    cipher.updateAAD(legacyAad)
                }

                val decryptedPass = cipher.doFinal(encryptedBytes)
                if (decryptedPass != null && decryptedPass.size == PASSPHRASE_LENGTH) {
                    writeDbw2Wrapper(context, decryptedPass, isDecoy)
                    prefs.edit().remove(prefKeyEncrypted).remove(prefKeyIv).remove(prefKeyVersion).apply()
                    if (isDecoy) {
                        cachedDecoyPassphrase = decryptedPass.clone()
                    } else {
                        cachedRealPassphrase = decryptedPass.clone()
                    }
                    VaultLogger.log(context, TAG, "Migrated legacy preferences to DBW2 wrapper (decoy=$isDecoy)")
                    return decryptedPass
                } else {
                    throw DatabaseCryptoException("Decrypted legacy passphrase had invalid length")
                }
            } catch (e: Exception) {
                // Try legacy Keystore key
                try {
                    val encryptedBytes = Base64.decode(encryptedB64, Base64.NO_WRAP)
                    val ivBytes = Base64.decode(ivB64, Base64.NO_WRAP)
                    val legacyKey = VaultKeyManager.getLegacyDatabaseWrapKey()
                    if (legacyKey != null) {
                        val legacyCipher = Cipher.getInstance(TRANSFORMATION)
                        legacyCipher.init(Cipher.DECRYPT_MODE, legacyKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes))
                        val decryptedPass = legacyCipher.doFinal(encryptedBytes)
                        if (decryptedPass != null && decryptedPass.size == PASSPHRASE_LENGTH) {
                            writeDbw2Wrapper(context, decryptedPass, isDecoy)
                            prefs.edit().remove(prefKeyEncrypted).remove(prefKeyIv).remove(prefKeyVersion).apply()
                            if (isDecoy) {
                                cachedDecoyPassphrase = decryptedPass.clone()
                            } else {
                                cachedRealPassphrase = decryptedPass.clone()
                            }
                            VaultLogger.log(context, TAG, "Migrated legacy Keystore wrapper to DBW2 (decoy=$isDecoy)")
                            return decryptedPass
                        }
                    }
                } catch (legacyE: Exception) {
                    VaultLogger.logError(context, TAG, "Legacy Keystore fallback unwrap failed", legacyE)
                }
                throw DatabaseCryptoException("RECOVERY_REQUIRED: Existing database wrapper failed unwrap. Refusing to regenerate DB secret.", e)
            }
        }

        // 3. Fail-Closed Check: If this is an existing vault, NEVER generate a new random secret
        if (isExistingVault(context, isDecoy)) {
            throw DatabaseCryptoException("RECOVERY_REQUIRED: Existing vault database wrapper missing. Refusing to regenerate DB secret to prevent data loss.")
        }

        // 4. Fresh vault initialization: Generate new 32-byte secret and store as DBW2
        val rawPassphrase = ByteArray(PASSPHRASE_LENGTH)
        SecureRandom().nextBytes(rawPassphrase)
        writeDbw2Wrapper(context, rawPassphrase, isDecoy)

        if (isDecoy) {
            cachedDecoyPassphrase = rawPassphrase.clone()
        } else {
            cachedRealPassphrase = rawPassphrase.clone()
        }
        return rawPassphrase
    }

    private fun unwrapDbw2File(context: Context, file: File, isDecoy: Boolean): ByteArray {
        val bytes = file.readBytes()
        if (bytes.size != EXPECTED_WRAPPER_SIZE) {
            throw DatabaseCryptoException("DBW2 wrapper length corrupt: expected $EXPECTED_WRAPPER_SIZE, got ${bytes.size}")
        }

        val bb = ByteBuffer.wrap(bytes)
        val magic = ByteArray(4)
        bb.get(magic)
        if (!magic.contentEquals(MAGIC_DBW2)) {
            throw DatabaseCryptoException("DBW2 magic mismatch")
        }

        val version = bb.get()
        if (version != DBW2_VERSION) {
            throw DatabaseCryptoException("DBW2 unsupported version: $version")
        }

        val realm = bb.get()
        val expectedRealm = if (isDecoy) REALM_DECOY else REALM_REAL
        if (realm != expectedRealm) {
            throw DatabaseCryptoException("DBW2 wrong realm: expected $expectedRealm, got $realm")
        }

        val generationId = bb.long
        val ivLen = bb.get().toInt() and 0xFF
        if (ivLen != IV_SIZE_BYTES) {
            throw DatabaseCryptoException("DBW2 malformed IV length: $ivLen")
        }
        val iv = ByteArray(IV_SIZE_BYTES)
        bb.get(iv)

        val cipherLen = bb.short.toInt() and 0xFFFF
        if (cipherLen != (PASSPHRASE_LENGTH + 16)) { // 32 bytes + 16 bytes GCM tag
            throw DatabaseCryptoException("DBW2 invalid ciphertext length: $cipherLen")
        }
        val ciphertext = ByteArray(cipherLen)
        bb.get(ciphertext)

        if (bb.hasRemaining()) {
            throw DatabaseCryptoException("DBW2 unexpected trailing data")
        }

        val aad = buildAad(isDecoy, generationId)
        val secretKey = VaultKeyManager.getDatabaseWrapKey(isDecoy)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aad)

        val decrypted = cipher.doFinal(ciphertext)
        if (decrypted.size != PASSPHRASE_LENGTH) {
            decrypted.fill(0)
            throw DatabaseCryptoException("DBW2 decrypted passphrase length mismatch")
        }
        return decrypted
    }

    fun writeDbw2Wrapper(
        context: Context,
        rawPassphrase: ByteArray,
        isDecoy: Boolean,
        generationId: Long = 1L
    ) {
        if (rawPassphrase.size != PASSPHRASE_LENGTH) {
            throw IllegalArgumentException("Raw passphrase must be exactly $PASSPHRASE_LENGTH bytes")
        }

        try {
            val secretKey = VaultKeyManager.getDatabaseWrapKey(isDecoy)
            val iv = ByteArray(IV_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            val aad = buildAad(isDecoy, generationId)
            cipher.updateAAD(aad)

            val cipherBytes = cipher.doFinal(rawPassphrase)
            val expectedRealm = if (isDecoy) REALM_DECOY else REALM_REAL

            val buffer = ByteBuffer.allocate(EXPECTED_WRAPPER_SIZE)
            buffer.put(MAGIC_DBW2)
            buffer.put(DBW2_VERSION)
            buffer.put(expectedRealm)
            buffer.putLong(generationId)
            buffer.put(IV_SIZE_BYTES.toByte())
            buffer.put(iv)
            buffer.putShort(cipherBytes.size.toShort())
            buffer.put(cipherBytes)

            val fileName = if (isDecoy) DBW2_FILE_DECOY else DBW2_FILE_REAL
            val targetFile = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")

            FileOutputStream(tempFile).use { fos ->
                fos.write(buffer.array())
                fos.flush()
                fos.fd.sync()
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            VaultLogger.log(context, TAG, "Generated and securely stored DBW2 wrapper (decoy=$isDecoy)")
        } catch (e: Exception) {
            VaultLogger.logError(context, TAG, "Exception writing DBW2 wrapper (decoy=$isDecoy)", e)
            throw DatabaseCryptoException("Failed to securely write DBW2 database wrapper", e)
        }
    }

    /**
     * Wipes only the in-memory cached database passphrases (e.g. when locking vault).
     */
    @Synchronized
    fun clearMemory() {
        cachedRealPassphrase?.fill(0)
        cachedRealPassphrase = null
        cachedDecoyPassphrase?.fill(0)
        cachedDecoyPassphrase = null
    }

    /**
     * Wipes all cached database passphrases, deletes preferences, and deletes DBW2 files.
     */
    @Synchronized
    fun destroyKeys(context: Context): Boolean {
        return try {
            clearMemory()
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            File(context.filesDir, DBW2_FILE_REAL).delete()
            File(context.filesDir, DBW2_FILE_DECOY).delete()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to destroy database keys", e)
            false
        }
    }
}
