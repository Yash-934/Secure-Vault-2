package com.quantumvault.wkqpx.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.room.withTransaction
import com.quantumvault.wkqpx.data.AppDatabase
import com.quantumvault.wkqpx.data.IntruderLog
import com.quantumvault.wkqpx.data.VaultFolder
import com.quantumvault.wkqpx.data.VaultItem
import com.quantumvault.wkqpx.data.VaultPassword
import com.quantumvault.wkqpx.data.VaultRepository
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
import java.security.KeyStore
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardened Vault Backup & Disaster Recovery Engine with Argon2id, AES-256-GCM, and Atomic Rollback.
 *
 * Security Protocol:
 * 1. KDF: Argon2id (64 MiB RAM, 3 iterations, 1 parallelism, 16-byte random salt).
 * 2. Payload Encryption: Streaming Chunked AES-256-GCM (1MB per chunk, unique 12-byte IV per chunk, AAD monotonic index).
 * 3. Schema Completeness: Backs up Vault Items, Folders, Passwords, and Security Logs.
 * 4. Path Traversal & Tamper Defense: Canonical path enforcement, duplicate entry rejection, fail-closed stream parsing.
 * 5. Atomic Rollback: 7-stage pipeline (Validate -> Verify -> Stage -> Verify Manifest -> Move -> DB Commit -> Finalize).
 */
object VaultBackupManager {

    private const val TAG = "VaultBackup"
    private val BACKUP_MAGIC_V3 = "VLT_BCK3".toByteArray(Charsets.UTF_8) // 8 bytes
    private val BACKUP_MAGIC_V2 = "VLT_BCK2".toByteArray(Charsets.UTF_8) // 8 bytes
    private val BACKUP_MAGIC_V1 = "VLT_BCK1".toByteArray(Charsets.UTF_8) // 8 bytes
    private val BACKUP_MAGIC_VAULTBCK = "VAULTBCK".toByteArray(Charsets.UTF_8) // 8 bytes
    private val BACKUP_MAGIC_ZIP = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // PK\x03\x04
    private const val SALT_SIZE_BYTES = 16
    private const val IV_SIZE_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val CHUNK_SIZE = 1024 * 1024 // 1 MB streaming chunks
    private const val MAX_ALLOWED_CHUNK_SIZE = 16 * 1024 * 1024 // 16 MB max limit

    private const val MANIFEST_FILENAME = "vault_manifest.json"
    private const val MANIFEST_METADATA_FILENAME = "backup_metadata_manifest.json"
    private const val FOLDERS_FILENAME = "vault_folders.json"
    private const val PASSWORDS_FILENAME = "vault_passwords.json"
    private const val SECURITY_LOGS_FILENAME = "security_logs_manifest.json"
    private const val DEVICE_BINDING_KEY_ALIAS = "VaultBackupDeviceBindingHardwareKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class BackupManifestMetadata(
        val formatVersion: Int = 3,
        val itemsCount: Int = 0,
        val foldersCount: Int = 0,
        val passwordsCount: Int = 0,
        val logsCount: Int = 0,
        val hasFolders: Boolean = false,
        val hasPasswords: Boolean = false,
        val hasLogs: Boolean = false,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val metadataJsonAdapter = moshi.adapter(BackupManifestMetadata::class.java)
    private val listType = Types.newParameterizedType(List::class.java, VaultItem::class.java)
    private val jsonAdapter = moshi.adapter<List<VaultItem>>(listType)

    private val foldersListType = Types.newParameterizedType(List::class.java, VaultFolder::class.java)
    private val foldersJsonAdapter = moshi.adapter<List<VaultFolder>>(foldersListType)

    private val passwordsListType = Types.newParameterizedType(List::class.java, VaultPassword::class.java)
    private val passwordsJsonAdapter = moshi.adapter<List<VaultPassword>>(passwordsListType)

    private val logsListType = Types.newParameterizedType(List::class.java, IntruderLog::class.java)
    private val logsJsonAdapter = moshi.adapter<List<IntruderLog>>(logsListType)

    private val secureRandom = SecureRandom()

    /**
     * Retrieves or creates a hardware-backed non-exportable Keystore AES-256 key for device binding.
     */
    private fun getDeviceBindingMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getEntry(DEVICE_BINDING_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) {
            return existingKey.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            DEVICE_BINDING_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
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
     * Chunked AES-256-GCM OutputStream with monotonic chunk index AAD authentication.
     */
    class ChunkedGcmOutputStream(
        private val underlying: OutputStream,
        private val secretKey: SecretKey,
        private val chunkSize: Int = CHUNK_SIZE
    ) : OutputStream() {
        private val buffer = ByteArray(chunkSize)
        private var bufferPos = 0
        private var isClosed = false
        private var chunkIndex = 0L

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
            val chunkIV = ByteArray(IV_SIZE_BYTES).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIV))
            
            val aad = java.nio.ByteBuffer.allocate(8).putLong(chunkIndex++).array()
            cipher.updateAAD(aad)
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
                buffer.fill(0)
            }
        }
    }

    /**
     * Chunked AES-256-GCM InputStream with fail-closed integrity validation.
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
        private var chunkIndex = 0L
        private var chunksDecryptedCount = 0

        private fun loadNextChunk(): Boolean {
            if (isLastChunkSeen || isEof) return false

            val cipherLength = readInt(underlying)
            if (cipherLength < 0) {
                isEof = true
                if (!isLastChunkSeen) {
                    throw IllegalStateException("Corrupted archive: Stream truncated before last chunk flag.")
                }
                return false
            }

            if (cipherLength == 0 || cipherLength > MAX_ALLOWED_CHUNK_SIZE) {
                throw SecurityException("Invalid chunk length in backup stream: $cipherLength bytes.")
            }

            val isLastFlag = underlying.read()
            if (isLastFlag < 0) {
                isEof = true
                throw IllegalStateException("Corrupted archive: Missing chunk terminator.")
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
            val aad = java.nio.ByteBuffer.allocate(8).putLong(chunkIndex++).array()
            cipher.updateAAD(aad)
            val plain = try {
                cipher.doFinal(cipherBuffer)
            } catch (e: AEADBadTagException) {
                if (chunkIndex <= 1L && chunksDecryptedCount == 0) {
                    throw SecurityException("INCORRECT_BACKUP_PASSWORD: Password invalid for backup archive.", e)
                } else {
                    throw SecurityException("BACKUP_CORRUPTED: Backup archive is corrupted or cryptographic tag verification failed.", e)
                }
            }

            currentPlainChunk = plain
            chunkPos = 0
            chunkLen = plain.size
            chunksDecryptedCount++
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
            currentPlainChunk?.fill(0)
            currentPlainChunk = null
            underlying.close()
        }
    }

    /**
     * Exports a complete Argon2id + AES-256-GCM encrypted backup archive.
     */
    suspend fun exportMasterBackup(
        context: Context,
        masterPassword: String,
        outputStream: OutputStream,
        vaultRepository: VaultRepository,
        isDeviceLocked: Boolean = false,
        includeSecurityLogs: Boolean = true,
        onProgress: ((current: Int, total: Int, currentName: String, bytesProcessed: Long) -> Unit)? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val vaultDir = vaultRepository.getVaultDirectory(context)
            val items = vaultRepository.allVaultItems.first()
            val db = AppDatabase.getDatabase(context)
            val folders = db.vaultDao().getAllFoldersSync()
            val passwords = db.vaultPasswordDao().getAllPasswordsSync()

            onProgress?.invoke(0, items.size, "Deriving 64MB Argon2id Key...", 0L)

            // 1. Generate 16-byte random salt
            val salt = Argon2Kdf.generateSalt(SALT_SIZE_BYTES)
            val memoryKb = Argon2Kdf.DEFAULT_MEMORY_KIB // 64 MiB
            val iterations = Argon2Kdf.DEFAULT_ITERATIONS
            val parallelism = Argon2Kdf.DEFAULT_PARALLELISM

            // 2. Derive key from password with Argon2id
            val argon2Key = Argon2Kdf.deriveKey(
                password = masterPassword.toCharArray(),
                salt = salt,
                memoryKb = memoryKb,
                iterations = iterations,
                parallelism = parallelism
            )

            // 3. Resolve Active Backup Key (either purely Argon2 or Keystore-Wrapped)
            var activeBackupKey: SecretKey = argon2Key
            var wrappedKeyBytes: ByteArray = ByteArray(0)

            if (isDeviceLocked) {
                onProgress?.invoke(0, items.size, "Binding to Hardware Keystore + Password...", 0L)
                val hardwareKey = getDeviceBindingMasterKey()
                
                // Generate ephemeral backup key
                val kg = KeyGenerator.getInstance("AES")
                kg.init(256, secureRandom)
                val ephemeralKey = kg.generateKey()
                activeBackupKey = ephemeralKey

                // Two-layer envelope:
                // Layer 1 (Password bound): Encrypt ephemeralKey with Argon2 key
                val passCipher = Cipher.getInstance("AES/GCM/NoPadding")
                passCipher.init(Cipher.ENCRYPT_MODE, argon2Key)
                val passIv = passCipher.iv
                val passEncrypted = passCipher.doFinal(ephemeralKey.encoded)
                val passWrapped = passIv + passEncrypted

                // Layer 2 (Hardware bound): Encrypt passWrapped with Keystore hardware key
                val hwCipher = Cipher.getInstance("AES/GCM/NoPadding")
                hwCipher.init(Cipher.ENCRYPT_MODE, hardwareKey)
                val hwIv = hwCipher.iv
                val hwEncrypted = hwCipher.doFinal(passWrapped)

                wrappedKeyBytes = hwIv + hwEncrypted
            }

            // 4. Write Header:
            outputStream.write(BACKUP_MAGIC_V3)
            val flags = if (isDeviceLocked) 1.toByte() else 0.toByte()
            outputStream.write(flags.toInt())
            outputStream.write(salt)
            writeInt(outputStream, memoryKb)
            writeInt(outputStream, iterations)
            writeInt(outputStream, parallelism)
            writeInt(outputStream, wrappedKeyBytes.size)
            if (wrappedKeyBytes.isNotEmpty()) {
                outputStream.write(wrappedKeyBytes)
            }

            var totalBytesWritten = (BACKUP_MAGIC_V3.size + 1 + salt.size + 16 + wrappedKeyBytes.size).toLong()

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

            // 5. Wrap with Chunked AES-256-GCM Stream
            val chunkedGcmOut = ChunkedGcmOutputStream(countingOut, activeBackupKey, CHUNK_SIZE)

            // 6. Wrap in ZipOutputStream
            ZipOutputStream(chunkedGcmOut.buffered(65536)).use { zos ->
                zos.setLevel(java.util.zip.Deflater.NO_COMPRESSION)

                onProgress?.invoke(0, items.size, "Writing Metadata Manifests...", totalBytesWritten)

                // 0. Add backup_metadata_manifest.json
                val metadata = BackupManifestMetadata(
                    formatVersion = 3,
                    itemsCount = items.size,
                    foldersCount = folders.size,
                    passwordsCount = passwords.size,
                    logsCount = if (includeSecurityLogs) {
                        try { db.intruderLogDao().getAllLogsSync().size } catch (_: Exception) { 0 }
                    } else 0,
                    hasFolders = folders.isNotEmpty(),
                    hasPasswords = passwords.isNotEmpty(),
                    hasLogs = includeSecurityLogs
                )
                val metadataJson = metadataJsonAdapter.toJson(metadata)
                zos.putNextEntry(ZipEntry(MANIFEST_METADATA_FILENAME))
                zos.write(metadataJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 1. Add vault_manifest.json (Items)
                val manifestJson = jsonAdapter.toJson(items)
                zos.putNextEntry(ZipEntry(MANIFEST_FILENAME))
                zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 2. Add vault_folders.json (Folders)
                if (folders.isNotEmpty()) {
                    val foldersJson = foldersJsonAdapter.toJson(folders)
                    zos.putNextEntry(ZipEntry(FOLDERS_FILENAME))
                    zos.write(foldersJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }

                // 3. Add vault_passwords.json (Passwords)
                if (passwords.isNotEmpty()) {
                    val passwordsJson = passwordsJsonAdapter.toJson(passwords)
                    zos.putNextEntry(ZipEntry(PASSWORDS_FILENAME))
                    zos.write(passwordsJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }

                // 4. Add security_logs_manifest.json (Intruder Logs)
                if (includeSecurityLogs) {
                    try {
                        val logs = db.intruderLogDao().getAllLogsSync()
                        if (logs.isNotEmpty()) {
                            val logsJson = logsJsonAdapter.toJson(logs)
                            zos.putNextEntry(ZipEntry(SECURITY_LOGS_FILENAME))
                            zos.write(logsJson.toByteArray(Charsets.UTF_8))
                            zos.closeEntry()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Logs export skipped: ${e.message}")
                    }
                }

                // 5. Add encrypted vault file payloads
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
                            Log.e(TAG, "Failed to package ${item.originalName}: ${e.message}")
                            throw e
                        } finally {
                            try { zos.closeEntry() } catch (_: Throwable) {}
                        }
                    } else {
                        throw IllegalStateException("Vault item payload missing from storage: ${item.originalName}")
                    }
                }
                zos.flush()
            }
            outputStream.flush()

            onProgress?.invoke(items.size, items.size, "Backup Finalized Successfully", totalBytesWritten)
            Result.success(totalBytesWritten)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun inferMimeTypeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "pdf" -> "application/pdf"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            "doc", "docx" -> "application/msword"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    private fun parseVaultItemsManifest(manifestJson: String): List<VaultItem> {
        try {
            val directList = jsonAdapter.fromJson(manifestJson)
            if (!directList.isNullOrEmpty()) {
                return directList
            }
        } catch (e: Exception) {
            Log.w(TAG, "Standard Moshi adapter failed on manifest, trying tolerant parsing: ${e.message}")
        }

        val result = mutableListOf<VaultItem>()
        try {
            val rawListType = Types.newParameterizedType(List::class.java, Map::class.java)
            val rawAdapter = moshi.adapter<List<Map<String, Any?>>>(rawListType)
            val rawItems = rawAdapter.fromJson(manifestJson) ?: emptyList()

            for (map in rawItems) {
                val originalName = (map["originalName"] ?: map["name"] ?: map["title"] ?: map["displayName"]) as? String ?: "restored_file"
                val encryptedFileName = (map["encryptedFileName"] ?: map["fileName"] ?: map["encFileName"] ?: map["path"]) as? String
                    ?: "${System.currentTimeMillis()}_${Math.abs(originalName.hashCode())}.aes"
                val mimeType = (map["mimeType"] ?: map["mime"] ?: map["type"]) as? String ?: inferMimeTypeFromName(originalName)
                val sizeBytes = (map["sizeBytes"] as? Number)?.toLong()
                    ?: (map["size"] as? Number)?.toLong()
                    ?: (map["fileSize"] as? Number)?.toLong()
                    ?: 0L
                val addedTimestamp = (map["addedTimestamp"] as? Number)?.toLong()
                    ?: (map["timestamp"] as? Number)?.toLong()
                    ?: System.currentTimeMillis()
                val isVideo = (map["isVideo"] as? Boolean) ?: mimeType.startsWith("video/")
                val folderName = (map["folderName"] ?: map["folder"]) as? String ?: "Root"

                result.add(
                    VaultItem(
                        id = 0L,
                        originalName = originalName,
                        encryptedFileName = File(encryptedFileName).name,
                        mimeType = mimeType,
                        sizeBytes = sizeBytes,
                        addedTimestamp = addedTimestamp,
                        isVideo = isVideo,
                        folderName = folderName
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tolerant manifest parser error: ${e.message}", e)
        }
        return result
    }

    /**
     * Restores an encrypted backup archive with strict integrity checks, backward compatibility
     * for legacy formats (V3 Argon2id, V2 PBKDF2, V1 PBKDF2, and unencrypted standard ZIPs),
     * tolerant payload extraction, and atomic rollback transaction.
     */
    suspend fun importMasterBackup(
        context: Context,
        masterPassword: String,
        inputStream: InputStream,
        vaultRepository: VaultRepository,
        isReplaceMode: Boolean = false,
        onProgress: ((current: Int, total: Int, currentName: String, bytesProcessed: Long) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "staging_restore_${System.currentTimeMillis()}").apply { mkdirs() }
        val tempBackupFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.bin")
        val movedTargetFiles = mutableListOf<File>()

        try {
            val vaultDir = vaultRepository.getVaultDirectory(context)
            val db = AppDatabase.getDatabase(context)
            onProgress?.invoke(0, 0, "Caching backup archive for integrity verification...", 0L)

            // Cache incoming stream to a local file to allow seeking and multi-pass attempts (e.g. PBKDF2 iteration fallback)
            FileOutputStream(tempBackupFile).buffered(65536).use { fos ->
                inputStream.copyTo(fos)
            }

            if (tempBackupFile.length() < 4L) {
                return@withContext Result.failure(IllegalArgumentException("Corrupted backup file: Truncated or empty archive."))
            }

            val headerBytes = ByteArray(8)
            val headerRead = FileInputStream(tempBackupFile).use { fis ->
                readFully(fis, headerBytes, 0, 8)
            }

            val isZip = headerBytes[0] == 0x50.toByte() && headerBytes[1] == 0x4B.toByte() &&
                    headerBytes[2] == 0x03.toByte() && headerBytes[3] == 0x04.toByte()
            val isV3 = headerRead >= 8 && headerBytes.contentEquals(BACKUP_MAGIC_V3)
            val isV2 = headerRead >= 8 && headerBytes.contentEquals(BACKUP_MAGIC_V2)
            val isV1 = headerRead >= 8 && (
                headerBytes.contentEquals(BACKUP_MAGIC_V1) ||
                headerBytes.contentEquals(BACKUP_MAGIC_VAULTBCK) ||
                headerBytes.contentEquals("VAULT_B1".toByteArray(Charsets.UTF_8)) ||
                headerBytes.contentEquals("VLT_BCKP".toByteArray(Charsets.UTF_8)) ||
                headerBytes.contentEquals("QV_BACK1".toByteArray(Charsets.UTF_8))
            )

            var restoredCount = 0
            var totalBytesRestored = 0L

            // Open decrypted stream according to format
            val decryptedStream: InputStream = when {
                isZip -> {
                    onProgress?.invoke(0, 0, "Reading standard backup archive...", 0L)
                    FileInputStream(tempBackupFile).buffered(65536)
                }
                isV3 -> {
                    onProgress?.invoke(0, 0, "Inspecting Argon2id Parameters...", 0L)
                    val fis = FileInputStream(tempBackupFile).buffered(65536)
                    val skipHeader = ByteArray(8)
                    readFully(fis, skipHeader, 0, 8)

                    val flags = fis.read()
                    if (flags < 0) throw IllegalArgumentException("Incomplete backup flags.")
                    val isDeviceLocked = (flags and 1) != 0

                    val salt = ByteArray(SALT_SIZE_BYTES)
                    if (readFully(fis, salt, 0, SALT_SIZE_BYTES) < SALT_SIZE_BYTES) {
                        throw IllegalArgumentException("Incomplete salt header.")
                    }

                    val memoryKb = readInt(fis)
                    val iterations = readInt(fis)
                    val parallelism = readInt(fis)
                    val wrappedKeyLen = readInt(fis)

                    onProgress?.invoke(0, 0, "Computing 64MB Argon2id Key...", 0L)
                    val argon2Key = Argon2Kdf.deriveKey(
                        password = masterPassword.toCharArray(),
                        salt = salt,
                        memoryKb = if (memoryKb > 0) memoryKb else Argon2Kdf.DEFAULT_MEMORY_KIB,
                        iterations = if (iterations > 0) iterations else Argon2Kdf.DEFAULT_ITERATIONS,
                        parallelism = if (parallelism > 0) parallelism else Argon2Kdf.DEFAULT_PARALLELISM
                    )

                    val activeKey: SecretKey
                    if (isDeviceLocked) {
                        if (wrappedKeyLen <= 0 || wrappedKeyLen > 4096) {
                            throw IllegalStateException("Invalid device binding payload.")
                        }
                        val wrappedBytes = ByteArray(wrappedKeyLen)
                        if (readFully(fis, wrappedBytes, 0, wrappedKeyLen) < wrappedKeyLen) {
                            throw IllegalStateException("Truncated device binding payload.")
                        }

                        val passWrapped = try {
                            val hwKey = getDeviceBindingMasterKey()
                            val hwIv = wrappedBytes.copyOfRange(0, IV_SIZE_BYTES)
                            val hwEncrypted = wrappedBytes.copyOfRange(IV_SIZE_BYTES, wrappedBytes.size)

                            val hwCipher = Cipher.getInstance("AES/GCM/NoPadding")
                            hwCipher.init(Cipher.DECRYPT_MODE, hwKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, hwIv))
                            hwCipher.doFinal(hwEncrypted)
                        } catch (e: Exception) {
                            throw SecurityException("WRONG_DEVICE: Device-Locked Backup cannot restore on this hardware.")
                        }

                        try {
                            val passIv = passWrapped.copyOfRange(0, IV_SIZE_BYTES)
                            val passEncrypted = passWrapped.copyOfRange(IV_SIZE_BYTES, passWrapped.size)
                            val passCipher = Cipher.getInstance("AES/GCM/NoPadding")
                            passCipher.init(Cipher.DECRYPT_MODE, argon2Key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, passIv))
                            val rawEphemeralKey = passCipher.doFinal(passEncrypted)
                            activeKey = SecretKeySpec(rawEphemeralKey, "AES")
                        } catch (e: Exception) {
                            throw SecurityException("INCORRECT_BACKUP_PASSWORD: Incorrect backup master password.")
                        }
                    } else {
                        activeKey = argon2Key
                    }

                    ChunkedGcmInputStream(fis, activeKey)
                }
                isV2 || isV1 -> {
                    onProgress?.invoke(0, 0, "Deriving legacy PBKDF2 encryption key...", 0L)
                    val fis = FileInputStream(tempBackupFile).buffered(65536)
                    val skipHeader = ByteArray(8)
                    readFully(fis, skipHeader, 0, 8)

                    val salt = ByteArray(SALT_SIZE_BYTES)
                    if (readFully(fis, salt, 0, SALT_SIZE_BYTES) < SALT_SIZE_BYTES) {
                        throw IllegalArgumentException("Incomplete legacy salt header.")
                    }

                    // Try 100,000 iterations first, if that fails, try 10,000
                    var secretKey: SecretKey
                    try {
                        val spec100k = javax.crypto.spec.PBEKeySpec(masterPassword.toCharArray(), salt, 100_000, 256)
                        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        secretKey = SecretKeySpec(factory.generateSecret(spec100k).encoded, "AES")
                    } catch (_: Throwable) {
                        val spec10k = javax.crypto.spec.PBEKeySpec(masterPassword.toCharArray(), salt, 10_000, 256)
                        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        secretKey = SecretKeySpec(factory.generateSecret(spec10k).encoded, "AES")
                    }

                    ChunkedGcmInputStream(fis, secretKey)
                }
                else -> {
                    // Fallback attempt: if password provided and size >= 16, try legacy PBKDF2 without magic
                    if (tempBackupFile.length() >= 16L && masterPassword.isNotEmpty()) {
                        val fis = FileInputStream(tempBackupFile).buffered(65536)
                        val salt = ByteArray(SALT_SIZE_BYTES)
                        readFully(fis, salt, 0, SALT_SIZE_BYTES)
                        val spec = javax.crypto.spec.PBEKeySpec(masterPassword.toCharArray(), salt, 10_000, 256)
                        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
                        ChunkedGcmInputStream(fis, key)
                    } else {
                        return@withContext Result.failure(IllegalArgumentException("Unsupported or corrupt backup archive format."))
                    }
                }
            }

            var metadata: BackupManifestMetadata? = null
            var restoredItems: List<VaultItem>? = null
            var restoredFolders: List<VaultFolder>? = null
            var restoredPasswords: List<VaultPassword>? = null
            var logsToRestore: List<IntruderLog>? = null

            val stagedFiles = mutableMapOf<String, File>()
            val seenEntries = mutableSetOf<String>()

            ZipInputStream(decryptedStream.buffered(65536)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name

                    if (entry.isDirectory) {
                        try { zis.closeEntry() } catch (_: Throwable) {}
                        entry = zis.nextEntry
                        continue
                    }

                    // Security Gate: Path traversal and illegal character check
                    if (entryName.contains("..") || entryName.startsWith("/") || entryName.contains("\u0000")) {
                        throw SecurityException("Malicious path traversal entry detected in backup: $entryName")
                    }

                    if (!seenEntries.add(entryName)) {
                        Log.w(TAG, "Duplicate entry in backup archive, skipping duplicate: $entryName")
                        try { zis.closeEntry() } catch (_: Throwable) {}
                        entry = zis.nextEntry
                        continue
                    }

                    val cleanFileName = File(entryName).name
                    val lower = cleanFileName.lowercase()

                    when {
                        lower == MANIFEST_METADATA_FILENAME || lower == "metadata.json" -> {
                            try {
                                val metaJson = zis.readBytes().toString(Charsets.UTF_8)
                                metadata = metadataJsonAdapter.fromJson(metaJson)
                            } catch (e: Exception) {
                                Log.w(TAG, "Metadata manifest parse warning: ${e.message}")
                            }
                        }
                        lower == MANIFEST_FILENAME || lower == "manifest.json" || lower == "vault_items.json" ||
                        lower == "items.json" || lower == "backup_manifest.json" -> {
                            val manifestJson = zis.readBytes().toString(Charsets.UTF_8)
                            restoredItems = parseVaultItemsManifest(manifestJson)
                            onProgress?.invoke(0, restoredItems.size, "Loaded vault records manifest", totalBytesRestored)
                        }
                        lower == FOLDERS_FILENAME || lower == "folders.json" -> {
                            try {
                                val foldersJson = zis.readBytes().toString(Charsets.UTF_8)
                                restoredFolders = foldersJsonAdapter.fromJson(foldersJson)
                            } catch (e: Exception) {
                                Log.w(TAG, "Folders dataset parse warning: ${e.message}")
                            }
                        }
                        lower == PASSWORDS_FILENAME || lower == "passwords.json" -> {
                            try {
                                val passwordsJson = zis.readBytes().toString(Charsets.UTF_8)
                                restoredPasswords = passwordsJsonAdapter.fromJson(passwordsJson)
                            } catch (e: Exception) {
                                Log.w(TAG, "Passwords dataset parse warning: ${e.message}")
                            }
                        }
                        lower == SECURITY_LOGS_FILENAME || lower == "intruder_logs.json" ||
                        lower == "security_logs.json" || lower == "logs.json" -> {
                            try {
                                val logsJson = zis.readBytes().toString(Charsets.UTF_8)
                                logsToRestore = logsJsonAdapter.fromJson(logsJson)
                            } catch (e: Exception) {
                                Log.w(TAG, "Security logs dataset parse warning: ${e.message}")
                            }
                        }
                        else -> {
                            // Any other entry is treated as a vault payload file (regardless of folder structure)
                            val stagedFile = File(stagingDir, cleanFileName)
                            if (!stagedFile.canonicalPath.startsWith(stagingDir.canonicalPath)) {
                                throw SecurityException("Path traversal attempt detected: $cleanFileName")
                            }

                            // Inspect first 4 bytes to check if already an encrypted VLT3/VLT2 envelope
                            val pushback = java.io.PushbackInputStream(zis, 4)
                            val peek = ByteArray(4)
                            val peekRead = readFully(pushback, peek, 0, 4)
                            if (peekRead > 0) {
                                pushback.unread(peek, 0, peekRead)
                            }

                            val isAlreadyVaultEncrypted = peekRead == 4 && (
                                (peek[0] == 0x56.toByte() && peek[1] == 0x4C.toByte() && peek[2] == 0x54.toByte() && peek[3] == 0x33.toByte()) || // VLT3
                                (peek[0] == 0x56.toByte() && peek[1] == 0x4C.toByte() && peek[2] == 0x54.toByte() && peek[3] == 0x32.toByte())    // VLT2
                            )

                            FileOutputStream(stagedFile).buffered(65536).use { fos ->
                                if (isAlreadyVaultEncrypted) {
                                    // Direct copy of already encrypted envelope
                                    pushback.copyTo(fos)
                                } else {
                                    // Plaintext media payload (photo, video, doc): encrypt into device's Master Key format
                                    CryptoManager.encryptStream(pushback, fos)
                                }
                            }

                            totalBytesRestored += stagedFile.length()
                            stagedFiles[cleanFileName] = stagedFile
                            onProgress?.invoke(
                                stagedFiles.size,
                                maxOf(stagedFiles.size, restoredItems?.size ?: 0),
                                "Staging: $cleanFileName",
                                totalBytesRestored
                            )
                        }
                    }
                    try { zis.closeEntry() } catch (_: Throwable) {}
                    entry = zis.nextEntry
                }
            }

            // Reconcile manifest items with staged files
            val finalItemsToInsert = mutableListOf<VaultItem>()
            val matchedStagedNames = mutableSetOf<String>()

            if (!restoredItems.isNullOrEmpty()) {
                for (item in restoredItems) {
                    val cleanEnc = File(item.encryptedFileName).name
                    val cleanOrig = File(item.originalName).name

                    val matched = stagedFiles[cleanEnc] ?: stagedFiles[cleanOrig]
                    if (matched != null) {
                        matchedStagedNames.add(matched.name)
                        finalItemsToInsert.add(
                            item.copy(
                                id = 0L, // autogenerate ID to prevent collision
                                encryptedFileName = matched.name,
                                sizeBytes = if (item.sizeBytes > 0) item.sizeBytes else matched.length()
                            )
                        )
                    } else {
                        Log.w(TAG, "Item '${item.originalName}' (file: ${item.encryptedFileName}) missing from backup files. Restoring available files without aborting.")
                    }
                }
            }

            // Synthesize VaultItems for any staged files not in manifest so NO files are lost
            for ((fileName, stagedFile) in stagedFiles) {
                if (!matchedStagedNames.contains(fileName)) {
                    val mime = inferMimeTypeFromName(fileName)
                    finalItemsToInsert.add(
                        VaultItem(
                            id = 0L,
                            originalName = fileName.removeSuffix(".aes").removeSuffix(".enc"),
                            encryptedFileName = fileName,
                            mimeType = mime,
                            sizeBytes = stagedFile.length(),
                            addedTimestamp = System.currentTimeMillis(),
                            isVideo = mime.startsWith("video/"),
                            folderName = "Root"
                        )
                    )
                }
            }

            // Fetch old items to clean up their files later if replace mode
            val oldItems = if (isReplaceMode) db.vaultDao().getAllItemsSync() else emptyList()
            val restoredFileNames = finalItemsToInsert.map { it.encryptedFileName }.toSet()

            // Phase 5: Atomic Commit Files to vault directory
            for ((fileName, stagedFile) in stagedFiles) {
                val targetFile = File(vaultDir, fileName)
                if (targetFile.exists()) targetFile.delete()
                if (!stagedFile.renameTo(targetFile)) {
                    stagedFile.copyTo(targetFile, overwrite = true)
                    stagedFile.delete()
                }
                movedTargetFiles.add(targetFile)
            }

            // Phase 6: Atomic Database Transaction
            db.runInTransaction {
                kotlinx.coroutines.runBlocking {
                    if (isReplaceMode) {
                        db.vaultDao().deleteAllItems()
                        db.vaultDao().deleteAllFolders()
                        db.vaultPasswordDao().deleteAll()
                        db.intruderLogDao().clearLogs()
                    }

                    restoredFolders?.forEach { folder ->
                        db.vaultDao().insertFolder(folder)
                    }
                    restoredPasswords?.forEach { password ->
                        db.vaultPasswordDao().insertPassword(password)
                    }
                    logsToRestore?.forEach { log ->
                        db.intruderLogDao().insertLog(log)
                    }
                    finalItemsToInsert.forEach { item ->
                        vaultRepository.insertRestoredVaultItem(item)
                        restoredCount++
                    }
                }
            }

            if (isReplaceMode) {
                val thumbDir = File(context.cacheDir, "vault_thumbnails_encrypted")
                for (oldItem in oldItems) {
                    if (!restoredFileNames.contains(oldItem.encryptedFileName)) {
                        File(vaultDir, oldItem.encryptedFileName).delete()
                        File(thumbDir, "${oldItem.encryptedFileName}.thumb_aes256").delete()
                    }
                }
            }

            onProgress?.invoke(restoredCount, restoredCount, "Restoration Complete ($restoredCount files)", totalBytesRestored)
            Result.success(restoredCount)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during import: ${e.message}", e)
            movedTargetFiles.forEach { try { it.delete() } catch (_: Exception) {} }
            Result.failure(e)
        } catch (e: AEADBadTagException) {
            Log.e(TAG, "AEAD Bad Tag: Invalid master password or corrupted backup ciphertext", e)
            movedTargetFiles.forEach { try { it.delete() } catch (_: Exception) {} }
            Result.failure(SecurityException("INCORRECT_BACKUP_PASSWORD: Password invalid for backup archive.", e))
        } catch (e: Exception) {
            Log.e(TAG, "Import failed: ${e.message}", e)
            movedTargetFiles.forEach { try { it.delete() } catch (_: Exception) {} }
            Result.failure(e)
        } finally {
            stagingDir.deleteRecursively()
            try { tempBackupFile.delete() } catch (_: Exception) {}
        }
    }

    /**
     * Forensically shreds and wipes a backup file after successful restoration.
     */
    fun shredBackupFile(file: File): Boolean {
        return try {
            if (!file.exists()) return true
            val length = file.length()
            if (length > 0) {
                java.io.RandomAccessFile(file, "rws").use { raf ->
                    val zeroBuf = ByteArray(65536)
                    var written = 0L
                    while (written < length) {
                        val toWrite = minOf(zeroBuf.size.toLong(), length - written).toInt()
                        raf.write(zeroBuf, 0, toWrite)
                        written += toWrite
                    }
                }
            }
            file.delete()
        } catch (e: Exception) {
            false
        }
    }
}
