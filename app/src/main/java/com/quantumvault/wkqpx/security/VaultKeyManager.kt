package com.quantumvault.wkqpx.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import com.quantumvault.wkqpx.data.AppDatabase
import com.quantumvault.wkqpx.data.local.SettingsDataStore
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

enum class VaultState {
    UNINITIALIZED,
    INITIALIZING,
    INITIALIZED,
    AUTHORIZED_REAL,
    AUTHORIZED_DECOY,
    LOCKED,
    CORRUPTED,
    RECOVERY_REQUIRED,
    DESTROYED
}

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

    const val ALIAS_BIOMETRIC_UNLOCK = VaultKeyAliases.ALIAS_BIOMETRIC_UNLOCK
    const val ALIAS_BIOMETRIC_SLOT_A = VaultKeyAliases.ALIAS_BIOMETRIC_SLOT_A
    const val ALIAS_BIOMETRIC_SLOT_B = VaultKeyAliases.ALIAS_BIOMETRIC_SLOT_B
    const val ALIAS_BIOMETRIC_UNLOCK_PROVISIONAL = VaultKeyAliases.ALIAS_BIOMETRIC_UNLOCK_PROVISIONAL
    const val ALIAS_DEVICE_BINDING = VaultKeyAliases.ALIAS_DEVICE_BINDING
    const val ALIAS_DEX_PROTECTION = VaultKeyAliases.ALIAS_DEX_PROTECTION
    const val ALIAS_ATTESTATION = VaultKeyAliases.ALIAS_ATTESTATION
    const val ALIAS_LEGACY_MASTER = VaultKeyAliases.ALIAS_LEGACY_MASTER
    const val ALIAS_AUDIT_PROBE = VaultKeyAliases.ALIAS_AUDIT_PROBE
    const val ALIAS_DB_WRAPPER = VaultKeyAliases.ALIAS_DB_WRAPPER
    const val ALIAS_DB_WRAPPER_DECOY = VaultKeyAliases.ALIAS_DB_WRAPPER_DECOY

    val ALL_KEY_ALIASES = VaultKeyAliases.ALL_KNOWN_ALIASES

    private var keyProvider: VaultKeyProvider = AndroidKeystoreKeyProvider()

    @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.NONE)
    fun setKeyProviderForTesting(provider: VaultKeyProvider) {
        if (!com.quantumvault.wkqpx.BuildConfig.DEBUG) {
            throw SecurityException("Test KeyProvider injection is forbidden in release builds.")
        }
        keyProvider = provider
    }

    @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.NONE)
    fun resetKeyProviderForTesting() {
        keyProvider = AndroidKeystoreKeyProvider()
    }

    @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.NONE)
    fun setAuthorizedSessionForTesting(vrk: ByteArray, isDecoy: Boolean = false) {
        if (!com.quantumvault.wkqpx.BuildConfig.DEBUG) {
            throw SecurityException("Test session authorization injection is forbidden in release builds.")
        }
        activeVrk = vrk.clone()
        isDecoyMode = isDecoy
        currentState = if (isDecoy) VaultState.AUTHORIZED_DECOY else VaultState.AUTHORIZED_REAL
    }

    @Volatile
    private var currentState: VaultState = VaultState.LOCKED

    @Volatile
    private var activeVrk: ByteArray? = null

    @Volatile
    private var isDecoyMode: Boolean = false

    @Volatile
    private var provisionalTargetSlot: Long? = null

    private const val VRK_PIN_WRAP_FILE = "vrk_pin_wrap.bin"
    private const val DECOY_VRK_PIN_WRAP_FILE = "decoy_vrk_pin_wrap.bin"
    private const val BIOMETRIC_WRAP_FILE = "biometric_wrap.bin"
    private const val KEK_SALT_FILE = "kek_salt.bin"

    private val MAGIC_VRK_WRAP = "QVRK".toByteArray(Charsets.US_ASCII)
    private val MAGIC_BIOMETRIC_WRAP = byteArrayOf(0x42, 0x49, 0x45, 0x31) // "BIE1"
    private const val BIE1_VERSION: Byte = 1
    private const val BIE1_REALM_REAL: Byte = 1
    private const val BIE1_REALM_DECOY: Byte = 2
    private const val EXPECTED_BIE1_SIZE = 77 // 4(magic)+1(ver)+1(realm)+8(gen)+1(ivLen)+12(iv)+2(cipherLen)+48(cipher)

    // EXACT SINGLE AAD FOR BIOMETRIC ENVELOPE (Rule 3.3)
    val BIOMETRIC_AAD = "QUANTUM_VAULT_REAL_BIOMETRIC_V1".toByteArray(Charsets.UTF_8)

    enum class BiometricSlotState {
        SLOT_A,
        SLOT_B,
        CORRUPT,
        MISSING
    }

    fun getVaultState(): VaultState = currentState

    fun setVaultState(state: VaultState) {
        currentState = state
    }

    fun hasCredentialWrap(context: Context, isDecoy: Boolean = false): Boolean {
        val fileName = if (isDecoy) DECOY_VRK_PIN_WRAP_FILE else VRK_PIN_WRAP_FILE
        val file = File(context.filesDir, fileName)
        return file.exists() && file.length() > 0
    }

    fun hasBiometricEnvelope(context: Context): Boolean {
        val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
        return file.exists() && (file.length() == EXPECTED_BIE1_SIZE.toLong() || file.length() >= 60)
    }

    fun isSessionAuthorized(): Boolean {
        val vrk = activeVrk ?: return false
        return vrk.size == 32 && (currentState == VaultState.AUTHORIZED_REAL || currentState == VaultState.AUTHORIZED_DECOY)
    }

    fun isRealVaultAuthorized(): Boolean = isSessionAuthorized() && !isDecoyMode && currentState == VaultState.AUTHORIZED_REAL

    fun isDecoyVaultAuthorized(): Boolean = isSessionAuthorized() && isDecoyMode && currentState == VaultState.AUTHORIZED_DECOY

    fun getActiveVrk(): ByteArray? = activeVrk?.copyOf()

    fun clearAuthorizedSessionKey() {
        activeVrk?.fill(0)
        activeVrk = null
        isDecoyMode = false
        currentState = VaultState.LOCKED
    }

    fun lockVault() {
        clearAuthorizedSessionKey()
        AppDatabase.closeDatabases()
    }

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

    fun getLegacyDatabaseWrapKey(): SecretKey? {
        return try {
            keyProvider.getKey("SecureVaultAES256MasterKey")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get legacy database wrap key", e)
            null
        }
    }

    private fun derivePinKekStrictArgon2(pin: String, salt: ByteArray): ByteArray {
        // Memory-hard Argon2id KEK derivation (16 MiB RAM, 3 iterations) to resist GPU/FPGA PIN brute-forcing
        // Strict: Throws exception if Argon2 derivation fails, refusing silent downgrade to legacy KDF.
        return Argon2Kdf.deriveKey(
            password = pin.toCharArray(),
            salt = salt,
            memoryKb = 16 * 1024,
            iterations = 3,
            parallelism = 1,
            keyLengthBytes = 32
        ).encoded
    }

    private fun deriveLegacyPinKek(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 12000, 256)
        val skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }

    /**
     * Atomically writes a VRK wrap file using a new random KEK salt, AES-256-GCM encryption,
     * and fsync before rename. Strictly enforces Argon2id.
     */
    fun writeVrkPinWrap(context: Context, vrk: ByteArray, pin: String, isDecoy: Boolean): Boolean {
        if (vrk.size != 32) return false
        return try {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val kek = derivePinKekStrictArgon2(pin, salt)

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
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write VRK wrap with Argon2id (isDecoy=$isDecoy)", e)
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
                
                // Try primary Argon2id KEK first
                var decrypted: ByteArray? = try {
                    val kek = derivePinKekStrictArgon2(pin, salt)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, iv))
                    cipher.doFinal(ciphertext)
                } catch (_: Exception) {
                    null
                }

                // Fallback to legacy 12k PBKDF2 KEK for existing envelopes
                if (decrypted == null) {
                    try {
                        val legacyKek = deriveLegacyPinKek(pin, salt)
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(legacyKek, "AES"), GCMParameterSpec(128, iv))
                        decrypted = cipher.doFinal(ciphertext)
                        // Seamlessly upgrade existing envelope to Argon2id upon successful PIN verification
                        if (decrypted != null && decrypted.size == 32 && VaultSentinelManager.verifyVrk(context, decrypted, isDecoy)) {
                            writeVrkPinWrap(context, decrypted, pin, isDecoy)
                            File(context.filesDir, KEK_SALT_FILE).delete()
                        }
                    } catch (_: Exception) {
                        null
                    }
                }

                decrypted ?: return null
            } else {
                val saltFile = File(context.filesDir, KEK_SALT_FILE)
                if (!saltFile.exists()) return null
                val salt = saltFile.readBytes()

                val ivLen = bytes[0].toInt() and 0xFF
                if (bytes.size < 1 + ivLen) return null
                val iv = bytes.copyOfRange(1, 1 + ivLen)
                val ciphertext = bytes.copyOfRange(1 + ivLen, bytes.size)

                var decrypted: ByteArray? = try {
                    val kek = derivePinKekStrictArgon2(pin, salt)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, iv))
                    cipher.doFinal(ciphertext)
                } catch (_: Exception) {
                    null
                }

                if (decrypted == null) {
                    try {
                        val legacyKek = deriveLegacyPinKek(pin, salt)
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(legacyKek, "AES"), GCMParameterSpec(128, iv))
                        decrypted = cipher.doFinal(ciphertext)
                        if (decrypted != null && decrypted.size == 32 && VaultSentinelManager.verifyVrk(context, decrypted, isDecoy)) {
                            writeVrkPinWrap(context, decrypted, pin, isDecoy)
                            saltFile.delete()
                        }
                    } catch (_: Exception) {
                        null
                    }
                }

                decrypted ?: return null
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
     * STRICT INVARIANT: Only fresh vault creation with zero existing security artifacts may call this!
     */
    fun createVrkForFreshVault(context: Context, pin: String, isDecoy: Boolean = false) {
        val hasRealWrap = File(context.filesDir, VRK_PIN_WRAP_FILE).exists()
        val hasDecoyWrap = File(context.filesDir, DECOY_VRK_PIN_WRAP_FILE).exists()
        val hasSentinel = File(context.filesDir, "vault_sentinel.bin").exists()
        val hasDecoySentinel = File(context.filesDir, "decoy_vault_sentinel.bin").exists()
        val hasRealDb = context.getDatabasePath("secure_vault_db").exists()
        val hasDecoyDb = context.getDatabasePath("secure_vault_decoy_db").exists()
        val hasRealDbw2 = File(context.filesDir, DatabaseKeyManager.DBW2_FILE_REAL).exists()
        val hasDecoyDbw2 = File(context.filesDir, DatabaseKeyManager.DBW2_FILE_DECOY).exists()

        if (!isDecoy && (hasRealWrap || hasSentinel || hasRealDb || hasRealDbw2)) {
            currentState = VaultState.RECOVERY_REQUIRED
            throw IllegalStateException("Security artifacts already exist for real vault. Cannot recreate VRK.")
        }
        if (isDecoy && (hasDecoyWrap || hasDecoySentinel || hasDecoyDb || hasDecoyDbw2)) {
            currentState = VaultState.RECOVERY_REQUIRED
            throw IllegalStateException("Security artifacts already exist for decoy vault. Cannot recreate VRK.")
        }

        currentState = VaultState.INITIALIZING
        val vrk = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            VaultGenerationManager.persistGeneration(context, isDecoy, 1L)
            val written = writeVrkPinWrap(context, vrk, pin, isDecoy)
            if (!written) {
                currentState = VaultState.CORRUPTED
                throw IllegalStateException("Failed to write VRK pin wrap file")
            }
            VaultSentinelManager.createSentinel(context, vrk, isDecoy, generationId = 1L)
            currentState = VaultState.INITIALIZED
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
        currentState = if (isDecoy) VaultState.AUTHORIZED_DECOY else VaultState.AUTHORIZED_REAL
        return true
    }

    /**
     * Inspects active biometric envelope and returns its precise structural state.
     */
    fun getBiometricSlotState(context: Context): BiometricSlotState {
        val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
        if (!file.exists() || file.length() == 0L) return BiometricSlotState.MISSING
        if (file.length() != EXPECTED_BIE1_SIZE.toLong()) return BiometricSlotState.CORRUPT

        return try {
            val bytes = file.readBytes()
            if (bytes.size != EXPECTED_BIE1_SIZE) return BiometricSlotState.CORRUPT
            if (!bytes.copyOfRange(0, 4).contentEquals(MAGIC_BIOMETRIC_WRAP)) return BiometricSlotState.CORRUPT
            val version = bytes[4]
            if (version != BIE1_VERSION) return BiometricSlotState.CORRUPT
            val realm = bytes[5]
            if (realm != BIE1_REALM_REAL && realm != BIE1_REALM_DECOY) return BiometricSlotState.CORRUPT
            val bb = ByteBuffer.wrap(bytes)
            val slot = bb.getLong(6)
            when (slot) {
                1L -> BiometricSlotState.SLOT_A
                2L -> BiometricSlotState.SLOT_B
                else -> BiometricSlotState.CORRUPT
            }
        } catch (_: Exception) {
            BiometricSlotState.CORRUPT
        }
    }

    /**
     * Inspects active BIE1 biometric envelope to determine the committed key slot (1L or 2L).
     */
    fun getActiveBiometricSlot(context: Context): Long {
        return when (getBiometricSlotState(context)) {
            BiometricSlotState.SLOT_A -> 1L
            BiometricSlotState.SLOT_B -> 2L
            BiometricSlotState.CORRUPT, BiometricSlotState.MISSING -> 0L
        }
    }

    /**
     * Resolves the Keystore alias corresponding to the currently active biometric envelope.
     * Returns null if envelope is corrupt, missing, or malformed, preventing legacy alias fallback.
     */
    fun getActiveBiometricAlias(context: Context): String? {
        return when (getBiometricSlotState(context)) {
            BiometricSlotState.SLOT_A -> ALIAS_BIOMETRIC_SLOT_A
            BiometricSlotState.SLOT_B -> ALIAS_BIOMETRIC_SLOT_B
            BiometricSlotState.CORRUPT, BiometricSlotState.MISSING -> null
        }
    }

    /**
     * Retrieves existing biometric master key strictly without generating a new one.
     * Section 3.1: Unlock path may ONLY retrieve existing key; never create inside unlock.
     */
    @Synchronized
    fun getExistingBiometricMasterKey(alias: String = ALIAS_BIOMETRIC_SLOT_A): SecretKey? {
        return keyProvider.getKey(alias)
    }

    /**
     * Initializes BiometricPrompt.CryptoObject in ENCRYPT mode using a provisional target slot key.
     * Transactional: Generates key under the target slot (A or B), leaving the active slot key
     * completely intact until the new envelope is 100% written and committed.
     */
    fun getBiometricEnrollCryptoObject(context: Context): androidx.biometric.BiometricPrompt.CryptoObject? {
        if (!isRealVaultAuthorized()) {
            Log.e(TAG, "Biometric enrollment rejected: Real vault session must be authorized")
            return null
        }
        return try {
            val currentSlot = getActiveBiometricSlot(context)
            val targetSlot = if (currentSlot == 1L) 2L else 1L
            provisionalTargetSlot = targetSlot
            val targetAlias = if (targetSlot == 2L) ALIAS_BIOMETRIC_SLOT_B else ALIAS_BIOMETRIC_SLOT_A
            val provisionalKey = createBiometricMasterKey(targetAlias)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, provisionalKey)
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
     * Uses strict BIE1 binary format, binds single AAD, commits staged file atomically, and verifies.
     * The key that performed the encryption is preserved as the active slot key.
     */
    fun provisionBiometricEnvelope(context: Context, authenticatedCipher: Cipher): Boolean {
        val vrk = activeVrk
        if (vrk == null || isDecoyMode || vrk.size != 32) {
            Log.e(TAG, "Cannot provision biometric envelope: Invalid vault state")
            return false
        }

        val targetSlot = provisionalTargetSlot ?: (if (getActiveBiometricSlot(context) == 1L) 2L else 1L)
        val stagedFile = File(context.filesDir, "$BIOMETRIC_WRAP_FILE.staged")
        val targetFile = File(context.filesDir, BIOMETRIC_WRAP_FILE)

        return try {
            authenticatedCipher.updateAAD(BIOMETRIC_AAD)
            val encryptedVrk = authenticatedCipher.doFinal(vrk)
            val iv = authenticatedCipher.iv
            if (iv == null || iv.size != 12) {
                Log.e(TAG, "Authenticated cipher returned invalid IV")
                return false
            }

            val buffer = ByteBuffer.allocate(EXPECTED_BIE1_SIZE)
            buffer.put(MAGIC_BIOMETRIC_WRAP)
            buffer.put(BIE1_VERSION)
            buffer.put(BIE1_REALM_REAL)
            buffer.putLong(targetSlot) // Key generation / slot ID
            buffer.put(iv.size.toByte())
            buffer.put(iv)
            buffer.putShort(encryptedVrk.size.toShort())
            buffer.put(encryptedVrk)

            FileOutputStream(stagedFile).use { fos ->
                fos.write(buffer.array())
                fos.flush()
                fos.fd.sync()
            }

            if (stagedFile.length() != EXPECTED_BIE1_SIZE.toLong()) {
                stagedFile.delete()
                return false
            }

            // Atomically rename staged envelope to active envelope with OS-level atomic move guarantee
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    java.nio.file.Files.move(
                        stagedFile.toPath(),
                        targetFile.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                } else {
                    if (!stagedFile.renameTo(targetFile)) {
                        throw java.io.IOException("Atomic renameTo failed for biometric envelope")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Critical failure during atomic biometric envelope commit: ${e.message}", e)
                stagedFile.delete()
                return false
            }

            // Post-commit verification: Ensure targetFile exists on disk and has valid envelope header before destroying old key
            if (!targetFile.exists() || targetFile.length() != EXPECTED_BIE1_SIZE.toLong()) {
                Log.e(TAG, "Biometric post-commit verification failed: Envelope missing or size mismatch on disk")
                return false
            }

            val committedBytes = targetFile.readBytes()
            if (!committedBytes.copyOfRange(0, 4).contentEquals(MAGIC_BIOMETRIC_WRAP)) {
                Log.e(TAG, "Biometric post-commit verification failed: Corrupted magic header in committed envelope")
                return false
            }

            // Transactional cleanup: New key + envelope are live and verified. Safely delete superseded old slot key.
            val oldAlias = if (targetSlot == 2L) ALIAS_BIOMETRIC_SLOT_A else ALIAS_BIOMETRIC_SLOT_B
            keyProvider.deleteKey(oldAlias)
            keyProvider.deleteKey(ALIAS_BIOMETRIC_UNLOCK)
            keyProvider.deleteKey(ALIAS_BIOMETRIC_UNLOCK_PROVISIONAL)
            provisionalTargetSlot = null

            Log.i(TAG, "BIE1 biometric envelope provisioned, committed, and verified successfully in slot $targetSlot")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to provision biometric envelope: ${e.message}", e)
            stagedFile.delete()
            false
        }
    }

    /**
     * Initializes BiometricPrompt.CryptoObject in DECRYPT mode using the IV stored in the envelope.
     * STRICT INVARIANT: Unlock path NEVER generates a key; only retrieves existing active key.
     * Current production biometric flow parses BIE1 ONLY. If malformed, returns null (ENVELOPE_CORRUPT).
     */
    fun getBiometricDecryptCryptoObject(context: Context): androidx.biometric.BiometricPrompt.CryptoObject? {
        val slotState = getBiometricSlotState(context)
        if (slotState != BiometricSlotState.SLOT_A && slotState != BiometricSlotState.SLOT_B) {
            Log.w(TAG, "getBiometricDecryptCryptoObject rejected: Biometric envelope state is $slotState")
            return null
        }

        val activeAlias = getActiveBiometricAlias(context) ?: return null
        val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
        return try {
            val bytes = file.readBytes()
            if (bytes.size != EXPECTED_BIE1_SIZE) return null
            val ivLen = bytes[14].toInt() and 0xFF
            if (ivLen != 12) return null
            val iv = bytes.copyOfRange(15, 15 + ivLen)

            val biometricKey = getExistingBiometricMasterKey(activeAlias) ?: run {
                Log.w(TAG, "Existing biometric master key not found for alias $activeAlias during unlock attempt")
                return null
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, biometricKey, GCMParameterSpec(128, iv))
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
     * Enforces strict BIE1 format, realm check, single AAD, and authenticated sentinel check.
     * NEVER falls back to legacy envelope parsing or alternative key aliases.
     */
    fun unwrapBiometricSessionKey(context: Context, authenticatedCipher: Cipher): Boolean {
        val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
        if (!file.exists()) return false

        return try {
            val bytes = file.readBytes()
            if (bytes.size != EXPECTED_BIE1_SIZE) {
                Log.e(TAG, "Biometric unwrap rejected: Envelope size ${bytes.size} != expected $EXPECTED_BIE1_SIZE")
                return false
            }
            if (!bytes.copyOfRange(0, 4).contentEquals(MAGIC_BIOMETRIC_WRAP)) {
                Log.e(TAG, "Biometric unwrap rejected: Invalid magic header (non-BIE1)")
                return false
            }

            val bb = ByteBuffer.wrap(bytes)
            val magic = ByteArray(4)
            bb.get(magic)
            val version = bb.get()
            if (version != BIE1_VERSION) {
                Log.e(TAG, "Biometric unwrap rejected: Unsupported BIE1 version ($version)")
                return false
            }
            val realm = bb.get()
            if (realm != BIE1_REALM_REAL) {
                Log.e(TAG, "Biometric unwrap rejected: Wrong realm ($realm)")
                return false
            }
            val gen = bb.long
            if (gen != 1L && gen != 2L) {
                Log.e(TAG, "Biometric unwrap rejected: Invalid slot ID ($gen)")
                return false
            }
            val ivLen = bb.get().toInt() and 0xFF
            if (ivLen != 12) {
                Log.e(TAG, "Biometric unwrap rejected: Invalid IV length ($ivLen)")
                return false
            }
            val iv = ByteArray(ivLen)
            bb.get(iv)
            val cipherLen = bb.short.toInt() and 0xFFFF
            if (cipherLen != 48) {
                Log.e(TAG, "Biometric unwrap rejected: Invalid ciphertext length ($cipherLen)")
                return false
            }
            val ciphertext = ByteArray(cipherLen)
            bb.get(ciphertext)

            authenticatedCipher.updateAAD(BIOMETRIC_AAD)
            val unwrappedBytes = authenticatedCipher.doFinal(ciphertext)

            if (unwrappedBytes != null && unwrappedBytes.size == 32) {
                if (VaultSentinelManager.verifyVrk(context, unwrappedBytes, isDecoy = false)) {
                    activeVrk = unwrappedBytes
                    isDecoyMode = false
                    currentState = VaultState.AUTHORIZED_REAL
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

        val slotState = getBiometricSlotState(context)
        if (slotState == BiometricSlotState.CORRUPT) {
            envelopeFile.delete()
            settingsDataStore.setBiometricsEnabled(false)
            return BiometricEnrollmentState.ENVELOPE_CORRUPT
        }

        val activeAlias = getActiveBiometricAlias(context) ?: run {
            envelopeFile.delete()
            settingsDataStore.setBiometricsEnabled(false)
            return BiometricEnrollmentState.ENVELOPE_CORRUPT
        }

        try {
            val key = keyProvider.getKey(activeAlias)
            if (key == null) {
                envelopeFile.delete()
                settingsDataStore.setBiometricsEnabled(false)
                return BiometricEnrollmentState.UNAVAILABLE
            }

            val testCipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            testCipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.w(TAG, "Biometric key invalidated by system biometric changes")
            envelopeFile.delete()
            settingsDataStore.setBiometricsEnabled(false)
            return BiometricEnrollmentState.KEY_INVALIDATED
        } catch (e: UserNotAuthenticatedException) {
            // Key is healthy
        } catch (e: Exception) {
            // Other transient / init states
        }

        return BiometricEnrollmentState.ENROLLED
    }

    fun removeBiometricEnvelope(context: Context) {
        try {
            val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
            if (file.exists()) file.delete()
            val staged = File(context.filesDir, "$BIOMETRIC_WRAP_FILE.staged")
            if (staged.exists()) staged.delete()
            listOf(ALIAS_BIOMETRIC_UNLOCK, ALIAS_BIOMETRIC_SLOT_A, ALIAS_BIOMETRIC_SLOT_B, ALIAS_BIOMETRIC_UNLOCK_PROVISIONAL).forEach { alias ->
                keyProvider.deleteKey(alias)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing biometric envelope", e)
        }
    }

    @Synchronized
    fun getOrCreateKey(alias: String): SecretKey {
        return keyProvider.getOrCreateKey(alias)
    }

    fun getDeviceBindingKey(): SecretKey = getOrCreateKey(ALIAS_DEVICE_BINDING)

    private fun createBiometricMasterKey(alias: String): SecretKey {
        return keyProvider.createBiometricMasterKey(alias)
    }

    /**
     * Authoritatively deletes all keys in the registry.
     * Fails closed with verified boolean per key.
     */
    @Synchronized
    fun destroyAllKeys(): Map<String, Boolean> {
        return keyProvider.destroyAllKeys(ALL_KEY_ALIASES)
    }
}
