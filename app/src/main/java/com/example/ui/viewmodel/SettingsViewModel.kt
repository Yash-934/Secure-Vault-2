package com.example.ui.viewmodel

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
            val result = securityAuditEngine.performSecurityAudit()
            _auditResult.value = result
            _isAuditing.value = false
        }
    }

    fun updateMasterPin(newPin: String) {
        viewModelScope.launch {
            settingsDataStore.updateMasterPin(newPin)
        }
    }

    fun updateDecoyPin(newPin: String) {
        viewModelScope.launch {
            settingsDataStore.updateDecoyPin(newPin)
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
