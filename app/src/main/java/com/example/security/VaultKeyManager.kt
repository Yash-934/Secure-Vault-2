package com.example.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Authoritative Central Key Hierarchy & Lifecycle Manager.
 *
 * Implements the Vault Root Key (VRK) architecture.
 */
object VaultKeyManager {
    private const val TAG = "VaultKeyManager"
    const val ANDROID_KEYSTORE = "AndroidKeyStore"

    const val ALIAS_BIOMETRIC_UNLOCK = "QuantumVaultBiometricUnlockMasterKey"
    const val ALIAS_DEVICE_BINDING = "VaultBackupDeviceBindingHardwareKey"
    const val ALIAS_DEX_PROTECTION = "SecureVaultDexKey"
    const val ALIAS_ATTESTATION = "SecureVaultHardwareAttestationKey_v2"

    val ALL_KEY_ALIASES = listOf(
        ALIAS_BIOMETRIC_UNLOCK,
        ALIAS_DEVICE_BINDING,
        ALIAS_DEX_PROTECTION,
        ALIAS_ATTESTATION
    )

    private val jvmFallbackKeys = mutableMapOf<String, SecretKey>()

    private val keyStore: KeyStore? = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load AndroidKeyStore", e)
        null
    }

    private var activeVrk: ByteArray? = null
    private var isDecoyMode = false

    private const val VRK_PIN_WRAP_FILE = "vrk_pin_wrap.bin"
    private const val DECOY_VRK_PIN_WRAP_FILE = "decoy_vrk_pin_wrap.bin"
    private const val BIOMETRIC_WRAP_FILE = "biometric_wrap.bin"
    private const val KEK_SALT_FILE = "kek_salt.bin"
    
    fun hasBiometricEnvelope(context: Context): Boolean {
        return File(context.filesDir, BIOMETRIC_WRAP_FILE).exists()
    }

    fun isSessionAuthorized(): Boolean = activeVrk != null

    fun clearAuthorizedSessionKey() {
        activeVrk?.fill(0)
        activeVrk = null
        isDecoyMode = false
    }

    private fun deriveKey(domain: String): SecretKey {
        val vrk = activeVrk ?: throw IllegalStateException("Vault is locked, cannot derive key for $domain")
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(vrk)
        val keyBytes = md.digest(domain.toByteArray(Charsets.UTF_8))
        return javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
    }

    fun getVaultMasterKey(): SecretKey = deriveKey("file_encryption_context")
    fun getPasswordMasterKey(): SecretKey = deriveKey("password_manager_context")
    fun getDatabaseWrapKey(isDecoy: Boolean = false): SecretKey {
        val context = if (isDecoy) "database_decoy_context" else "database_real_context"
        return deriveKey(context)
    }

    private fun derivePinKek(pin: String, salt: ByteArray): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 12000, 256)
        val skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }

    fun initializeVrkWithPin(context: Context, pin: String, isDecoy: Boolean = false) {
        val vrk = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        
        val saltFile = File(context.filesDir, KEK_SALT_FILE)
        val salt = if (saltFile.exists()) {
            saltFile.readBytes()
        } else {
            ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }.also {
                saltFile.writeBytes(it)
            }
        }
        
        val kek = derivePinKek(pin, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(kek, "AES"))
        
        val iv = cipher.iv
        val encryptedVrk = cipher.doFinal(vrk)
        
        val wrapFile = File(context.filesDir, if (isDecoy) DECOY_VRK_PIN_WRAP_FILE else VRK_PIN_WRAP_FILE)
        wrapFile.outputStream().use { fos ->
            fos.write(iv.size)
            fos.write(iv)
            fos.write(encryptedVrk)
        }
    }

    fun authorizeWithPin(context: Context, pin: String, isDecoy: Boolean = false): Boolean {
        val saltFile = File(context.filesDir, KEK_SALT_FILE)
        if (!saltFile.exists()) return false
        val salt = saltFile.readBytes()
        val kek = derivePinKek(pin, salt)

        val wrapFile = File(context.filesDir, if (isDecoy) DECOY_VRK_PIN_WRAP_FILE else VRK_PIN_WRAP_FILE)
        if (!wrapFile.exists()) return false
        
        return try {
            val bytes = wrapFile.readBytes()
            if (bytes.isEmpty()) return false
            val ivLen = bytes[0].toInt() and 0xFF
            if (bytes.size < 1 + ivLen) return false
            val iv = bytes.copyOfRange(1, 1 + ivLen)
            val ciphertext = bytes.copyOfRange(1 + ivLen, bytes.size)
            
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(kek, "AES"), GCMParameterSpec(128, iv))
            
            val vrk = cipher.doFinal(ciphertext)
            if (vrk.size != 32) return false
            activeVrk = vrk
            isDecoyMode = isDecoy
            true
        } catch (e: Exception) {
            Log.e(TAG, "PIN unwrap failed", e)
            false
        }
    }

    fun getBiometricEnrollCryptoObject(context: Context): androidx.biometric.BiometricPrompt.CryptoObject? {
        if (activeVrk == null) return null // Must be authorized via PIN first
        return try {
            val biometricKey = getOrCreateBiometricMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, biometricKey)
            androidx.biometric.BiometricPrompt.CryptoObject(cipher)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize biometric enroll cipher", e)
            null
        }
    }

    fun provisionBiometricEnvelope(context: Context, authenticatedCipher: Cipher): Boolean {
        val vrk = activeVrk ?: return false
        return try {
            val encryptedVrk = authenticatedCipher.doFinal(vrk)
            val iv = authenticatedCipher.iv
            
            val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
            file.outputStream().use { fos ->
                fos.write(iv.size)
                fos.write(iv)
                fos.write(encryptedVrk)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to provision biometric envelope", e)
            false
        }
    }

    fun getBiometricDecryptCryptoObject(context: Context): androidx.biometric.BiometricPrompt.CryptoObject? {
        val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
        if (!file.exists()) return null

        return try {
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return null
            val ivLen = bytes[0].toInt() and 0xFF
            if (bytes.size < 1 + ivLen) return null
            val iv = bytes.copyOfRange(1, 1 + ivLen)

            val biometricKey = getOrCreateBiometricMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, biometricKey, GCMParameterSpec(128, iv))
            androidx.biometric.BiometricPrompt.CryptoObject(cipher)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize biometric decrypt cipher", e)
            null
        }
    }

    fun unwrapBiometricSessionKey(context: Context, authenticatedCipher: Cipher): Boolean {
        return try {
            val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
            if (!file.exists()) return false
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return false
            val ivLen = bytes[0].toInt() and 0xFF
            if (bytes.size < 1 + ivLen) return false
            val ciphertext = bytes.copyOfRange(1 + ivLen, bytes.size)

            val unwrappedBytes = authenticatedCipher.doFinal(ciphertext)
            if (unwrappedBytes != null && unwrappedBytes.size == 32) {
                activeVrk = unwrappedBytes
                isDecoyMode = false
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unwrap biometric key: ${e.message}", e)
            false
        }
    }

    fun removeBiometricEnvelope(context: Context) {
        val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
        if (file.exists()) file.delete()
    }

    private fun isRunningInTestEnvironment(): Boolean {
        return try {
            Class.forName("org.junit.Test") != null || android.os.Build.FINGERPRINT.lowercase(java.util.Locale.US).contains("robolectric")
        } catch (e: Exception) {
            false
        }
    }

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
                Log.e(TAG, "Keystore unavailable or exception for alias $alias: ${e.message}")
                if (!isRunningInTestEnvironment()) {
                    throw IllegalStateException("Critical KeyStore failure for alias $alias. App cannot proceed.", e)
                }
            }
        }
        if (!isRunningInTestEnvironment()) {
            throw IllegalStateException("Keystore is null in production environment. Failing securely.")
        }
        // JVM Test fallback
        return jvmFallbackKeys.getOrPut(alias) {
            val kg = KeyGenerator.getInstance("AES")
            kg.init(256)
            kg.generateKey()
        }
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
                        KeyProperties.AUTH_BIOMETRIC_STRONG
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setUserAuthenticationValidityDurationSeconds(-1)
                }
                keyGenerator.init(builder.build())
                return keyGenerator.generateKey()
            } catch (e: Exception) {
                Log.e(TAG, "Biometric key generation failed on this hardware: ${e.message}", e)
                if (!isRunningInTestEnvironment()) {
                    throw IllegalStateException("Critical KeyStore failure for biometric key. App cannot proceed.", e)
                }
            }
        }
        if (!isRunningInTestEnvironment()) {
            throw IllegalStateException("Keystore is null in production environment. Failing securely.")
        }
        return getOrCreateKey(ALIAS_BIOMETRIC_UNLOCK)
    }

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
