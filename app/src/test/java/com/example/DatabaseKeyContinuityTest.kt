package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.VaultFolder
import com.example.data.VaultItem
import com.example.data.VaultPassword
import com.example.data.IntruderLog
import com.example.data.local.SettingsDataStore
import com.example.security.CredentialRotationManager
import com.example.security.DatabaseKeyManager
import com.example.security.VaultKeyManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import net.sqlcipher.database.SQLiteDatabase

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseKeyContinuityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @org.junit.Before
    fun setUp() {
        runBlocking {
            val settingsDataStore = SettingsDataStore(context)
            settingsDataStore.clearAllForTesting()
            File(context.filesDir, "vrk_wrap.bin").delete()
            File(context.filesDir, "decoy_vrk_pin_wrap.bin").delete()
            File(context.filesDir, "vault_sentinel.bin").delete()
            File(context.filesDir, "decoy_vault_sentinel.bin").delete()
            File(context.filesDir, "vrk_biometric_envelope.bin").delete()
            com.example.security.DatabaseKeyManager.destroyKeys(context)
        }
    }

    @Test
    fun testP0_14_and_15_DatabaseKeyContinuityAndDataPreservation() = runBlocking {
        val settingsDataStore = SettingsDataStore(context)
        val initialPin = "1234"
        val newPin = "5678"

        // 1. Initial Creation
        settingsDataStore.bootstrapFreshVault(initialPin)
        VaultKeyManager.createVrkForFreshVault(context, initialPin)
        VaultKeyManager.authorizeWithPin(context, initialPin)

        val dbPassphrase1 = DatabaseKeyManager.getDatabasePassphrase(context)
        val vrk1 = VaultKeyManager.getActiveVrk()
        assertNotNull("VRK should not be null", vrk1)

        val db1 = AppDatabase.getDatabase(context)
        val vaultDao1 = db1.vaultDao()
        val intruderDao1 = db1.intruderLogDao()
        val passwordDao1 = db1.vaultPasswordDao()

        // Insert records
        vaultDao1.insertFolder(VaultFolder("TestFolder", System.currentTimeMillis(), "DOCUMENTS"))
        vaultDao1.insertVaultItem(VaultItem(originalName = "test.txt", encryptedFileName = "enc.txt", mimeType = "text/plain", sizeBytes = 1024L, folderName = "TestFolder"))
        passwordDao1.insertPassword(VaultPassword(title = "Gmail", category = "Email", usernameOrEmail = "test@gmail.com", encryptedPasswordBlob = "blob", websiteOrUrl = "gmail.com", encryptedNotesBlob = "notes", isFavorite = true, createdTimestamp = System.currentTimeMillis(), updatedTimestamp = System.currentTimeMillis()))
        intruderDao1.insertLog(IntruderLog(timestamp = System.currentTimeMillis(), attemptType = "PIN", details = "Failed", imagePath = null))
        
        // 2. Restart Simulation
        VaultKeyManager.lockVault()
        DatabaseKeyManager.clearMemory()

        VaultKeyManager.authorizeWithPin(context, initialPin)
        val dbPassphrase2 = DatabaseKeyManager.getDatabasePassphrase(context)
        val vrk2 = VaultKeyManager.getActiveVrk()

        assertTrue("VRK must remain identical after restart", vrk1!!.contentEquals(vrk2!!))
        assertTrue("DB passphrase must remain identical after restart", dbPassphrase1.contentEquals(dbPassphrase2))

        val db2 = AppDatabase.getDatabase(context)
        assertEquals("Data must be preserved after restart", 1, db2.vaultDao().getAllFoldersSync().size)

        // 3. PIN Rotation
        val rotationResult = CredentialRotationManager.rotateMasterPin(context, initialPin, newPin, settingsDataStore)
        assertEquals("Rotation should succeed", com.example.security.RotationResult.Success, rotationResult)

        VaultKeyManager.lockVault()
        DatabaseKeyManager.clearMemory()

        VaultKeyManager.authorizeWithPin(context, newPin)
        val dbPassphrase3 = DatabaseKeyManager.getDatabasePassphrase(context)
        val vrk3 = VaultKeyManager.getActiveVrk()

        assertTrue("VRK must remain identical after PIN rotation", vrk1.contentEquals(vrk3!!))
        assertTrue("DB passphrase must remain identical after PIN rotation", dbPassphrase1.contentEquals(dbPassphrase3))

        val db3 = AppDatabase.getDatabase(context)
        assertEquals("Data must be preserved after PIN rotation", 1, db3.vaultDao().getAllFoldersSync().size)
        
        // 4. Negative Test: New random VRK should fail to unwrap old DB passphrase
        VaultKeyManager.lockVault()
        DatabaseKeyManager.clearMemory()
        
        // Emulate a destructive new VRK creation (which shouldn't happen, but we bypass the check for the test)
        val tempVrk = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        VaultKeyManager.writeVrkPinWrap(context, tempVrk, "9999", isDecoy = false)
        VaultKeyManager.authorizeWithPin(context, "9999")
        
        var exceptionThrown = false
        try {
            DatabaseKeyManager.getDatabasePassphrase(context)
        } catch (e: com.example.security.DatabaseCryptoException) {
            exceptionThrown = true
        }
        assertTrue("Must throw DatabaseCryptoException when trying to unwrap old DB passphrase with new VRK", exceptionThrown)
    }
}
