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

    val settings: StateFlow<VaultSettings> = settingsDataStore.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VaultSettings()
        )

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
