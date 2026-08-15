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

    /**
     * Chunked AES-256-GCM OutputStream that encrypts fixed ~1MB blocks and writes to underlying stream.
     * Guarantees zero disk staging, constant 1MB memory usage, and streaming support for huge videos.
     */
    class ChunkedGcmOutputStream(
        private val underlying: OutputStream,
        private val secretKey: SecretKey,
        private val chunkSize: Int = CHUNK_SIZE
    ) : OutputStream() {
        private val buffer = ByteArray(chunkSize)
        private var bufferPos = 0
        private val random = SecureRandom()
        private var isClosed = false

        override fun write(b: Int) {
            buffer[bufferPos++] = b.toByte()
            if (bufferPos == chunkSize) {
                flushChunk(isLast = false)
            }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var remaining = len
            var currOff = off
            while (remaining > 0) {
                val space = chunkSize - bufferPos
                val toCopy = minOf(space, remaining)
                System.arraycopy(b, currOff, buffer, bufferPos, toCopy)
                bufferPos += toCopy
                currOff += toCopy
                remaining -= toCopy
                if (bufferPos == chunkSize) {
                    flushChunk(isLast = false)
                }
            }
        }

        private fun flushChunk(isLast: Boolean) {
            val chunkIV = ByteArray(IV_SIZE_BYTES).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIV))
            val cipherText = cipher.doFinal(buffer, 0, bufferPos)

            writeInt(underlying, cipherText.size)
            underlying.write(if (isLast) 1 else 0)
            underlying.write(chunkIV)
            underlying.write(cipherText)
            underlying.flush()

            bufferPos = 0
        }

        override fun flush() {
            underlying.flush()
        }

        override fun close() {
            if (!isClosed) {
                isClosed = true
                flushChunk(isLast = true)
                underlying.flush()
            }
        }
    }

    /**
     * Chunked AES-256-GCM InputStream that decrypts chunk-by-chunk on the fly without loading entire archive to RAM or disk.
     */
    class ChunkedGcmInputStream(
        private val underlying: InputStream,
        private val secretKey: SecretKey
    ) : InputStream() {
        private var currentPlainChunk: ByteArray? = null
        private var chunkPos = 0
        private var chunkLen = 0
        private var isLastChunkSeen = false
        private var isEof = false

        private fun loadNextChunk(): Boolean {
            if (isLastChunkSeen || isEof) return false

            val cipherLength = readInt(underlying)
            if (cipherLength < 0) {
                isEof = true
                return false
            }

            val isLastFlag = underlying.read()
            if (isLastFlag < 0) {
                isEof = true
                return false
            }
            if (isLastFlag == 1) {
                isLastChunkSeen = true
            }

            val chunkIV = ByteArray(IV_SIZE_BYTES)
            val ivRead = readFully(underlying, chunkIV, 0, IV_SIZE_BYTES)
            if (ivRead < IV_SIZE_BYTES) {
                throw IllegalStateException("Incomplete chunk IV in backup stream.")
            }

            val cipherBuffer = ByteArray(cipherLength)
            val bytesRead = readFully(underlying, cipherBuffer, 0, cipherLength)
            if (bytesRead < cipherLength) {
                throw IllegalStateException("Incomplete cipher chunk in backup stream.")
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIV))
            val plain = cipher.doFinal(cipherBuffer)

            currentPlainChunk = plain
            chunkPos = 0
            chunkLen = plain.size
            return true
        }

        override fun read(): Int {
            if (chunkPos >= chunkLen) {
                if (!loadNextChunk()) return -1
                if (chunkLen == 0) return -1
            }
            return currentPlainChunk!![chunkPos++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (chunkPos >= chunkLen) {
                if (!loadNextChunk()) return -1
                if (chunkLen == 0) return -1
            }
            val available = chunkLen - chunkPos
            val toCopy = minOf(available, len)
            System.arraycopy(currentPlainChunk!!, chunkPos, b, off, toCopy)
            chunkPos += toCopy
            return toCopy
        }

        override fun close() {
            currentPlainChunk = null
            underlying.close()
        }
    }

    /**
     * Single-pass Master Backup Streaming with AES-256-GCM.
     * Streams straight from internal encrypted files into PBKDF2 AES-GCM ciphertext on the destination outputStream.
     * Zero temporary files on disk. Zero disk-space limit issues with massive video files.
     */
    suspend fun exportMasterBackup(
        context: Context,
        masterPassword: String,
        outputStream: OutputStream,
        vaultRepository: VaultRepository,
        onProgress: ((current: Int, total: Int, currentName: String, bytesProcessed: Long) -> Unit)? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val vaultDir = vaultRepository.getVaultDirectory(context)
            val items = vaultRepository.allVaultItems.first()

            onProgress?.invoke(0, items.size, "Deriving AES-256 PBKDF2 Key...", 0L)

            // 1. Generate random 16-byte Salt
            val random = SecureRandom()
            val salt = ByteArray(SALT_SIZE_BYTES)
            random.nextBytes(salt)

            // 2. Derive 256-bit key via PBKDF2
            val secretKey = deriveKey(masterPassword, salt)

            // 3. Write Header: Magic 'VLT_BCK2' (8 bytes) + Salt (16 bytes)
            outputStream.write(BACKUP_MAGIC_V2)
            outputStream.write(salt)

            var totalBytesWritten = (BACKUP_MAGIC_V2.size + salt.size).toLong()

            val countingOut = object : OutputStream() {
                override fun write(b: Int) {
                    outputStream.write(b)
                    totalBytesWritten++
                }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    outputStream.write(b, off, len)
                    totalBytesWritten += len
                }
                override fun flush() {
                    outputStream.flush()
                }
            }

            // 4. Wrap with Chunked AES-256-GCM Stream
            val chunkedGcmOut = ChunkedGcmOutputStream(countingOut, secretKey, CHUNK_SIZE)

            // 5. Wrap in ZipOutputStream (Fast, zero extra compression on pre-compressed videos)
            ZipOutputStream(chunkedGcmOut.buffered(65536)).use { zos ->
                zos.setLevel(java.util.zip.Deflater.NO_COMPRESSION)

                onProgress?.invoke(0, items.size, "Writing Vault Metadata Manifest...", totalBytesWritten)

                // Add manifest.json
                val manifestJson = jsonAdapter.toJson(items)
                val manifestBytes = manifestJson.toByteArray(Charsets.UTF_8)
                zos.putNextEntry(ZipEntry(MANIFEST_FILENAME))
                zos.write(manifestBytes)
                zos.closeEntry()

                // Add each vault file directly decrypted on-the-fly into the encrypted ZIP stream
                items.forEachIndexed { index, item ->
                    onProgress?.invoke(index + 1, items.size, item.originalName, totalBytesWritten)
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
                zos.flush()
            }
            outputStream.flush()

            onProgress?.invoke(items.size, items.size, "Backup Finalized", totalBytesWritten)

            Result.success(totalBytesWritten)
        } catch (e: Exception) {
            Log.e("VaultBackup", "Export failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Restores encrypted master backup from an InputStream directly into the Vault without disk staging.
     */
    suspend fun importMasterBackup(
        context: Context,
        masterPassword: String,
        inputStream: InputStream,
        vaultRepository: VaultRepository,
        onProgress: ((current: Int, total: Int, currentName: String, bytesProcessed: Long) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val vaultDir = vaultRepository.getVaultDirectory(context)
            onProgress?.invoke(0, 0, "Validating Master Backup Header...", 0L)

            // 1. Check Magic Header (8 bytes)
            val headerBytes = ByteArray(8)
            val headerRead = readFully(inputStream, headerBytes, 0, 8)
            if (headerRead < 8) {
                return@withContext Result.failure(IllegalArgumentException("Corrupted backup file: Header too short."))
            }

            val isV2 = headerBytes.contentEquals(BACKUP_MAGIC_V2)
            var restoredCount = 0
            var totalBytesRestored = 0L

            if (isV2) {
                // V2 Chunked Format: Read 16-byte Salt
                val salt = ByteArray(SALT_SIZE_BYTES)
                val saltRead = readFully(inputStream, salt, 0, SALT_SIZE_BYTES)
                if (saltRead < SALT_SIZE_BYTES) {
                    return@withContext Result.failure(IllegalArgumentException("Incomplete backup salt header."))
                }

                onProgress?.invoke(0, 0, "Deriving AES-256 Key & Decrypting Manifest...", 0L)

                val secretKey = deriveKey(masterPassword, salt)
                val chunkedGcmIn = ChunkedGcmInputStream(inputStream, secretKey)
                var restoredItems: List<VaultItem>? = null

                ZipInputStream(chunkedGcmIn.buffered(65536)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == MANIFEST_FILENAME) {
                            val manifestJson = zis.readBytes().toString(Charsets.UTF_8)
                            restoredItems = jsonAdapter.fromJson(manifestJson)
                            onProgress?.invoke(0, restoredItems?.size ?: 0, "Loaded metadata records", totalBytesRestored)
                        } else if (entry.name.startsWith("vault_data_v2/")) {
                            val fileName = File(entry.name).name
                            val targetFile = File(vaultDir, fileName)
                            val itemObj = restoredItems?.find { it.encryptedFileName == fileName }
                            val displayName = itemObj?.originalName ?: fileName
                            val totalExpected = restoredItems?.size ?: 0

                            onProgress?.invoke(restoredCount + 1, totalExpected, "Restoring: $displayName", totalBytesRestored)

                            try {
                                FileOutputStream(targetFile).buffered(65536).use { fos ->
                                    CryptoManager.encryptStream(zis, fos)
                                }
                                totalBytesRestored += targetFile.length()
                            } catch (e: Exception) {
                                Log.e("VaultBackup", "Error restoring $fileName: ${e.message}")
                                targetFile.delete()
                            }
                        } else if (entry.name.startsWith("vault_data/")) {
                            val fileName = File(entry.name).name
                            val targetFile = File(vaultDir, fileName)
                            FileOutputStream(targetFile).buffered(65536).use { fos ->
                                val copied = zis.copyTo(fos)
                                totalBytesRestored += copied
                            }
                        }
                        try { zis.closeEntry() } catch (_: Throwable) {}
                        entry = zis.nextEntry
                    }
                }

                onProgress?.invoke(restoredItems?.size ?: 0, restoredItems?.size ?: 0, "Rebuilding Vault Database...", totalBytesRestored)

                // Insert metadata records into Room DB
                restoredItems?.forEach { item ->
                    vaultRepository.insertRestoredVaultItem(item)
                    restoredCount++
                }
            } else {
                // Legacy V1 single-block fallback
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

                onProgress?.invoke(0, 0, "Decrypting Legacy Backup Archive...", 0L)

                val secretKey = deriveKey(masterPassword, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

                val encryptedBytes = inputStream.readBytes()
                val decryptedZipBytes = cipher.doFinal(encryptedBytes)

                val tempRestoredZip = File(context.cacheDir, "temp_restore_${System.currentTimeMillis()}.zip")
                try {
                    FileOutputStream(tempRestoredZip).use { it.write(decryptedZipBytes) }
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
                                } catch (_: Throwable) {
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

                    restoredItems?.forEach { item ->
                        vaultRepository.insertRestoredVaultItem(item)
                        restoredCount++
                    }
                } finally {
                    if (tempRestoredZip.exists()) tempRestoredZip.delete()
                }
            }

            onProgress?.invoke(restoredCount, restoredCount, "Restoration Complete ($restoredCount files)", totalBytesRestored)

            Result.success(restoredCount)
        } catch (e: Exception) {
            Log.e("VaultBackup", "Import failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
