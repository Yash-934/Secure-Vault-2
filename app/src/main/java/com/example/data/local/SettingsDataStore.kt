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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vault_settings")

data class VaultSettings(
    val masterPin: String = "1234",
    val decoyPin: String = "9999",
    val isBiometricsEnabled: Boolean = true,
    val isPanicFlipEnabled: Boolean = true,
    val isStealthModeEnabled: Boolean = false
)

class SettingsDataStore(private val context: Context) {

    companion object {
        private val MASTER_PIN_KEY = stringPreferencesKey("master_pin")
        private val DECOY_PIN_KEY = stringPreferencesKey("decoy_pin")
        private val BIOMETRICS_ENABLED_KEY = booleanPreferencesKey("biometrics_enabled")
        private val PANIC_FLIP_ENABLED_KEY = booleanPreferencesKey("panic_flip_enabled")
        private val STEALTH_MODE_ENABLED_KEY = booleanPreferencesKey("stealth_mode_enabled")
    }

    val settingsFlow: Flow<VaultSettings> = context.dataStore.data.map { prefs ->
        VaultSettings(
            masterPin = prefs[MASTER_PIN_KEY] ?: "1234",
            decoyPin = prefs[DECOY_PIN_KEY] ?: "9999",
            isBiometricsEnabled = prefs[BIOMETRICS_ENABLED_KEY] ?: true,
            isPanicFlipEnabled = prefs[PANIC_FLIP_ENABLED_KEY] ?: true,
            isStealthModeEnabled = prefs[STEALTH_MODE_ENABLED_KEY] ?: false
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
}
