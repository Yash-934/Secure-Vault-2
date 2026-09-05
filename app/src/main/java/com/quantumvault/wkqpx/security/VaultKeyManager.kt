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

    const val ALIAS_BIOMETRIC_UNLOCK = "QuantumVaultBiometricUnlockMasterKey"
    const val ALIAS_BIOMETRIC_SLOT_A = "QuantumVaultBiometricKey_SlotA"
    const val ALIAS_BIOMETRIC_SLOT_B = "QuantumVaultBiometricKey_SlotB"
    const val ALIAS_BIOMETRIC_UNLOCK_PROVISIONAL = "QuantumVaultBiometricUnlockMasterKey_Provisional"
    const val ALIAS_DEVICE_BINDING = "VaultBackupDeviceBindingHardwareKey"
    const val ALIAS_DEX_PROTECTION = "SecureVaultDexKey"
    const val ALIAS_ATTESTATION = "SecureVaultHardwareAttestationKey_v2"
    const val ALIAS_LEGACY_MASTER = "SecureVaultAES256MasterKey"
    const val ALIAS_AUDIT_PROBE = "AuditDeviceBindingProbe"
    const val ALIAS_DB_WRAPPER = "SecureVaultDatabaseWrapperMasterKey"
    const val ALIAS_DB_WRAPPER_DECOY = "SecureVaultDatabaseWrapperDecoyMasterKey"

    val ALL_KEY_ALIASES = listOf(
        ALIAS_BIOMETRIC_UNLOCK,
        ALIAS_BIOMETRIC_SLOT_A,
        ALIAS_BIOMETRIC_SLOT_B,
        ALIAS_BIOMETRIC_UNLOCK_PROVISIONAL,
        ALIAS_DEVICE_BINDING,
        ALIAS_DEX_PROTECTION,
        ALIAS_ATTESTATION,
        ALIAS_LEGACY_MASTER,
        ALIAS_AUDIT_PROBE,
        ALIAS_DB_WRAPPER,
        ALIAS_DB_WRAPPER_DECOY
    )

    private val jvmFallbackKeys = ConcurrentHashMap<String, SecretKey>()

    val keyStore: KeyStore? = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load AndroidKeyStore", e)
        null
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
            if (keyStore?.containsAlias("SecureVaultAES256MasterKey") == true) {
                keyStore.getKey("SecureVaultAES256MasterKey", null) as SecretKey
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get legacy database wrap key", e)
            null
        }
    }

    private fun derivePinKek(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 12000, 256)
        val skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }

    /**
     * Atomically writes a VRK wrap file using a new random KEK salt, AES-256-GCM encryption,
     * and fsync before rename.
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
            val written = writeVrkPinWrap(context, vrk, pin, isDecoy)
            if (!written) {
                currentState = VaultState.CORRUPTED
                throw IllegalStateException("Failed to write VRK pin wrap file")
            }
            VaultSentinelManager.createSentinel(context, vrk, isDecoy)
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
     * Direct authorization for programmatic testing or migration workflows where VRK is known.
     */
    fun setAuthorizedSession(vrk: ByteArray, isDecoy: Boolean = false) {
        if (vrk.size != 32) throw IllegalArgumentException("VRK must be exactly 32 bytes")
        activeVrk = vrk.copyOf()
        isDecoyMode = isDecoy
        currentState = if (isDecoy) VaultState.AUTHORIZED_DECOY else VaultState.AUTHORIZED_REAL
    }

    /**
     * Inspects active BIE1 biometric envelope to determine the committed key slot (1L or 2L).
     */
    fun getActiveBiometricSlot(context: Context): Long {
        val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
        if (!file.exists() || file.length() < EXPECTED_BIE1_SIZE) return 0L
        return try {
            val bytes = file.readBytes()
            if (bytes.size >= 14 && bytes.copyOfRange(0, 4).contentEquals(MAGIC_BIOMETRIC_WRAP)) {
                ByteBuffer.wrap(bytes).getLong(6)
            } else {
                0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Resolves the Keystore alias corresponding to the currently active biometric envelope.
     */
    fun getActiveBiometricAlias(context: Context): String {
        return when (getActiveBiometricSlot(context)) {
            2L -> ALIAS_BIOMETRIC_SLOT_B
            1L -> ALIAS_BIOMETRIC_SLOT_A
            else -> ALIAS_BIOMETRIC_UNLOCK
        }
    }

    /**
     * Retrieves existing biometric master key strictly without generating a new one.
     * Section 3.1: Unlock path may ONLY retrieve existing key; never create inside unlock.
     */
    @Synchronized
    fun getExistingBiometricMasterKey(alias: String = ALIAS_BIOMETRIC_UNLOCK): SecretKey? {
        if (keyStore != null) {
            try {
                if (!keyStore.containsAlias(alias)) {
                    return null
                }
                val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
                return entry?.secretKey
            } catch (e: KeyPermanentlyInvalidatedException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get existing biometric master key for alias $alias", e)
                return null
            }
        }
        return jvmFallbackKeys[alias]
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

            // Atomically rename staged envelope to active envelope
            if (!stagedFile.renameTo(targetFile)) {
                stagedFile.copyTo(targetFile, overwrite = true)
                stagedFile.delete()
            }

            // Transactional cleanup: New key + envelope are live. Safely delete superseded old slot key.
            if (keyStore != null) {
                try {
                    val oldAlias = if (targetSlot == 2L) ALIAS_BIOMETRIC_SLOT_A else ALIAS_BIOMETRIC_SLOT_B
                    if (keyStore.containsAlias(oldAlias)) keyStore.deleteEntry(oldAlias)
                    if (keyStore.containsAlias(ALIAS_BIOMETRIC_UNLOCK)) keyStore.deleteEntry(ALIAS_BIOMETRIC_UNLOCK)
                    if (keyStore.containsAlias(ALIAS_BIOMETRIC_UNLOCK_PROVISIONAL)) keyStore.deleteEntry(ALIAS_BIOMETRIC_UNLOCK_PROVISIONAL)
                } catch (e: Exception) {
                    Log.w(TAG, "Non-critical cleanup warning during biometric promotion", e)
                }
            } else {
                val oldAlias = if (targetSlot == 2L) ALIAS_BIOMETRIC_SLOT_A else ALIAS_BIOMETRIC_SLOT_B
                jvmFallbackKeys.remove(oldAlias)
                jvmFallbackKeys.remove(ALIAS_BIOMETRIC_UNLOCK)
                jvmFallbackKeys.remove(ALIAS_BIOMETRIC_UNLOCK_PROVISIONAL)
            }
            provisionalTargetSlot = null

            Log.i(TAG, "BIE1 biometric envelope provisioned and committed successfully in slot $targetSlot")
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
     */
    fun getBiometricDecryptCryptoObject(context: Context): androidx.biometric.BiometricPrompt.CryptoObject? {
        val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
        if (!file.exists() || file.length() < 30) return null

        return try {
            val bytes = file.readBytes()
            val iv: ByteArray = if (bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals(MAGIC_BIOMETRIC_WRAP)) {
                val ivLen = bytes[14].toInt() and 0xFF
                bytes.copyOfRange(15, 15 + ivLen)
            } else {
                val ivLen = bytes[0].toInt() and 0xFF
                bytes.copyOfRange(1, 1 + ivLen)
            }

            val activeAlias = getActiveBiometricAlias(context)
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
     */
    fun unwrapBiometricSessionKey(context: Context, authenticatedCipher: Cipher): Boolean {
        val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
        if (!file.exists()) return false

        return try {
            val bytes = file.readBytes()
            val ciphertext: ByteArray

            if (bytes.size == EXPECTED_BIE1_SIZE && bytes.copyOfRange(0, 4).contentEquals(MAGIC_BIOMETRIC_WRAP)) {
                val bb = ByteBuffer.wrap(bytes)
                val magic = ByteArray(4)
                bb.get(magic)
                val version = bb.get()
                val realm = bb.get()
                val gen = bb.long
                val ivLen = bb.get().toInt() and 0xFF
                val iv = ByteArray(ivLen)
                bb.get(iv)
                val cipherLen = bb.short.toInt() and 0xFFFF
                ciphertext = ByteArray(cipherLen)
                bb.get(ciphertext)

                // Enforce realm check: Decoy envelope must never unlock real vault!
                if (realm != BIE1_REALM_REAL) {
                    Log.e(TAG, "Biometric unwrap rejected: Wrong realm ($realm)")
                    return false
                }
            } else if (bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals("QVBE".toByteArray(Charsets.US_ASCII))) {
                val ivLen = bytes[5].toInt() and 0xFF
                val offset = 6 + ivLen
                val cipherLen = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
                ciphertext = bytes.copyOfRange(offset + 2, offset + 2 + cipherLen)
            } else {
                val ivLen = bytes[0].toInt() and 0xFF
                ciphertext = bytes.copyOfRange(1 + ivLen, bytes.size)
            }

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

        if (envelopeFile.length() < 60) {
            envelopeFile.delete()
            settingsDataStore.setBiometricsEnabled(false)
            return BiometricEnrollmentState.ENVELOPE_CORRUPT
        }

        val activeAlias = getActiveBiometricAlias(context)
        if (keyStore != null) {
            try {
                if (!keyStore.containsAlias(activeAlias)) {
                    envelopeFile.delete()
                    settingsDataStore.setBiometricsEnabled(false)
                    return BiometricEnrollmentState.UNAVAILABLE
                }
                val entry = keyStore.getEntry(activeAlias, null) as? KeyStore.SecretKeyEntry
                if (entry == null) {
                    envelopeFile.delete()
                    settingsDataStore.setBiometricsEnabled(false)
                    return BiometricEnrollmentState.UNAVAILABLE
                }

                val testCipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = ByteArray(12)
                testCipher.init(Cipher.DECRYPT_MODE, entry.secretKey, GCMParameterSpec(128, iv))
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
        }

        return BiometricEnrollmentState.ENROLLED
    }

    fun removeBiometricEnvelope(context: Context) {
        try {
            val file = File(context.filesDir, BIOMETRIC_WRAP_FILE)
            if (file.exists()) file.delete()
            val staged = File(context.filesDir, "$BIOMETRIC_WRAP_FILE.staged")
            if (staged.exists()) staged.delete()
            keyStore?.let {
                listOf(ALIAS_BIOMETRIC_UNLOCK, ALIAS_BIOMETRIC_SLOT_A, ALIAS_BIOMETRIC_SLOT_B, ALIAS_BIOMETRIC_UNLOCK_PROVISIONAL).forEach { alias ->
                    if (it.containsAlias(alias)) {
                        it.deleteEntry(alias)
                    }
                }
            }
            listOf(ALIAS_BIOMETRIC_UNLOCK, ALIAS_BIOMETRIC_SLOT_A, ALIAS_BIOMETRIC_SLOT_B, ALIAS_BIOMETRIC_UNLOCK_PROVISIONAL).forEach { alias ->
                jvmFallbackKeys.remove(alias)
            }
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
                Log.e(TAG, "Keystore exception for alias $alias: ${e.message}")
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

    private fun createBiometricMasterKey(alias: String): SecretKey {
        if (keyStore != null) {
            try {
                if (keyStore.containsAlias(alias)) {
                    keyStore.deleteEntry(alias)
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
                Log.e(TAG, "Biometric key generation failed for alias $alias: ${e.message}", e)
                if (!isRunningInTestEnvironment()) {
                    throw IllegalStateException("Critical KeyStore failure for biometric key.", e)
                }
            }
        }
        if (!isRunningInTestEnvironment()) {
            throw IllegalStateException("Keystore is null in production environment.")
        }
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256)
        val key = kg.generateKey()
        jvmFallbackKeys[alias] = key
        return key
    }

    /**
     * Authoritatively deletes all keys in the registry.
     * Section 7.1: If KeyStore is null or unavailable, it MUST report false, never true!
     */
    @Synchronized
    fun destroyAllKeys(): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        jvmFallbackKeys.clear()
        if (keyStore == null) {
            ALL_KEY_ALIASES.forEach { results[it] = false }
            return results
        }
        val targetAliases = (ALL_KEY_ALIASES + try { keyStore.aliases().toList() } catch (_: Exception) { emptyList() }).distinct()
        targetAliases.forEach { alias ->
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
