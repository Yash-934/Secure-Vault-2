package com.example.security

import android.content.Context
import com.example.data.VaultItem
import com.example.data.VaultRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted Vault Master Backup & Disaster Recovery Engine.
 *
 * Security Spec:
 * 1. Key Derivation: PBKDF2WithHmacSHA256 with a 16-byte SecureRandom salt and 10,000 iterations.
 * 2. Payload Encryption: AES-256-GCM with a fresh 12-byte IV.
 * 3. Container: Zip archive containing encrypted vault payloads and a JSON metadata manifest.
 */
object VaultBackupManager {

    private const val SALT_SIZE_BYTES = 16
    private const val IV_SIZE_BYTES = 12
    private const val PBKDF2_ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val MANIFEST_FILENAME = "vault_manifest.json"

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, VaultItem::class.java)
    private val jsonAdapter = moshi.adapter<List<VaultItem>>(listType)

    private fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    suspend fun exportMasterBackup(
        context: Context,
        masterPassword: String,
        outputStream: OutputStream,
        vaultRepository: VaultRepository
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val vaultDir = File(context.filesDir, "vault")
            val items = vaultRepository.allVaultItems.first()

            // 1. Create temporary unencrypted ZIP buffer containing vault files & manifest JSON
            val tempZipFile = File(context.cacheDir, "temp_backup_${System.currentTimeMillis()}.zip")
            ZipOutputStream(FileOutputStream(tempZipFile)).use { zos ->
                // Add manifest.json
                val manifestJson = jsonAdapter.toJson(items)
                val manifestBytes = manifestJson.toByteArray(Charsets.UTF_8)
                zos.putNextEntry(ZipEntry(MANIFEST_FILENAME))
                zos.write(manifestBytes)
                zos.closeEntry()

                // Add vault files
                if (vaultDir.exists()) {
                    vaultDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            zos.putNextEntry(ZipEntry("vault_data/${file.name}"))
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }

            // 2. Generate random 16-byte Salt and 12-byte IV
            val random = SecureRandom()
            val salt = ByteArray(SALT_SIZE_BYTES)
            val iv = ByteArray(IV_SIZE_BYTES)
            random.nextBytes(salt)
            random.nextBytes(iv)

            // 3. Derive key via PBKDF2
            val secretKey = deriveKey(masterPassword, salt)

            // 4. Initialize AES-GCM cipher
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            // 5. Write header (Salt + IV) to outputStream
            outputStream.write(salt)
            outputStream.write(iv)

            // 6. Encrypt zip file payload into outputStream
            FileInputStream(tempZipFile).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    val encryptedChunk = cipher.update(buffer, 0, bytesRead)
                    if (encryptedChunk != null && encryptedChunk.isNotEmpty()) {
                        outputStream.write(encryptedChunk)
                    }
                }
            }
            val finalChunk = cipher.doFinal()
            if (finalChunk != null && finalChunk.isNotEmpty()) {
                outputStream.write(finalChunk)
            }
            outputStream.flush()

            // Clean temp zip
            tempZipFile.delete()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importMasterBackup(
        context: Context,
        masterPassword: String,
        inputStream: InputStream,
        vaultRepository: VaultRepository
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val vaultDir = File(context.filesDir, "vault").apply { if (!exists()) mkdirs() }

            // 1. Read 16-byte Salt and 12-byte IV header
            val salt = ByteArray(SALT_SIZE_BYTES)
            val iv = ByteArray(IV_SIZE_BYTES)

            val saltRead = inputStream.read(salt)
            val ivRead = inputStream.read(iv)

            if (saltRead < SALT_SIZE_BYTES || ivRead < IV_SIZE_BYTES) {
                return@withContext Result.failure(IllegalArgumentException("Invalid backup format: Missing Salt or IV header."))
            }

            // 2. Derive Key via PBKDF2
            val secretKey = deriveKey(masterPassword, salt)

            // 3. Initialize AES-GCM cipher in DECRYPT_MODE
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            // 4. Decrypt backup stream payload
            val encryptedBytes = inputStream.readBytes()
            val decryptedZipBytes = cipher.doFinal(encryptedBytes)

            // 5. Read ZIP from decrypted bytes
            var restoredCount = 0
            var restoredItems: List<VaultItem>? = null

            ZipInputStream(ByteArrayInputStream(decryptedZipBytes)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name == MANIFEST_FILENAME) {
                        val manifestJson = zis.readBytes().toString(Charsets.UTF_8)
                        restoredItems = jsonAdapter.fromJson(manifestJson)
                    } else if (entry.name.startsWith("vault_data/")) {
                        val fileName = File(entry.name).name
                        val targetFile = File(vaultDir, fileName)
                        FileOutputStream(targetFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 6. Restore DB items
            restoredItems?.forEach { item ->
                vaultRepository.insertRestoredVaultItem(item)
                restoredCount++
            }

            Result.success(restoredCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
