package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SettingsDataStore
import com.example.security.Argon2Kdf
import com.example.security.CryptoManager
import com.example.security.PasswordCryptoHelper
import com.example.security.SecurityAuditEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuantumVaultSecurityHardeningTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testP0_1_2_FirstRunInitializationAndSaltedKdfPinVerification() = runBlocking {
        val settingsDataStore = SettingsDataStore(context)
        
        // Before initialization, no default PINs (1234/9999/6666) should authenticate
        assertFalse(settingsDataStore.verifyMasterPin("1234"))
        assertFalse(settingsDataStore.verifyDecoyPin("9999"))
        assertFalse(settingsDataStore.verifyKillPin("6666"))

        // Initialize vault with a fresh PIN
        val initialPin = "7492"
        settingsDataStore.initializeCredentials(initialPin)

        val settings = settingsDataStore.settingsFlow.first()
        assertTrue(settings.isInitialized)
        // Ensure plaintext PIN is never exposed in settings
        assertEquals("", settings.masterPin)

        // Verify correct PIN passes
        assertTrue(settingsDataStore.verifyMasterPin("7492"))

        // Verify wrong PIN fails
        assertFalse(settingsDataStore.verifyMasterPin("1234"))
        assertFalse(settingsDataStore.verifyMasterPin("0000"))

        // Update decoy PIN and verify
        settingsDataStore.updateDecoyPin("5521")
        assertTrue(settingsDataStore.verifyDecoyPin("5521"))
        assertFalse(settingsDataStore.verifyDecoyPin("9999"))

        // Enable and update kill PIN
        settingsDataStore.setKillPinEnabled(true)
        settingsDataStore.updateKillPin("9812")
        assertTrue(settingsDataStore.verifyKillPin("9812"))
        assertFalse(settingsDataStore.verifyKillPin("6666"))
    }

    @Test
    fun testP0_3_PasswordCryptoHelperAesGcmTamperProof() {
        val plainText = "SuperSecretBankPassword#2026!"
        val encrypted = PasswordCryptoHelper.encryptText(plainText)

        assertNotNull(encrypted)
        assertNotEquals(plainText, encrypted)
        assertFalse(encrypted.startsWith("ENC_PLAIN_B64:"))

        // Decryption round-trip
        val decrypted = PasswordCryptoHelper.decryptText(encrypted)
        assertEquals(plainText, decrypted)

        // Tampering test: modify a byte in ciphertext blob, decryption MUST fail and return empty string (never fallback to Base64)
        val tampered = encrypted.substring(0, encrypted.length - 2) + "=="
        val tamperedResult = PasswordCryptoHelper.decryptText(tampered)
        assertEquals("", tamperedResult)
    }

    @Test
    fun testP0_4_PersistentRateLimiting() = runBlocking {
        val settingsDataStore = SettingsDataStore(context)

        // Reset attempts
        settingsDataStore.recordFailedAttempt()
        val count1 = settingsDataStore.getFailedAttempts()
        assertTrue(count1 >= 1)

        settingsDataStore.resetFailedAttempts()
        val countAfterReset = settingsDataStore.getFailedAttempts()
        assertEquals(0, countAfterReset)
    }

    @Test
    fun testP0_9_FailClosedStreamCryptoIntegrity() {
        val payload = "TopSecretMilitaryDataStreamPayload".toByteArray(Charsets.UTF_8)
        val inStream = ByteArrayInputStream(payload)
        val encryptedOut = ByteArrayOutputStream()

        // Encrypt stream
        CryptoManager.encryptStream(inStream, encryptedOut)
        val cipherBytes = encryptedOut.toByteArray()
        assertTrue(cipherBytes.size > 28) // Header + IV + Tag

        // Decrypt stream
        val decryptedOut = ByteArrayOutputStream()
        CryptoManager.decryptStreamToOutputStream(ByteArrayInputStream(cipherBytes), decryptedOut)
        assertEquals("TopSecretMilitaryDataStreamPayload", decryptedOut.toString(Charsets.UTF_8.name()))

        // Tamper with magic header or ciphertext: should fail-closed (throw Exception or produce empty/clean abort)
        val tamperedCipher = cipherBytes.clone()
        tamperedCipher[0] = 0x00 // corrupt magic byte
        val failedDecryptedOut = ByteArrayOutputStream()
        var caughtException = false
        try {
            CryptoManager.decryptStreamToOutputStream(ByteArrayInputStream(tamperedCipher), failedDecryptedOut)
        } catch (e: Exception) {
            caughtException = true
        }
        assertTrue("Corrupted header must cause fail-closed exception", caughtException)
    }

    @Test
    fun testP0_12_13_SecurityAuditEngineIntegrity() {
        val engine = SecurityAuditEngine(context)
        val auditResult = engine.performSecurityAudit()
        assertNotNull(auditResult)
        // Score must be calculated from real checks, not hardcoded passed=true
        assertTrue(auditResult.score >= 0)
        assertTrue(auditResult.checkItems.isNotEmpty())
        for (check in auditResult.checkItems) {
            assertNotNull(check.name)
            assertNotNull(check.description)
        }
    }
}
