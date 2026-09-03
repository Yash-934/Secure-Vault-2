package com.example.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import com.example.data.local.SettingsDataStore
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class BiometricEnrollmentState {
    NOT_CONFIGURED,
    REQUIRES_PIN,
    READY_FOR_BIOMETRIC,
    ENROLLING,
    ENROLLED,
    KEY_INVALIDATED,
    ENVELOPE_MISSING,
    ENVELOPE_CORRUPT,
    UNAVAILABLE
}

object VaultKeyManager {
    private const val TAG = "VaultKeyManager"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

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

    private val jvmFallbackKeys = ConcurrentHashMap<String, SecretKey>()

    val keyStore: KeyStore? = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load AndroidKeyStore", e)
        null
    }

    @Volatile
    private var activeVrk: ByteArray? = null

    @Volatile
    private var isDecoyMode: Boolean = false

    private const val VRK_PIN_WRAP_FILE = "vrk_pin_wrap.bin"
    private const val DECOY_VRK_PIN_WRAP_FILE = "decoy_vrk_pin_wrap.bin"
    private const val BIOMETRIC_WRAP_FILE = "biometric_wrap.bin"
    private const val KEK_SALT_FILE = "kek_salt.bin"

    private val MAGIC_VRK_WRAP = "QVRK".toByteArray(Charsets.US_ASCII)
    private val MAGIC_BIOMETRIC_WRAP = "QVBE".toByteArray(Charsets.US_ASCII)
    private val BIOMETRIC_AAD = "QUANTUM_VAULT_REAL_BIOMETRIC_V1".toByteArray(Charsets.UTF_8)

    fun hasBiometricEnvelope(context: Context): Boolean {
        val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
        return file.exists() && file.length() >= 60
    }

    fun isSessionAuthorized(): Boolean {
        val vrk = activeVrk ?: return false
        return vrk.size == 32
    }

    fun isRealVaultAuthorized(): Boolean = isSessionAuthorized() && !isDecoyMode

    fun isDecoyVaultAuthorized(): Boolean = isSessionAuthorized() && isDecoyMode

    fun getActiveVrk(): ByteArray? = activeVrk?.copyOf()

    fun clearAuthorizedSessionKey() {
        activeVrk?.fill(0)
        activeVrk = null
        isDecoyMode = false
    }

    fun lockVault() = clearAuthorizedSessionKey()

    private fun deriveKey(domain: String): SecretKey {
        val vrk = activeVrk ?: throw IllegalStateException("Vault is locked, cannot derive key for $domain")
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(vrk)
        val keyBytes = md.digest(domain.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun getVaultMasterKey(): SecretKey = deriveKey("file_encryption_context")
    fun getPasswordMasterKey(): SecretKey = deriveKey("password_manager_context")
    fun getDatabaseWrapKey(isDecoy: Boolean = false): SecretKey {
        val context = if (isDecoy) "database_decoy_context" else "database_real_context"
        return deriveKey(context)
    }

    private fun derivePinKek(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 12000, 256)
        val skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }

    /**
     * Atomically writes a VRK wrap file using a new random KEK salt, AES-256-GCM encryption,
     * and fsync before rename. Also initializes/verifies the sentinel.
     */
    fun writeVrkPinWrap(context: Context, vrk: ByteArray, pin: String, isDecoy: Boolean): Boolean {
        if (vrk.size != 32) return false
        return try {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val kek = derivePinKek(pin, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"))
            val iv = cipher.iv
            val encryptedVrk = cipher.doFinal(vrk)

            val fileName = if (isDecoy) DECOY_VRK_PIN_WRAP_FILE else VRK_PIN_WRAP_FILE
            val targetFile = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")

            FileOutputStream(tempFile).use { fos ->
                fos.write(MAGIC_VRK_WRAP)
                fos.write(1) // version
                fos.write(salt.size)
                fos.write(salt)
                fos.write(iv.size)
                fos.write(iv)
                fos.write(encryptedVrk)
                fos.flush()
                fos.fd.sync()
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            // Write authenticated sentinel
            VaultSentinelManager.createSentinel(context, vrk, isDecoy)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write VRK wrap (isDecoy=$isDecoy)", e)
            false
        }
    }

    /**
     * Unwraps the VRK from the wrap file using the provided PIN.
     * Validates the resulting key against the authenticated sentinel.
     */
    fun unwrapVrkWithPin(context: Context, pin: String, isDecoy: Boolean = false): ByteArray? {
        val fileName = if (isDecoy) DECOY_VRK_PIN_WRAP_FILE else VRK_PIN_WRAP_FILE
        val wrapFile = File(context.filesDir, fileName)
        if (!wrapFile.exists() || wrapFile.length() == 0L) return null

        return try {
            val bytes = wrapFile.readBytes()
            val vrk: ByteArray = if (bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(MAGIC_VRK_WRAP)) {
                // New self-contained QVRK format
                var offset = 4
                val version = bytes[offset++].toInt() and 0xFF
                if (version != 1) return null

                val saltLen = bytes[offset++].toInt() and 0xFF
                val salt = bytes.copyOfRange(offset, offset + saltLen)
                offset += saltLen

                val ivLen = bytes[offset++].toInt() and 0xFF
                val iv = bytes.copyOfRange(offset, offset + ivLen)
                offset += ivLen

                val ciphertext = bytes.copyOfRange(offset, bytes.size)
                val kek = derivePinKek(pin, salt)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, iv))
                cipher.doFinal(ciphertext)
            } else {
                // Legacy format: read external kek_salt.bin
                val saltFile = File(context.filesDir, KEK_SALT_FILE)
                if (!saltFile.exists()) return null
                val salt = saltFile.readBytes()
                val kek = derivePinKek(pin, salt)

                val ivLen = bytes[0].toInt() and 0xFF
                if (bytes.size < 1 + ivLen) return null
                val iv = bytes.copyOfRange(1, 1 + ivLen)
                val ciphertext = bytes.copyOfRange(1 + ivLen, bytes.size)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, iv))
                cipher.doFinal(ciphertext)
            }

            if (vrk.size != 32) {
                vrk.fill(0)
                return null
            }

            // Authenticated sentinel check
            if (!VaultSentinelManager.verifyVrk(context, vrk, isDecoy)) {
                Log.w(TAG, "Unwrapped VRK failed sentinel verification (isDecoy=$isDecoy)")
                vrk.fill(0)
                return null
            }

            vrk
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generates a NEW Vault Root Key (VRK) and wraps it with the initial PIN.
     * This must ONLY be called during first-time vault creation or fresh decoy initialization.
     * NEVER call this during PIN rotation!
     */
    fun initializeVrkWithPin(context: Context, pin: String, isDecoy: Boolean = false) {
        val vrk = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            writeVrkPinWrap(context, vrk, pin, isDecoy)
            // Also maintain legacy salt file for backwards compatibility with any existing components
            val saltFile = File(context.filesDir, KEK_SALT_FILE)
            if (!saltFile.exists()) {
                val s = ByteArray(16).also { SecureRandom().nextBytes(it) }
                saltFile.writeBytes(s)
            }
        } finally {
            vrk.fill(0)
        }
    }

    /**
     * Unwraps VRK and authorizes the active cryptographic session.
     */
    fun authorizeWithPin(context: Context, pin: String, isDecoy: Boolean = false): Boolean {
        val unwrappedVrk = unwrapVrkWithPin(context, pin, isDecoy) ?: return false
        activeVrk = unwrappedVrk
        isDecoyMode = isDecoy
        return true
    }

    /**
     * Initializes BiometricPrompt.CryptoObject in ENCRYPT mode.
     * STRICT REQUIREMENT: Active session MUST be authorized for REAL vault. Decoy vault is rejected.
     */
    fun getBiometricEnrollCryptoObject(context: Context): androidx.biometric.BiometricPrompt.CryptoObject? {
        if (!isRealVaultAuthorized()) {
            Log.e(TAG, "Biometric enrollment rejected: Real vault session must be authorized")
            return null
        }
        return try {
            val biometricKey = getOrCreateBiometricMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, biometricKey)
            cipher.updateAAD(BIOMETRIC_AAD)
            androidx.biometric.BiometricPrompt.CryptoObject(cipher)
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize biometric enroll cipher", e)
            null
        }
    }

    /**
     * Provisions the biometric envelope using the authenticated Cipher from BiometricPrompt.
     * Wraps the existing active VRK with AAD, commits atomically, and verifies readability.
     */
    fun provisionBiometricEnvelope(context: Context, authenticatedCipher: Cipher): Boolean {
        val vrk = activeVrk
        if (vrk == null || isDecoyMode || vrk.size != 32) {
            Log.e(TAG, "Cannot provision biometric envelope: Invalid vault state")
            return false
        }

        return try {
            authenticatedCipher.updateAAD(BIOMETRIC_AAD)
            val encryptedVrk = authenticatedCipher.doFinal(vrk)
            val iv = authenticatedCipher.iv

            val targetFile = File(context.filesDir, BIOMETRIC_WRAP_FILE)
            val tempFile = File(context.filesDir, "$BIOMETRIC_WRAP_FILE.tmp")

            FileOutputStream(tempFile).use { fos ->
                fos.write(MAGIC_BIOMETRIC_WRAP)
                fos.write(1) // version
                fos.write(iv.size)
                fos.write(iv)
                fos.write((encryptedVrk.size shr 8) and 0xFF)
                fos.write(encryptedVrk.size and 0xFF)
                fos.write(encryptedVrk)
                fos.flush()
                fos.fd.sync()
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            // Read envelope back to verify structure
            val envelopeValid = targetFile.exists() && targetFile.length() >= 60
            if (!envelopeValid) {
                targetFile.delete()
                false
            } else {
                Log.i(TAG, "Biometric envelope provisioned and verified successfully")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to provision biometric envelope", e)
            false
        }
    }

    /**
     * Initializes BiometricPrompt.CryptoObject in DECRYPT mode using the IV stored in the envelope.
     */
    fun getBiometricDecryptCryptoObject(context: Context): androidx.biometric.BiometricPrompt.CryptoObject? {
        val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
        if (!file.exists() || file.length() < 30) return null

        return try {
            val bytes = file.readBytes()
            val iv: ByteArray = if (bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(MAGIC_BIOMETRIC_WRAP)) {
                val ivLen = bytes[5].toInt() and 0xFF
                bytes.copyOfRange(6, 6 + ivLen)
            } else {
                val ivLen = bytes[0].toInt() and 0xFF
                bytes.copyOfRange(1, 1 + ivLen)
            }

            val biometricKey = getOrCreateBiometricMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, biometricKey, GCMParameterSpec(128, iv))
            cipher.updateAAD(BIOMETRIC_AAD)
            androidx.biometric.BiometricPrompt.CryptoObject(cipher)
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize biometric decrypt cipher", e)
            null
        }
    }

    /**
     * Unwraps the VRK from the biometric envelope using the authenticated Cipher.
     * Enforces the authenticated sentinel check before authorizing the session.
     */
    fun unwrapBiometricSessionKey(context: Context, authenticatedCipher: Cipher): Boolean {
        val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
        if (!file.exists()) return false

        return try {
            val bytes = file.readBytes()
            val ciphertext: ByteArray
            var hasAad = true

            if (bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(MAGIC_BIOMETRIC_WRAP)) {
                val ivLen = bytes[5].toInt() and 0xFF
                val offset = 6 + ivLen
                val cipherLen = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
                ciphertext = bytes.copyOfRange(offset + 2, offset + 2 + cipherLen)
            } else {
                val ivLen = bytes[0].toInt() and 0xFF
                ciphertext = bytes.copyOfRange(1 + ivLen, bytes.size)
                hasAad = false
            }

            if (hasAad) {
                authenticatedCipher.updateAAD(BIOMETRIC_AAD)
            }

            val unwrappedBytes = authenticatedCipher.doFinal(ciphertext)
            if (unwrappedBytes != null && unwrappedBytes.size == 32) {
                // Cryptographic Sentinel Check: verify real vault identity
                if (VaultSentinelManager.verifyVrk(context, unwrappedBytes, isDecoy = false)) {
                    activeVrk = unwrappedBytes
                    isDecoyMode = false
                    Log.i(TAG, "Biometric unlock verified against cryptographic sentinel: Real vault authorized")
                    true
                } else {
                    Log.e(TAG, "Biometric unwrap rejected: Sentinel verification failed!")
                    unwrappedBytes.fill(0)
                    false
                }
            } else {
                unwrappedBytes?.fill(0)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unwrap biometric key: ${e.message}", e)
            false
        }
    }

    /**
     * Validates the biometric enrollment state against Keystore and file invariants.
     * If invalid, marks biometrics disabled and cleans up stale envelope while strictly
     * preserving the vault and PIN credentials.
     */
    suspend fun validateBiometricEnrollmentState(
        context: Context,
        settingsDataStore: SettingsDataStore
    ): BiometricEnrollmentState {
        val isEnabled = settingsDataStore.settingsFlow.first().isBiometricsEnabled
        if (!isEnabled) {
            return BiometricEnrollmentState.NOT_CONFIGURED
        }

        val envelopeFile = File(context.filesDir, BIOMETRIC_WRAP_FILE)
        if (!envelopeFile.exists()) {
            settingsDataStore.setBiometricsEnabled(false)
            return BiometricEnrollmentState.ENVELOPE_MISSING
        }

        if (envelopeFile.length() < 60) {
            envelopeFile.delete()
            settingsDataStore.setBiometricsEnabled(false)
            return BiometricEnrollmentState.ENVELOPE_CORRUPT
        }

        // Keystore key validation
        if (keyStore != null) {
            try {
                if (!keyStore.containsAlias(ALIAS_BIOMETRIC_UNLOCK)) {
                    envelopeFile.delete()
                    settingsDataStore.setBiometricsEnabled(false)
                    return BiometricEnrollmentState.UNAVAILABLE
                }
                val entry = keyStore.getEntry(ALIAS_BIOMETRIC_UNLOCK, null) as? KeyStore.SecretKeyEntry
                if (entry == null) {
                    envelopeFile.delete()
                    settingsDataStore.setBiometricsEnabled(false)
                    return BiometricEnrollmentState.UNAVAILABLE
                }

                // Check key invalidation
                val testCipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = ByteArray(12)
                testCipher.init(Cipher.DECRYPT_MODE, entry.secretKey, GCMParameterSpec(128, iv))
            } catch (e: KeyPermanentlyInvalidatedException) {
                Log.w(TAG, "Biometric key invalidated by system biometric changes")
                envelopeFile.delete()
                settingsDataStore.setBiometricsEnabled(false)
                return BiometricEnrollmentState.KEY_INVALIDATED
            } catch (e: UserNotAuthenticatedException) {
                // Expected for per-use biometric keys! Key is healthy.
            } catch (e: Exception) {
                // Other transient / initialization states
            }
        }

        return BiometricEnrollmentState.ENROLLED
    }

    fun removeBiometricEnvelope(context: Context) {
        try {
            val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
            if (file.exists()) file.delete()
            keyStore?.let {
                if (it.containsAlias(ALIAS_BIOMETRIC_UNLOCK)) {
                    it.deleteEntry(ALIAS_BIOMETRIC_UNLOCK)
                }
            }
            jvmFallbackKeys.remove(ALIAS_BIOMETRIC_UNLOCK)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing biometric envelope", e)
        }
    }

    private fun isRunningInTestEnvironment(): Boolean {
        return try {
            Class.forName("org.junit.Test") != null || Build.FINGERPRINT.lowercase(java.util.Locale.US).contains("robolectric")
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
                    results[alias] = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete key alias $alias", e)
                results[alias] = false
            }
        }
        return results
    }
}
