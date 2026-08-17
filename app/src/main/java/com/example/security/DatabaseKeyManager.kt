package com.example.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

object DatabaseKeyManager {
    private const val KS_ALIAS_DB = "SecureVaultDBKey"
    private const val PREF_NAME = "DBKeyPrefs"
    private const val PREF_WRAPPED_KEY = "wrapped_db_key"
    private const val PREF_WRAPPED_IV = "wrapped_db_iv"

    fun getDatabasePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val wrappedKeyHex = prefs.getString(PREF_WRAPPED_KEY, null)
        val wrappedIvHex = prefs.getString(PREF_WRAPPED_IV, null)

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        if (wrappedKeyHex == null || wrappedIvHex == null || !keyStore.containsAlias(KS_ALIAS_DB)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val builder = KeyGenParameterSpec.Builder(
                KS_ALIAS_DB,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    builder.setIsStrongBoxBacked(true)
                    keyGenerator.init(builder.build())
                    keyGenerator.generateKey()
                } catch (e: Exception) {
                    builder.setIsStrongBoxBacked(false)
                    keyGenerator.init(builder.build())
                    keyGenerator.generateKey()
                }
            } else {
                keyGenerator.init(builder.build())
                keyGenerator.generateKey()
            }

            val rawDbKey = ByteArray(32)
            SecureRandom().nextBytes(rawDbKey)

            val ksEntry = keyStore.getEntry(KS_ALIAS_DB, null) as KeyStore.SecretKeyEntry
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, ksEntry.secretKey)

            val iv = cipher.iv
            val cipherText = cipher.doFinal(rawDbKey)

            prefs.edit()
                .putString(PREF_WRAPPED_KEY, cipherText.joinToString("") { "%02x".format(it) })
                .putString(PREF_WRAPPED_IV, iv.joinToString("") { "%02x".format(it) })
                .apply()
                
            val directBuffer = java.nio.ByteBuffer.allocateDirect(32)
            directBuffer.put(rawDbKey)
            directBuffer.flip()
            NativeBridge.safeMlock(directBuffer)
            
            val pass = ByteArray(32)
            directBuffer.get(pass)
            return pass
        } else {
            val ksEntry = keyStore.getEntry(KS_ALIAS_DB, null) as KeyStore.SecretKeyEntry
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")

            val cipherText = wrappedKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val iv = wrappedIvHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

            cipher.init(Cipher.DECRYPT_MODE, ksEntry.secretKey, GCMParameterSpec(128, iv))
            val rawDbKey = cipher.doFinal(cipherText)
            
            val directBuffer = java.nio.ByteBuffer.allocateDirect(rawDbKey.size)
            directBuffer.put(rawDbKey)
            directBuffer.flip()
            NativeBridge.safeMlock(directBuffer)
            
            val pass = ByteArray(rawDbKey.size)
            directBuffer.get(pass)
            rawDbKey.fill(0)
            return pass
        }
    }
}
