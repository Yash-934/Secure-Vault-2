import re

with open("app/src/main/java/com/example/ui/VaultViewModel.kt", "r") as f:
    content = f.read()

# Update initializeCredentials
content = content.replace(
"""    suspend fun initializeCredentials(context: Context, masterPin: String) {
        val settingsDataStore = com.example.data.local.SettingsDataStore(context)
        settingsDataStore.initializeCredentials(masterPin)
        unlockRealVault()
        consecutiveFailedAttempts = 0
        _lockoutSecondsRemaining.value = 0
        settingsDataStore.resetFailedAttempts()
    }""",
"""    suspend fun initializeCredentials(context: Context, masterPin: String) {
        val settingsDataStore = com.example.data.local.SettingsDataStore(context)
        settingsDataStore.initializeCredentials(masterPin)
        com.example.security.VaultKeyManager.initializeVrkWithPin(context, masterPin, isDecoy = false)
        com.example.security.VaultKeyManager.authorizeWithPin(context, masterPin, isDecoy = false)
        unlockRealVault()
        consecutiveFailedAttempts = 0
        _lockoutSecondsRemaining.value = 0
        settingsDataStore.resetFailedAttempts()
    }"""
)

# Update unlockRealVault
content = content.replace(
"""    fun unlockRealVault() {
        com.example.security.VaultKeyManager.authorizeWithMasterKey()
        _vaultMode.value = VaultMode.REAL
        _isUnlocked.value = true
        simulateLoading()
    }""",
"""    fun unlockRealVault() {
        _vaultMode.value = VaultMode.REAL
        _isUnlocked.value = true
        simulateLoading()
    }"""
)

# Update unlockDecoyVault
content = content.replace(
"""    fun unlockDecoyVault() {
        com.example.security.VaultKeyManager.authorizeWithMasterKey()
        _vaultMode.value = VaultMode.DECOY
        _isUnlocked.value = true
        simulateLoading()
    }""",
"""    fun unlockDecoyVault() {
        _vaultMode.value = VaultMode.DECOY
        _isUnlocked.value = true
        simulateLoading()
    }"""
)

# Replace authorizeWithMasterKey
content = content.replace("com.example.security.VaultKeyManager.authorizeWithMasterKey()", "")

# Update authenticateWithPin
content = content.replace(
"""        // 2. Check Master PIN
        if (settingsDataStore.verifyMasterPin(enteredPin)) {
            unlockRealVault()
            consecutiveFailedAttempts = 0""",
"""        // 2. Check Master PIN
        if (settingsDataStore.verifyMasterPin(enteredPin)) {
            if (com.example.security.VaultKeyManager.authorizeWithPin(context, enteredPin, false)) {
                unlockRealVault()
                consecutiveFailedAttempts = 0"""
)

# Need to close the brace for Master PIN
content = content.replace(
"""            lockoutJob?.cancel()
            settingsDataStore.resetFailedAttempts()
            return true
        }

        // 3. Check Decoy PIN""",
"""            lockoutJob?.cancel()
            settingsDataStore.resetFailedAttempts()
            return true
            }
        }

        // 3. Check Decoy PIN"""
)

content = content.replace(
"""        // 3. Check Decoy PIN
        if (settingsDataStore.verifyDecoyPin(enteredPin)) {
            unlockDecoyVault()
            consecutiveFailedAttempts = 0""",
"""        // 3. Check Decoy PIN
        if (settingsDataStore.verifyDecoyPin(enteredPin)) {
            if (com.example.security.VaultKeyManager.authorizeWithPin(context, enteredPin, true)) {
                unlockDecoyVault()
                consecutiveFailedAttempts = 0"""
)

content = content.replace(
"""            lockoutJob?.cancel()
            settingsDataStore.resetFailedAttempts()
            return true
        }

        // 4. Failed Attempt""",
"""            lockoutJob?.cancel()
            settingsDataStore.resetFailedAttempts()
            return true
            }
        }

        // 4. Failed Attempt"""
)

with open("app/src/main/java/com/example/ui/VaultViewModel.kt", "w") as f:
    f.write(content)
