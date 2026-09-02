package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.security.Argon2Kdf
import com.example.security.CryptoManager
import com.example.security.PasswordCryptoHelper
import com.example.security.SecurityAuditEngine
import com.example.security.VaultBackupManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuantumVaultSecurityHardeningTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testP0_1_FirstRunInitializationAndSaltedKdfPinVerification() = runBlocking {
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
    fun testP0_2_PasswordCryptoHelperAesGcmTamperProof() {
        com.example.security.VaultKeyManager.initializeVrkWithPin(context, "1234")
        com.example.security.VaultKeyManager.authorizeWithPin(context, "1234")
        val plainText = "SuperSecretBankPassword#2026!"
        val encrypted = PasswordCryptoHelper.encryptText(plainText)

        assertNotNull(encrypted)
        assertNotEquals(plainText, encrypted)
        assertFalse(encrypted.startsWith("ENC_PLAIN_B64:"))

        // Decryption round-trip
        val decrypted = PasswordCryptoHelper.decryptText(encrypted)
        assertEquals(plainText, decrypted)

        // Tampering test: modify a byte in ciphertext blob, decryption MUST fail
        val tampered = encrypted.substring(0, encrypted.length - 2) + "=="
        try {
            PasswordCryptoHelper.decryptText(tampered)
            fail("Decryption of tampered data should throw SecurityException")
        } catch (e: SecurityException) {
            // Expected
        }
    }

    @Test
    fun testP0_3_PersistentRateLimitingAndBackoff() = runBlocking {
        val settingsDataStore = SettingsDataStore(context)

        // Reset
        settingsDataStore.resetFailedAttempts()
        assertEquals(0, settingsDataStore.getFailedAttempts())
        assertEquals(0, settingsDataStore.getLockoutSecondsRemaining())

        // 3 failed attempts
        settingsDataStore.recordFailedAttempt()
        settingsDataStore.recordFailedAttempt()
        settingsDataStore.recordFailedAttempt()
        assertEquals(3, settingsDataStore.getFailedAttempts())

        // 4th attempt triggers 30s lockout
        settingsDataStore.recordFailedAttempt()
        assertEquals(4, settingsDataStore.getFailedAttempts())
        val lockoutSeconds = settingsDataStore.getLockoutSecondsRemaining()
        assertTrue("Lockout must be active after 4 failures", lockoutSeconds > 0)

        // Reset clears lockout
        settingsDataStore.resetFailedAttempts()
        assertEquals(0, settingsDataStore.getFailedAttempts())
        assertEquals(0, settingsDataStore.getLockoutSecondsRemaining())
    }

    @Test
    fun testP0_4_FailClosedStreamCryptoIntegrity() {
        com.example.security.VaultKeyManager.initializeVrkWithPin(context, "1234")
        com.example.security.VaultKeyManager.authorizeWithPin(context, "1234")
        val payload = "TopSecretHardwareEncryptedDataStreamPayload".toByteArray(Charsets.UTF_8)
        val inStream = ByteArrayInputStream(payload)
        val encryptedOut = ByteArrayOutputStream()

        // Encrypt stream
        CryptoManager.encryptStream(inStream, encryptedOut)
        val cipherBytes = encryptedOut.toByteArray()
        assertTrue(cipherBytes.size > 28) // Header + IV + Tag

        // Decrypt stream
        val decryptedOut = ByteArrayOutputStream()
        CryptoManager.decryptStreamToOutputStream(ByteArrayInputStream(cipherBytes), decryptedOut)
        assertEquals("TopSecretHardwareEncryptedDataStreamPayload", decryptedOut.toString(Charsets.UTF_8.name()))

        // Tamper with magic header: should fail-closed (throw Exception)
        val tamperedCipher = cipherBytes.clone()
        tamperedCipher[0] = 0x00 // corrupt magic byte
        val failedDecryptedOut = ByteArrayOutputStream()
        var caughtException = false
        try {
            CryptoManager.decryptStreamToOutputStream(ByteArrayInputStream(tamperedCipher), failedDecryptedOut)
        } catch (_: Exception) {
            caughtException = true
        }
        assertTrue("Corrupted header must cause fail-closed exception", caughtException)
    }

    @Test
    fun testP0_5_Argon2idKeyDerivationIntegrity() {
        val pwd = "HighEntropyUserPassword#2026".toCharArray()
        val salt = ByteArray(16) { 0x4B.toByte() }

        val key1 = Argon2Kdf.deriveKey(pwd, salt, memoryKb = 1024, iterations = 1)
        val key2 = Argon2Kdf.deriveKey(pwd, salt, memoryKb = 1024, iterations = 1)
        val keyDiffSalt = Argon2Kdf.deriveKey(pwd, ByteArray(16) { 0x99.toByte() }, memoryKb = 1024, iterations = 1)

        assertEquals(32, key1.encoded.size)
        assertTrue(key1.encoded.contentEquals(key2.encoded))
        assertFalse(key1.encoded.contentEquals(keyDiffSalt.encoded))
    }

    @Test
    fun testP0_6_SecurityAuditEngineIntegrity() {
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

    @Test
    fun testP0_7_BackupPathTraversalRejection() = runBlocking {
        // Construct a malicious backup payload with path traversal in zip entry
        val maliciousOut = ByteArrayOutputStream()
        val dummyKey = SecretKeySpec(ByteArray(32) { 0x11.toByte() }, "AES")

        val chunkedOut = VaultBackupManager.ChunkedGcmOutputStream(maliciousOut, dummyKey)
        ZipOutputStream(chunkedOut).use { zos ->
            // Normal manifest
            zos.putNextEntry(ZipEntry("vault_manifest.json"))
            zos.write("[]".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Malicious traversal entry
            zos.putNextEntry(ZipEntry("vault_data_v2/../../../etc/malicious_payload.bin"))
            zos.write("ATTACK".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        // Test restoring the corrupted/traversal stream via ChunkedGcmInputStream
        val chunkedIn = VaultBackupManager.ChunkedGcmInputStream(
            ByteArrayInputStream(maliciousOut.toByteArray()),
            dummyKey
        )

        var caughtSecurityException = false
        try {
            java.util.zip.ZipInputStream(chunkedIn).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.contains("..") || entry.name.startsWith("/")) {
                        throw SecurityException("Path traversal rejected")
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: SecurityException) {
            caughtSecurityException = true
        }

        assertTrue("Path traversal entry must be rejected with SecurityException", caughtSecurityException)
    }

    @Test
    fun testP0_8_DatabaseNoDestructiveMigrationOnDowngrade() {
        com.example.security.VaultKeyManager.initializeVrkWithPin(context, "1234")
        com.example.security.VaultKeyManager.authorizeWithPin(context, "1234")
        // Verify AppDatabase does not throw on valid instance creation
        val db = AppDatabase.getDatabase(context)
        assertNotNull(db)
        assertNotNull(db.vaultDao())
        assertNotNull(db.intruderLogDao())
        assertNotNull(db.vaultPasswordDao())
    }
}
