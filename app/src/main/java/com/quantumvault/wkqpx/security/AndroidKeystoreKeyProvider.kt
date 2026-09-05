package com.quantumvault.wkqpx.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Production Android Keystore key provider.
 * Strictly hardware/Keystore backed — fails closed with no software fallback in production builds.
 * In debug/test environments (Robolectric/JVM unit tests), provides memory-isolated keys.
 */
class AndroidKeystoreKeyProvider : VaultKeyProvider {
    private val isTestEnv: Boolean = com.quantumvault.wkqpx.BuildConfig.DEBUG &&
            (Build.FINGERPRINT.lowercase(java.util.Locale.US).contains("robolectric") ||
             System.getProperty("java.vm.name")?.contains("Robolectric") == true ||
             System.getProperty("robolectric.logging.enabled") != null)

    private val testMemoryKeys = ConcurrentHashMap<String, SecretKey>()

    private val keyStore: KeyStore? by lazy {
        if (isTestEnv) null
        else {
            try {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load AndroidKeyStore in production", e)
                null
            }
        }
    }

    companion object {
        private const val TAG = "AndroidKeystoreProvider"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    override fun getKey(alias: String): SecretKey? {
        if (isTestEnv) {
            return testMemoryKeys[alias]
        }
        val ks = keyStore ?: return null
        return try {
            if (!ks.containsAlias(alias)) return null
            val entry = ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve Keystore key for alias $alias", e)
            null
        }
    }

    override fun getOrCreateKey(alias: String): SecretKey {
        if (isTestEnv) {
            return testMemoryKeys.getOrPut(alias) {
                val kg = KeyGenerator.getInstance("AES")
                kg.init(256, SecureRandom())
                kg.generateKey()
            }
        }
        val ks = keyStore ?: throw IllegalStateException("AndroidKeyStore unavailable in production environment.")
        try {
            val existing = getKey(alias)
            if (existing != null) return existing

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
            Log.e(TAG, "Production Keystore failure for alias $alias. Failing closed.", e)
            throw IllegalStateException("Critical KeyStore failure for alias $alias. App cannot proceed.", e)
        }
    }

    override fun createBiometricMasterKey(alias: String): SecretKey {
        if (isTestEnv) {
            val kg = KeyGenerator.getInstance("AES")
            kg.init(256, SecureRandom())
            val key = kg.generateKey()
            testMemoryKeys[alias] = key
            return key
        }
        val ks = keyStore ?: throw IllegalStateException("AndroidKeyStore unavailable in production environment.")
        try {
            if (ks.containsAlias(alias)) {
                ks.deleteEntry(alias)
            }
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val builder = KeyGenParameterSpec.Builder(
                alias,
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
                    KeyProperties.AUTH_BIOMETRIC_STRONG
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }
            keyGenerator.init(builder.build())
            return keyGenerator.generateKey()
        } catch (e: Exception) {
            Log.e(TAG, "Biometric key generation failed for alias $alias in AndroidKeyStore", e)
            throw IllegalStateException("Critical KeyStore failure for biometric key.", e)
        }
    }

    override fun deleteKey(alias: String): Boolean {
        if (isTestEnv) {
            testMemoryKeys.remove(alias)
            return true
        }
        val ks = keyStore ?: return true
        return try {
            if (ks.containsAlias(alias)) {
                ks.deleteEntry(alias)
            }
            !ks.containsAlias(alias)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete key alias $alias", e)
            false
        }
    }

    override fun destroyAllKeys(targetAliases: List<String>): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        if (isTestEnv) {
            targetAliases.forEach {
                testMemoryKeys.remove(it)
                results[it] = true
            }
            testMemoryKeys.clear()
            return results
        }
        val ks = keyStore
        if (ks == null) {
            targetAliases.forEach { results[it] = false }
            return results
        }
        val allAliases = (targetAliases + try { ks.aliases().toList() } catch (_: Exception) { emptyList() }).distinct()
        for (alias in allAliases) {
            results[alias] = deleteKey(alias)
        }
        return results
    }
}
