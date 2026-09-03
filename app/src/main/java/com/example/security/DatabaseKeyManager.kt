package com.example.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.util.VaultLogger
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class DatabaseCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

object DatabaseKeyManager {
    private const val TAG = "DatabaseKeyManager"
    private const val PREF_NAME = "DBKeyPrefs"

    // We store the encrypted passphrase and IV
    private const val PREF_KEY_ENCRYPTED_REAL = "encrypted_db_passphrase_b64"
    private const val PREF_KEY_IV_REAL = "db_passphrase_iv_b64"
    private const val PREF_KEY_ENCRYPTED_DECOY = "encrypted_decoy_db_passphrase_b64"
    private const val PREF_KEY_IV_DECOY = "decoy_db_passphrase_iv_b64"
    
    // P0-7: version and generation tracking
    private const val PREF_KEY_VERSION_REAL = "db_passphrase_version_real"
    private const val PREF_KEY_VERSION_DECOY = "db_passphrase_version_decoy"

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    @Volatile
    private var cachedRealPassphrase: ByteArray? = null
    @Volatile
    private var cachedDecoyPassphrase: ByteArray? = null

    @Synchronized
    fun verifyPassphraseAvailability(context: Context, isDecoy: Boolean = false): Boolean {
        try {
            getDatabasePassphrase(context, isDecoy)
            return true
        } catch (e: DatabaseCryptoException) {
            return false
        }
    }

    @Synchronized
    fun getDatabasePassphrase(context: Context, isDecoy: Boolean = false): ByteArray {
        if (!VaultKeyManager.isSessionAuthorized()) {
            throw DatabaseCryptoException("Cannot access Database Passphrase. Vault session is not authorized.")
        }
        
        val cached = if (isDecoy) cachedDecoyPassphrase else cachedRealPassphrase
        if (cached != null) {
            return cached.clone()
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val prefKeyEncrypted = if (isDecoy) PREF_KEY_ENCRYPTED_DECOY else PREF_KEY_ENCRYPTED_REAL
        val prefKeyIv = if (isDecoy) PREF_KEY_IV_DECOY else PREF_KEY_IV_REAL
        val prefKeyVersion = if (isDecoy) PREF_KEY_VERSION_DECOY else PREF_KEY_VERSION_REAL

        val encryptedB64 = prefs.getString(prefKeyEncrypted, null)
        val ivB64 = prefs.getString(prefKeyIv, null)
        val version = prefs.getInt(prefKeyVersion, 1) // default to 1 for older wrappers

        // P0-8: Add DB Key Wrap AAD
        val aad = if (isDecoy) "QUANTUM_VAULT_DB_DECOY_V2".toByteArray() else "QUANTUM_VAULT_DB_REAL_V2".toByteArray()

        if (!encryptedB64.isNullOrEmpty() && !ivB64.isNullOrEmpty()) {
            // P0-9: Strict DB wrapper parsing. Fail closed.
            try {
                val encryptedBytes = Base64.decode(encryptedB64, Base64.NO_WRAP)
                val ivBytes = Base64.decode(ivB64, Base64.NO_WRAP)
                val secretKey = VaultKeyManager.getDatabaseWrapKey(isDecoy)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                
                if (version >= 2) {
                    cipher.updateAAD(aad)
                }
                
                val decryptedPass = cipher.doFinal(encryptedBytes)
                
                if (decryptedPass != null && decryptedPass.isNotEmpty()) {
                    if (isDecoy) {
                        cachedDecoyPassphrase = decryptedPass.clone()
                    } else {
                        cachedRealPassphrase = decryptedPass.clone()
                    }
                    VaultLogger.log(context, TAG, "Successfully unwrapped persistent database passphrase (decoy=$isDecoy)")
                    return decryptedPass
                } else {
                    throw DatabaseCryptoException("Decrypted database passphrase was empty")
                }
            } catch (e: Exception) {
                // Legacy unwrap attempt
                VaultLogger.log(context, TAG, "V2 unwrap failed, attempting legacy Keystore migration (decoy=$isDecoy)")
                try {
                    val encryptedBytes = Base64.decode(encryptedB64, Base64.NO_WRAP)
                    val ivBytes = Base64.decode(ivB64, Base64.NO_WRAP)
                    val legacyKey = VaultKeyManager.getLegacyDatabaseWrapKey()
                    if (legacyKey != null) {
                        val legacyCipher = Cipher.getInstance(TRANSFORMATION)
                        legacyCipher.init(Cipher.DECRYPT_MODE, legacyKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes))
                        
                        val decryptedPass = legacyCipher.doFinal(encryptedBytes)
                        
                        // IF we successfully decrypt with legacy, we MUST re-wrap it with V2 and save!
                        VaultLogger.log(context, TAG, "Legacy unwrap SUCCESS! Migrating to V2 wrapper (decoy=$isDecoy)")
                        migrateToV2Wrapper(context, decryptedPass, isDecoy)
                        
                        if (isDecoy) {
                            cachedDecoyPassphrase = decryptedPass.clone()
                        } else {
                            cachedRealPassphrase = decryptedPass.clone()
                        }
                        return decryptedPass
                    } else {
                        throw Exception("No legacy key found")
                    }
                } catch (legacyE: Exception) {
                    VaultLogger.logError(context, TAG, "Legacy Keystore unwrap ALSO failed (decoy=$isDecoy)", legacyE)
                    throw DatabaseCryptoException("DATABASE_KEY_UNWRAP_FAILED", e)
                }
            }
        }

        // Generate a new 32-byte cryptographically secure database passphrase (only on fresh vault creation)
        val rawPassphrase = ByteArray(32)
        SecureRandom().nextBytes(rawPassphrase)
        migrateToV2Wrapper(context, rawPassphrase, isDecoy)
        
        if (isDecoy) {
            cachedDecoyPassphrase = rawPassphrase.clone()
        } else {
            cachedRealPassphrase = rawPassphrase.clone()
        }
        return rawPassphrase
    }
    
    private fun migrateToV2Wrapper(context: Context, rawPassphrase: ByteArray, isDecoy: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val prefKeyEncrypted = if (isDecoy) PREF_KEY_ENCRYPTED_DECOY else PREF_KEY_ENCRYPTED_REAL
        val prefKeyIv = if (isDecoy) PREF_KEY_IV_DECOY else PREF_KEY_IV_REAL
        val prefKeyVersion = if (isDecoy) PREF_KEY_VERSION_DECOY else PREF_KEY_VERSION_REAL
        val aad = if (isDecoy) "QUANTUM_VAULT_DB_DECOY_V2".toByteArray() else "QUANTUM_VAULT_DB_REAL_V2".toByteArray()

        try {
            val secretKey = VaultKeyManager.getDatabaseWrapKey(isDecoy)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            // P0-8: Store with V2 AAD
            cipher.updateAAD(aad)
            
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(rawPassphrase)
            val editor = prefs.edit()
                .putString(prefKeyEncrypted, Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
                .putString(prefKeyIv, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putInt(prefKeyVersion, 2)
                .remove("persistent_db_passphrase_hex") // Clean up any legacy plaintext
            
            val committed = editor.commit()
            if (!committed) {
                Log.w(TAG, "Shared preferences commit returned false, falling back to apply")
                editor.apply()
            }
            VaultLogger.log(context, TAG, "Generated and securely stored V2 database encryption wrapper (decoy=$isDecoy)")
        } catch (e: Exception) {
            VaultLogger.logError(context, TAG, "Exception wrapping DB passphrase (decoy=$isDecoy) with Keystore", e)
            throw DatabaseCryptoException("Failed to securely store database encryption key", e)
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
     * Wipes all cached database passphrases and deletes preferences.
     */
    @Synchronized
    fun destroyKeys(context: Context): Boolean {
        return try {
            cachedRealPassphrase?.fill(0)
            cachedRealPassphrase = null
            cachedDecoyPassphrase?.fill(0)
            cachedDecoyPassphrase = null
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to destroy database keys", e)
            false
        }
    }
}

