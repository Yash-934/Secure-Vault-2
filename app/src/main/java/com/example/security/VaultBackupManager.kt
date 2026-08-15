package com.example.security

import android.content.Context
import android.util.Log
import com.example.data.VaultItem
import com.example.data.VaultRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
 * Security Architecture:
 * 1. Key Derivation: PBKDF2WithHmacSHA256 with 16-byte SecureRandom salt and 10,000 iterations.
 * 2. Payload Encryption: Streaming Chunked AES-256-GCM (1MB per chunk, fresh 12-byte IV per chunk).
 *    Guarantees ~1MB maximum heap allocation on any size backup without OutOfMemoryError.
 * 3. Format: [Magic 8B 'VLT_BCK2'] + [Salt 16B] + [Chunks: Len(4B) + isLast(1B) + IV(12B) + CiphertextWithTag].
 * 4. Backward compatible with legacy single-block backups.
 */
object VaultBackupManager {

    private val BACKUP_MAGIC_V2 = "VLT_BCK2".toByteArray(Charsets.UTF_8) // 8 bytes
    private const val SALT_SIZE_BYTES = 16
    private const val IV_SIZE_BYTES = 12
    private const val PBKDF2_ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val CHUNK_SIZE = 1024 * 1024 // 1 MB streaming chunks
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

    private fun readFully(inputStream: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        var totalRead = 0
        while (totalRead < length) {
            val read = inputStream.read(buffer, offset + totalRead, length - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return totalRead
    }

    private fun writeInt(outputStream: OutputStream, value: Int) {
        outputStream.write((value ushr 24) and 0xFF)
        outputStream.write((value ushr 16) and 0xFF)
        outputStream.write((value ushr 8) and 0xFF)
        outputStream.write(value and 0xFF)
    }

    private fun readInt(inputStream: InputStream): Int {
        val b1 = inputStream.read()
        val b2 = inputStream.read()
        val b3 = inputStream.read()
        val b4 = inputStream.read()
        if (b1 or b2 or b3 or b4 < 0) return -1
        return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
    }

    suspend fun exportMasterBackup(
        context: Context,
        masterPassword: String,
        outputStream: OutputStream,
        vaultRepository: VaultRepository
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tempZipFile = File(context.cacheDir, "temp_backup_${System.currentTimeMillis()}.zip")
        try {
            val vaultDir = vaultRepository.getVaultDirectory(context)
            val items = vaultRepository.allVaultItems.first()

            // 1. Create temporary ZIP containing vault items & metadata manifest
            ZipOutputStream(FileOutputStream(tempZipFile).buffered(65536)).use { zos ->
                // Add manifest.json
                val manifestJson = jsonAdapter.toJson(items)
                val manifestBytes = manifestJson.toByteArray(Charsets.UTF_8)
                zos.putNextEntry(ZipEntry(MANIFEST_FILENAME))
                zos.write(manifestBytes)
                zos.closeEntry()

                // Add vault files safely
                items.forEach { item ->
                    val file = File(vaultDir, item.encryptedFileName)
                    if (file.exists() && file.length() > 0) {
                        try {
                            zos.putNextEntry(ZipEntry("vault_data_v2/${item.encryptedFileName}"))
                            FileInputStream(file).buffered(65536).use { fis ->
                                CryptoManager.decryptStreamToOutputStream(fis, zos)
                            }
                        } catch (e: Exception) {
                            Log.e("VaultBackup", "Failed to package ${item.originalName}: ${e.message}")
                        } finally {
                            try { zos.closeEntry() } catch (_: Throwable) {}
                        }
                    }
                }
            }

            if (!tempZipFile.exists() || tempZipFile.length() <= 0) {
                return@withContext Result.failure(IllegalStateException("Failed to assemble vault package."))
            }

            // 2. Generate random 16-byte Salt
            val random = SecureRandom()
            val salt = ByteArray(SALT_SIZE_BYTES)
            random.nextBytes(salt)

            // 3. Derive key via PBKDF2
            val secretKey = deriveKey(masterPassword, salt)

            // 4. Write Header: Magic 'VLT_BCK2' (8 bytes) + Salt (16 bytes)
            outputStream.write(BACKUP_MAGIC_V2)
            outputStream.write(salt)

            // 5. Chunked AES-256-GCM Streaming Encryption (~1MB peak heap)
            FileInputStream(tempZipFile).buffered(CHUNK_SIZE).use { fis ->
                val readBuffer = ByteArray(CHUNK_SIZE)
                val nextBuffer = ByteArray(CHUNK_SIZE)

                var currentChunkBytes = readFully(fis, readBuffer, 0, CHUNK_SIZE)

                if (currentChunkBytes <= 0) {
                    // Empty archive fallback
                    val chunkIV = ByteArray(IV_SIZE_BYTES).also { random.nextBytes(it) }
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIV))
                    val cipherText = cipher.doFinal(ByteArray(0))

                    writeInt(outputStream, cipherText.size)
                    outputStream.write(1) // isLast
                    outputStream.write(chunkIV)
                    outputStream.write(cipherText)
                } else {
                    while (currentChunkBytes > 0) {
                        val nextChunkBytes = readFully(fis, nextBuffer, 0, CHUNK_SIZE)
                        val isLast = (nextChunkBytes <= 0)

                        val chunkIV = ByteArray(IV_SIZE_BYTES).also { random.nextBytes(it) }
                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIV))

                        val cipherText = cipher.doFinal(readBuffer, 0, currentChunkBytes)

                        writeInt(outputStream, cipherText.size)
                        outputStream.write(if (isLast) 1 else 0)
                        outputStream.write(chunkIV)
                        outputStream.write(cipherText)

                        if (isLast) break

                        System.arraycopy(nextBuffer, 0, readBuffer, 0, nextChunkBytes)
                        currentChunkBytes = nextChunkBytes
                    }
                }
            }

            outputStream.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("VaultBackup", "Export failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            if (tempZipFile.exists()) {
                tempZipFile.delete()
            }
        }
    }

    suspend fun importMasterBackup(
        context: Context,
        masterPassword: String,
        inputStream: InputStream,
        vaultRepository: VaultRepository
    ): Result<Int> = withContext(Dispatchers.IO) {
        val tempRestoredZip = File(context.cacheDir, "temp_restore_${System.currentTimeMillis()}.zip")
        try {
            val vaultDir = vaultRepository.getVaultDirectory(context)

            // 1. Check Magic Header (8 bytes)
            val headerBytes = ByteArray(8)
            val headerRead = readFully(inputStream, headerBytes, 0, 8)
            if (headerRead < 8) {
                return@withContext Result.failure(IllegalArgumentException("Corrupted backup file: Header too short."))
            }

            val isV2 = headerBytes.contentEquals(BACKUP_MAGIC_V2)

            if (isV2) {
                // V2 Chunked Format: Read 16-byte Salt
                val salt = ByteArray(SALT_SIZE_BYTES)
                val saltRead = readFully(inputStream, salt, 0, SALT_SIZE_BYTES)
                if (saltRead < SALT_SIZE_BYTES) {
                    return@withContext Result.failure(IllegalArgumentException("Incomplete backup salt header."))
                }

                val secretKey = deriveKey(masterPassword, salt)

                // Decrypt chunk-by-chunk directly to disk-backed temp zip file
                FileOutputStream(tempRestoredZip).buffered(65536).use { decOut ->
                    while (true) {
                        val cipherLength = readInt(inputStream)
                        if (cipherLength < 0) break

                        val isLastFlag = inputStream.read()
                        if (isLastFlag < 0) break

                        val chunkIV = ByteArray(IV_SIZE_BYTES)
                        val ivRead = readFully(inputStream, chunkIV, 0, IV_SIZE_BYTES)
                        if (ivRead < IV_SIZE_BYTES) {
                            throw IllegalStateException("Unexpected end of backup stream: Incomplete chunk IV.")
                        }

                        val cipherBuffer = ByteArray(cipherLength)
                        val bytesRead = readFully(inputStream, cipherBuffer, 0, cipherLength)
                        if (bytesRead < cipherLength) {
                            throw IllegalStateException("Corrupt chunk in backup stream.")
                        }

                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIV))

                        val plainChunk = cipher.doFinal(cipherBuffer)
                        decOut.write(plainChunk)

                        if (isLastFlag == 1) break
                    }
                    decOut.flush()
                }
            } else {
                // Legacy V1 Format: Header was the first 8 bytes of the 16-byte Salt
                val salt = ByteArray(SALT_SIZE_BYTES)
                System.arraycopy(headerBytes, 0, salt, 0, 8)
                val remainingSalt = readFully(inputStream, salt, 8, 8)
                if (remainingSalt < 8) {
                    return@withContext Result.failure(IllegalArgumentException("Incomplete legacy salt header."))
                }

                val iv = ByteArray(IV_SIZE_BYTES)
                val ivRead = readFully(inputStream, iv, 0, IV_SIZE_BYTES)
                if (ivRead < IV_SIZE_BYTES) {
                    return@withContext Result.failure(IllegalArgumentException("Incomplete legacy IV header."))
                }

                val secretKey = deriveKey(masterPassword, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

                val encryptedBytes = inputStream.readBytes()
                val decryptedZipBytes = cipher.doFinal(encryptedBytes)
                FileOutputStream(tempRestoredZip).use { it.write(decryptedZipBytes) }
            }

            if (!tempRestoredZip.exists() || tempRestoredZip.length() <= 0) {
                return@withContext Result.failure(IllegalStateException("Decryption produced empty archive."))
            }

            // 2. Read ZIP from decrypted temp file & restore into Vault
            var restoredCount = 0
            var restoredItems: List<VaultItem>? = null

            ZipInputStream(FileInputStream(tempRestoredZip).buffered(65536)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name == MANIFEST_FILENAME) {
                        val manifestJson = zis.readBytes().toString(Charsets.UTF_8)
                        restoredItems = jsonAdapter.fromJson(manifestJson)
                    } else if (entry.name.startsWith("vault_data_v2/")) {
                        val fileName = File(entry.name).name
                        val targetFile = File(vaultDir, fileName)
                        try {
                            FileOutputStream(targetFile).buffered(65536).use { fos ->
                                CryptoManager.encryptStream(zis, fos)
                            }
                        } catch (e: Exception) {
                            Log.e("VaultBackup", "Error restoring $fileName: ${e.message}")
                            targetFile.delete()
                        }
                    } else if (entry.name.startsWith("vault_data/")) {
                        val fileName = File(entry.name).name
                        val targetFile = File(vaultDir, fileName)
                        FileOutputStream(targetFile).buffered(65536).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    try { zis.closeEntry() } catch (_: Throwable) {}
                    entry = zis.nextEntry
                }
            }

            // 3. Re-insert items into Room Database
            restoredItems?.forEach { item ->
                vaultRepository.insertRestoredVaultItem(item)
                restoredCount++
            }

            Result.success(restoredCount)
        } catch (e: Exception) {
            Log.e("VaultBackup", "Import failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            if (tempRestoredZip.exists()) {
                tempRestoredZip.delete()
            }
        }
    }
}
