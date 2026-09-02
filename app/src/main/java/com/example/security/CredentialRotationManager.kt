package com.example.security

import android.content.Context
import android.util.Log
import com.example.data.local.SettingsDataStore
import java.io.File

sealed class RotationResult {
    object Success : RotationResult()
    object InvalidOldPin : RotationResult()
    data class WeakNewPin(val reason: String) : RotationResult()
    object VrkUnwrapFailed : RotationResult()
    object WrapFailed : RotationResult()
    data class CommitFailed(val error: String) : RotationResult()
}

enum class CredentialIntegrityResult {
    VALID,
    INVALID_PIN,
    AUTH_STATE_CORRUPTED
}

/**
 * Service responsible for atomic credential updates and Vault Root Key (VRK) re-wrapping.
 * Invariant: The underlying VRK is NEVER regenerated during PIN rotation; it is unwrapped with the old
 * credential and re-wrapped with the new credential using atomic file replacement.
 */
object CredentialRotationManager {
    private const val TAG = "CredentialRotationManager"

    /**
     * Rotates the Master PIN by unwrapping the existing VRK with [oldPin],
     * rewrapping the SAME VRK with [newPin]-derived KEK, atomically committing
     * the new wrap file, and then updating the PIN verifier in SettingsDataStore.
     */
    suspend fun rotateMasterPin(
        context: Context,
        oldPin: String,
        newPin: String,
        settingsDataStore: SettingsDataStore
    ): RotationResult {
        if (newPin.length < 4) {
            return RotationResult.WeakNewPin("PIN must be at least 4 digits")
        }

        // 1. Verify old credential
        if (!settingsDataStore.verifyMasterPin(oldPin)) {
            Log.w(TAG, "Master PIN rotation rejected: Old PIN verification failed")
            return RotationResult.InvalidOldPin
        }

        // 2. Unwrap EXISTING VRK
        val existingVrk = VaultKeyManager.unwrapVrkWithPin(context, oldPin, isDecoy = false)
            ?: VaultKeyManager.getActiveVrk()
        if (existingVrk == null || existingVrk.size != 32) {
            Log.e(TAG, "Master PIN rotation failed: Could not unwrap existing VRK")
            return RotationResult.VrkUnwrapFailed
        }

        // 3. Rewrap SAME VRK with new PIN using atomic file replacement
        val wrapSuccess = VaultKeyManager.writeVrkPinWrap(context, existingVrk, newPin, isDecoy = false)
        if (!wrapSuccess) {
            Log.e(TAG, "Master PIN rotation failed: Could not write new VRK wrap")
            return RotationResult.WrapFailed
        }

        // 4. Update credential verifier in SettingsDataStore
        return try {
            settingsDataStore.updateMasterPin(newPin)
            // Keep session active if currently authorized
            if (VaultKeyManager.isRealVaultAuthorized()) {
                VaultKeyManager.authorizeWithPin(context, newPin, isDecoy = false)
            }
            Log.i(TAG, "Master PIN rotated successfully without regenerating VRK")
            RotationResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Master PIN rotation failed during verifier commit", e)
            RotationResult.CommitFailed(e.message ?: "Commit failed")
        }
    }

    /**
     * Rotates the Master PIN using the in-memory active VRK when already in an authenticated session.
     */
    suspend fun rotateMasterPinWithActiveVrk(
        context: Context,
        newPin: String,
        settingsDataStore: SettingsDataStore
    ): RotationResult {
        if (newPin.length < 4) {
            return RotationResult.WeakNewPin("PIN must be at least 4 digits")
        }

        val vrk = VaultKeyManager.getActiveVrk()
        if (vrk == null || vrk.size != 32) {
            return RotationResult.VrkUnwrapFailed
        }

        val wrapSuccess = VaultKeyManager.writeVrkPinWrap(context, vrk, newPin, isDecoy = false)
        if (!wrapSuccess) {
            return RotationResult.WrapFailed
        }

        return try {
            settingsDataStore.updateMasterPin(newPin)
            VaultKeyManager.authorizeWithPin(context, newPin, isDecoy = false)
            RotationResult.Success
        } catch (e: Exception) {
            RotationResult.CommitFailed(e.message ?: "Commit failed")
        }
    }

    /**
     * Rotates or configures Decoy PIN.
     * If [newPin] is blank, disables decoy mode and removes decoy wrapper without touching real VRK.
     * If decoy exists, unwraps EXISTING decoy VRK and rewraps with new PIN.
     */
    suspend fun rotateDecoyPin(
        context: Context,
        oldPin: String,
        newPin: String,
        settingsDataStore: SettingsDataStore
    ): RotationResult {
        if (newPin.isBlank()) {
            // Disabling decoy PIN
            if (oldPin.isNotBlank() && !settingsDataStore.verifyDecoyPin(oldPin)) {
                return RotationResult.InvalidOldPin
            }
            try {
                File(context.filesDir, "decoy_vrk_pin_wrap.bin").delete()
                VaultSentinelManager.removeDecoySentinel(context)
                settingsDataStore.updateDecoyPin("")
                Log.i(TAG, "Decoy PIN disabled and wrapper safely removed")
                return RotationResult.Success
            } catch (e: Exception) {
                return RotationResult.CommitFailed(e.message ?: "Failed to disable decoy PIN")
            }
        }

        if (newPin.length < 4) {
            return RotationResult.WeakNewPin("Decoy PIN must be at least 4 digits")
        }

        val decoyWrapFile = File(context.filesDir, "decoy_vrk_pin_wrap.bin")
        if (decoyWrapFile.exists()) {
            // Rotating existing decoy PIN
            if (oldPin.isNotBlank() && !settingsDataStore.verifyDecoyPin(oldPin)) {
                return RotationResult.InvalidOldPin
            }
            val existingDecoyVrk = VaultKeyManager.unwrapVrkWithPin(context, oldPin, isDecoy = true)
            if (existingDecoyVrk == null || existingDecoyVrk.size != 32) {
                return RotationResult.VrkUnwrapFailed
            }
            val wrapSuccess = VaultKeyManager.writeVrkPinWrap(context, existingDecoyVrk, newPin, isDecoy = true)
            if (!wrapSuccess) {
                return RotationResult.WrapFailed
            }
            return try {
                settingsDataStore.updateDecoyPin(newPin)
                RotationResult.Success
            } catch (e: Exception) {
                RotationResult.CommitFailed(e.message ?: "Commit failed")
            }
        } else {
            // First time enabling decoy PIN
            VaultKeyManager.initializeVrkWithPin(context, newPin, isDecoy = true)
            return try {
                settingsDataStore.updateDecoyPin(newPin)
                RotationResult.Success
            } catch (e: Exception) {
                RotationResult.CommitFailed(e.message ?: "Commit failed")
            }
        }
    }

    /**
     * Integrity checker to verify that:
     * stored PIN verifier <-> PIN-derived KEK <-> VRK wrapper <-> actual VRK <-> sentinel
     * are completely consistent.
     */
    suspend fun verifyMasterCredentialIntegrity(
        context: Context,
        pin: String,
        settingsDataStore: SettingsDataStore
    ): CredentialIntegrityResult {
        val verifierPassed = settingsDataStore.verifyMasterPin(pin)
        if (!verifierPassed) {
            return CredentialIntegrityResult.INVALID_PIN
        }

        val unwrappedVrk = VaultKeyManager.unwrapVrkWithPin(context, pin, isDecoy = false)
        if (unwrappedVrk == null || unwrappedVrk.size != 32) {
            Log.e(TAG, "Integrity failure: Stored PIN verifier passed but VRK unwrap failed!")
            return CredentialIntegrityResult.AUTH_STATE_CORRUPTED
        }

        val sentinelValid = VaultSentinelManager.verifyVrk(context, unwrappedVrk, isDecoy = false)
        return if (sentinelValid) {
            CredentialIntegrityResult.VALID
        } else {
            Log.e(TAG, "Integrity failure: VRK unwrap succeeded but sentinel verification failed!")
            CredentialIntegrityResult.AUTH_STATE_CORRUPTED
        }
    }
}
