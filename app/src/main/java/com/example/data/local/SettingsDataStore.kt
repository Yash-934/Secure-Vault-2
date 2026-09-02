package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vault_settings")

data class VaultSettings(
    val isInitialized: Boolean = false,
    val masterPin: String = "",
    val decoyPin: String = "",
    val killPin: String = "",
    val isKillPinEnabled: Boolean = false,
    val isBiometricsEnabled: Boolean = false,
    val isPanicFlipEnabled: Boolean = true,
    val isStealthModeEnabled: Boolean = false,
    val isCamouflageEnabled: Boolean = false,
    val camouflageType: String = "CALCULATOR", // "CALCULATOR" or "NOTES"
    val isScreenProtectionEnabled: Boolean = false,
    val isIntruderSelfieEnabled: Boolean = false,
    val isDeadManSwitchEnabled: Boolean = false,
    val deadManDays: Int = 30,
    val lastLoginTimestamp: Long = System.currentTimeMillis(),
    val isThumbnailsEnabled: Boolean = true
)

class SettingsDataStore(private val context: Context) {

    companion object {
        private val authMutex = Mutex()

        private val IS_INITIALIZED_KEY = booleanPreferencesKey("is_vault_initialized")
        private val MASTER_PIN_HASH_KEY = stringPreferencesKey("master_pin_hash")
        private val MASTER_PIN_SALT_KEY = stringPreferencesKey("master_pin_salt_hex")
        private val DECOY_PIN_HASH_KEY = stringPreferencesKey("decoy_pin_hash")
        private val DECOY_PIN_SALT_KEY = stringPreferencesKey("decoy_pin_salt_hex")
        private val KILL_PIN_HASH_KEY = stringPreferencesKey("kill_pin_hash")
        private val KILL_PIN_SALT_KEY = stringPreferencesKey("kill_pin_salt_hex")
        private val KILL_PIN_ENABLED_KEY = booleanPreferencesKey("kill_pin_enabled")
        private val BIOMETRICS_ENABLED_KEY = booleanPreferencesKey("biometrics_enabled")
        private val PANIC_FLIP_ENABLED_KEY = booleanPreferencesKey("panic_flip_enabled")
        private val STEALTH_MODE_ENABLED_KEY = booleanPreferencesKey("stealth_mode_enabled")
        private val CAMOUFLAGE_ENABLED_KEY = booleanPreferencesKey("camouflage_enabled")
        private val CAMOUFLAGE_TYPE_KEY = stringPreferencesKey("camouflage_type")
        private val SCREEN_PROTECTION_ENABLED_KEY = booleanPreferencesKey("screen_protection_enabled")
        private val INTRUDER_SELFIE_ENABLED_KEY = booleanPreferencesKey("intruder_selfie_enabled")
        private val DEAD_MAN_SWITCH_ENABLED_KEY = booleanPreferencesKey("dead_man_switch_enabled")
        private val DEAD_MAN_DAYS_KEY = intPreferencesKey("dead_man_days")
        private val LAST_LOGIN_TIMESTAMP_KEY = longPreferencesKey("last_login_timestamp")
        private val THUMBNAILS_ENABLED_KEY = booleanPreferencesKey("thumbnails_enabled")
        private val FAILED_ATTEMPTS_COUNT_KEY = intPreferencesKey("failed_attempts_count")
        private val LOCKOUT_EXPIRATION_TIMESTAMP_KEY = longPreferencesKey("lockout_expiration_ts")

        private const val PBKDF2_ITERATIONS = 12000
        private const val KEY_LENGTH_BITS = 256

        fun generateRandomSalt(): ByteArray {
            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)
            return salt
        }

        fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

        fun hexToBytes(hex: String): ByteArray {
            if (hex.length % 2 != 0) return ByteArray(0)
            val result = ByteArray(hex.length / 2)
            for (i in result.indices) {
                val byteVal = hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return ByteArray(0)
                result[i] = byteVal.toByte()
            }
            return result
        }

        fun hashPin(pin: String, salt: ByteArray): String {
            if (pin.isEmpty()) return ""
            val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = skf.generateSecret(spec).encoded
            return bytesToHex(hash)
        }

        fun constantTimeEquals(a: String, b: String): Boolean {
            if (a.isEmpty() || b.isEmpty()) return false
            val aBytes = a.toByteArray(Charsets.UTF_8)
            val bBytes = b.toByteArray(Charsets.UTF_8)
            return MessageDigest.isEqual(aBytes, bBytes)
        }
    }

    val settingsFlow: Flow<VaultSettings> = context.dataStore.data.map { prefs ->
        VaultSettings(
            isInitialized = prefs[IS_INITIALIZED_KEY] ?: false,
            masterPin = "", // Never expose plaintext PIN in settings
            decoyPin = "",
            killPin = "",
            isKillPinEnabled = prefs[KILL_PIN_ENABLED_KEY] ?: false,
            isBiometricsEnabled = prefs[BIOMETRICS_ENABLED_KEY] ?: false,
            isPanicFlipEnabled = prefs[PANIC_FLIP_ENABLED_KEY] ?: true,
            isStealthModeEnabled = prefs[STEALTH_MODE_ENABLED_KEY] ?: false,
            isCamouflageEnabled = prefs[CAMOUFLAGE_ENABLED_KEY] ?: false,
            camouflageType = prefs[CAMOUFLAGE_TYPE_KEY] ?: "CALCULATOR",
            isScreenProtectionEnabled = prefs[SCREEN_PROTECTION_ENABLED_KEY] ?: false,
            isIntruderSelfieEnabled = prefs[INTRUDER_SELFIE_ENABLED_KEY] ?: false,
            isDeadManSwitchEnabled = prefs[DEAD_MAN_SWITCH_ENABLED_KEY] ?: false,
            deadManDays = prefs[DEAD_MAN_DAYS_KEY] ?: 30,
            lastLoginTimestamp = prefs[LAST_LOGIN_TIMESTAMP_KEY] ?: System.currentTimeMillis(),
            isThumbnailsEnabled = prefs[THUMBNAILS_ENABLED_KEY] ?: true
        )
    }

    suspend fun initializeCredentials(masterPin: String) = authMutex.withLock {
        val salt = generateRandomSalt()
        val masterHash = hashPin(masterPin, salt)
        context.dataStore.edit { prefs ->
            prefs[IS_INITIALIZED_KEY] = true
            prefs[MASTER_PIN_HASH_KEY] = masterHash
            prefs[MASTER_PIN_SALT_KEY] = bytesToHex(salt)
            prefs[FAILED_ATTEMPTS_COUNT_KEY] = 0
            prefs[LOCKOUT_EXPIRATION_TIMESTAMP_KEY] = 0L
        }
    }

    suspend fun verifyMasterPin(pin: String): Boolean = authMutex.withLock {
        if (pin.isEmpty()) return false
        val prefs = context.dataStore.data.first()
        val isInit = prefs[IS_INITIALIZED_KEY] ?: false
        if (!isInit) return false
        val storedHash = prefs[MASTER_PIN_HASH_KEY] ?: return false
        val saltHex = prefs[MASTER_PIN_SALT_KEY]
        val salt = if (!saltHex.isNullOrEmpty()) hexToBytes(saltHex) else "QVLT_MASTER_SALT_2026_SECURE_AUTH".toByteArray(Charsets.UTF_8)
        val computedHash = hashPin(pin, salt)
        return constantTimeEquals(storedHash, computedHash)
    }

    suspend fun verifyDecoyPin(pin: String): Boolean = authMutex.withLock {
        if (pin.isEmpty()) return false
        val prefs = context.dataStore.data.first()
        val isInit = prefs[IS_INITIALIZED_KEY] ?: false
        if (!isInit) return false
        val storedHash = prefs[DECOY_PIN_HASH_KEY] ?: return false
        if (storedHash.isEmpty()) return false
        val saltHex = prefs[DECOY_PIN_SALT_KEY]
        val salt = if (!saltHex.isNullOrEmpty()) hexToBytes(saltHex) else "QVLT_DECOY_SALT_2026_SECURE_AUTH".toByteArray(Charsets.UTF_8)
        val computedHash = hashPin(pin, salt)
        return constantTimeEquals(storedHash, computedHash)
    }

    suspend fun verifyKillPin(pin: String): Boolean = authMutex.withLock {
        if (pin.isEmpty()) return false
        val prefs = context.dataStore.data.first()
        val isInit = prefs[IS_INITIALIZED_KEY] ?: false
        val isEnabled = prefs[KILL_PIN_ENABLED_KEY] ?: false
        if (!isInit || !isEnabled) return false
        val storedHash = prefs[KILL_PIN_HASH_KEY] ?: return false
        if (storedHash.isEmpty()) return false
        val saltHex = prefs[KILL_PIN_SALT_KEY]
        val salt = if (!saltHex.isNullOrEmpty()) hexToBytes(saltHex) else "QVLT_KILL_SALT_2026_SECURE_AUTH".toByteArray(Charsets.UTF_8)
        val computedHash = hashPin(pin, salt)
        return constantTimeEquals(storedHash, computedHash)
    }

    suspend fun updateMasterPin(newPin: String) = authMutex.withLock {
        val salt = generateRandomSalt()
        val newHash = hashPin(newPin, salt)
        context.dataStore.edit { prefs ->
            prefs[IS_INITIALIZED_KEY] = true
            prefs[MASTER_PIN_HASH_KEY] = newHash
            prefs[MASTER_PIN_SALT_KEY] = bytesToHex(salt)
        }
    }

    suspend fun updateDecoyPin(newPin: String) = authMutex.withLock {
        val salt = generateRandomSalt()
        val newHash = if (newPin.isNotBlank()) hashPin(newPin, salt) else ""
        context.dataStore.edit { prefs ->
            prefs[DECOY_PIN_HASH_KEY] = newHash
            prefs[DECOY_PIN_SALT_KEY] = if (newPin.isNotBlank()) bytesToHex(salt) else ""
        }
    }

    suspend fun updateKillPin(newPin: String) = authMutex.withLock {
        val salt = generateRandomSalt()
        val newHash = if (newPin.isNotBlank()) hashPin(newPin, salt) else ""
        context.dataStore.edit { prefs ->
            prefs[KILL_PIN_HASH_KEY] = newHash
            prefs[KILL_PIN_SALT_KEY] = if (newPin.isNotBlank()) bytesToHex(salt) else ""
        }
    }

    suspend fun getFailedAttempts(): Int {
        val prefs = context.dataStore.data.first()
        return prefs[FAILED_ATTEMPTS_COUNT_KEY] ?: 0
    }

    suspend fun recordFailedAttempt(): Int = authMutex.withLock {
        var count = 0
        context.dataStore.edit { prefs ->
            val current = prefs[FAILED_ATTEMPTS_COUNT_KEY] ?: 0
            count = current + 1
            prefs[FAILED_ATTEMPTS_COUNT_KEY] = count

            // Progressive Rate-Limiting Backoff
            val lockoutSeconds = when {
                count < 4 -> 0
                count == 4 -> 30
                count == 5 -> 60
                count == 6 -> 300 // 5 minutes
                else -> 1800     // 30 minutes
            }

            if (lockoutSeconds > 0) {
                val expirationTs = System.currentTimeMillis() + (lockoutSeconds * 1000L)
                prefs[LOCKOUT_EXPIRATION_TIMESTAMP_KEY] = expirationTs
            }
        }
        return count
    }

    suspend fun resetFailedAttempts() = authMutex.withLock {
        context.dataStore.edit { prefs ->
            prefs[FAILED_ATTEMPTS_COUNT_KEY] = 0
            prefs[LOCKOUT_EXPIRATION_TIMESTAMP_KEY] = 0L
        }
    }

    suspend fun setLockoutExpiration(expirationTimestamp: Long) = authMutex.withLock {
        context.dataStore.edit { prefs ->
            prefs[LOCKOUT_EXPIRATION_TIMESTAMP_KEY] = expirationTimestamp
        }
    }

    suspend fun getLockoutSecondsRemaining(): Int {
        val prefs = context.dataStore.data.first()
        val exp = prefs[LOCKOUT_EXPIRATION_TIMESTAMP_KEY] ?: 0L
        val now = System.currentTimeMillis()
        return if (exp > now) ((exp - now) / 1000).toInt() else 0
    }

    suspend fun setKillPinEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KILL_PIN_ENABLED_KEY] = enabled
        }
    }

    suspend fun setBiometricsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BIOMETRICS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setPanicFlipEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PANIC_FLIP_ENABLED_KEY] = enabled
        }
    }

    suspend fun setStealthModeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[STEALTH_MODE_ENABLED_KEY] = enabled
        }
    }

    suspend fun setCamouflageEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[CAMOUFLAGE_ENABLED_KEY] = enabled
        }
    }

    suspend fun setCamouflageType(type: String) {
        context.dataStore.edit { prefs ->
            prefs[CAMOUFLAGE_TYPE_KEY] = type
        }
    }

    suspend fun setScreenProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SCREEN_PROTECTION_ENABLED_KEY] = enabled
        }
    }

    suspend fun setIntruderSelfieEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[INTRUDER_SELFIE_ENABLED_KEY] = enabled
        }
    }

    suspend fun setDeadManSwitchEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DEAD_MAN_SWITCH_ENABLED_KEY] = enabled
        }
    }

    suspend fun setDeadManDays(days: Int) {
        context.dataStore.edit { prefs ->
            prefs[DEAD_MAN_DAYS_KEY] = days
        }
    }

    suspend fun setThumbnailsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[THUMBNAILS_ENABLED_KEY] = enabled
        }
    }

    suspend fun updateLastLoginTimestamp() {
        context.dataStore.edit { prefs ->
            prefs[LAST_LOGIN_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }
}
