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
import com.quantumvault.wkqpx.security.RestoreFaultPhase
import com.quantumvault.wkqpx.security.InvalidBackupPasswordException
import com.quantumvault.wkqpx.security.CorruptedBackupException
import com.quantumvault.wkqpx.security.BackupManifestIntegrityException
import com.quantumvault.wkqpx.security.GenerationCorruptionException
import com.quantumvault.wkqpx.security.VaultRestoreJournal
import com.quantumvault.wkqpx.security.RestoreJournalRecord
import com.quantumvault.wkqpx.security.RestoreJournalState
import com.quantumvault.wkqpx.security.Argon2Kdf
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

    @Test
    fun `test fault injection after FS swap rolls back filesystem and preserves generation`() = runBlocking {
        VaultKeyManager.createVrkForFreshVault(context, "1234")
        VaultKeyManager.authorizeWithPin(context, "1234")
        val db = AppDatabase.getDatabase(context)
        val repo = VaultRepository(db.vaultDao())
        val backupPassword = "FaultPassword123!"

        // Create initial item
        val vaultDir = repo.getVaultDirectory(context)
        val initialFile = File(vaultDir, "initial_item.bin")
        java.io.FileOutputStream(initialFile).use { fos ->
            com.quantumvault.wkqpx.security.CryptoManager.encryptStream(ByteArrayInputStream("INITIAL_CONTENT".toByteArray(Charsets.UTF_8)), fos)
        }
        db.vaultDao().insertVaultItem(
            VaultItem(
                id = 1,
                originalName = "initial.txt",
                encryptedFileName = "initial_item.bin",
                sizeBytes = 15,
                mimeType = "text/plain",
                addedTimestamp = System.currentTimeMillis(),
                isVideo = false,
                folderName = "Root"
            )
        )
        val initialGen = VaultGenerationManager.getActiveGeneration(context, isDecoy = false)

        val backupOut = ByteArrayOutputStream()
        val exportResult = VaultBackupManager.exportMasterBackup(
            context = context,
            masterPassword = backupPassword,
            outputStream = backupOut,
            vaultRepository = repo,
            isDeviceLocked = false
        )
        assertTrue(exportResult.isSuccess)

        // Inject simulated process death / crash after FS swap
        VaultBackupManager.testFaultInjectionHook = { phase ->
            if (phase == RestoreFaultPhase.AFTER_FS_SWAP) {
                throw RuntimeException("SIMULATED_DISK_IO_FAILURE_AFTER_FS_SWAP")
            }
        }

        try {
            val restoreResult = VaultBackupManager.importMasterBackup(
                context = context,
                masterPassword = backupPassword,
                inputStream = ByteArrayInputStream(backupOut.toByteArray()),
                vaultRepository = repo,
                isReplaceMode = true
            )
            assertFalse("Restore must fail when fault is injected", restoreResult.isSuccess)
            assertTrue(
                "Exception should be simulated fault: ${restoreResult.exceptionOrNull()?.message}",
                restoreResult.exceptionOrNull()?.message?.contains("SIMULATED_DISK_IO_FAILURE") == true
            )

            // Rollback verification: Initial state and generation must remain intact
            val curGen = VaultGenerationManager.getActiveGeneration(context, isDecoy = false)
            assertEquals("Generation must not advance on rolled back restore", initialGen, curGen)
            assertTrue("Initial physical file must be preserved by rollback", initialFile.exists())
            assertEquals("Initial DB item must be intact", 1, db.vaultDao().getAllItemsSync().size)
        } finally {
            VaultBackupManager.testFaultInjectionHook = null
        }
    }

    @Test
    fun `test fault injection before generation commit triggers clean rollback`() = runBlocking {
        VaultKeyManager.createVrkForFreshVault(context, "1234")
        VaultKeyManager.authorizeWithPin(context, "1234")
        val db = AppDatabase.getDatabase(context)
        val repo = VaultRepository(db.vaultDao())
        val backupPassword = "FaultPassword123!"

        val vaultDir = repo.getVaultDirectory(context)
        val initialFile = File(vaultDir, "initial_item2.bin")
        java.io.FileOutputStream(initialFile).use { fos ->
            com.quantumvault.wkqpx.security.CryptoManager.encryptStream(ByteArrayInputStream("INITIAL_CONTENT_2".toByteArray(Charsets.UTF_8)), fos)
        }
        db.vaultDao().insertVaultItem(
            VaultItem(
                id = 2,
                originalName = "initial2.txt",
                encryptedFileName = "initial_item2.bin",
                sizeBytes = 17,
                mimeType = "text/plain",
                addedTimestamp = System.currentTimeMillis(),
                isVideo = false,
                folderName = "Root"
            )
        )
        val initialGen = VaultGenerationManager.getActiveGeneration(context, isDecoy = false)

        val backupOut = ByteArrayOutputStream()
        val exportResult = VaultBackupManager.exportMasterBackup(
            context = context,
            masterPassword = backupPassword,
            outputStream = backupOut,
            vaultRepository = repo,
            isDeviceLocked = false
        )
        assertTrue(exportResult.isSuccess)

        // Inject simulated failure right before generation commit
        VaultBackupManager.testFaultInjectionHook = { phase ->
            if (phase == RestoreFaultPhase.BEFORE_GENERATION_COMMIT) {
                throw RuntimeException("SIMULATED_PROCESS_KILL_BEFORE_GEN_COMMIT")
            }
        }

        try {
            val restoreResult = VaultBackupManager.importMasterBackup(
                context = context,
                masterPassword = backupPassword,
                inputStream = ByteArrayInputStream(backupOut.toByteArray()),
                vaultRepository = repo,
                isReplaceMode = true
            )
            assertFalse("Restore must fail when fault is injected", restoreResult.isSuccess)
            val curGen = VaultGenerationManager.getActiveGeneration(context, isDecoy = false)
            assertEquals("Generation must remain unchanged on abort", initialGen, curGen)
            assertTrue("Initial physical file must still exist after rollback", initialFile.exists())
        } finally {
            VaultBackupManager.testFaultInjectionHook = null
        }
    }

    @Test
    fun `test corrupted generation file throws GenerationCorruptionException with RECOVERY_REQUIRED`() {
        val genFile = File(context.filesDir, "vault_gen_real.bin")
        // Overwrite generation file with corrupted data (invalid CRC / invalid magic)
        genFile.writeBytes(ByteArray(24) { 0xAA.toByte() })

        try {
            VaultGenerationManager.getActiveGeneration(context, isDecoy = false)
            org.junit.Assert.fail("Expected GenerationCorruptionException on corrupted generation file")
        } catch (e: GenerationCorruptionException) {
            assertTrue("Exception message must require recovery: ${e.message}", e.message?.contains("RECOVERY_REQUIRED") == true)
        }
    }

    @Test
    fun `test wrong password on V4 backup throws InvalidBackupPasswordException`() = runBlocking {
        VaultKeyManager.createVrkForFreshVault(context, "1234")
        VaultKeyManager.authorizeWithPin(context, "1234")
        val db = AppDatabase.getDatabase(context)
        val repo = VaultRepository(db.vaultDao())

        val backupPassword = "RealCorrectPassword123!"
        val backupOut = ByteArrayOutputStream()
        val exportResult = VaultBackupManager.exportMasterBackup(
            context = context,
            masterPassword = backupPassword,
            outputStream = backupOut,
            vaultRepository = repo,
            isDeviceLocked = false
        )
        assertTrue(exportResult.isSuccess)

        // Attempt restore with WRONG password
        val restoreResult = VaultBackupManager.importMasterBackup(
            context = context,
            masterPassword = "WrongPassword999!",
            inputStream = ByteArrayInputStream(backupOut.toByteArray()),
            vaultRepository = repo,
            isReplaceMode = true
        )

        assertFalse(restoreResult.isSuccess)
        val ex = restoreResult.exceptionOrNull()
        assertTrue(
            "Exception must be InvalidBackupPasswordException or cause: ${ex?.javaClass?.name} - ${ex?.message}",
            ex is InvalidBackupPasswordException || ex?.cause is javax.crypto.AEADBadTagException
        )
    }

    @Test
    fun `test production Argon2 parameters derivation succeeds`() {
        val password = "StrongProductionPassword#2026".toCharArray()
        val salt = ByteArray(16) { 0x77.toByte() }

        // Test production profile: 64 MiB (65536 KiB), 3 iterations, 1 parallelism
        val key = Argon2Kdf.deriveKey(
            password = password,
            salt = salt,
            memoryKb = 65536,
            iterations = 3,
            parallelism = 1
        )
        assertNotNull(key)
        assertEquals(32, key.encoded.size)

        // Verify deterministic output
        val key2 = Argon2Kdf.deriveKey(
            password = password,
            salt = salt,
            memoryKb = 65536,
            iterations = 3,
            parallelism = 1
        )
        org.junit.Assert.assertArrayEquals(key.encoded, key2.encoded)
    }

    @Test
    fun `test crash-consistent restore rolls back FS_SWAPPED state during startup recovery`() = runBlocking {
        VaultKeyManager.createVrkForFreshVault(context, "1234")
        VaultKeyManager.authorizeWithPin(context, "1234")

        val vaultDir = File(context.filesDir, "vault_data")
        vaultDir.mkdirs()
        // Simulate an uncommitted new generation that was moved to vaultDir during restore
        val newGenFile = File(vaultDir, "new_uncommitted_file.enc")
        newGenFile.writeBytes("NEW_GEN_DATA".toByteArray())

        // Simulate prevDir holding the original valid generation files
        val prevDir = File(context.filesDir, "vault_data_prev_gen_test")
        prevDir.mkdirs()
        val originalFile = File(prevDir, "original_safe_file.enc")
        originalFile.writeBytes("ORIGINAL_SAFE_DATA".toByteArray())

        val currentGen = VaultGenerationManager.getActiveGeneration(context, isDecoy = false)
        val nextGen = currentGen + 1L
        val intentFile = VaultGenerationManager.prepareGenerationIntent(context, isDecoy = false, nextGen)

        // Write FS_SWAPPED journal record
        VaultRestoreJournal.recordState(
            context,
            RestoreJournalRecord(
                state = RestoreJournalState.FS_SWAPPED.name,
                isDecoy = false,
                nextGen = nextGen,
                intentFileName = intentFile.name,
                vaultDirPath = vaultDir.absolutePath,
                backupPrevGenDirPath = prevDir.absolutePath,
                nextGenDirPath = "",
                isMergeMode = false
            )
        )

        assertTrue(VaultRestoreJournal.hasPendingRestore(context))

        // Execute recovery as would happen on App Startup
        val recovered = VaultBackupManager.recoverPendingRestoreIfAny(context)
        assertTrue("Pending restore in FS_SWAPPED must be recovered", recovered)

        // Verify Journal cleared
        assertFalse("Restore journal must be cleared after recovery", VaultRestoreJournal.hasPendingRestore(context))

        // Verify vaultDir was rolled back to the original safe content
        val restoredOriginal = File(vaultDir, "original_safe_file.enc")
        assertTrue("Original file must be restored to vaultDir", restoredOriginal.exists())
        assertEquals("ORIGINAL_SAFE_DATA", restoredOriginal.readText())

        // Verify uncommitted file is removed and prevDir is cleaned up
        val lingeringNewFile = File(vaultDir, "new_uncommitted_file.enc")
        assertFalse("Uncommitted file must be removed", lingeringNewFile.exists())
        assertFalse("prevDir must be cleaned up", prevDir.exists())
        assertFalse("Intent file must be cleaned up", intentFile.exists())
    }

    @Test
    fun `test crash-consistent restore rolls forward DB_COMMITTED state during startup recovery`() = runBlocking {
        VaultKeyManager.createVrkForFreshVault(context, "1234")
        VaultKeyManager.authorizeWithPin(context, "1234")

        val vaultDir = File(context.filesDir, "vault_data")
        vaultDir.mkdirs()
        val committedFile = File(vaultDir, "committed_file.enc")
        committedFile.writeBytes("COMMITTED_DATA".toByteArray())

        val prevDir = File(context.filesDir, "vault_data_prev_gen_test2")
        prevDir.mkdirs()
        val oldFile = File(prevDir, "old_file.enc")
        oldFile.writeBytes("OLD_DATA".toByteArray())

        val currentGen = VaultGenerationManager.getActiveGeneration(context, isDecoy = false)
        val nextGen = currentGen + 1L
        val intentFile = VaultGenerationManager.prepareGenerationIntent(context, isDecoy = false, nextGen)

        // Write DB_COMMITTED journal record
        VaultRestoreJournal.recordState(
            context,
            RestoreJournalRecord(
                state = RestoreJournalState.DB_COMMITTED.name,
                isDecoy = false,
                nextGen = nextGen,
                intentFileName = intentFile.name,
                vaultDirPath = vaultDir.absolutePath,
                backupPrevGenDirPath = prevDir.absolutePath,
                nextGenDirPath = "",
                isMergeMode = false
            )
        )

        assertTrue(VaultRestoreJournal.hasPendingRestore(context))

        // Execute recovery
        val recovered = VaultBackupManager.recoverPendingRestoreIfAny(context)
        assertTrue("Pending restore in DB_COMMITTED must be recovered", recovered)

        // Verify generation was advanced to nextGen
        val activeGen = VaultGenerationManager.getActiveGeneration(context, isDecoy = false)
        assertEquals("Active generation must be committed to nextGen", nextGen, activeGen)

        // Verify prevDir and intent cleaned up
        assertFalse("Restore journal must be cleared", VaultRestoreJournal.hasPendingRestore(context))
        assertFalse("prevDir must be deleted", prevDir.exists())
        assertFalse("intentFile must be deleted", intentFile.exists())
        assertTrue("Committed file remains in vaultDir", committedFile.exists())
    }

    @Test
    fun `test merge mode restore atomically rolls back modified and added files on failure`() = runBlocking {
        VaultKeyManager.createVrkForFreshVault(context, "1234")
        VaultKeyManager.authorizeWithPin(context, "1234")
        val db = AppDatabase.getDatabase(context)
        val repo = VaultRepository(db.vaultDao())

        val vaultDir = repo.getVaultDirectory(context)

        // Create an existing file and item in vault
        val existingFile = File(vaultDir, "existing_doc.bin")
        java.io.FileOutputStream(existingFile).use { fos ->
            com.quantumvault.wkqpx.security.CryptoManager.encryptStream(ByteArrayInputStream("ORIGINAL_EXISTING_DOCUMENT".toByteArray(Charsets.UTF_8)), fos)
        }
        val existingItem = VaultItem(
            id = 0L,
            originalName = "my_doc.txt",
            encryptedFileName = "existing_doc.bin",
            mimeType = "text/plain",
            sizeBytes = existingFile.length(),
            addedTimestamp = System.currentTimeMillis(),
            isVideo = false,
            folderName = "Root"
        )
        db.vaultDao().insertVaultItem(existingItem)

        // Export a backup with a different item
        val newVaultFile = File(vaultDir, "new_file.bin")
        java.io.FileOutputStream(newVaultFile).use { fos ->
            com.quantumvault.wkqpx.security.CryptoManager.encryptStream(ByteArrayInputStream("NEW_FILE_PAYLOAD".toByteArray(Charsets.UTF_8)), fos)
        }
        val newItem = VaultItem(
            id = 0L,
            originalName = "new_doc.txt",
            encryptedFileName = "new_file.bin",
            mimeType = "text/plain",
            sizeBytes = newVaultFile.length(),
            addedTimestamp = System.currentTimeMillis(),
            isVideo = false,
            folderName = "Root"
        )
        db.vaultDao().insertVaultItem(newItem)

        val backupPassword = "MergeBackupPassword123!"
        val backupOut = ByteArrayOutputStream()
        val exportResult = VaultBackupManager.exportMasterBackup(
            context = context,
            masterPassword = backupPassword,
            outputStream = backupOut,
            vaultRepository = repo,
            isDeviceLocked = false
        )
        assertTrue(exportResult.isSuccess)

        // Delete newItem from db and disk to simulate receiving it from external backup
        db.vaultDao().deleteVaultItem(newItem)
        newVaultFile.delete()

        // Attempt merge-mode restore with fault injection before generation commit
        val restoreResult = VaultBackupManager.importMasterBackup(
            context = context,
            masterPassword = backupPassword,
            inputStream = ByteArrayInputStream(backupOut.toByteArray()),
            vaultRepository = repo,
            isReplaceMode = false,
            testFaultInjectionHook = { phase ->
                if (phase == RestoreFaultPhase.BEFORE_GENERATION_COMMIT) {
                    throw RuntimeException("SIMULATED_CRASH_BEFORE_GEN_COMMIT")
                }
            }
        )

        assertFalse("Merge restore should fail on fault injection", restoreResult.isSuccess)

        // Verify existing file is preserved and rollback restored state
        assertTrue("Existing original file must be preserved", existingFile.exists())
        val decryptedExisting = ByteArrayOutputStream()
        java.io.FileInputStream(existingFile).use { fis ->
            com.quantumvault.wkqpx.security.CryptoManager.decryptStreamToOutputStream(fis, decryptedExisting)
        }
        assertEquals("ORIGINAL_EXISTING_DOCUMENT", decryptedExisting.toString(Charsets.UTF_8.name()))
        assertFalse("Restore journal must be cleared", VaultRestoreJournal.hasPendingRestore(context))
    }

    @Test
    fun `test V4 backup validates exact manifest-payload bijection and rejects unmanifested orphan payload`() = runBlocking {
        VaultKeyManager.createVrkForFreshVault(context, "1234")
        VaultKeyManager.authorizeWithPin(context, "1234")
        val db = AppDatabase.getDatabase(context)
        val repo = VaultRepository(db.vaultDao())

        val vaultDir = repo.getVaultDirectory(context)
        val file1 = File(vaultDir, "file1.bin")
        java.io.FileOutputStream(file1).use { fos ->
            com.quantumvault.wkqpx.security.CryptoManager.encryptStream(ByteArrayInputStream("PAYLOAD_ONE".toByteArray(Charsets.UTF_8)), fos)
        }
        val item1 = VaultItem(
            id = 0L,
            originalName = "test1.txt",
            encryptedFileName = "file1.bin",
            mimeType = "text/plain",
            sizeBytes = file1.length(),
            addedTimestamp = System.currentTimeMillis(),
            isVideo = false,
            folderName = "Root"
        )
        db.vaultDao().insertVaultItem(item1)

        val backupPassword = "BijectionPassword123!"
        val backupOut = ByteArrayOutputStream()
        val exportResult = VaultBackupManager.exportMasterBackup(
            context = context,
            masterPassword = backupPassword,
            outputStream = backupOut,
            vaultRepository = repo,
            isDeviceLocked = false
        )
        assertTrue(exportResult.isSuccess)

        // Step 1: Normal restore must succeed
        val restoreResult = VaultBackupManager.importMasterBackup(
            context = context,
            masterPassword = backupPassword,
            inputStream = ByteArrayInputStream(backupOut.toByteArray()),
            vaultRepository = repo,
            isReplaceMode = true
        )
        assertTrue("Normal V4 restore must succeed: ${restoreResult.exceptionOrNull()?.message}", restoreResult.isSuccess)

        // Step 2: Inject an undeclared/orphaned file into the inner zip
        val backupBytes = backupOut.toByteArray()
        val header = backupBytes.copyOfRange(0, 41)
        val salt = backupBytes.copyOfRange(9, 25)
        val bb = java.nio.ByteBuffer.wrap(backupBytes, 25, 16)
        val memoryKb = bb.getInt()
        val iterations = bb.getInt()
        val parallelism = bb.getInt()

        val derivedKey = Argon2Kdf.deriveKey(
            backupPassword.toCharArray(),
            salt,
            memoryKb = memoryKb,
            iterations = iterations,
            parallelism = parallelism
        )

        val payloadStream = ByteArrayInputStream(backupBytes, 41, backupBytes.size - 41)
        val chunkedIn = VaultBackupManager.ChunkedGcmInputStream(payloadStream, derivedKey, useAad = true)
        val decryptedZipBytes = chunkedIn.readBytes()

        // Re-pack inner zip adding an unmanifested payload: "vault_data_v4/unmanifested_trojan.enc"
        val modifiedZipOut = ByteArrayOutputStream()
        val zis = java.util.zip.ZipInputStream(ByteArrayInputStream(decryptedZipBytes))
        val zos = ZipOutputStream(modifiedZipOut)

        var entry = zis.nextEntry
        while (entry != null) {
            zos.putNextEntry(ZipEntry(entry.name))
            zis.copyTo(zos)
            zos.closeEntry()
            entry = zis.nextEntry
        }
        // Add undeclared payload
        zos.putNextEntry(ZipEntry("vault_data_v4/unmanifested_trojan.enc"))
        zos.write("EVIL_PAYLOAD".toByteArray())
        zos.closeEntry()
        zos.finish()

        // Re-encrypt modified zip using ChunkedGcmOutputStream with V4 header
        val tamperedBackupOut = ByteArrayOutputStream()
        tamperedBackupOut.write(header)
        val chunkedOut = VaultBackupManager.ChunkedGcmOutputStream(tamperedBackupOut, derivedKey)
        chunkedOut.write(modifiedZipOut.toByteArray())
        chunkedOut.close()

        // Step 3: Attempt restore with tampered zip containing undeclared payload
        val tamperedRestoreResult = VaultBackupManager.importMasterBackup(
            context = context,
            masterPassword = backupPassword,
            inputStream = ByteArrayInputStream(tamperedBackupOut.toByteArray()),
            vaultRepository = repo,
            isReplaceMode = true
        )

        assertFalse("Restore must fail when archive contains unmanifested payload", tamperedRestoreResult.isSuccess)
        val ex = tamperedRestoreResult.exceptionOrNull()
        assertTrue(
            "Exception must be BackupManifestIntegrityException: ${ex?.message}",
            ex is BackupManifestIntegrityException && ex.message?.contains("undeclared") == true
        )
    }
}

