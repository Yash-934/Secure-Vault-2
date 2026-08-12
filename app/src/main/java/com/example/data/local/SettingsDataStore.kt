package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vault_settings")

data class VaultSettings(
    val masterPin: String = "1234",
    val decoyPin: String = "9999",
    val killPin: String = "6666",
    val isKillPinEnabled: Boolean = true,
    val isBiometricsEnabled: Boolean = true,
    val isPanicFlipEnabled: Boolean = true,
    val isStealthModeEnabled: Boolean = false,
    val isCamouflageEnabled: Boolean = false,
    val isScreenProtectionEnabled: Boolean = false,
    val isIntruderSelfieEnabled: Boolean = false,
    val isDeadManSwitchEnabled: Boolean = false,
    val deadManDays: Int = 30,
    val lastLoginTimestamp: Long = System.currentTimeMillis()
)

class SettingsDataStore(private val context: Context) {

    companion object {
        private val MASTER_PIN_KEY = stringPreferencesKey("master_pin")
        private val DECOY_PIN_KEY = stringPreferencesKey("decoy_pin")
        private val KILL_PIN_KEY = stringPreferencesKey("kill_pin")
        private val KILL_PIN_ENABLED_KEY = booleanPreferencesKey("kill_pin_enabled")
        private val BIOMETRICS_ENABLED_KEY = booleanPreferencesKey("biometrics_enabled")
        private val PANIC_FLIP_ENABLED_KEY = booleanPreferencesKey("panic_flip_enabled")
        private val STEALTH_MODE_ENABLED_KEY = booleanPreferencesKey("stealth_mode_enabled")
        private val CAMOUFLAGE_ENABLED_KEY = booleanPreferencesKey("camouflage_enabled")
        private val SCREEN_PROTECTION_ENABLED_KEY = booleanPreferencesKey("screen_protection_enabled")
        private val INTRUDER_SELFIE_ENABLED_KEY = booleanPreferencesKey("intruder_selfie_enabled")
        private val DEAD_MAN_SWITCH_ENABLED_KEY = booleanPreferencesKey("dead_man_switch_enabled")
        private val DEAD_MAN_DAYS_KEY = intPreferencesKey("dead_man_days")
        private val LAST_LOGIN_TIMESTAMP_KEY = longPreferencesKey("last_login_timestamp")
    }

    val settingsFlow: Flow<VaultSettings> = context.dataStore.data.map { prefs ->
        VaultSettings(
            masterPin = prefs[MASTER_PIN_KEY] ?: "1234",
            decoyPin = prefs[DECOY_PIN_KEY] ?: "9999",
            killPin = prefs[KILL_PIN_KEY] ?: "6666",
            isKillPinEnabled = prefs[KILL_PIN_ENABLED_KEY] ?: true,
            isBiometricsEnabled = prefs[BIOMETRICS_ENABLED_KEY] ?: true,
            isPanicFlipEnabled = prefs[PANIC_FLIP_ENABLED_KEY] ?: true,
            isStealthModeEnabled = prefs[STEALTH_MODE_ENABLED_KEY] ?: false,
            isCamouflageEnabled = prefs[CAMOUFLAGE_ENABLED_KEY] ?: false,
            isScreenProtectionEnabled = prefs[SCREEN_PROTECTION_ENABLED_KEY] ?: false,
            isIntruderSelfieEnabled = prefs[INTRUDER_SELFIE_ENABLED_KEY] ?: false,
            isDeadManSwitchEnabled = prefs[DEAD_MAN_SWITCH_ENABLED_KEY] ?: false,
            deadManDays = prefs[DEAD_MAN_DAYS_KEY] ?: 30,
            lastLoginTimestamp = prefs[LAST_LOGIN_TIMESTAMP_KEY] ?: System.currentTimeMillis()
        )
    }

    suspend fun updateMasterPin(newPin: String) {
        context.dataStore.edit { prefs ->
            prefs[MASTER_PIN_KEY] = newPin
        }
    }

    suspend fun updateDecoyPin(newPin: String) {
        context.dataStore.edit { prefs ->
            prefs[DECOY_PIN_KEY] = newPin
        }
    }

    suspend fun updateKillPin(newPin: String) {
        context.dataStore.edit { prefs ->
            prefs[KILL_PIN_KEY] = newPin
        }
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

    suspend fun updateLastLoginTimestamp() {
        context.dataStore.edit { prefs ->
            prefs[LAST_LOGIN_TIMESTAMP_KEY] = System.currentTimeMillis()
        }
    }
}
