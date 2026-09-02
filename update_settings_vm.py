import re

with open("app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt", "r") as f:
    content = f.read()

content = content.replace(
"""    fun updateMasterPin(newPin: String) {
        viewModelScope.launch {
            settingsDataStore.updateMasterPin(newPin)
        }
    }""",
"""    fun updateMasterPin(newPin: String) {
        viewModelScope.launch {
            settingsDataStore.updateMasterPin(newPin)
            com.example.security.VaultKeyManager.initializeVrkWithPin(getApplication(), newPin, false)
        }
    }"""
)

content = content.replace(
"""    fun updateDecoyPin(newPin: String) {
        viewModelScope.launch {
            settingsDataStore.updateDecoyPin(newPin)
        }
    }""",
"""    fun updateDecoyPin(newPin: String) {
        viewModelScope.launch {
            settingsDataStore.updateDecoyPin(newPin)
            if (newPin.isNotBlank()) {
                com.example.security.VaultKeyManager.initializeVrkWithPin(getApplication(), newPin, true)
            }
        }
    }"""
)

with open("app/src/main/java/com/example/ui/viewmodel/SettingsViewModel.kt", "w") as f:
    f.write(content)
