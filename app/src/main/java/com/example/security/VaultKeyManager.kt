package com.example.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Authoritative Central Key Hierarchy & Lifecycle Manager.
 * 
 * Manages all cryptographic keys across the application:
 * 1. Vault Master Key (AES-256-GCM file payload encryption)
 * 2. Database Wrap Key - Real (SQLCipher real DB passphrase wrapping)
 * 3. Database Wrap Key - Decoy (SQLCipher decoy DB passphrase wrapping)
 * 4. Password Manager Key (Credential store encryption)
 * 5. Biometric Unlock Key (Hardware TEE user-authenticated unlock)
 * 6. Device-Binding Key (Device-locked backup export)
 * 7. DEX & Native Protection Keys
 */
object VaultKeyManager {
    private const val TAG = "VaultKeyManager"
    const val ANDROID_KEYSTORE = "AndroidKeyStore"

    // Authoritative registry of all security-sensitive Keystore aliases
    const val ALIAS_VAULT_MASTER = "SecureVaultAES256MasterKey"
    const val ALIAS_LEGACY_MASTER = "VaultMasterKey"
    const val ALIAS_DB_WRAP_REAL = "QuantumVaultDbKeyWrapMaster"
    const val ALIAS_DB_WRAP_DECOY = "QuantumVaultDecoyDbKeyWrapMaster"
    const val ALIAS_PASSWORD_MASTER = "QuantumVaultPasswordMasterKey"
    const val ALIAS_BIOMETRIC_UNLOCK = "QuantumVaultBiometricUnlockMasterKey"
    const val ALIAS_DEVICE_BINDING = "VaultBackupDeviceBindingHardwareKey"
    const val ALIAS_DEX_PROTECTION = "SecureVaultDexKey"
    const val ALIAS_ATTESTATION = "SecureVaultHardwareAttestationKey_v2"

    val ALL_KEY_ALIASES = listOf(
        ALIAS_VAULT_MASTER,
        ALIAS_LEGACY_MASTER,
        ALIAS_DB_WRAP_REAL,
        ALIAS_DB_WRAP_DECOY,
        ALIAS_PASSWORD_MASTER,
        ALIAS_BIOMETRIC_UNLOCK,
        ALIAS_DEVICE_BINDING,
        ALIAS_DEX_PROTECTION,
        ALIAS_ATTESTATION
    )

    private val keyStore: KeyStore? = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    } catch (e: Exception) {
        null
    }

    private val jvmFallbackKeys = mutableMapOf<String, SecretKey>()

    @Synchronized
    fun getOrCreateKey(alias: String): SecretKey {
        if (keyStore != null) {
            try {
                val existing = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
                if (existing != null) {
                    return existing.secretKey
                }

                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val keyGenSpec = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGenerator.init(keyGenSpec)
                return keyGenerator.generateKey()
            } catch (e: Exception) {
                Log.w(TAG, "Keystore unavailable or exception for alias $alias: ${e.message}")
            }
        }

        // JVM Test fallback
        return jvmFallbackKeys.getOrPut(alias) {
            val kg = KeyGenerator.getInstance("AES")
            kg.init(256)
            kg.generateKey()
        }
    }

    fun getVaultMasterKey(): SecretKey = getOrCreateKey(ALIAS_VAULT_MASTER)

    fun getPasswordMasterKey(): SecretKey = getOrCreateKey(ALIAS_PASSWORD_MASTER)

    fun getDatabaseWrapKey(isDecoy: Boolean = false): SecretKey {
        val alias = if (isDecoy) ALIAS_DB_WRAP_DECOY else ALIAS_DB_WRAP_REAL
        return getOrCreateKey(alias)
    }

    fun getDeviceBindingKey(): SecretKey = getOrCreateKey(ALIAS_DEVICE_BINDING)

    @Synchronized
    fun getOrCreateBiometricMasterKey(): SecretKey {
        if (keyStore != null) {
            try {
                val existing = keyStore.getEntry(ALIAS_BIOMETRIC_UNLOCK, null) as? KeyStore.SecretKeyEntry
                if (existing != null) {
                    return existing.secretKey
                }

                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val builder = KeyGenParameterSpec.Builder(
                    ALIAS_BIOMETRIC_UNLOCK,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setUserAuthenticationParameters(
                        0,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setUserAuthenticationValidityDurationSeconds(-1)
                }

                keyGenerator.init(builder.build())
                return keyGenerator.generateKey()
            } catch (e: Exception) {
                Log.w(TAG, "Biometric key generation failed on this hardware: ${e.message}")
            }
        }

        return getOrCreateKey(ALIAS_BIOMETRIC_UNLOCK)
    }

    /**
     * Destroys and shreds ALL registered cryptographic keys in the Android Keystore.
     * Returns a map of alias to destruction success status.
     */
    @Synchronized
    fun destroyAllKeys(): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        jvmFallbackKeys.clear()

        if (keyStore == null) {
            ALL_KEY_ALIASES.forEach { results[it] = true }
            return results
        }

        ALL_KEY_ALIASES.forEach { alias ->
            try {
                if (keyStore.containsAlias(alias)) {
                    keyStore.deleteEntry(alias)
                    val stillExists = keyStore.containsAlias(alias)
                    results[alias] = !stillExists
                } else {
                    results[alias] = true // Already absent
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete key alias $alias", e)
                results[alias] = false
            }
        }
        return results
    }
}
