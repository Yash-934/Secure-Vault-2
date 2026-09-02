package com.example.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.util.VaultLogger
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

object DatabaseKeyManager {
    private const val TAG = "DatabaseKeyManager"
    private const val PREF_NAME = "DBKeyPrefs"
    private const val PREF_KEY_ENCRYPTED_REAL = "encrypted_db_passphrase_b64"
    private const val PREF_KEY_IV_REAL = "db_passphrase_iv_b64"
    private const val PREF_KEY_ENCRYPTED_DECOY = "encrypted_decoy_db_passphrase_b64"
    private const val PREF_KEY_IV_DECOY = "decoy_db_passphrase_iv_b64"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    @Volatile
    private var cachedRealPassphrase: ByteArray? = null

    @Volatile
    private var cachedDecoyPassphrase: ByteArray? = null

    @Synchronized
    fun getDatabasePassphrase(context: Context, isDecoy: Boolean = false): ByteArray {
        val cached = if (isDecoy) cachedDecoyPassphrase else cachedRealPassphrase
        if (cached != null) {
            return cached.clone()
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val prefKeyEncrypted = if (isDecoy) PREF_KEY_ENCRYPTED_DECOY else PREF_KEY_ENCRYPTED_REAL
        val prefKeyIv = if (isDecoy) PREF_KEY_IV_DECOY else PREF_KEY_IV_REAL

        val encryptedB64 = prefs.getString(prefKeyEncrypted, null)
        val ivB64 = prefs.getString(prefKeyIv, null)

        if (!encryptedB64.isNullOrEmpty() && !ivB64.isNullOrEmpty()) {
            try {
                val encryptedBytes = Base64.decode(encryptedB64, Base64.NO_WRAP)
                val ivBytes = Base64.decode(ivB64, Base64.NO_WRAP)
                val secretKey = VaultKeyManager.getDatabaseWrapKey(isDecoy)

                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val decryptedPass = cipher.doFinal(encryptedBytes)
                if (decryptedPass != null && decryptedPass.isNotEmpty()) {
                    if (isDecoy) {
                        cachedDecoyPassphrase = decryptedPass.clone()
                    } else {
                        cachedRealPassphrase = decryptedPass.clone()
                    }
                    VaultLogger.log(context, TAG, "Successfully unwrapped persistent database passphrase (decoy=$isDecoy) from Android Keystore")
                    return decryptedPass
                } else {
                    throw IllegalStateException("Decrypted database passphrase was empty")
                }
            } catch (e: Exception) {
                VaultLogger.logError(context, TAG, "Failed to unwrap persistent database encryption key (decoy=$isDecoy) from Android Keystore.", e)
                throw IllegalStateException("Database encryption key (decoy=$isDecoy) could not be unwrapped: ${e.localizedMessage}", e)
            }
        }

        // Generate a new 32-byte cryptographically secure database passphrase (only on fresh vault creation)
        val rawPassphrase = ByteArray(32)
        SecureRandom().nextBytes(rawPassphrase)

        try {
            val secretKey = VaultKeyManager.getDatabaseWrapKey(isDecoy)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(rawPassphrase)

            val editor = prefs.edit()
                .putString(prefKeyEncrypted, Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
                .putString(prefKeyIv, Base64.encodeToString(iv, Base64.NO_WRAP))
                .remove("persistent_db_passphrase_hex") // Clean up any legacy plaintext
            
            val committed = editor.commit()
            if (!committed) {
                Log.w(TAG, "Shared preferences commit returned false, falling back to apply")
                editor.apply()
            }
            VaultLogger.log(context, TAG, "Generated and securely stored new 256-bit database encryption key (decoy=$isDecoy) in Android Keystore")
        } catch (e: Exception) {
            VaultLogger.logError(context, TAG, "Exception wrapping DB passphrase (decoy=$isDecoy) with Keystore", e)
            throw IllegalStateException("Failed to securely store database encryption key in Keystore: ${e.localizedMessage}", e)
        }

        if (isDecoy) {
            cachedDecoyPassphrase = rawPassphrase.clone()
        } else {
            cachedRealPassphrase = rawPassphrase.clone()
        }
        return rawPassphrase
    }
}

