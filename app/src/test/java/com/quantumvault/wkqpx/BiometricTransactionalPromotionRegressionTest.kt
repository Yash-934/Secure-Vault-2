package com.quantumvault.wkqpx

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.quantumvault.wkqpx.data.local.SettingsDataStore
import com.quantumvault.wkqpx.security.SecurityAuditEngine
import com.quantumvault.wkqpx.security.VaultKeyManager
import com.quantumvault.wkqpx.security.VaultSentinelManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BiometricTransactionalPromotionRegressionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runBlocking {
        val settingsDataStore = SettingsDataStore(context)
        settingsDataStore.clearAllForTesting()
        File(context.filesDir, "vrk_pin_wrap.bin").delete()
        File(context.filesDir, "decoy_vrk_pin_wrap.bin").delete()
        File(context.filesDir, "vault_sentinel.bin").delete()
        File(context.filesDir, "decoy_vault_sentinel.bin").delete()
        File(context.filesDir, "biometric_wrap.bin").delete()
        File(context.filesDir, "biometric_wrap.bin.staged").delete()
        VaultKeyManager.lockVault()
    }

    @Test
    fun testP0_1_BiometricSlotPromotionPreservesEnvelopeDecryptability() {
        // 1. Authorize real vault with PIN
        val initialPin = "1234"
        VaultKeyManager.createVrkForFreshVault(context, initialPin)
        VaultKeyManager.authorizeWithPin(context, initialPin)
        assertTrue(VaultKeyManager.isRealVaultAuthorized())

        // 2. Initial Biometric Enrollment (Slot A)
        val enrollCryptoObj1 = VaultKeyManager.getBiometricEnrollCryptoObject(context)
        assertNotNull("Enrollment crypto object must be created", enrollCryptoObj1)
        val cipher1 = enrollCryptoObj1!!.cipher!!
        assertTrue("Provisioning envelope into Slot A must succeed", VaultKeyManager.provisionBiometricEnvelope(context, cipher1))

        // Verify committed slot is Slot 1 (Slot A)
        assertEquals(1L, VaultKeyManager.getActiveBiometricSlot(context))
        assertEquals(VaultKeyManager.ALIAS_BIOMETRIC_SLOT_A, VaultKeyManager.getActiveBiometricAlias(context))

        // 3. Lock vault and test unlock using Slot A key
        VaultKeyManager.lockVault()
        assertFalse(VaultKeyManager.isRealVaultAuthorized())

        val decryptCryptoObj1 = VaultKeyManager.getBiometricDecryptCryptoObject(context)
        assertNotNull("Decrypt crypto object must be retrieved for Slot A", decryptCryptoObj1)
        val decryptCipher1 = decryptCryptoObj1!!.cipher!!
        assertTrue("Unwrapping with Slot A key must succeed and authorize real vault", VaultKeyManager.unwrapBiometricSessionKey(context, decryptCipher1))
        assertTrue(VaultKeyManager.isRealVaultAuthorized())

        // 4. Re-enrollment into Slot B (Simulate user re-enrolling biometrics)
        val enrollCryptoObj2 = VaultKeyManager.getBiometricEnrollCryptoObject(context)
        assertNotNull("Re-enrollment crypto object must be created for Slot B", enrollCryptoObj2)
        val cipher2 = enrollCryptoObj2!!.cipher!!
        assertTrue("Provisioning envelope into Slot B must succeed", VaultKeyManager.provisionBiometricEnvelope(context, cipher2))

        // Verify committed slot is now Slot 2 (Slot B)
        assertEquals(2L, VaultKeyManager.getActiveBiometricSlot(context))
        assertEquals(VaultKeyManager.ALIAS_BIOMETRIC_SLOT_B, VaultKeyManager.getActiveBiometricAlias(context))

        // 5. Lock vault and test unlock using Slot B key
        VaultKeyManager.lockVault()
        assertFalse(VaultKeyManager.isRealVaultAuthorized())

        val decryptCryptoObj2 = VaultKeyManager.getBiometricDecryptCryptoObject(context)
        assertNotNull("Decrypt crypto object must be retrieved for Slot B", decryptCryptoObj2)
        val decryptCipher2 = decryptCryptoObj2!!.cipher!!
        assertTrue("Unwrapping with Slot B key must succeed and authorize real vault", VaultKeyManager.unwrapBiometricSessionKey(context, decryptCipher2))
        assertTrue(VaultKeyManager.isRealVaultAuthorized())
    }

    @Test
    fun testP0_3_BiometricAdversarialTamperAndFailClosed() {
        // Authorize and enroll
        VaultKeyManager.createVrkForFreshVault(context, "1234")
        VaultKeyManager.authorizeWithPin(context, "1234")
        val enrollCryptoObj = VaultKeyManager.getBiometricEnrollCryptoObject(context)
        VaultKeyManager.provisionBiometricEnvelope(context, enrollCryptoObj!!.cipher!!)
        val envelopeFile = File(context.filesDir, "biometric_wrap.bin")
        assertTrue(envelopeFile.exists())
        assertEquals(77L, envelopeFile.length())

        // Adversarial 1: Truncate envelope
        val originalBytes = envelopeFile.readBytes()
        envelopeFile.writeBytes(originalBytes.copyOfRange(0, 40))
        VaultKeyManager.lockVault()
        val truncatedDecryptObj = VaultKeyManager.getBiometricDecryptCryptoObject(context)
        // With truncated file, decrypt cipher must fail or reject
        if (truncatedDecryptObj?.cipher != null) {
            assertFalse(VaultKeyManager.unwrapBiometricSessionKey(context, truncatedDecryptObj.cipher!!))
        }

        // Adversarial 2: Tamper ciphertext byte (AEAD auth tag mismatch)
        val tamperedCiphertext = originalBytes.copyOf()
        tamperedCiphertext[70] = (tamperedCiphertext[70].toInt() xor 0xFF).toByte()
        envelopeFile.writeBytes(tamperedCiphertext)
        VaultKeyManager.lockVault()
        val tamperedDecryptObj = VaultKeyManager.getBiometricDecryptCryptoObject(context)
        assertNotNull(tamperedDecryptObj)
        assertFalse("Tampered AEAD ciphertext must fail unwrap", VaultKeyManager.unwrapBiometricSessionKey(context, tamperedDecryptObj!!.cipher!!))
        assertFalse("Vault must remain locked", VaultKeyManager.isRealVaultAuthorized())

        // Adversarial 3: Decoy realm injection
        val decoyRealmBytes = originalBytes.copyOf()
        decoyRealmBytes[5] = 0x02 // BIE1_REALM_DECOY
        envelopeFile.writeBytes(decoyRealmBytes)
        VaultKeyManager.lockVault()
        val decoyDecryptObj = VaultKeyManager.getBiometricDecryptCryptoObject(context)
        assertNotNull(decoyDecryptObj)
        assertFalse("Decoy realm envelope must be rejected from real vault unlock", VaultKeyManager.unwrapBiometricSessionKey(context, decoyDecryptObj!!.cipher!!))
        assertFalse("Vault must remain locked", VaultKeyManager.isRealVaultAuthorized())
    }

    @Test
    fun testP1_SecurityAuditEngineDynamicScoring() {
        val auditEngine = SecurityAuditEngine(context)
        val report = auditEngine.performSecurityAudit()

        assertNotNull(report)
        assertTrue("Security score must be calculated dynamically", report.score > 0)
        assertTrue("Audit must contain check items", report.checkItems.isNotEmpty())

        // Verify specific required security checks
        assertTrue("Argon2id KDF check must pass", auditEngine.checkArgon2idKdfTest())
        assertTrue("Private storage check must pass", auditEngine.checkPrivateStoragePath())
        assertTrue("Allow backup check must pass", auditEngine.checkAllowBackupDisabled())
        assertTrue("Native string masking check must pass", auditEngine.checkNativeStringMasking())
        assertTrue("Clipboard purge check must pass", auditEngine.checkClipboardPurgeCapability())
        assertTrue("Scrambled keypad check must pass", auditEngine.checkScrambledKeypadCapability())
    }
}
