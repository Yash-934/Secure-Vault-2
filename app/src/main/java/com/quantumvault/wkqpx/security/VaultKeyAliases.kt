package com.quantumvault.wkqpx.security

/**
 * Single source of truth for all Android Keystore aliases used across QuantumVault.
 * Centralizing all aliases prevents self-destruct gaps, duplicate probe definitions,
 * and untracked key leaks.
 */
object VaultKeyAliases {
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
    const val ALIAS_GENERATION_AUTH = "VaultGenerationAuthHardwareKey"

    val ALL_KNOWN_ALIASES: List<String> = listOf(
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
        ALIAS_DB_WRAPPER_DECOY,
        ALIAS_GENERATION_AUTH
    )
}
