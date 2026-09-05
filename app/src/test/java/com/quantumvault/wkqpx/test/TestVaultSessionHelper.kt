package com.quantumvault.wkqpx.test

import com.quantumvault.wkqpx.security.VaultKeyManager
import com.quantumvault.wkqpx.security.VaultState

/**
 * Helper strictly located in src/test/ for unit test session initialization.
 * Release APK contains no reference or arbitrary session injection API.
 */
object TestVaultSessionHelper {
    fun setAuthorizedSession(vrk: ByteArray, isDecoy: Boolean = false) {
        VaultKeyManager.setAuthorizedSessionForTesting(vrk, isDecoy)
    }

    fun setTestKeyProvider(provider: com.quantumvault.wkqpx.security.VaultKeyProvider) {
        VaultKeyManager.setKeyProviderForTesting(provider)
    }

    fun resetTestKeyProvider() {
        VaultKeyManager.resetKeyProviderForTesting()
    }
}
