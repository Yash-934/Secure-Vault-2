package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SettingsDataStore
import com.example.security.CredentialIntegrityResult
import com.example.security.CredentialRotationManager
import com.example.security.LegacyPasswordMigrator
import com.example.security.PasswordCryptoHelper
import com.example.security.PasswordDecryptResult
import com.example.security.RotationResult
import com.example.security.VaultKeyManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuantumVaultVrkRotationAndAuthTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var settingsDataStore: SettingsDataStore

    @Before
    fun setUp() {
        runBlocking {
            settingsDataStore = SettingsDataStore(context)
            VaultKeyManager.lockVault()
            File(context.filesDir, "vrk_wrap.bin").delete()
            File(context.filesDir, "decoy_vrk_pin_wrap.bin").delete()
            File(context.filesDir, "vault_sentinel.bin").delete()
            File(context.filesDir, "decoy_vault_sentinel.bin").delete()
            File(context.filesDir, "vrk_biometric_envelope.bin").delete()
        }
    }

    @Test
    fun testMasterPinRotationPreservesVrkAndDecryptsPreRotationData() {
        runBlocking {
            val initialPin = "1234"
            val rotatedPin = "9876"

            // Step 1: Initialize credentials and VRK
            settingsDataStore.initializeCredentials(initialPin)
            VaultKeyManager.initializeVrkWithPin(context, initialPin)
            val authSuccess = VaultKeyManager.authorizeWithPin(context, initialPin)
            assertTrue("Initial PIN authorization must succeed", authSuccess)
            assertTrue("Real vault must be cryptographically authorized", VaultKeyManager.isRealVaultAuthorized())

            // Step 2: Encrypt password record under initial VRK
            val secretPassword = "ConfidentialPassword#2026!"
            val encryptedBlob = PasswordCryptoHelper.encryptText(secretPassword)
            assertTrue("Encrypted blob must use QVPM2 format", encryptedBlob.startsWith("QVPM2:"))

            // Verify initial decryption succeeds
            val initialDecryptResult = PasswordCryptoHelper.decryptPassword(encryptedBlob)
            assertTrue(initialDecryptResult is PasswordDecryptResult.Success)
            assertEquals(secretPassword, (initialDecryptResult as PasswordDecryptResult.Success).plaintext)

            // Step 3: Rotate master PIN using CredentialRotationManager
            val rotationResult = CredentialRotationManager.rotateMasterPin(
                context = context,
                oldPin = initialPin,
                newPin = rotatedPin,
                settingsDataStore = settingsDataStore
            )
            assertTrue("PIN rotation must succeed", rotationResult is RotationResult.Success)

            // Step 4: Lock vault to clear in-memory key state
            VaultKeyManager.lockVault()
            assertFalse("Vault must be locked", VaultKeyManager.isRealVaultAuthorized())

            // Step 5: Old PIN must fail authorization
            val oldPinAuth = VaultKeyManager.authorizeWithPin(context, initialPin)
            assertFalse("Old PIN must no longer authorize", oldPinAuth)
            assertFalse(VaultKeyManager.isRealVaultAuthorized())

            // Step 6: New PIN must authorize and unwrap the EXACT SAME VRK
            val newPinAuth = VaultKeyManager.authorizeWithPin(context, rotatedPin)
            assertTrue("New PIN must successfully authorize", newPinAuth)
            assertTrue("Real vault must be cryptographically authorized with new PIN", VaultKeyManager.isRealVaultAuthorized())

            // Step 7: Decrypt pre-rotation data with post-rotation unlocked session
            val postRotationDecryptResult = PasswordCryptoHelper.decryptPassword(encryptedBlob)
            assertTrue(
                "Pre-rotation encrypted record must be readable post-rotation without corruption",
                postRotationDecryptResult is PasswordDecryptResult.Success
            )
            assertEquals(
                secretPassword,
                (postRotationDecryptResult as PasswordDecryptResult.Success).plaintext
            )

            // Step 8: Verify full credential and wrap file integrity
            val integrity = CredentialRotationManager.verifyMasterCredentialIntegrity(
                context = context,
                pin = rotatedPin,
                settingsDataStore = settingsDataStore
            )
            assertEquals("Credential and wrapper integrity check must be valid", CredentialIntegrityResult.VALID, integrity)
        }
    }

    @Test
    fun testDecoyPinRotationPreservesDecoyVrk() {
        runBlocking {
            val masterPin = "1234"
            val initialDecoyPin = "0000"
            val newDecoyPin = "7777"

            settingsDataStore.initializeCredentials(masterPin)
            VaultKeyManager.initializeVrkWithPin(context, masterPin)
            settingsDataStore.updateDecoyPin(initialDecoyPin)
            VaultKeyManager.initializeVrkWithPin(context, initialDecoyPin, isDecoy = true)

            // Authorize with initial decoy PIN
            val initialAuth = VaultKeyManager.authorizeWithPin(context, initialDecoyPin, isDecoy = true)
            assertTrue("Initial decoy PIN must authorize", initialAuth)
            assertTrue("Decoy vault must be cryptographically authorized", VaultKeyManager.isDecoyVaultAuthorized())

            // Rotate decoy PIN
            val rotationResult = CredentialRotationManager.rotateDecoyPin(
                context = context,
                oldPin = initialDecoyPin,
                newPin = newDecoyPin,
                settingsDataStore = settingsDataStore
            )
            assertTrue("Decoy PIN rotation must succeed", rotationResult is RotationResult.Success)

            // Lock vault
            VaultKeyManager.lockVault()
            assertFalse("Decoy vault must be locked", VaultKeyManager.isDecoyVaultAuthorized())

            // Verify old decoy PIN fails
            val oldDecoyAuth = VaultKeyManager.authorizeWithPin(context, initialDecoyPin, isDecoy = true)
            assertFalse("Old decoy PIN must not authorize", oldDecoyAuth)

            // Verify new decoy PIN succeeds
            val newDecoyAuth = VaultKeyManager.authorizeWithPin(context, newDecoyPin, isDecoy = true)
            assertTrue("New decoy PIN must authorize", newDecoyAuth)
            assertTrue("Decoy vault must be authorized with new decoy PIN", VaultKeyManager.isDecoyVaultAuthorized())
        }
    }

    @Test
    fun testVaultSentinelVerificationAndTamperRejection() {
        val pin = "2468"
        VaultKeyManager.initializeVrkWithPin(context, pin)

        // Verify sentinel exists and is valid
        val sentinelFile = File(context.filesDir, "vault_sentinel.bin")
        assertTrue("Sentinel file must be created on VRK initialization", sentinelFile.exists())

        // Authorize with valid PIN
        val authResult = VaultKeyManager.authorizeWithPin(context, pin)
        assertTrue("Authorization with valid sentinel must succeed", authResult)

        // Tamper with the sentinel file
        val sentinelBytes = sentinelFile.readBytes()
        sentinelBytes[sentinelBytes.size - 1] = (sentinelBytes[sentinelBytes.size - 1].toInt() xor 0xFF).toByte()
        sentinelFile.writeBytes(sentinelBytes)

        // Re-attempt authorization; sentinel verification MUST fail and lock down
        VaultKeyManager.lockVault()
        val tamperedAuth = VaultKeyManager.authorizeWithPin(context, pin)
        assertFalse("Tampered sentinel must cause authorization failure", tamperedAuth)
        assertFalse("Session must not be authorized when sentinel fails", VaultKeyManager.isRealVaultAuthorized())
    }

    @Test
    fun testLegacyPasswordMigratorIsIsolatedAndConvertsToV2() {
        val legacyPlaintext = "LegacyHistoricalSecret#99"
        // Create a legacy encrypted string using the legacy isolated cipher
        val legacyEncrypted = LegacyPasswordMigrator.encryptWithLegacyKeyForTesting(legacyPlaintext)
        assertFalse("Legacy record must NOT have QVPM2 prefix", legacyEncrypted.startsWith("QVPM2:"))

        // Unlock real vault with a valid VRK
        VaultKeyManager.initializeVrkWithPin(context, "1111")
        VaultKeyManager.authorizeWithPin(context, "1111")

        // Decrypt using PasswordCryptoHelper
        val decryptResult = PasswordCryptoHelper.decryptPassword(legacyEncrypted)
        assertTrue(
            "Legacy record must return LegacyRecordRequiresMigration",
            decryptResult is PasswordDecryptResult.LegacyRecordRequiresMigration
        )
        assertEquals(legacyPlaintext, (decryptResult as PasswordDecryptResult.LegacyRecordRequiresMigration).legacyPlaintext)

        // Migrate the legacy plaintext to modern V2 format
        val migratedBlob = PasswordCryptoHelper.encryptText(legacyPlaintext)
        assertTrue("Migrated blob must use QVPM2 format", migratedBlob.startsWith("QVPM2:"))

        // Verify modern decryption of migrated blob
        val modernDecryptResult = PasswordCryptoHelper.decryptPassword(migratedBlob)
        assertTrue("Modern decryption must succeed", modernDecryptResult is PasswordDecryptResult.Success)
        assertEquals(legacyPlaintext, (modernDecryptResult as PasswordDecryptResult.Success).plaintext)
    }

    @Test
    fun testTypedDecryptionResultsOnLockedVault() {
        VaultKeyManager.lockVault()
        val result = PasswordCryptoHelper.decryptPassword("QVPM2:AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=")
        assertTrue("Locked vault must return VaultLocked result", result is PasswordDecryptResult.VaultLocked)
    }

    @Test
    fun testBiometricHardInvariantDetection() {
        runBlocking {
            // Case: Preference is true, but envelope file does not exist
            settingsDataStore.setBiometricsEnabled(true)
            val envelopeFile = File(context.filesDir, "vrk_biometric_envelope.bin")
            envelopeFile.delete()

            val status = VaultKeyManager.validateBiometricEnrollmentState(context, settingsDataStore)
            assertEquals(
                "Missing envelope must return MISSING_ENVELOPE and reset preference",
                com.example.security.BiometricEnrollmentStatus.MISSING_ENVELOPE,
                status
            )
            assertFalse(
                "SettingsDataStore biometrics flag must be reset to false",
                settingsDataStore.settingsFlow.first().isBiometricsEnabled
            )
        }
    }
}
