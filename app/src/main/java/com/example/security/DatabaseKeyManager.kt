package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.util.VaultLogger
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object DatabaseKeyManager {
    private const val TAG = "DatabaseKeyManager"
    private const val PREF_NAME = "DBKeyPrefs"
    private const val PREF_KEY_ENCRYPTED = "encrypted_db_passphrase_b64"
    private const val PREF_KEY_IV = "db_passphrase_iv_b64"
    private const val KEYSTORE_ALIAS = "QuantumVaultDbKeyWrapMaster"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    @Volatile
    private var cachedPassphrase: ByteArray? = null

    @Synchronized
    fun getDatabasePassphrase(context: Context): ByteArray {
        cachedPassphrase?.let {
            return it.clone()
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val encryptedB64 = prefs.getString(PREF_KEY_ENCRYPTED, null)
        val ivB64 = prefs.getString(PREF_KEY_IV, null)

        if (!encryptedB64.isNullOrEmpty() && !ivB64.isNullOrEmpty()) {
            try {
                val encryptedBytes = Base64.decode(encryptedB64, Base64.NO_WRAP)
                val ivBytes = Base64.decode(ivB64, Base64.NO_WRAP)
                val secretKey = getOrCreateMasterKey()

                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val decryptedPass = cipher.doFinal(encryptedBytes)
                if (decryptedPass != null && decryptedPass.isNotEmpty()) {
                    cachedPassphrase = decryptedPass.clone()
                    VaultLogger.log(context, TAG, "Successfully unwrapped persistent database passphrase from Android Keystore")
                    return decryptedPass
                } else {
                    throw IllegalStateException("Decrypted database passphrase was empty")
                }
            } catch (e: Exception) {
                VaultLogger.logError(context, TAG, "Failed to unwrap persistent database encryption key from Android Keystore. Refusing to regenerate.", e)
                throw IllegalStateException("Database encryption key could not be unwrapped from Android Keystore: ${e.localizedMessage}. Cannot proceed to avoid data loss.", e)
            }
        }

        // Generate a new 32-byte cryptographically secure database passphrase (only on fresh vault creation)
        val rawPassphrase = ByteArray(32)
        SecureRandom().nextBytes(rawPassphrase)

        try {
            val secretKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(rawPassphrase)

            val editor = prefs.edit()
                .putString(PREF_KEY_ENCRYPTED, Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
                .putString(PREF_KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .remove("persistent_db_passphrase_hex") // Clean up any legacy plaintext
            
            val committed = editor.commit() // Synchronous commit to ensure disk persistence immediately
            if (!committed) {
                Log.w(TAG, "Shared preferences commit returned false, falling back to apply")
                editor.apply()
            }
            VaultLogger.log(context, TAG, "Generated and securely stored new 256-bit database encryption key in Android Keystore")
        } catch (e: Exception) {
            VaultLogger.logError(context, TAG, "Exception wrapping DB passphrase with Keystore", e)
            throw IllegalStateException("Failed to securely store database encryption key in Keystore: ${e.localizedMessage}", e)
        }

        cachedPassphrase = rawPassphrase.clone()
        return rawPassphrase
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (entry != null) {
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }
}

