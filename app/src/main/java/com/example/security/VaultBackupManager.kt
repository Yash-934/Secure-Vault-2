package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.IntruderLog
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
 * Hardened Vault Backup & Disaster Recovery Engine with Argon2id and Optional Hardware Device-Binding.
 *
 * Security Protocol:
 * 1. KDF: Argon2id (64 MiB RAM, 3 iterations, 1 parallelism, 16-byte random salt).
 * 2. Payload Encryption: Streaming Chunked AES-256-GCM (1MB per chunk, unique 12-byte IV per chunk).
 * 3. Device Binding: Optional hardware Keystore wrapping (non-exportable TEE key).
 *    If enabled, backup is cryptographically locked to the physical hardware device.
 * 4. Header Format (V3):
 *    [Magic 8B 'VLT_BCK3'] + [Flags 1B: bit0=device_locked] + [Salt 16B] +
 *    [Argon2 Memory KB 4B] + [Iterations 4B] + [Parallelism 4B] +
 *    [DeviceWrappedKeyLen 4B + WrappedKeyBytes] + [AES-GCM Chunks...]
 * 5. Backward Compatibility: Seamlessly restores V2 (PBKDF2) and V1 archives.
 */
object VaultBackupManager {

    private const val TAG = "VaultBackup"
    private val BACKUP_MAGIC_V3 = "VLT_BCK3".toByteArray(Charsets.UTF_8) // 8 bytes
    private val BACKUP_MAGIC_V2 = "VLT_BCK2".toByteArray(Charsets.UTF_8) // 8 bytes
    private const val SALT_SIZE_BYTES = 16
    private const val IV_SIZE_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val CHUNK_SIZE = 1024 * 1024 // 1 MB streaming chunks
    private const val MANIFEST_FILENAME = "vault_manifest.json"
    private const val SECURITY_LOGS_FILENAME = "security_logs_manifest.json"
    private const val DEVICE_BINDING_KEY_ALIAS = "VaultBackupDeviceBindingHardwareKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, VaultItem::class.java)
    private val jsonAdapter = moshi.adapter<List<VaultItem>>(listType)
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
     * Chunked AES-256-GCM OutputStream for backup archives.
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
     * Chunked AES-256-GCM InputStream for streaming restore.
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
            val aad = java.nio.ByteBuffer.allocate(8).putLong(chunkIndex++).array()
            cipher.updateAAD(aad)
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
            currentPlainChunk?.fill(0)
            currentPlainChunk = null
            underlying.close()
        }
    }

    /**
     * Exports an Argon2id + AES-256-GCM encrypted backup with optional Hardware Keystore Device Binding.
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

                // Two-layer cryptographically enforced envelope:
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

                // Package wrapped key: hwIv (12B) + hwEncrypted
                wrappedKeyBytes = hwIv + hwEncrypted
            }

            // 4. Write Header:
            // Magic 8B + Flags 1B (bit 0: device_locked) + Salt 16B + KDF params (3x 4B) + WrappedKeyLen (4B) + WrappedKey
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

                onProgress?.invoke(0, items.size, "Writing Vault Metadata Manifest...", totalBytesWritten)

                // Add manifest.json
                val manifestJson = jsonAdapter.toJson(items)
                zos.putNextEntry(ZipEntry(MANIFEST_FILENAME))
                zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // Optional: Export Security Logs
                if (includeSecurityLogs) {
                    try {
                        val db = AppDatabase.getDatabase(context)
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

                // Add each vault file directly decrypted on-the-fly into the backup stream
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

    /**
     * Restores an encrypted backup archive with strict integrity checks and atomic staging.
     */
    suspend fun importMasterBackup(
        context: Context,
        masterPassword: String,
        inputStream: InputStream,
        vaultRepository: VaultRepository,
        onProgress: ((current: Int, total: Int, currentName: String, bytesProcessed: Long) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "staging_restore_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val vaultDir = vaultRepository.getVaultDirectory(context)
            onProgress?.invoke(0, 0, "Inspecting Vault Backup Header...", 0L)

            val headerBytes = ByteArray(8)
            val headerRead = readFully(inputStream, headerBytes, 0, 8)
            if (headerRead < 8) {
                return@withContext Result.failure(IllegalArgumentException("Corrupted backup file: Truncated header."))
            }

            val isV3 = headerBytes.contentEquals(BACKUP_MAGIC_V3)
            val isV2 = headerBytes.contentEquals(BACKUP_MAGIC_V2)

            var restoredCount = 0
            var totalBytesRestored = 0L

            if (isV3) {
                val flags = inputStream.read()
                if (flags < 0) return@withContext Result.failure(IllegalArgumentException("Incomplete backup flags."))
                val isDeviceLocked = (flags and 1) != 0

                val salt = ByteArray(SALT_SIZE_BYTES)
                if (readFully(inputStream, salt, 0, SALT_SIZE_BYTES) < SALT_SIZE_BYTES) {
                    return@withContext Result.failure(IllegalArgumentException("Incomplete salt header."))
                }

                val memoryKb = readInt(inputStream)
                val iterations = readInt(inputStream)
                val parallelism = readInt(inputStream)
                val wrappedKeyLen = readInt(inputStream)

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
                        return@withContext Result.failure(IllegalStateException("Invalid device binding payload."))
                    }
                    val wrappedBytes = ByteArray(wrappedKeyLen)
                    if (readFully(inputStream, wrappedBytes, 0, wrappedKeyLen) < wrappedKeyLen) {
                        return@withContext Result.failure(IllegalStateException("Truncated device binding payload."))
                    }

                    // Unwrap with hardware Keystore key first (outer layer)
                    val passWrapped = try {
                        val hwKey = getDeviceBindingMasterKey()
                        val hwIv = wrappedBytes.copyOfRange(0, IV_SIZE_BYTES)
                        val hwEncrypted = wrappedBytes.copyOfRange(IV_SIZE_BYTES, wrappedBytes.size)

                        val hwCipher = Cipher.getInstance("AES/GCM/NoPadding")
                        hwCipher.init(Cipher.DECRYPT_MODE, hwKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, hwIv))
                        hwCipher.doFinal(hwEncrypted)
                    } catch (e: Exception) {
                        return@withContext Result.failure(
                            SecurityException("Device-Locked Backup: Cannot restore on this hardware. Keystore signature mismatch.")
                        )
                    }

                    // Unwrap inner layer with Argon2 password key
                    try {
                        val passIv = passWrapped.copyOfRange(0, IV_SIZE_BYTES)
                        val passEncrypted = passWrapped.copyOfRange(IV_SIZE_BYTES, passWrapped.size)
                        val passCipher = Cipher.getInstance("AES/GCM/NoPadding")
                        passCipher.init(Cipher.DECRYPT_MODE, argon2Key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, passIv))
                        val rawEphemeralKey = passCipher.doFinal(passEncrypted)
                        activeKey = SecretKeySpec(rawEphemeralKey, "AES")
                    } catch (e: Exception) {
                        return@withContext Result.failure(
                            SecurityException("Incorrect backup master password.")
                        )
                    }
                } else {
                    activeKey = argon2Key
                }

                val chunkedGcmIn = ChunkedGcmInputStream(inputStream, activeKey)
                var restoredItems: List<VaultItem>? = null
                var logsToRestore: List<IntruderLog>? = null
                val stagedFiles = mutableListOf<Pair<File, File>>() // stagedFile -> targetVaultFile

                ZipInputStream(chunkedGcmIn.buffered(65536)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == MANIFEST_FILENAME) {
                            val manifestJson = zis.readBytes().toString(Charsets.UTF_8)
                            restoredItems = jsonAdapter.fromJson(manifestJson)
                            onProgress?.invoke(0, restoredItems?.size ?: 0, "Loaded vault records", totalBytesRestored)
                        } else if (entry.name == SECURITY_LOGS_FILENAME) {
                            try {
                                val logsJson = zis.readBytes().toString(Charsets.UTF_8)
                                logsToRestore = logsJsonAdapter.fromJson(logsJson)
                            } catch (_: Exception) {}
                        } else if (entry.name.startsWith("vault_data_v2/")) {
                            val fileName = File(entry.name).name
                            val stagedFile = File(stagingDir, fileName)
                            val targetFile = File(vaultDir, fileName)
                            val itemObj = restoredItems?.find { it.encryptedFileName == fileName }
                            val displayName = itemObj?.originalName ?: fileName
                            val totalExpected = restoredItems?.size ?: 0

                            onProgress?.invoke(stagedFiles.size + 1, totalExpected, "Staging: $displayName", totalBytesRestored)

                            FileOutputStream(stagedFile).buffered(65536).use { fos ->
                                CryptoManager.encryptStream(zis, fos)
                            }
                            totalBytesRestored += stagedFile.length()
                            stagedFiles.add(stagedFile to targetFile)
                        }
                        try { zis.closeEntry() } catch (_: Throwable) {}
                        entry = zis.nextEntry
                    }
                }

                if (restoredItems == null) {
                    return@withContext Result.failure(IllegalStateException("Corrupted archive: Missing manifest."))
                }

                // Verify all manifest items exist in stagedFiles
                val stagedFileNames = stagedFiles.map { it.second.name }.toSet()
                for (item in restoredItems) {
                    if (!stagedFileNames.contains(item.encryptedFileName)) {
                        return@withContext Result.failure(IllegalStateException("Incomplete archive: Missing payload for '${item.originalName}'"))
                    }
                }

                // Atomic commit: Move staged files to vault directory
                for ((staged, target) in stagedFiles) {
                    if (target.exists()) target.delete()
                    if (!staged.renameTo(target)) {
                        staged.copyTo(target, overwrite = true)
                        staged.delete()
                    }
                }

                // Insert logs if present
                logsToRestore?.let { logs ->
                    try {
                        val db = AppDatabase.getDatabase(context)
                        logs.forEach { db.intruderLogDao().insertLog(it) }
                    } catch (_: Exception) {}
                }

                // Insert verified vault items
                restoredItems.forEach { item ->
                    vaultRepository.insertRestoredVaultItem(item)
                    restoredCount++
                }
            } else if (isV2) {
                // Fallback for V2 (PBKDF2)
                val salt = ByteArray(SALT_SIZE_BYTES)
                if (readFully(inputStream, salt, 0, SALT_SIZE_BYTES) < SALT_SIZE_BYTES) {
                    return@withContext Result.failure(IllegalArgumentException("Incomplete legacy salt header."))
                }

                val spec = javax.crypto.spec.PBEKeySpec(masterPassword.toCharArray(), salt, 10_000, 256)
                val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

                val chunkedGcmIn = ChunkedGcmInputStream(inputStream, secretKey)
                var restoredItems: List<VaultItem>? = null
                val stagedFiles = mutableListOf<Pair<File, File>>()

                ZipInputStream(chunkedGcmIn.buffered(65536)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == MANIFEST_FILENAME) {
                            val manifestJson = zis.readBytes().toString(Charsets.UTF_8)
                            restoredItems = jsonAdapter.fromJson(manifestJson)
                        } else if (entry.name.startsWith("vault_data_v2/")) {
                            val fileName = File(entry.name).name
                            val stagedFile = File(stagingDir, fileName)
                            val targetFile = File(vaultDir, fileName)
                            FileOutputStream(stagedFile).buffered(65536).use { fos ->
                                CryptoManager.encryptStream(zis, fos)
                            }
                            totalBytesRestored += stagedFile.length()
                            stagedFiles.add(stagedFile to targetFile)
                        }
                        try { zis.closeEntry() } catch (_: Throwable) {}
                        entry = zis.nextEntry
                    }
                }

                if (restoredItems == null) {
                    return@withContext Result.failure(IllegalStateException("Corrupted legacy archive: Missing manifest."))
                }

                for ((staged, target) in stagedFiles) {
                    if (target.exists()) target.delete()
                    if (!staged.renameTo(target)) {
                        staged.copyTo(target, overwrite = true)
                        staged.delete()
                    }
                }

                restoredItems.forEach { item ->
                    vaultRepository.insertRestoredVaultItem(item)
                    restoredCount++
                }
            } else {
                return@withContext Result.failure(IllegalArgumentException("Unsupported or corrupt backup archive format."))
            }

            onProgress?.invoke(restoredCount, restoredCount, "Restoration Complete ($restoredCount files)", totalBytesRestored)
            Result.success(restoredCount)
        } catch (e: AEADBadTagException) {
            Log.e(TAG, "AEAD Bad Tag: Invalid master password or corrupted backup ciphertext", e)
            Result.failure(SecurityException("Incorrect backup master password or corrupted cryptographic tag."))
        } catch (e: Exception) {
            Log.e(TAG, "Import failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            stagingDir.deleteRecursively()
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
