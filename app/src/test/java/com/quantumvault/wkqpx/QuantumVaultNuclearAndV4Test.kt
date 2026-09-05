package com.quantumvault.wkqpx

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.quantumvault.wkqpx.data.AppDatabase
import com.quantumvault.wkqpx.data.VaultItem
import com.quantumvault.wkqpx.data.VaultRepository
import com.quantumvault.wkqpx.data.local.SettingsDataStore
import com.quantumvault.wkqpx.security.SelfDestructManager
import com.quantumvault.wkqpx.security.SelfDestructStatus
import com.quantumvault.wkqpx.security.VaultBackupManager
import com.quantumvault.wkqpx.security.VaultGenerationManager
import com.quantumvault.wkqpx.security.VaultKeyManager
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuantumVaultNuclearAndV4Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        runBlocking {
            val settingsDataStore = SettingsDataStore(context)
            settingsDataStore.clearAllForTesting()
            VaultKeyManager.lockVault()
            File(context.filesDir, "vrk_wrap.bin").delete()
            File(context.filesDir, "decoy_vrk_pin_wrap.bin").delete()
            File(context.filesDir, "vault_sentinel.bin").delete()
            File(context.filesDir, "decoy_vault_sentinel.bin").delete()
            File(context.filesDir, "vrk_biometric_envelope.bin").delete()
        }
    }

    @Test
    fun testSelfDestructManagerAuthoritativeKeyDestruction() = runBlocking {
        VaultKeyManager.createVrkForFreshVault(context, "1234")
        VaultKeyManager.authorizeWithPin(context, "1234")

        // Seed a dummy test file in filesDir
        val testFile = File(context.filesDir, "test_sensitive_data.bin")
        testFile.writeText("SECRET_CONTENT_TO_SHRED")
        assertTrue(testFile.exists())

        // Execute Nuclear Self-Destruct
        val result = SelfDestructManager.executeNuclearSelfDestruct(context)
        assertNotNull(result)
        assertFalse("Test file should be deleted/shredded", testFile.exists())
        assertTrue("Storage wiped flag must be true", result.storageWiped)
        assertTrue("Database destroyed flag must be true", result.databaseDestroyed)
        assertTrue("Status must be COMPLETE or PARTIAL", result.status == SelfDestructStatus.COMPLETE || result.status == SelfDestructStatus.PARTIAL)
    }

    @Test
    fun testShredFileOverwritesAndDeletes() {
        val sensitiveFile = File(context.filesDir, "file_to_shred.bin")
        sensitiveFile.writeBytes(ByteArray(1024) { 0xFF.toByte() })
        assertTrue(sensitiveFile.exists())

        SelfDestructManager.shredFile(sensitiveFile)
        assertFalse("File must be deleted after shredding", sensitiveFile.exists())
    }

    @Test
    fun testV4BackupManifestTamperDetectionFailsClosed() = runBlocking {
        VaultKeyManager.createVrkForFreshVault(context, "1234")
        VaultKeyManager.authorizeWithPin(context, "1234")
        val db = AppDatabase.getDatabase(context)
        val repo = VaultRepository(db.vaultDao())

        val backupPassword = "V4BackupPassword2026!"
        val salt = ByteArray(16) { 0x33.toByte() }
        val derivedKey = com.quantumvault.wkqpx.security.Argon2Kdf.deriveKey(
            backupPassword.toCharArray(),
            salt,
            memoryKb = 1024,
            iterations = 1
        )

        // Construct a VLT_BCK3 archive with a tampered V4 manifest (checksum mismatch)
        val backupOut = ByteArrayOutputStream()
        backupOut.write("VLT_BCK3".toByteArray(Charsets.UTF_8))
        backupOut.write(0) // flags: 0 (password only)
        backupOut.write(salt) // 16 bytes
        val bb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.BIG_ENDIAN)
        bb.putInt(1024)
        bb.putInt(1)
        bb.putInt(1)
        bb.putInt(0)
        backupOut.write(bb.array())

        val chunkedOut = VaultBackupManager.ChunkedGcmOutputStream(backupOut, derivedKey)
        ZipOutputStream(chunkedOut).use { zos ->
            // Manifest declaring an expected checksum
            val manifestJson = """
            {
                "formatVersion": 4,
                "sourceRealm": 1,
                "itemsCount": 1,
                "foldersCount": 0,
                "passwordsCount": 0,
                "logsCount": 0,
                "fileInventory": [
                    {
                        "fileName": "file_1.enc",
                        "originalName": "doc.pdf",
                        "sizeBytes": 100,
                        "sha256Hex": "0000000000000000000000000000000000000000000000000000000000000000",
                        "mimeType": "application/pdf",
                        "folderName": "Root"
                    }
                ],
                "createdAt": 1700000000000
            }
            """.trimIndent()

            val itemsJson = """
            [
                {
                    "id": 0,
                    "originalName": "doc.pdf",
                    "encryptedFileName": "file_1.enc",
                    "sizeBytes": 100,
                    "mimeType": "application/pdf",
                    "addedTimestamp": 1700000000000,
                    "isVideo": false,
                    "folderName": "Root"
                }
            ]
            """.trimIndent()
            zos.putNextEntry(ZipEntry("items.json"))
            zos.write(itemsJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("backup_manifest_v4.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // File content whose actual SHA-256 does NOT match the manifest's declared 000...000
            zos.putNextEntry(ZipEntry("vault_data_v2/file_1.enc"))
            zos.write("TAMPERED_OR_CORRUPT_PAYLOAD_BYTES".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        // Restore attempt: MUST fail due to checksum validation mismatch
        val restoreInput = ByteArrayInputStream(backupOut.toByteArray())
        val result = VaultBackupManager.importMasterBackup(
            context = context,
            masterPassword = backupPassword,
            inputStream = restoreInput,
            vaultRepository = repo,
            isReplaceMode = false
        )

        assertFalse("Backup with tampered checksum must fail", result.isSuccess)
        val exceptionMessage = result.exceptionOrNull()?.message ?: ""
        assertTrue(
            "Exception must indicate checksum mismatch or verification failure: $exceptionMessage",
            exceptionMessage.contains("Checksum mismatch") || exceptionMessage.contains("tampered")
        )
    }

    @Test
    fun testV4BackupRealmIsolationFailsOnDecoyCrossRestore() = runBlocking {
        VaultKeyManager.createVrkForFreshVault(context, "1234")
        VaultKeyManager.authorizeWithPin(context, "1234") // Authorize REAL vault
        val db = AppDatabase.getDatabase(context)
        val repo = VaultRepository(db.vaultDao())

        val backupPassword = "V4BackupPassword2026!"
        val salt = ByteArray(16) { 0x44.toByte() }
        val derivedKey = com.quantumvault.wkqpx.security.Argon2Kdf.deriveKey(
            backupPassword.toCharArray(),
            salt,
            memoryKb = 1024,
            iterations = 1
        )

        val backupOut = ByteArrayOutputStream()
        backupOut.write("VLT_BCK3".toByteArray(Charsets.UTF_8))
        backupOut.write(0) // flags: 0
        backupOut.write(salt)
        val bb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.BIG_ENDIAN)
        bb.putInt(1024)
        bb.putInt(1)
        bb.putInt(1)
        bb.putInt(0)
        backupOut.write(bb.array())

        val chunkedOut = VaultBackupManager.ChunkedGcmOutputStream(backupOut, derivedKey)
        ZipOutputStream(chunkedOut).use { zos ->
            // Manifest declaring sourceRealm = 2 (Decoy backup)
            val manifestJson = """
            {
                "formatVersion": 4,
                "sourceRealm": 2,
                "itemsCount": 0,
                "foldersCount": 0,
                "passwordsCount": 0,
                "logsCount": 0,
                "fileInventory": [],
                "createdAt": 1700000000000
            }
            """.trimIndent()

            zos.putNextEntry(ZipEntry("backup_manifest_v4.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        // Attempt to import Decoy backup while logged in to Real vault: MUST be rejected
        val restoreInput = ByteArrayInputStream(backupOut.toByteArray())
        val result = VaultBackupManager.importMasterBackup(
            context = context,
            masterPassword = backupPassword,
            inputStream = restoreInput,
            vaultRepository = repo,
            isReplaceMode = false
        )

        assertFalse("Cross-realm restore must fail", result.isSuccess)
        val exceptionMessage = result.exceptionOrNull()?.message ?: ""
        assertTrue(
            "Exception must indicate realm mismatch: $exceptionMessage",
            exceptionMessage.lowercase().contains("realm mismatch") || exceptionMessage.lowercase().contains("decoy")
        )
    }

    @Test
    fun testV4BackupRejectsDuplicateZipEntries() = runBlocking {
        VaultKeyManager.createVrkForFreshVault(context, "1234")
        VaultKeyManager.authorizeWithPin(context, "1234")
        val db = AppDatabase.getDatabase(context)
        val repo = VaultRepository(db.vaultDao())

        val backupPassword = "V4BackupPassword2026!"
        val salt = ByteArray(16) { 0x55.toByte() }
        val derivedKey = com.quantumvault.wkqpx.security.Argon2Kdf.deriveKey(
            backupPassword.toCharArray(),
            salt,
            memoryKb = 1024,
            iterations = 1
        )

        val zip1 = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry("vault_manifest.json"))
                zos.write("[]".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }.toByteArray()

        val zip2 = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry("vault_manifest.json"))
                zos.write("[]".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }.toByteArray()

        val backupOut = ByteArrayOutputStream()
        backupOut.write("VLT_BCK4".toByteArray(Charsets.UTF_8))
        backupOut.write(0)
        backupOut.write(salt)
        val bb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.BIG_ENDIAN)
        bb.putInt(1024)
        bb.putInt(1)
        bb.putInt(1)
        bb.putInt(0)
        backupOut.write(bb.array())

        val chunkedOut = VaultBackupManager.ChunkedGcmOutputStream(backupOut, derivedKey)
        chunkedOut.write(zip1)
        chunkedOut.write(zip2)
        chunkedOut.close()

        val restoreInput = ByteArrayInputStream(backupOut.toByteArray())
        val result = VaultBackupManager.importMasterBackup(
            context = context,
            masterPassword = backupPassword,
            inputStream = restoreInput,
            vaultRepository = repo,
            isReplaceMode = false
        )

        assertFalse("Duplicate entry archive must fail", result.isSuccess)
        val exceptionMessage = result.exceptionOrNull()?.message ?: ""
        assertTrue(
            "Exception must indicate duplicate entry rejection or corruption: $exceptionMessage",
            exceptionMessage.contains("Duplicate entry") || exceptionMessage.contains("ambiguity") || exceptionMessage.contains("failed")
        )
    }

    @Test
    fun testV4MissingManifestFailsClosed() = runBlocking {
        VaultKeyManager.createVrkForFreshVault(context, "1234")
        VaultKeyManager.authorizeWithPin(context, "1234")
        val db = AppDatabase.getDatabase(context)
        val repo = VaultRepository(db.vaultDao())

        val backupPassword = "V4BackupPassword2026!"
        val salt = ByteArray(16) { 0x66.toByte() }
        val derivedKey = com.quantumvault.wkqpx.security.Argon2Kdf.deriveKey(
            backupPassword.toCharArray(),
            salt,
            memoryKb = 1024,
            iterations = 1
        )

        val backupOut = ByteArrayOutputStream()
        backupOut.write("VLT_BCK4".toByteArray(Charsets.UTF_8))
        backupOut.write(0)
        backupOut.write(salt)
        val bb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.BIG_ENDIAN)
        bb.putInt(1024)
        bb.putInt(1)
        bb.putInt(1)
        bb.putInt(0)
        backupOut.write(bb.array())

        val chunkedOut = VaultBackupManager.ChunkedGcmOutputStream(backupOut, derivedKey)
        ZipOutputStream(chunkedOut).use { zos ->
            // Omitting backup_manifest_v4.json in a V4 backup
            zos.putNextEntry(ZipEntry("vault_manifest.json"))
            zos.write("[]".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val restoreInput = ByteArrayInputStream(backupOut.toByteArray())
        val result = VaultBackupManager.importMasterBackup(
            context = context,
            masterPassword = backupPassword,
            inputStream = restoreInput,
            vaultRepository = repo,
            isReplaceMode = false
        )

        assertFalse("V4 backup with missing manifest must fail", result.isSuccess)
        val exceptionMessage = result.exceptionOrNull()?.message ?: ""
        assertTrue(
            "Exception must indicate missing manifest: $exceptionMessage",
            exceptionMessage.contains("Strict V4") || exceptionMessage.contains("validation failed") || exceptionMessage.contains("manifest")
        )
    }

    @Test
    fun `test complete V4 export and atomic generational import roundtrip`() = runBlocking {
        VaultKeyManager.createVrkForFreshVault(context, "1234")
        VaultKeyManager.authorizeWithPin(context, "1234")

        val db = AppDatabase.getDatabase(context)
        val repo = VaultRepository(db.vaultDao())
        val backupPassword = "StrongBackupPassword99#"
        val initialGen = VaultGenerationManager.getActiveGeneration(context, isDecoy = false)

        // Seed a sample vault item and physical file properly encrypted with CryptoManager
        val vaultDir = repo.getVaultDirectory(context)
        val encFileName = "test_enc_file_${System.currentTimeMillis()}.bin"
        val physicalFile = File(vaultDir, encFileName)
        val plainBytes = "Confidential Encrypted Content".toByteArray(Charsets.UTF_8)
        java.io.FileOutputStream(physicalFile).use { fos ->
            com.quantumvault.wkqpx.security.CryptoManager.encryptStream(ByteArrayInputStream(plainBytes), fos)
        }

        val item = VaultItem(
            id = 1,
            originalName = "test_document.txt",
            encryptedFileName = encFileName,
            sizeBytes = plainBytes.size.toLong(),
            mimeType = "text/plain",
            addedTimestamp = System.currentTimeMillis(),
            isVideo = false,
            folderName = "Documents"
        )
        db.vaultDao().insertVaultItem(item)

        val exportedBackupStream = ByteArrayOutputStream()
        val exportResult = VaultBackupManager.exportMasterBackup(
            context = context,
            masterPassword = backupPassword,
            outputStream = exportedBackupStream,
            vaultRepository = repo,
            isDeviceLocked = false
        )
        assertTrue("Export must succeed: ${exportResult.exceptionOrNull()?.message}", exportResult.isSuccess)
        val backupBytes = exportedBackupStream.toByteArray()
        assertTrue("Backup bytes must be non-empty", backupBytes.isNotEmpty())

        // Clear local state
        db.vaultDao().deleteAllItems()
        physicalFile.delete()
        assertEquals(0, db.vaultDao().getAllItemsSync().size)
        assertFalse(File(vaultDir, encFileName).exists())

        // Execute import in REPLACE mode (atomic generation swap)
        val importResult = VaultBackupManager.importMasterBackup(
            context = context,
            masterPassword = backupPassword,
            inputStream = ByteArrayInputStream(backupBytes),
            vaultRepository = repo,
            isReplaceMode = true
        )

        assertTrue("Import must succeed: ${importResult.exceptionOrNull()?.message}", importResult.isSuccess)
        assertEquals(1, importResult.getOrNull())

        // Verify item restored in DB
        val restoredItems = db.vaultDao().getAllItemsSync()
        assertEquals(1, restoredItems.size)
        assertEquals("test_document.txt", restoredItems[0].originalName)

        // Verify physical file restored in vaultDir and decrypts correctly
        val restoredPhysicalFile = File(vaultDir, restoredItems[0].encryptedFileName)
        assertTrue("Restored file must exist in vault directory", restoredPhysicalFile.exists())
        val decryptedOut = ByteArrayOutputStream()
        java.io.FileInputStream(restoredPhysicalFile).use { fis ->
            com.quantumvault.wkqpx.security.CryptoManager.decryptStreamToOutputStream(fis, decryptedOut)
        }
        assertEquals("Confidential Encrypted Content", decryptedOut.toString(Charsets.UTF_8.name()))

        // Verify generation incremented
        val nextGen = VaultGenerationManager.getActiveGeneration(context, isDecoy = false)
        assertTrue("Generation epoch must increment after restore (initial=$initialGen, next=$nextGen)", nextGen > initialGen)
    }
}

