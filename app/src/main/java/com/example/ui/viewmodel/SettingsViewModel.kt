package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SettingsDataStore
import com.example.data.local.VaultSettings
import com.example.security.AuditResult
import com.example.security.SecurityAuditEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val securityAuditEngine: SecurityAuditEngine
) : ViewModel() {

    private val _auditResult = MutableStateFlow<AuditResult?>(null)
    val auditResult: StateFlow<AuditResult?> = _auditResult.asStateFlow()

    private val _isAuditing = MutableStateFlow(false)
    val isAuditing: StateFlow<Boolean> = _isAuditing.asStateFlow()

    private val _settings = MutableStateFlow(VaultSettings())
    val settings: StateFlow<VaultSettings> = _settings.asStateFlow()

    private val _isSettingsLoaded = MutableStateFlow(false)
    val isSettingsLoaded: StateFlow<Boolean> = _isSettingsLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { newSettings ->
                _settings.value = newSettings
                _isSettingsLoaded.value = true
            }
        }
    }

    fun runSecurityAudit() {
        viewModelScope.launch {
            _isAuditing.value = true
            // Allow radar scanner to complete visual sweeps
            kotlinx.coroutines.delay(2400)
            val result = securityAuditEngine.performSecurityAudit()
            _auditResult.value = result
            _isAuditing.value = false
        }
    }

    fun rotateMasterPin(
        context: Context,
        oldPin: String,
        newPin: String,
        onResult: ((com.example.security.RotationResult) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = com.example.security.CredentialRotationManager.rotateMasterPin(
                context = context,
                oldPin = oldPin,
                newPin = newPin,
                settingsDataStore = settingsDataStore
            )
            onResult?.invoke(result)
        }
    }

    fun updateMasterPin(context: Context, newPin: String) {
        viewModelScope.launch {
            com.example.security.CredentialRotationManager.rotateMasterPinWithActiveVrk(
                context = context,
                newPin = newPin,
                settingsDataStore = settingsDataStore
            )
        }
    }

    fun rotateDecoyPin(
        context: Context,
        oldPin: String,
        newPin: String,
        onResult: ((com.example.security.RotationResult) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = com.example.security.CredentialRotationManager.rotateDecoyPin(
                context = context,
                oldPin = oldPin,
                newPin = newPin,
                settingsDataStore = settingsDataStore
            )
            onResult?.invoke(result)
        }
    }

    fun updateDecoyPin(context: Context, newPin: String) {
        viewModelScope.launch {
            com.example.security.CredentialRotationManager.rotateDecoyPin(
                context = context,
                oldPin = "",
                newPin = newPin,
                settingsDataStore = settingsDataStore
            )
        }
    }

    fun disableBiometrics(context: Context) {
        viewModelScope.launch {
            com.example.security.VaultKeyManager.removeBiometricEnvelope(context)
            settingsDataStore.setBiometricsEnabled(false)
        }
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setBiometricsEnabled(enabled)
        }
    }

    fun setPanicFlipEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setPanicFlipEnabled(enabled)
        }
    }

    fun setStealthModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setStealthModeEnabled(enabled)
        }
    }

    fun setCamouflageEnabled(context: android.content.Context, enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setCamouflageEnabled(enabled)
            com.example.security.CamouflageManager.setAppIconCamouflage(context, enabled)
        }
    }

    fun setCamouflageType(type: String) {
        viewModelScope.launch {
            settingsDataStore.setCamouflageType(type)
        }
    }

    fun updateKillPin(newPin: String) {
        viewModelScope.launch {
            settingsDataStore.updateKillPin(newPin)
        }
    }

    fun setKillPinEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setKillPinEnabled(enabled)
        }
    }

    fun setIntruderSelfieEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setIntruderSelfieEnabled(enabled)
        }
    }

    fun setDeadManSwitchEnabled(context: android.content.Context, enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setDeadManSwitchEnabled(enabled)
            if (enabled) {
                scheduleDeadManWork(context)
            } else {
                androidx.work.WorkManager.getInstance(context).cancelUniqueWork("DeadManSwitchWork")
            }
        }
    }

    fun setDeadManDays(days: Int) {
        viewModelScope.launch {
            settingsDataStore.setDeadManDays(days)
        }
    }

    private fun scheduleDeadManWork(context: android.content.Context) {
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.security.DeadManSwitchWorker>(
            12, java.util.concurrent.TimeUnit.HOURS
        ).build()

        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "DeadManSwitchWork",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun setScreenProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setScreenProtectionEnabled(enabled)
        }
    }

    fun setThumbnailsEnabled(enabled: Boolean, context: Context? = null) {
        viewModelScope.launch {
            settingsDataStore.setThumbnailsEnabled(enabled)
            if (!enabled) {
                com.example.security.VaultThumbnailManager.clearCache(context)
            }
        }
    }

    class Factory(
        private val settingsDataStore: SettingsDataStore,
        private val securityAuditEngine: SecurityAuditEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsDataStore, securityAuditEngine) as T
        }
    }
}
