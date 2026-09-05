package com.quantumvault.wkqpx.security

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

// Typed Backup & Restore Exceptions
open class BackupException(message: String, cause: Throwable? = null) : SecurityException(message, cause)
class DeviceBindingMismatchException(message: String, cause: Throwable? = null) : BackupException(message, cause)
class InvalidBackupPasswordException(message: String, cause: Throwable? = null) : BackupException(message, cause)
class CorruptedBackupException(message: String, cause: Throwable? = null) : BackupException(message, cause)
class BackupManifestIntegrityException(message: String, cause: Throwable? = null) : BackupException(message, cause)
class CryptoDowngradeException(message: String, cause: Throwable? = null) : BackupException(message, cause)
class GenerationRegressionException(message: String, cause: Throwable? = null) : BackupException(message, cause)

enum class RestoreFaultPhase {
    AFTER_FS_SWAP,
    DURING_DB_TRANSACTION,
    BEFORE_GENERATION_COMMIT
}

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

    @androidx.annotation.VisibleForTesting(otherwise = androidx.annotation.VisibleForTesting.NONE)
    var testFaultInjectionHook: ((phase: RestoreFaultPhase) -> Unit)? = null

    private const val TAG = "VaultBackup"
    const val V4_PINNED_MEMORY_KIB = 65536 // Exactly 64 MiB
    const val V4_PINNED_ITERATIONS = 3
    const val V4_PINNED_PARALLELISM = 1
    const val V4_ALGORITHM_SUITE = "ARGON2ID_HKDF_AES256GCM_V4"

    private val BACKUP_MAGIC_V4 = "VLT_BCK4".toByteArray(Charsets.UTF_8) // 8 bytes
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

    private const val MANIFEST_V4_FILENAME = "backup_manifest_v4.json"

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class BackupFileEntry(
        val itemId: Long = 0L,
        val payloadId: String = "",
        val archivePath: String = "",
        val fileName: String = "",
        val originalName: String = "",
        val sizeBytes: Long = 0L,
        val sha256Hex: String = "",
        val mimeType: String = "",
        val folderName: String = ""
    )

    @com.squareup.moshi.JsonClass(generateAdapter = true)
    data class VaultBackupManifestV4(
        val formatVersion: Int = 4,
        val sourceRealm: Int = 1, // 1: Real Vault, 2: Decoy Vault
        val backupUuid: String = java.util.UUID.randomUUID().toString(),
        val algorithmSuite: String = "ARGON2ID_HKDF_AES256GCM_V4",
        val payloadRootDigest: String = "",
        val itemsCount: Int = 0,
        val foldersCount: Int = 0,
        val passwordsCount: Int = 0,
        val logsCount: Int = 0,
        val fileInventory: List<BackupFileEntry> = emptyList(),
        val createdAt: Long = System.currentTimeMillis()
    )

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
    private val manifestV4Adapter = moshi.adapter(VaultBackupManifestV4::class.java)
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
     * Derives a cryptographic context byte array (SHA-256 digest) binding container identity, realm, and manifest digest.
     */
    fun buildCryptoContext(backupUuid: String, sourceRealm: Int, rootDigest: String): ByteArray {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update("QUANTUM_VAULT_V4_AAD_CONTEXT:".toByteArray(Charsets.UTF_8))
        md.update(backupUuid.toByteArray(Charsets.UTF_8))
        md.update(java.nio.ByteBuffer.allocate(4).putInt(sourceRealm).array())
        md.update(rootDigest.toByteArray(Charsets.UTF_8))
        return md.digest()
    }

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
        private val chunkSize: Int = CHUNK_SIZE,
        private val cryptoContext: ByteArray? = null
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

            val expectedCipherLength = bufferPos + (GCM_TAG_LENGTH_BITS / 8)
            val isLastByte = if (isLast) 1.toByte() else 0.toByte()

            // V4 Authenticated Additional Data binding:
            // [cryptoContext] (optional 32 bytes) + chunkIndex (8 bytes) + cipherLength (4 bytes) + isLast (1 byte)
            val ctxLen = cryptoContext?.size ?: 0
            val aad = java.nio.ByteBuffer.allocate(ctxLen + 8 + 4 + 1)
            if (cryptoContext != null) {
                aad.put(cryptoContext)
            }
            aad.putLong(chunkIndex++)
            aad.putInt(expectedCipherLength)
            aad.put(isLastByte)
            cipher.updateAAD(aad.array())
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
     * Supported AAD Modes for Chunked AES-256-GCM streaming.
     */
    enum class AadMode {
        NONE,
        INDEX_ONLY,
        HARDENED_V4
    }

    /**
     * Chunked AES-256-GCM InputStream with fail-closed integrity validation.
     * Supports hardened V4 AAD (index + length + isLast), legacy monotonic AAD (index only),
     * and unauthenticated AAD (legacy pre-V3 format).
     */
    class ChunkedGcmInputStream(
        private val underlying: InputStream,
        private val secretKey: SecretKey,
        private val useAad: Boolean = true,
        val aadMode: AadMode = if (useAad) AadMode.HARDENED_V4 else AadMode.NONE,
        private val cryptoContext: ByteArray? = null
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
            when (aadMode) {
                AadMode.HARDENED_V4 -> {
                    val ctxLen = cryptoContext?.size ?: 0
                    val aad = java.nio.ByteBuffer.allocate(ctxLen + 8 + 4 + 1)
                    if (cryptoContext != null) {
                        aad.put(cryptoContext)
                    }
                    aad.putLong(chunkIndex)
                    aad.putInt(cipherLength)
                    aad.put(isLastFlag.toByte())
                    cipher.updateAAD(aad.array())
                }
                AadMode.INDEX_ONLY -> {
                    val aad = java.nio.ByteBuffer.allocate(8).putLong(chunkIndex).array()
                    cipher.updateAAD(aad)
                }
                AadMode.NONE -> {
                    // No AAD
                }
            }
            chunkIndex++
            val plain = try {
                cipher.doFinal(cipherBuffer)
            } catch (e: AEADBadTagException) {
                if (chunkIndex <= 1L && chunksDecryptedCount == 0) {
                    throw InvalidBackupPasswordException("INCORRECT_BACKUP_PASSWORD: Password invalid for backup archive.", e)
                } else {
                    throw CorruptedBackupException("BACKUP_CORRUPTED: Backup archive is corrupted or cryptographic tag verification failed.", e)
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
     * Single continuous AES-256-GCM cipher stream for legacy or alternative stream backups.
     */
    class SingleAesGcmInputStream(
        private val underlying: InputStream,
        private val secretKey: SecretKey,
        private val iv: ByteArray
    ) : InputStream() {
        private val cipherIn: InputStream

        init {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipherIn = javax.crypto.CipherInputStream(underlying, cipher)
        }

        override fun read(): Int = cipherIn.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = cipherIn.read(b, off, len)
        override fun close() = cipherIn.close()
    }

    /**
     * Single continuous AES-256-CBC cipher stream for legacy backups.
     */
    class SingleAesCbcInputStream(
        private val underlying: InputStream,
        private val secretKey: SecretKey,
        private val iv: ByteArray
    ) : InputStream() {
        private val cipherIn: InputStream

        init {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, javax.crypto.spec.IvParameterSpec(iv))
            cipherIn = javax.crypto.CipherInputStream(underlying, cipher)
        }

        override fun read(): Int = cipherIn.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = cipherIn.read(b, off, len)
        override fun close() = cipherIn.close()
    }

    data class DecryptedStreamResult(
        val stream: InputStream,
        val formatDescription: String,
        val isSqliteDatabase: Boolean = false
    )

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

            // 4. Precompute V4 authenticated inventory with UUID payloadId, SHA-256 and realm binding
            val fileEntries = mutableListOf<BackupFileEntry>()
            for (item in items) {
                val file = File(vaultDir, item.encryptedFileName)
                if (!file.exists() || file.length() == 0L) {
                    throw IllegalStateException("Vault item '${item.originalName}' (${item.encryptedFileName}) missing or empty on disk! Cannot create consistent V4 backup.")
                }
                val sha = computePlaintextSha256(file)
                val uniquePayloadId = "v4_${java.util.UUID.randomUUID().toString().replace("-", "")}"
                val archivePath = "vault_data_v4/$uniquePayloadId.enc"
                fileEntries.add(
                    BackupFileEntry(
                        itemId = item.id,
                        payloadId = uniquePayloadId,
                        archivePath = archivePath,
                        fileName = item.encryptedFileName,
                        originalName = item.originalName,
                        sizeBytes = item.sizeBytes,
                        sha256Hex = sha,
                        mimeType = item.mimeType,
                        folderName = item.folderName
                    )
                )
            }
            val currentRealm = if (VaultKeyManager.isDecoyVaultAuthorized()) 2 else 1
            val backupUuid = java.util.UUID.randomUUID().toString()

            val sortedShaList = fileEntries.map { it.sha256Hex }.sorted()
            val rootDigest = if (sortedShaList.isNotEmpty()) {
                val md = java.security.MessageDigest.getInstance("SHA-256")
                sortedShaList.forEach { md.update(it.toByteArray(Charsets.UTF_8)) }
                val digestBytes = md.digest()
                val sb = StringBuilder(digestBytes.size * 2)
                for (b in digestBytes) {
                    sb.append(String.format("%02x", b))
                }
                sb.toString()
            } else ""

            val cryptoContext = buildCryptoContext(backupUuid, currentRealm, rootDigest)

            // 5. Write Header:
            outputStream.write(BACKUP_MAGIC_V4)
            val flags = if (isDeviceLocked) 1.toByte() else 0.toByte()
            outputStream.write(flags.toInt())
            outputStream.write(salt)
            writeInt(outputStream, memoryKb)
            writeInt(outputStream, iterations)
            writeInt(outputStream, parallelism)
            writeInt(outputStream, wrappedKeyBytes.size)
            writeInt(outputStream, currentRealm)
            val uuidBytes = backupUuid.toByteArray(Charsets.UTF_8).copyOf(36)
            outputStream.write(uuidBytes)
            val digestBytes = rootDigest.padEnd(64, '0').toByteArray(Charsets.UTF_8).copyOf(64)
            outputStream.write(digestBytes)
            if (wrappedKeyBytes.isNotEmpty()) {
                outputStream.write(wrappedKeyBytes)
            }

            var totalBytesWritten = (BACKUP_MAGIC_V4.size + 1 + salt.size + 16 + 4 + 36 + 64 + wrappedKeyBytes.size).toLong()

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

            // 6. Wrap with Chunked AES-256-GCM Stream bound to container cryptoContext
            val chunkedGcmOut = ChunkedGcmOutputStream(countingOut, activeBackupKey, CHUNK_SIZE, cryptoContext = cryptoContext)

            // 7. Wrap in ZipOutputStream
            ZipOutputStream(chunkedGcmOut.buffered(65536)).use { zos ->
                zos.setLevel(java.util.zip.Deflater.NO_COMPRESSION)

                onProgress?.invoke(0, items.size, "Writing Metadata Manifests...", totalBytesWritten)

                val securityLogs = if (includeSecurityLogs) {
                    try {
                        db.intruderLogDao().getAllLogsSync()
                    } catch (e: Exception) {
                        throw SecurityException("Security logs export requested but database extraction failed: ${e.message}", e)
                    }
                } else emptyList()

                val manifestV4 = VaultBackupManifestV4(
                    formatVersion = 4,
                    sourceRealm = currentRealm,
                    backupUuid = backupUuid,
                    algorithmSuite = "ARGON2ID_HKDF_AES256GCM_V4",
                    payloadRootDigest = rootDigest,
                    itemsCount = items.size,
                    foldersCount = folders.size,
                    passwordsCount = passwords.size,
                    logsCount = securityLogs.size,
                    fileInventory = fileEntries,
                    createdAt = System.currentTimeMillis()
                )
                val manifestV4Json = manifestV4Adapter.toJson(manifestV4)
                zos.putNextEntry(ZipEntry(MANIFEST_V4_FILENAME))
                zos.write(manifestV4Json.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 0b. Add backup_metadata_manifest.json (V3 backward compatibility)
                val metadata = BackupManifestMetadata(
                    formatVersion = 4,
                    itemsCount = items.size,
                    foldersCount = folders.size,
                    passwordsCount = passwords.size,
                    logsCount = securityLogs.size,
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
                if (includeSecurityLogs && securityLogs.isNotEmpty()) {
                    val logsJson = logsJsonAdapter.toJson(securityLogs)
                    zos.putNextEntry(ZipEntry(SECURITY_LOGS_FILENAME))
                    zos.write(logsJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }

                // 5. Add encrypted vault file payloads mapped to unique payloadId in V4
                items.forEachIndexed { index, item ->
                    onProgress?.invoke(index + 1, items.size, item.originalName, totalBytesWritten)
                    val file = File(vaultDir, item.encryptedFileName)
                    if (file.exists() && file.length() > 0) {
                        try {
                            val entry = fileEntries.find { it.itemId == item.id && it.fileName == item.encryptedFileName }
                                ?: fileEntries.find { it.fileName == item.encryptedFileName }
                            val entryPath = entry?.archivePath ?: "vault_data_v4/v4_${item.encryptedFileName}"
                            zos.putNextEntry(ZipEntry(entryPath))
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

    private fun computeSha256Hex(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(65536).use { fis ->
            val buf = ByteArray(65536)
            var r: Int
            while (fis.read(buf).also { r = it } != -1) {
                digest.update(buf, 0, r)
            }
        }
        val bytes = digest.digest()
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun computePlaintextSha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered(65536).use { fis ->
            val out = object : OutputStream() {
                override fun write(b: Int) {
                    digest.update(b.toByte())
                }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    digest.update(b, off, len)
                }
            }
            CryptoManager.decryptStreamToOutputStream(fis, out)
        }
        val bytes = digest.digest()
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
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
     * Rapidly checks whether a candidate decryption stream yields a valid archive or document header.
     */
    private fun testDecryptionCandidate(streamSupplier: () -> InputStream): Boolean {
        return try {
            streamSupplier().use { input ->
                val buf = ByteArray(4)
                val read = readFully(input, buf, 0, 4)
                if (read < 4) return false
                val isZip = buf[0] == 0x50.toByte() && buf[1] == 0x4B.toByte()
                val isJson = buf[0] == '{'.toByte() || buf[0] == '['.toByte()
                val isSqlite = buf[0] == 'S'.toByte() && buf[1] == 'Q'.toByte() && buf[2] == 'L'.toByte() && buf[3] == 'i'.toByte()
                val isMedia = (buf[0] == 0xFF.toByte() && buf[1] == 0xD8.toByte() && buf[2] == 0xFF.toByte()) ||
                        (buf[0] == 0x89.toByte() && buf[1] == 0x50.toByte() && buf[2] == 0x4E.toByte() && buf[3] == 0x47.toByte()) ||
                        (buf[0] == '%'.toByte() && buf[1] == 'P'.toByte() && buf[2] == 'D'.toByte() && buf[3] == 'F'.toByte())

                isZip || isJson || isSqlite || isMedia
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Resolves the decrypted stream from any backup version (V3 Argon2id, V2/V1 PBKDF2, SQLite, or unencrypted ZIP).
     */
    private fun resolveDecryptedBackupStream(
        context: Context,
        tempBackupFile: File,
        masterPassword: String,
        onProgress: ((current: Int, total: Int, currentName: String, bytesProcessed: Long) -> Unit)?
    ): DecryptedStreamResult {
        if (tempBackupFile.length() < 4L) {
            throw IllegalArgumentException("Corrupted backup file: Truncated or empty archive.")
        }

        val headerBytes = ByteArray(minOf(256, tempBackupFile.length().toInt()))
        FileInputStream(tempBackupFile).use { fis ->
            readFully(fis, headerBytes, 0, headerBytes.size)
        }

        // 1. Direct standard ZIP check (unencrypted)
        if (headerBytes.size >= 4 && headerBytes[0] == 0x50.toByte() && headerBytes[1] == 0x4B.toByte()) {
            if (testDecryptionCandidate { FileInputStream(tempBackupFile).buffered(65536) }) {
                Log.i(TAG, "Archive resolved as unencrypted standard ZIP")
                return DecryptedStreamResult(
                    stream = FileInputStream(tempBackupFile).buffered(65536),
                    formatDescription = "Standard ZIP archive"
                )
            }
        }

        // 2. Direct SQLite check (unencrypted database export)
        if (headerBytes.size >= 16 && String(headerBytes.copyOfRange(0, 15), Charsets.UTF_8).startsWith("SQLite format 3")) {
            Log.i(TAG, "Archive resolved as direct SQLite database")
            return DecryptedStreamResult(
                stream = FileInputStream(tempBackupFile).buffered(65536),
                formatDescription = "Direct SQLite database backup",
                isSqliteDatabase = true
            )
        }

        val passwordsToTry = mutableListOf<String>()
        if (masterPassword.isNotEmpty()) {
            passwordsToTry.add(masterPassword)
            val trimmed = masterPassword.trim()
            if (trimmed != masterPassword && trimmed.isNotEmpty()) {
                passwordsToTry.add(trimmed)
            }
        } else {
            passwordsToTry.add("")
        }

        // Header detection
        val isV4 = headerBytes.size >= 8 && headerBytes.copyOfRange(0, 8).contentEquals(BACKUP_MAGIC_V4)
        val isV3 = headerBytes.size >= 8 && headerBytes.copyOfRange(0, 8).contentEquals(BACKUP_MAGIC_V3)
        val isV2 = headerBytes.size >= 8 && headerBytes.copyOfRange(0, 8).contentEquals(BACKUP_MAGIC_V2)
        val isV1 = headerBytes.size >= 8 && (
            headerBytes.copyOfRange(0, 8).contentEquals(BACKUP_MAGIC_V1) ||
            headerBytes.copyOfRange(0, 8).contentEquals(BACKUP_MAGIC_VAULTBCK) ||
            headerBytes.copyOfRange(0, 8).contentEquals("VAULT_B1".toByteArray(Charsets.UTF_8)) ||
            headerBytes.copyOfRange(0, 8).contentEquals("VLT_BCKP".toByteArray(Charsets.UTF_8)) ||
            headerBytes.copyOfRange(0, 8).contentEquals("QV_BACK1".toByteArray(Charsets.UTF_8))
        )
        val is4ByteVlt = headerBytes.size >= 4 && (
            (headerBytes[0] == 0x56.toByte() && headerBytes[1] == 0x4C.toByte() && headerBytes[2] == 0x54.toByte() && headerBytes[3] == 0x34.toByte()) ||
            (headerBytes[0] == 0x56.toByte() && headerBytes[1] == 0x4C.toByte() && headerBytes[2] == 0x54.toByte() && headerBytes[3] == 0x33.toByte()) ||
            (headerBytes[0] == 0x56.toByte() && headerBytes[1] == 0x4C.toByte() && headerBytes[2] == 0x54.toByte() && headerBytes[3] == 0x32.toByte()) ||
            (headerBytes[0] == 0x56.toByte() && headerBytes[1] == 0x4C.toByte() && headerBytes[2] == 0x54.toByte() && headerBytes[3] == 0x31.toByte())
        )

        var detectedDeviceLocked = false

        // Strict V4 Backup Header Grammar:
        // [0..7]: MAGIC_V4 ("VLT_BCK4")
        // [8]: flags (1 byte: 1 = device-locked, 0 = portable)
        // [9..24]: Argon2id Salt (16 bytes)
        // [25..28]: memoryKb (4 bytes Int)
        // [29..32]: iterations (4 bytes Int)
        // [33..36]: parallelism (4 bytes Int)
        // [37..40]: wrappedKeyLen (4 bytes Int)
        // [41..44]: sourceRealm (4 bytes Int)
        // [45..80]: backupUuid (36 bytes UTF-8)
        // [81..144]: payloadRootDigest (64 bytes UTF-8)
        // [145..145+wrappedKeyLen-1]: wrappedKeyBytes (if device-locked)
        // [Offset]: ChunkedGcmInputStream ciphertext stream (strict bound cryptoContext AAD)
        if (isV4) {
            onProgress?.invoke(0, 0, "Inspecting V4 Argon2id Parameters & Crypto Context...", 0L)
            if (headerBytes.size < 145) {
                throw CorruptedBackupException("Corrupted V4 backup: Header truncated (less than 145 bytes)")
            }

            val flags = headerBytes[8].toInt()
            detectedDeviceLocked = (flags and 1) != 0
            val salt = headerBytes.copyOfRange(9, 25)

            val bb = java.nio.ByteBuffer.wrap(headerBytes, 25, 20)
            val memoryKb = bb.getInt()
            val iterations = bb.getInt()
            val parallelism = bb.getInt()
            val wrappedKeyLen = bb.getInt()
            val headerRealm = bb.getInt()

            val headerUuid = String(headerBytes.copyOfRange(45, 81), Charsets.UTF_8).trim('\u0000', ' ')
            val headerDigest = String(headerBytes.copyOfRange(81, 145), Charsets.UTF_8).trim('\u0000', ' ')

            if (memoryKb != V4_PINNED_MEMORY_KIB || iterations != V4_PINNED_ITERATIONS || parallelism != V4_PINNED_PARALLELISM) {
                throw CorruptedBackupException("Corrupted V4 backup: Argon2id parameters do not match pinned V4 protocol specification (expected mem=$V4_PINNED_MEMORY_KIB, iter=$V4_PINNED_ITERATIONS, par=$V4_PINNED_PARALLELISM; found mem=$memoryKb, iter=$iterations, par=$parallelism)")
            }

            val currentRealm = if (VaultKeyManager.isDecoyVaultAuthorized()) 2 else 1
            if (headerRealm != currentRealm) {
                throw DeviceBindingMismatchException("Backup realm mismatch: Backup was created for realm $headerRealm, but active vault is in realm $currentRealm. Decoy/Real isolation violation.")
            }

            val cryptoContext = buildCryptoContext(headerUuid, headerRealm, headerDigest)

            val payloadOffset: Long
            val activeKey: SecretKey

            if (detectedDeviceLocked) {
                if (wrappedKeyLen !in 16..4096) {
                    throw CorruptedBackupException("Corrupted V4 device-locked backup: Invalid wrapped key length ($wrappedKeyLen)")
                }
                payloadOffset = 145L + wrappedKeyLen
                if (tempBackupFile.length() < payloadOffset) {
                    throw CorruptedBackupException("Corrupted V4 backup: File size smaller than header + wrapped key")
                }

                val fis = FileInputStream(tempBackupFile).buffered(65536)
                fis.skip(145L)
                val wrappedBytes = ByteArray(wrappedKeyLen)
                readFully(fis, wrappedBytes, 0, wrappedKeyLen)
                fis.close()

                val argon2Key = Argon2Kdf.deriveKey(masterPassword.toCharArray(), salt, memoryKb, iterations, parallelism)
                val hwKey = getDeviceBindingMasterKey()

                val hwIv = wrappedBytes.copyOfRange(0, IV_SIZE_BYTES)
                val hwEncrypted = wrappedBytes.copyOfRange(IV_SIZE_BYTES, wrappedBytes.size)

                val hwCipher = Cipher.getInstance("AES/GCM/NoPadding")
                hwCipher.init(Cipher.DECRYPT_MODE, hwKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, hwIv))
                val passWrapped = try {
                    hwCipher.doFinal(hwEncrypted)
                } catch (e: Exception) {
                    throw DeviceBindingMismatchException(
                        "Device-bound backup cannot be decrypted on this device: Keystore device-binding mismatch or backup originated from another physical device.",
                        e
                    )
                }

                val passIv = passWrapped.copyOfRange(0, IV_SIZE_BYTES)
                val passEncrypted = passWrapped.copyOfRange(IV_SIZE_BYTES, passWrapped.size)
                val passCipher = Cipher.getInstance("AES/GCM/NoPadding")
                passCipher.init(Cipher.DECRYPT_MODE, argon2Key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, passIv))
                val rawEphemeralKey = try {
                    passCipher.doFinal(passEncrypted)
                } catch (e: Exception) {
                    throw InvalidBackupPasswordException(
                        "INCORRECT_BACKUP_PASSWORD: Password invalid for device-bound V4 archive.",
                        e
                    )
                }
                activeKey = SecretKeySpec(rawEphemeralKey, "AES")
            } else {
                if (wrappedKeyLen != 0) {
                    throw CorruptedBackupException("Corrupted V4 portable backup: Non-zero wrapped key length ($wrappedKeyLen)")
                }
                payloadOffset = 145L
                activeKey = Argon2Kdf.deriveKey(masterPassword.toCharArray(), salt, memoryKb, iterations, parallelism)
            }

            // Probe first chunk decryption with bound cryptoContext to fail-fast with typed exception
            val probeStream = FileInputStream(tempBackupFile).buffered(65536)
            probeStream.skip(payloadOffset)
            val probeChunked = ChunkedGcmInputStream(probeStream, activeKey, useAad = true, aadMode = AadMode.HARDENED_V4, cryptoContext = cryptoContext)
            try {
                val peek = probeChunked.read()
                if (peek == -1 && tempBackupFile.length() > payloadOffset) {
                    throw CorruptedBackupException("Corrupted V4 backup: Unexpected end of stream.")
                }
            } catch (e: BackupException) {
                throw e
            } catch (e: AEADBadTagException) {
                throw InvalidBackupPasswordException("INCORRECT_BACKUP_PASSWORD: Password invalid for V4 archive.", e)
            } catch (e: IllegalStateException) {
                throw CorruptedBackupException("Corrupted V4 backup stream: ${e.message}", e)
            } catch (e: java.io.IOException) {
                val cause = e.cause
                if (cause is BackupException) throw cause
                if (cause is AEADBadTagException || cause?.cause is AEADBadTagException || e.message?.contains("AEADBadTagException") == true || e.message?.contains("Tag mismatch") == true || e.message?.contains("GCM") == true) {
                    throw InvalidBackupPasswordException("INCORRECT_BACKUP_PASSWORD: Password invalid for V4 archive.", e)
                }
                throw CorruptedBackupException("Corrupted V4 backup stream I/O failure: ${e.message}", e)
            } catch (e: Exception) {
                throw InvalidBackupPasswordException("INCORRECT_BACKUP_PASSWORD: Password invalid for V4 archive.", e)
            } finally {
                try { probeChunked.close() } catch (_: Exception) {}
            }

            // Strict V4 stream initialization with deterministic bound cryptoContext
            val stream = FileInputStream(tempBackupFile).buffered(65536)
            stream.skip(payloadOffset)
            val chunkedStream = ChunkedGcmInputStream(stream, activeKey, useAad = true, aadMode = AadMode.HARDENED_V4, cryptoContext = cryptoContext)
            val desc = if (detectedDeviceLocked) "V4 Argon2id (Device-Locked, Bound-AAD)" else "V4 Argon2id Portable (Bound-AAD)"
            return DecryptedStreamResult(chunkedStream, desc)
        }

        // Phase A2: Legacy V3 format parsing
        if (isV3) {
            if (headerBytes.size < 25) {
                throw CorruptedBackupException("Corrupted V3 backup: Header truncated (less than 25 bytes)")
            }
            onProgress?.invoke(0, 0, "Inspecting V3 Argon2id Parameters...", 0L)
            val flags = headerBytes[8].toInt()
            detectedDeviceLocked = (flags and 1) != 0
            val salt = headerBytes.copyOfRange(9, 25)

            var memoryKb = Argon2Kdf.DEFAULT_MEMORY_KIB
            var iterations = Argon2Kdf.DEFAULT_ITERATIONS
            var parallelism = Argon2Kdf.DEFAULT_PARALLELISM
            var wrappedKeyLen = 0
            var payloadOffset = 25L

            if (headerBytes.size >= 41) {
                val bb = java.nio.ByteBuffer.wrap(headerBytes, 25, 16)
                val mem = bb.getInt()
                val iter = bb.getInt()
                val par = bb.getInt()
                val wLen = bb.getInt()
                if (mem in 1024..524288) memoryKb = mem
                if (iter in 1..20) iterations = iter
                if (par in 1..8) parallelism = par
                wrappedKeyLen = wLen
                payloadOffset = 41L
            }

            if (detectedDeviceLocked && wrappedKeyLen in 16..4096) {
                try {
                    val fis = FileInputStream(tempBackupFile).buffered(65536)
                    fis.skip(payloadOffset)
                    val wrappedBytes = ByteArray(wrappedKeyLen)
                    readFully(fis, wrappedBytes, 0, wrappedKeyLen)
                    fis.close()
                    val actualDataOffset = payloadOffset + wrappedKeyLen

                    for (pwd in passwordsToTry) {
                        val argon2Key = Argon2Kdf.deriveKey(pwd.toCharArray(), salt, memoryKb, iterations, parallelism)
                        val hwKey = getDeviceBindingMasterKey()
                        val hwIv = wrappedBytes.copyOfRange(0, IV_SIZE_BYTES)
                        val hwEncrypted = wrappedBytes.copyOfRange(IV_SIZE_BYTES, wrappedBytes.size)

                        val hwCipher = Cipher.getInstance("AES/GCM/NoPadding")
                        hwCipher.init(Cipher.DECRYPT_MODE, hwKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, hwIv))
                        val passWrapped = hwCipher.doFinal(hwEncrypted)

                        val passIv = passWrapped.copyOfRange(0, IV_SIZE_BYTES)
                        val passEncrypted = passWrapped.copyOfRange(IV_SIZE_BYTES, passWrapped.size)
                        val passCipher = Cipher.getInstance("AES/GCM/NoPadding")
                        passCipher.init(Cipher.DECRYPT_MODE, argon2Key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, passIv))
                        val rawEphemeralKey = passCipher.doFinal(passEncrypted)
                        val activeKey = SecretKeySpec(rawEphemeralKey, "AES")

                        if (testDecryptionCandidate {
                            val s = FileInputStream(tempBackupFile).buffered(65536)
                            s.skip(actualDataOffset)
                            ChunkedGcmInputStream(s, activeKey, useAad = true)
                        }) {
                            val s = FileInputStream(tempBackupFile).buffered(65536)
                            s.skip(actualDataOffset)
                            return DecryptedStreamResult(ChunkedGcmInputStream(s, activeKey, useAad = true), "V3 Argon2id (Device-Locked, AAD)")
                        }

                        if (testDecryptionCandidate {
                            val s = FileInputStream(tempBackupFile).buffered(65536)
                            s.skip(actualDataOffset)
                            ChunkedGcmInputStream(s, activeKey, useAad = false)
                        }) {
                            val s = FileInputStream(tempBackupFile).buffered(65536)
                            s.skip(actualDataOffset)
                            return DecryptedStreamResult(ChunkedGcmInputStream(s, activeKey, useAad = false), "V3 Argon2id (Device-Locked, Standard)")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "V3 Device-locked unwrapping attempt failed: ${e.message}")
                }
            }

            // Try direct Argon2id key for V3
            for (pwd in passwordsToTry) {
                val argon2Key = Argon2Kdf.deriveKey(pwd.toCharArray(), salt, memoryKb, iterations, parallelism)

                if (testDecryptionCandidate {
                    val s = FileInputStream(tempBackupFile).buffered(65536)
                    s.skip(payloadOffset)
                    ChunkedGcmInputStream(s, argon2Key, useAad = true)
                }) {
                    val s = FileInputStream(tempBackupFile).buffered(65536)
                    s.skip(payloadOffset)
                    return DecryptedStreamResult(ChunkedGcmInputStream(s, argon2Key, useAad = true), "V3 Argon2id Portable (AAD)")
                }

                if (testDecryptionCandidate {
                    val s = FileInputStream(tempBackupFile).buffered(65536)
                    s.skip(payloadOffset)
                    ChunkedGcmInputStream(s, argon2Key, useAad = false)
                }) {
                    val s = FileInputStream(tempBackupFile).buffered(65536)
                    s.skip(payloadOffset)
                    return DecryptedStreamResult(ChunkedGcmInputStream(s, argon2Key, useAad = false), "V3 Argon2id Portable (Standard)")
                }
            }

            if (detectedDeviceLocked) {
                throw DeviceBindingMismatchException(
                    "Device-bound V3 backup cannot be decrypted on this device: Keystore device-binding mismatch."
                )
            }
            throw InvalidBackupPasswordException("INCORRECT_BACKUP_PASSWORD: Password invalid for V3 archive.")
        }

        // Phase A3: Legacy V2 format parsing
        if (isV2) {
            if (headerBytes.size < 24) {
                throw CorruptedBackupException("Corrupted V2 backup: Header truncated")
            }
            val salt = headerBytes.copyOfRange(8, 24)
            val dataOffset = 24L
            val v2CandidateIters = listOf(12_000, 10_000, 65_536)

            for (pwd in passwordsToTry) {
                for (iterations in v2CandidateIters) {
                    val spec = javax.crypto.spec.PBEKeySpec(pwd.toCharArray(), salt, iterations, 256)
                    val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

                    for (useAad in listOf(false, true)) {
                        if (testDecryptionCandidate {
                            val s = FileInputStream(tempBackupFile).buffered(65536)
                            s.skip(dataOffset)
                            ChunkedGcmInputStream(s, key, useAad = useAad)
                        }) {
                            val s = FileInputStream(tempBackupFile).buffered(65536)
                            s.skip(dataOffset)
                            return DecryptedStreamResult(ChunkedGcmInputStream(s, key, useAad = useAad), "V2 PBKDF2 (aad=$useAad)")
                        }
                    }
                }
            }
            throw InvalidBackupPasswordException("INCORRECT_BACKUP_PASSWORD: Password invalid for V2 archive.")
        }

        // Phase A4: Legacy V1 format parsing
        if (isV1 || is4ByteVlt) {
            val offset = if (is4ByteVlt) 4L else 8L
            if (tempBackupFile.length() < offset + 16L) {
                throw CorruptedBackupException("Corrupted legacy backup: File truncated")
            }
            val salt = ByteArray(16)
            FileInputStream(tempBackupFile).use { fis ->
                fis.skip(offset)
                readFully(fis, salt, 0, 16)
            }
            val dataOffset = offset + 16L
            val legacyIters = listOf(10_000, 12_000, 1_000, 65_536)

            for (pwd in passwordsToTry) {
                for (iterations in legacyIters) {
                    for (algo in listOf("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1")) {
                        val key = try {
                            val spec = javax.crypto.spec.PBEKeySpec(pwd.toCharArray(), salt, iterations, 256)
                            val factory = javax.crypto.SecretKeyFactory.getInstance(algo)
                            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
                        } catch (_: Exception) { null } ?: continue

                        for (useAad in listOf(true, false)) {
                            if (testDecryptionCandidate {
                                val s = FileInputStream(tempBackupFile).buffered(65536)
                                s.skip(dataOffset)
                                ChunkedGcmInputStream(s, key, useAad = useAad)
                            }) {
                                val s = FileInputStream(tempBackupFile).buffered(65536)
                                s.skip(dataOffset)
                                return DecryptedStreamResult(ChunkedGcmInputStream(s, key, useAad = useAad), "Legacy V1 ($algo, aad=$useAad)")
                            }
                        }
                    }
                }
            }
            throw InvalidBackupPasswordException("INCORRECT_BACKUP_PASSWORD: Password invalid for legacy archive.")
        }

        // Phase B: Legacy Multi-Candidate PBKDF2 / Argon2 Resolution for Unversioned Archives
        onProgress?.invoke(0, 0, "Testing unversioned legacy encryption formats...", 0L)

        val candidateOffsets = listOf(0L, 4L, 8L, 9L).filter { it + 16L <= tempBackupFile.length() }
        val candidateIterations = listOf(10_000, 12_000, 100_000, 1_000, 65_536, 50_000)

        for (offset in candidateOffsets) {
            val salt = ByteArray(16)
            try {
                FileInputStream(tempBackupFile).use { fis ->
                    fis.skip(offset)
                    readFully(fis, salt, 0, 16)
                }
            } catch (_: Exception) {
                continue
            }

            val dataOffset = offset + 16L
            if (dataOffset >= tempBackupFile.length()) continue

            for (pwd in passwordsToTry) {
                for (iterations in candidateIterations) {
                    val algorithms = listOf("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1")
                    for (algo in algorithms) {
                        val key = try {
                            val spec = javax.crypto.spec.PBEKeySpec(pwd.toCharArray(), salt, iterations, 256)
                            val factory = javax.crypto.SecretKeyFactory.getInstance(algo)
                            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
                        } catch (_: Exception) {
                            null
                        } ?: continue

                        // Candidate 1: ChunkedGcm with monotonic AAD
                        if (testDecryptionCandidate {
                            val s = FileInputStream(tempBackupFile).buffered(65536)
                            s.skip(dataOffset)
                            ChunkedGcmInputStream(s, key, useAad = true)
                        }) {
                            Log.i(TAG, "Legacy format matched: $algo ($iterations iters, offset $offset, AAD)")
                            val s = FileInputStream(tempBackupFile).buffered(65536)
                            s.skip(dataOffset)
                            return DecryptedStreamResult(ChunkedGcmInputStream(s, key, useAad = true), "Legacy Backup ($algo, $iterations iters)")
                        }

                        // Candidate 2: ChunkedGcm without monotonic AAD
                        if (testDecryptionCandidate {
                            val s = FileInputStream(tempBackupFile).buffered(65536)
                            s.skip(dataOffset)
                            ChunkedGcmInputStream(s, key, useAad = false)
                        }) {
                            Log.i(TAG, "Legacy format matched: $algo ($iterations iters, offset $offset, No-AAD)")
                            val s = FileInputStream(tempBackupFile).buffered(65536)
                            s.skip(dataOffset)
                            return DecryptedStreamResult(ChunkedGcmInputStream(s, key, useAad = false), "Legacy Backup ($algo, $iterations iters, Standard)")
                        }

                        // Candidate 3: Single-stream AES-GCM (12B IV + ciphertext)
                        if (dataOffset + 12L <= tempBackupFile.length()) {
                            val iv = ByteArray(12)
                            val hasIv = try {
                                FileInputStream(tempBackupFile).use { s ->
                                    s.skip(dataOffset)
                                    readFully(s, iv, 0, 12) == 12
                                }
                            } catch (_: Exception) { false }

                            if (hasIv && testDecryptionCandidate {
                                val s = FileInputStream(tempBackupFile).buffered(65536)
                                s.skip(dataOffset + 12L)
                                SingleAesGcmInputStream(s, key, iv)
                            }) {
                                Log.i(TAG, "Legacy format matched: Single-stream AES-GCM ($algo, $iterations iters)")
                                val s = FileInputStream(tempBackupFile).buffered(65536)
                                s.skip(dataOffset + 12L)
                                return DecryptedStreamResult(SingleAesGcmInputStream(s, key, iv), "Legacy Stream ($algo, $iterations iters)")
                            }
                        }

                        // Candidate 4: Single-stream AES-CBC (16B IV + ciphertext)
                        if (dataOffset + 16L <= tempBackupFile.length()) {
                            val iv16 = ByteArray(16)
                            val hasIv16 = try {
                                FileInputStream(tempBackupFile).use { s ->
                                    s.skip(dataOffset)
                                    readFully(s, iv16, 0, 16) == 16
                                }
                            } catch (_: Exception) { false }

                            if (hasIv16 && testDecryptionCandidate {
                                val s = FileInputStream(tempBackupFile).buffered(65536)
                                s.skip(dataOffset + 16L)
                                SingleAesCbcInputStream(s, key, iv16)
                            }) {
                                Log.i(TAG, "Legacy format matched: Single-stream AES-CBC ($algo, $iterations iters)")
                                val s = FileInputStream(tempBackupFile).buffered(65536)
                                s.skip(dataOffset + 16L)
                                return DecryptedStreamResult(SingleAesCbcInputStream(s, key, iv16), "Legacy CBC Stream ($algo)")
                            }
                        }
                    }
                }

                // Test Raw SHA-256(password)
                try {
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    val rawKey = SecretKeySpec(md.digest(pwd.toByteArray(Charsets.UTF_8)), "AES")

                    if (testDecryptionCandidate {
                        val s = FileInputStream(tempBackupFile).buffered(65536)
                        s.skip(dataOffset)
                        ChunkedGcmInputStream(s, rawKey, useAad = true)
                    }) {
                        val s = FileInputStream(tempBackupFile).buffered(65536)
                        s.skip(dataOffset)
                        return DecryptedStreamResult(ChunkedGcmInputStream(s, rawKey, useAad = true), "Legacy SHA-256 Chunked")
                    }

                    if (testDecryptionCandidate {
                        val s = FileInputStream(tempBackupFile).buffered(65536)
                        s.skip(dataOffset)
                        ChunkedGcmInputStream(s, rawKey, useAad = false)
                    }) {
                        val s = FileInputStream(tempBackupFile).buffered(65536)
                        s.skip(dataOffset)
                        return DecryptedStreamResult(ChunkedGcmInputStream(s, rawKey, useAad = false), "Legacy SHA-256 Chunked (Standard)")
                    }
                } catch (_: Exception) {}
            }
        }

        throw CorruptedBackupException("Unrecognized or corrupt backup archive. The selected file could not be decrypted.")
    }

    /**
     * Directly restores records from a legacy SQLite database export.
     */
    private suspend fun restoreFromSqliteDb(
        context: Context,
        sqliteFile: File,
        db: AppDatabase,
        vaultRepository: VaultRepository,
        isReplaceMode: Boolean,
        onProgress: ((current: Int, total: Int, currentName: String, bytesProcessed: Long) -> Unit)?
    ): Result<Int> {
        var restoredCount = 0
        try {
            onProgress?.invoke(0, 0, "Inspecting SQLite database tables...", 0L)
            val sqlite = SQLiteDatabase.openDatabase(
                sqliteFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            sqlite.use { sdb ->
                val tables = mutableListOf<String>()
                sdb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        tables.add(cursor.getString(0))
                    }
                }

                if (isReplaceMode) {
                    db.vaultDao().deleteAllItems()
                    db.vaultDao().deleteAllFolders()
                    db.vaultPasswordDao().deleteAll()
                    db.intruderLogDao().clearLogs()
                }

                // Restore folders
                val folderTable = tables.firstOrNull { it.contains("folder", ignoreCase = true) }
                if (folderTable != null) {
                    sdb.rawQuery("SELECT * FROM $folderTable", null).use { cursor ->
                        val colNames = cursor.columnNames.map { it.lowercase() }
                        val nameIdx = colNames.indexOfFirst { it.contains("name") || it.contains("title") }
                        while (cursor.moveToNext()) {
                            if (nameIdx >= 0) {
                                val name = cursor.getString(nameIdx) ?: continue
                                try { db.vaultDao().insertFolder(VaultFolder(name = name)) } catch (_: Exception) {}
                            }
                        }
                    }
                }

                // Restore passwords
                val pwdTable = tables.firstOrNull { it.contains("password", ignoreCase = true) }
                if (pwdTable != null) {
                    sdb.rawQuery("SELECT * FROM $pwdTable", null).use { cursor ->
                        val colNames = cursor.columnNames.map { it.lowercase() }
                        val accIdx = colNames.indexOfFirst { it.contains("account") || it.contains("title") || it.contains("name") }
                        val pwdIdx = colNames.indexOfFirst { it.contains("password") || it.contains("enc") }
                        val catIdx = colNames.indexOfFirst { it.contains("category") }
                        val noteIdx = colNames.indexOfFirst { it.contains("note") }
                        while (cursor.moveToNext()) {
                            val acc = if (accIdx >= 0) cursor.getString(accIdx) ?: "Account" else "Account"
                            val pwd = if (pwdIdx >= 0) cursor.getString(pwdIdx) ?: "" else ""
                            val cat = if (catIdx >= 0) cursor.getString(catIdx) ?: "General" else "General"
                            val note = if (noteIdx >= 0) cursor.getString(noteIdx) ?: "" else ""
                            try {
                                db.vaultPasswordDao().insertPassword(
                                    VaultPassword(
                                        id = 0L,
                                        title = acc,
                                        category = cat,
                                        usernameOrEmail = acc,
                                        encryptedPasswordBlob = pwd,
                                        encryptedNotesBlob = note
                                    )
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }

                // Restore items
                val itemTable = tables.firstOrNull { it.equals("vault_items", ignoreCase = true) || it.equals("items", ignoreCase = true) || it.contains("item", ignoreCase = true) }
                if (itemTable != null) {
                    sdb.rawQuery("SELECT * FROM $itemTable", null).use { cursor ->
                        val colNames = cursor.columnNames.map { it.lowercase() }
                        val origNameIdx = colNames.indexOfFirst { it.contains("original") || it.contains("name") || it.contains("title") }
                        val encNameIdx = colNames.indexOfFirst { it.contains("encrypted") || it.contains("filename") || it.contains("path") }
                        val mimeIdx = colNames.indexOfFirst { it.contains("mime") || it.contains("type") }
                        val sizeIdx = colNames.indexOfFirst { it.contains("size") }
                        val isVideoIdx = colNames.indexOfFirst { it.contains("video") }
                        val folderIdx = colNames.indexOfFirst { it.contains("folder") }

                        while (cursor.moveToNext()) {
                            val origName = if (origNameIdx >= 0) cursor.getString(origNameIdx) ?: "restored_file" else "restored_file"
                            val encName = if (encNameIdx >= 0) cursor.getString(encNameIdx) ?: "${System.currentTimeMillis()}_${origName}.aes" else "${System.currentTimeMillis()}_${origName}.aes"
                            val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: inferMimeTypeFromName(origName) else inferMimeTypeFromName(origName)
                            val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                            val isVideo = if (isVideoIdx >= 0) cursor.getInt(isVideoIdx) == 1 else mime.startsWith("video/")
                            val folder = if (folderIdx >= 0) cursor.getString(folderIdx) ?: "Root" else "Root"

                            vaultRepository.insertRestoredVaultItem(
                                VaultItem(
                                    id = 0L,
                                    originalName = origName,
                                    encryptedFileName = File(encName).name,
                                    mimeType = mime,
                                    sizeBytes = size,
                                    addedTimestamp = System.currentTimeMillis(),
                                    isVideo = isVideo,
                                    folderName = folder
                                )
                            )
                            restoredCount++
                            onProgress?.invoke(restoredCount, restoredCount, "Restored database record: $origName", 0L)
                        }
                    }
                }
            }
            onProgress?.invoke(restoredCount, restoredCount, "Database restoration complete ($restoredCount records)", 0L)
            return Result.success(restoredCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore from SQLite DB: ${e.message}", e)
            return Result.failure(e)
        }
    }

    /**
     * Crash-Consistent Disaster Recovery Engine: Recovers unfinalized restore transactions
     * across filesystem generation directories, database, and generation metadata.
     */
    @Synchronized
    fun recoverPendingRestoreIfAny(context: Context): Boolean {
        val journal = VaultRestoreJournal.readJournal(context) ?: return false
        Log.w(TAG, "Pending restore journal detected in state: ${journal.state}, gen: ${journal.nextGen}, mergeMode=${journal.isMergeMode}")

        val isDecoy = journal.isDecoy
        val vaultDir = File(journal.vaultDirPath)
        val prevDir = if (journal.backupPrevGenDirPath.isNotBlank()) File(journal.backupPrevGenDirPath) else null
        val nextGenDir = File(journal.nextGenDirPath)
        val intentFile = File(context.filesDir, journal.intentFileName)

        try {
            if (journal.isMergeMode) {
                val replacedDir = journal.replacedFilesBackupDirPath?.let { File(it) }
                when (journal.state) {
                    RestoreJournalState.PREPARED.name,
                    RestoreJournalState.FS_SWAPPED.name -> {
                        Log.i(TAG, "Recovery: Reverting uncommitted merge restore.")
                        journal.newlyAddedFilesPaths.forEach { path ->
                            try { File(path).delete() } catch (_: Exception) {}
                        }
                        if (replacedDir != null && replacedDir.exists()) {
                            replacedDir.listFiles()?.forEach { orig ->
                                val dest = File(vaultDir, orig.name)
                                try {
                                    Files.move(orig.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                                } catch (_: Exception) {}
                            }
                            try { replacedDir.deleteRecursively() } catch (_: Exception) {}
                        }
                        try { intentFile.delete() } catch (_: Exception) {}
                        try { nextGenDir.deleteRecursively() } catch (_: Exception) {}
                        VaultRestoreJournal.clearJournal(context)
                    }
                    RestoreJournalState.DB_COMMITTED.name -> {
                        Log.i(TAG, "Recovery: Merge restore DB committed. Rolling forward generation commit.")
                        VaultGenerationManager.commitGeneration(context, isDecoy, journal.nextGen, intentFile)
                        if (replacedDir != null && replacedDir.exists()) {
                            try { replacedDir.deleteRecursively() } catch (_: Exception) {}
                        }
                        try { nextGenDir.deleteRecursively() } catch (_: Exception) {}
                        VaultRestoreJournal.clearJournal(context)
                    }
                    RestoreJournalState.COMPLETED.name -> {
                        if (replacedDir != null && replacedDir.exists()) {
                            try { replacedDir.deleteRecursively() } catch (_: Exception) {}
                        }
                        VaultRestoreJournal.clearJournal(context)
                    }
                }
            } else {
                when (journal.state) {
                    RestoreJournalState.PREPARED.name -> {
                        Log.i(TAG, "Recovery: Aborting PREPARED restore transaction. Cleaning staging artifacts.")
                        try { nextGenDir.deleteRecursively() } catch (_: Exception) {}
                        try { intentFile.delete() } catch (_: Exception) {}
                        VaultRestoreJournal.clearJournal(context)
                    }
                    RestoreJournalState.FS_SWAPPED.name -> {
                        Log.w(TAG, "Recovery: Crash occurred during FS_SWAPPED (DB uncommitted). Rolling back FS to match old DB.")
                        if (vaultDir.exists()) {
                            vaultDir.deleteRecursively()
                        }
                        if (prevDir != null && prevDir.exists()) {
                            Files.move(prevDir.toPath(), vaultDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
                        }
                        try { intentFile.delete() } catch (_: Exception) {}
                        try { nextGenDir.deleteRecursively() } catch (_: Exception) {}
                        VaultRestoreJournal.clearJournal(context)
                    }
                    RestoreJournalState.DB_COMMITTED.name -> {
                        Log.i(TAG, "Recovery: DB was committed! Rolling forward generation commit to match new DB and FS.")
                        VaultGenerationManager.commitGeneration(context, isDecoy, journal.nextGen, intentFile)
                        try { prevDir?.deleteRecursively() } catch (_: Exception) {}
                        try { nextGenDir.deleteRecursively() } catch (_: Exception) {}
                        VaultRestoreJournal.clearJournal(context)
                    }
                    RestoreJournalState.COMPLETED.name -> {
                        try { prevDir?.deleteRecursively() } catch (_: Exception) {}
                        VaultRestoreJournal.clearJournal(context)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error during pending restore recovery", e)
            throw e
        }
    }

    /**
     * Restores an encrypted backup archive with strict integrity checks, backward compatibility
     * for legacy formats (V3 Argon2id, V2 PBKDF2, V1 PBKDF2, SQLite, and unencrypted standard ZIPs),
     * tolerant payload extraction, and atomic rollback transaction.
     */
    suspend fun importMasterBackup(
        context: Context,
        masterPassword: String,
        inputStream: InputStream,
        vaultRepository: VaultRepository,
        isReplaceMode: Boolean = false,
        onProgress: ((current: Int, total: Int, currentName: String, bytesProcessed: Long) -> Unit)? = null,
        testFaultInjectionHook: ((RestoreFaultPhase) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val activeFaultHook = testFaultInjectionHook ?: VaultBackupManager.testFaultInjectionHook
        recoverPendingRestoreIfAny(context)

        val stagingDir = File(context.cacheDir, "staging_restore_${System.currentTimeMillis()}").apply { mkdirs() }
        val tempBackupFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.bin")
        var nextGenDir: File? = null
        var backupPrevGenDir: File? = null

        try {
            val vaultDir = vaultRepository.getVaultDirectory(context)
            val db = AppDatabase.getDatabase(context)
            onProgress?.invoke(0, 0, "Caching backup archive for integrity verification...", 0L)

            FileOutputStream(tempBackupFile).buffered(65536).use { fos ->
                inputStream.copyTo(fos)
            }

            if (tempBackupFile.length() < 4L) {
                return@withContext Result.failure(IllegalArgumentException("Corrupted backup file: Truncated or empty archive."))
            }

            // Resolve decrypted stream via Multi-Format Smart Candidate Engine
            val streamResult = resolveDecryptedBackupStream(context, tempBackupFile, masterPassword, onProgress)

            if (streamResult.isSqliteDatabase) {
                return@withContext restoreFromSqliteDb(context, tempBackupFile, db, vaultRepository, isReplaceMode, onProgress)
            }

            onProgress?.invoke(0, 0, "Unpacking archive (${streamResult.formatDescription})...", 0L)

            var restoredCount = 0
            var totalBytesRestored = 0L

            var metadata: BackupManifestMetadata? = null
            var manifestV4: VaultBackupManifestV4? = null
            var restoredItems: List<VaultItem>? = null
            var restoredFolders: List<VaultFolder>? = null
            var restoredPasswords: List<VaultPassword>? = null
            var logsToRestore: List<IntruderLog>? = null

            val stagedFiles = mutableMapOf<String, File>()
            val stagedV4PayloadFiles = mutableMapOf<String, File>()
            val stagedPlaintextShaMap = mutableMapOf<String, String>()
            val stagedV4PlaintextShaMap = mutableMapOf<String, String>()
            val seenEntries = mutableSetOf<String>()
            val isV4 = streamResult.formatDescription.startsWith("V4")

            streamResult.stream.use { rawDecryptedStream ->
                ZipInputStream(rawDecryptedStream).use { zis ->
                    var entry: ZipEntry? = try { zis.nextEntry } catch (e: Exception) { null }

                    while (entry != null) {
                        val entryName = entry.name.replace('\\', '/')
                        val cleanFileName = File(entryName).name

                        if (entry.isDirectory || cleanFileName.isBlank() || cleanFileName == "." || cleanFileName == "..") {
                            try { zis.closeEntry() } catch (_: Throwable) {}
                            entry = try { zis.nextEntry } catch (_: Throwable) { null }
                            continue
                        }

                        if (cleanFileName.startsWith(".") || cleanFileName.equals("thumbs.db", ignoreCase = true) || entryName.startsWith("__MACOSX")) {
                            try { zis.closeEntry() } catch (_: Throwable) {}
                            entry = try { zis.nextEntry } catch (_: Throwable) { null }
                            continue
                        }

                        if (entryName.contains("..") || entryName.startsWith("/") || entryName.contains("\u0000")) {
                            throw SecurityException("Malicious path traversal entry detected in backup: $entryName")
                        }

                        if (!seenEntries.add(entryName)) {
                            throw SecurityException("Duplicate entry detected in backup archive: $entryName. Archive rejected for ambiguity.")
                        }

                        val lower = cleanFileName.lowercase()

                        when {
                            lower == MANIFEST_V4_FILENAME || lower == "backup_manifest_v4.json" -> {
                                try {
                                    val v4Json = zis.readBytes().toString(Charsets.UTF_8)
                                    manifestV4 = manifestV4Adapter.fromJson(v4Json)
                                        ?: throw SecurityException("Parsed V4 manifest was null")
                                } catch (e: Exception) {
                                    if (isV4) {
                                        throw SecurityException("Failed to parse mandatory V4 manifest: ${e.message}", e)
                                    } else {
                                        Log.w(TAG, "V4 manifest parse warning: ${e.message}")
                                    }
                                }
                            }
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
                                    if (isV4) {
                                        throw SecurityException("Corrupted folders dataset in V4 backup archive: ${e.message}", e)
                                    } else {
                                        Log.w(TAG, "Folders dataset parse warning: ${e.message}")
                                    }
                                }
                            }
                            lower == PASSWORDS_FILENAME || lower == "passwords.json" -> {
                                try {
                                    val passwordsJson = zis.readBytes().toString(Charsets.UTF_8)
                                    restoredPasswords = passwordsJsonAdapter.fromJson(passwordsJson)
                                } catch (e: Exception) {
                                    if (isV4) {
                                        throw SecurityException("Corrupted passwords dataset in V4 backup archive: ${e.message}", e)
                                    } else {
                                        Log.w(TAG, "Passwords dataset parse warning: ${e.message}")
                                    }
                                }
                            }
                            lower == SECURITY_LOGS_FILENAME || lower == "intruder_logs.json" ||
                            lower == "security_logs.json" || lower == "logs.json" -> {
                                try {
                                    val logsJson = zis.readBytes().toString(Charsets.UTF_8)
                                    logsToRestore = logsJsonAdapter.fromJson(logsJson)
                                } catch (e: Exception) {
                                    if (isV4) {
                                        throw SecurityException("Corrupted security logs dataset in V4 backup archive: ${e.message}", e)
                                    } else {
                                        Log.w(TAG, "Security logs dataset parse warning: ${e.message}")
                                    }
                                }
                            }
                            else -> {
                                val uniqueStagedName = if (stagedFiles.containsKey(cleanFileName)) {
                                    "${System.currentTimeMillis()}_${cleanFileName}"
                                } else {
                                    cleanFileName
                                }
                                val stagedFile = File(stagingDir, uniqueStagedName)
                                if (!stagedFile.canonicalPath.startsWith(stagingDir.canonicalPath)) {
                                    throw SecurityException("Path traversal attempt detected: $cleanFileName")
                                }

                                val pushback = java.io.PushbackInputStream(zis, 4)
                                val peek = ByteArray(4)
                                val peekRead = readFully(pushback, peek, 0, 4)
                                if (peekRead > 0) {
                                    pushback.unread(peek, 0, peekRead)
                                }

                                val isAlreadyVaultEncrypted = peekRead == 4 && (
                                    (peek[0] == 0x56.toByte() && peek[1] == 0x4C.toByte() && peek[2] == 0x54.toByte() &&
                                     (peek[3] == 0x34.toByte() || peek[3] == 0x33.toByte() || peek[3] == 0x32.toByte()))
                                )

                                val digest = java.security.MessageDigest.getInstance("SHA-256")
                                val teeIn = object : InputStream() {
                                    override fun read(): Int {
                                        val b = pushback.read()
                                        if (b != -1) digest.update(b.toByte())
                                        return b
                                    }
                                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                                        val r = pushback.read(b, off, len)
                                        if (r > 0) digest.update(b, off, r)
                                        return r
                                    }
                                    override fun close() {
                                        pushback.close()
                                    }
                                }

                                FileOutputStream(stagedFile).buffered(65536).use { fos ->
                                    if (isAlreadyVaultEncrypted) {
                                        pushback.copyTo(fos)
                                    } else {
                                        CryptoManager.encryptStream(teeIn, fos)
                                    }
                                }

                                if (!isAlreadyVaultEncrypted) {
                                    val bytes = digest.digest()
                                    val sb = StringBuilder(bytes.size * 2)
                                    for (b in bytes) {
                                        sb.append(String.format("%02x", b))
                                    }
                                    val sha = sb.toString()
                                    stagedPlaintextShaMap[cleanFileName] = sha
                                    stagedPlaintextShaMap[entryName] = sha
                                    val payloadIdKey = cleanFileName.removeSuffix(".enc").removeSuffix(".aes").removeSuffix(".bin")
                                    stagedPlaintextShaMap[payloadIdKey] = sha
                                    if (uniqueStagedName != cleanFileName) {
                                        stagedPlaintextShaMap[uniqueStagedName] = sha
                                    }
                                    if (entryName.startsWith("vault_data_v4/")) {
                                        stagedV4PlaintextShaMap[payloadIdKey] = sha
                                    }
                                }

                                totalBytesRestored += stagedFile.length()
                                stagedFiles[cleanFileName] = stagedFile
                                stagedFiles[entryName] = stagedFile
                                val payloadIdKey = cleanFileName.removeSuffix(".enc").removeSuffix(".aes").removeSuffix(".bin")
                                stagedFiles[payloadIdKey] = stagedFile
                                if (uniqueStagedName != cleanFileName) {
                                    stagedFiles[uniqueStagedName] = stagedFile
                                }
                                if (entryName.startsWith("vault_data_v4/")) {
                                    stagedV4PayloadFiles[payloadIdKey] = stagedFile
                                }
                                val targetTotal = maxOf(stagedFiles.size + 4, (restoredItems?.size ?: 0) + 4)
                                onProgress?.invoke(
                                    stagedFiles.size,
                                    targetTotal,
                                    "Staging: $cleanFileName",
                                    totalBytesRestored
                                )
                            }
                        }
                        try { zis.closeEntry() } catch (_: Throwable) {}
                        entry = try { zis.nextEntry } catch (_: Throwable) { null }
                    }
                }
            }

            // Phase 3: Manifest Authentication, Strict Bijection, and Integrity Checks
            val targetTotal = stagedFiles.size + 4
            onProgress?.invoke(stagedFiles.size + 1, targetTotal, "Verifying manifest integrity & checksums...", totalBytesRestored)
            if (manifestV4 != null) {
                if (manifestV4.algorithmSuite != V4_ALGORITHM_SUITE) {
                    throw CryptoDowngradeException("CRYPTO_DOWNGRADE_DETECTED: Backup claims V4 format but uses unsupported algorithm suite '${manifestV4.algorithmSuite}'")
                }

                val currentRealm = if (VaultKeyManager.isDecoyVaultAuthorized()) 2 else 1
                if (manifestV4.sourceRealm != currentRealm) {
                    throw DeviceBindingMismatchException("Backup realm mismatch: Backup was created for realm ${manifestV4.sourceRealm}, but active vault is in realm $currentRealm. Decoy/Real isolation violation.")
                }

                val actualItemsCount = restoredItems?.size ?: 0
                val actualFoldersCount = restoredFolders?.size ?: 0
                val actualPasswordsCount = restoredPasswords?.size ?: 0
                val actualLogsCount = logsToRestore?.size ?: 0
                val actualInventoryCount = manifestV4.fileInventory.size

                if (manifestV4.itemsCount != actualItemsCount) {
                    throw BackupManifestIntegrityException("Manifest count mismatch: manifest declares ${manifestV4.itemsCount} items, but archive contains $actualItemsCount items.")
                }
                if (manifestV4.foldersCount != actualFoldersCount) {
                    throw BackupManifestIntegrityException("Manifest count mismatch: manifest declares ${manifestV4.foldersCount} folders, but archive contains $actualFoldersCount folders.")
                }
                if (manifestV4.passwordsCount != actualPasswordsCount) {
                    throw BackupManifestIntegrityException("Manifest count mismatch: manifest declares ${manifestV4.passwordsCount} passwords, but archive contains $actualPasswordsCount passwords.")
                }
                if (manifestV4.logsCount != actualLogsCount) {
                    throw BackupManifestIntegrityException("Manifest count mismatch: manifest declares ${manifestV4.logsCount} logs, but archive contains $actualLogsCount logs.")
                }
                if (actualInventoryCount != actualItemsCount) {
                    throw BackupManifestIntegrityException("Manifest inventory mismatch: fileInventory has $actualInventoryCount entries, but items manifest has $actualItemsCount items.")
                }

                // Required dataset fail-closed checks
                if (manifestV4.foldersCount > 0 && restoredFolders == null) {
                    throw BackupManifestIntegrityException("Strict V4 integrity failure: Manifest declares ${manifestV4.foldersCount} folders, but folders dataset is missing or corrupted.")
                }
                if (manifestV4.passwordsCount > 0 && restoredPasswords == null) {
                    throw BackupManifestIntegrityException("Strict V4 integrity failure: Manifest declares ${manifestV4.passwordsCount} passwords, but passwords dataset is missing or corrupted.")
                }
                if (manifestV4.logsCount > 0 && logsToRestore == null) {
                    throw BackupManifestIntegrityException("Strict V4 integrity failure: Manifest declares ${manifestV4.logsCount} logs, but security logs dataset is missing or corrupted.")
                }
                if (manifestV4.itemsCount > 0 && restoredItems == null) {
                    throw BackupManifestIntegrityException("Strict V4 integrity failure: Manifest declares ${manifestV4.itemsCount} items, but items dataset is missing or corrupted.")
                }

                // Verify payload root digest if present
                val declaredRootDigest = manifestV4.payloadRootDigest
                if (declaredRootDigest.isNotBlank()) {
                    val sortedShaList = manifestV4.fileInventory.map { it.sha256Hex }.sorted()
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    sortedShaList.forEach { md.update(it.toByteArray(Charsets.UTF_8)) }
                    val digestBytes = md.digest()
                    val sb = StringBuilder(digestBytes.size * 2)
                    for (b in digestBytes) {
                        sb.append(String.format("%02x", b))
                    }
                    val recomputedRootDigest = sb.toString()
                    if (!declaredRootDigest.equals(recomputedRootDigest, ignoreCase = true)) {
                        throw BackupManifestIntegrityException("Payload root digest mismatch: Manifest inventory digest does not match recomputed payload root digest. Tamper detected.")
                    }
                }

                // Check 1: Strict Bijection: Every declared file in manifest must exist in staged V4 payloads by payloadId and match SHA-256
                for (entry in manifestV4.fileInventory) {
                    if (entry.payloadId.isBlank()) {
                        throw BackupManifestIntegrityException("Strict V4 Bijection failure: Manifest entry '${entry.originalName}' has missing payloadId.")
                    }
                    val staged = stagedV4PayloadFiles[entry.payloadId]
                        ?: throw BackupManifestIntegrityException("Strict V4 Bijection failure: Declared payloadId '${entry.payloadId}' (${entry.originalName}) is missing from archive.")

                    val actualSha = stagedV4PlaintextShaMap[entry.payloadId] ?: computeSha256Hex(staged)
                    if (!actualSha.equals(entry.sha256Hex, ignoreCase = true)) {
                        throw BackupManifestIntegrityException("Backup integrity violation: Checksum mismatch for payloadId '${entry.payloadId}' (${entry.originalName}). Archive payload corrupted or tampered.")
                    }
                }

                // Check 2: Strict Reverse Bijection: Every staged V4 payload must be explicitly declared in manifestV4 by payloadId
                val declaredPayloadIds = manifestV4.fileInventory.map { it.payloadId }.toSet()
                for (stagedPayloadId in stagedV4PayloadFiles.keys) {
                    if (!declaredPayloadIds.contains(stagedPayloadId)) {
                        throw BackupManifestIntegrityException("Strict V4 Bijection failure: Archive contains undeclared file payload '$stagedPayloadId'. Undeclared entries are forbidden in strict V4.")
                    }
                }
            } else if (isV4) {
                throw BackupManifestIntegrityException("Strict V4 Backup validation failed: Missing or unparseable backup_manifest_v4.json")
            }

            // Reconcile manifest items with staged files
            val finalItemsToInsert = mutableListOf<VaultItem>()
            val matchedStagedNames = mutableSetOf<String>()

            if (manifestV4 != null) {
                for (entry in manifestV4.fileInventory) {
                    val matched = stagedV4PayloadFiles[entry.payloadId]
                        ?: throw BackupManifestIntegrityException("Strict V4 Backup validation failed: Item '${entry.originalName}' missing payload in archive.")

                    matchedStagedNames.add(matched.name)
                    val corresp = restoredItems?.find { it.id == entry.itemId || it.originalName == entry.originalName }
                    finalItemsToInsert.add(
                        VaultItem(
                            id = 0L,
                            originalName = entry.originalName,
                            encryptedFileName = matched.name,
                            mimeType = entry.mimeType.ifBlank { corresp?.mimeType ?: inferMimeTypeFromName(entry.originalName) },
                            sizeBytes = if (entry.sizeBytes > 0) entry.sizeBytes else matched.length(),
                            addedTimestamp = corresp?.addedTimestamp ?: System.currentTimeMillis(),
                            isVideo = (entry.mimeType.startsWith("video/") || corresp?.isVideo == true),
                            folderName = entry.folderName.ifBlank { corresp?.folderName ?: "Root" }
                        )
                    )
                }
            } else if (!restoredItems.isNullOrEmpty()) {
                for (item in restoredItems) {
                    val cleanEnc = File(item.encryptedFileName).name
                    val cleanOrig = File(item.originalName).name

                    val matched = stagedFiles[cleanEnc] ?: stagedFiles[cleanOrig]
                    if (matched != null) {
                        matchedStagedNames.add(matched.name)
                        finalItemsToInsert.add(
                            item.copy(
                                id = 0L,
                                encryptedFileName = matched.name,
                                sizeBytes = if (item.sizeBytes > 0) item.sizeBytes else matched.length()
                            )
                        )
                    } else {
                        Log.w(TAG, "Item '${item.originalName}' (file: ${item.encryptedFileName}) missing from backup files.")
                    }
                }
            }

            // Legacy fallback only for pre-V4 backups: synthesize items for orphaned staged files
            if (!isV4) {
                for ((fileName, stagedFile) in stagedFiles) {
                    if (!matchedStagedNames.contains(stagedFile.name)) {
                        matchedStagedNames.add(stagedFile.name)
                        val mime = inferMimeTypeFromName(fileName)
                        finalItemsToInsert.add(
                            VaultItem(
                                id = 0L,
                                originalName = fileName.removeSuffix(".aes").removeSuffix(".enc").removeSuffix(".bin"),
                                encryptedFileName = stagedFile.name,
                                mimeType = mime,
                                sizeBytes = stagedFile.length(),
                                addedTimestamp = System.currentTimeMillis(),
                                isVideo = mime.startsWith("video/"),
                                folderName = "Root"
                            )
                        )
                    }
                }
            }

            // Phase 4: Stage Verified Generation Files in nextGenDir
            nextGenDir = File(context.filesDir, "${vaultDir.name}_next_gen_${System.currentTimeMillis()}").apply { mkdirs() }
            val genDir = nextGenDir!!

            for (item in finalItemsToInsert) {
                val stagedFile = stagedFiles[item.encryptedFileName] ?: stagedFiles[File(item.originalName).name]
                if (stagedFile != null && stagedFile.exists()) {
                    val targetInNextGen = File(genDir, item.encryptedFileName)
                    Files.move(
                        stagedFile.toPath(),
                        targetInNextGen.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                    if (!targetInNextGen.exists() || targetInNextGen.length() == 0L) {
                        throw SecurityException("Generation staging failed: ${item.encryptedFileName} could not be staged.")
                    }
                }
            }

            // Phase 5: Atomic Switch & Database Commit with Write-Ahead Restore Journal
            onProgress?.invoke(
                stagedFiles.size + 2,
                targetTotal,
                "Committing generation switch...",
                totalBytesRestored
            )

            val isDecoy = VaultKeyManager.isDecoyVaultAuthorized()
            val currentGen = VaultGenerationManager.getActiveGeneration(context, isDecoy)
            val nextGen = currentGen + 1L
            val genIntentFile = VaultGenerationManager.prepareGenerationIntent(context, isDecoy, nextGen)

            if (isReplaceMode) {
                backupPrevGenDir = File(context.filesDir, "${vaultDir.name}_prev_gen_${System.currentTimeMillis()}")
                val prevDir = backupPrevGenDir!!
                var switched = false
                try {
                    // Record PREPARED state in Journal
                    VaultRestoreJournal.recordState(
                        context,
                        RestoreJournalRecord(
                            state = RestoreJournalState.PREPARED.name,
                            isDecoy = isDecoy,
                            nextGen = nextGen,
                            intentFileName = genIntentFile.name,
                            vaultDirPath = vaultDir.absolutePath,
                            backupPrevGenDirPath = prevDir.absolutePath,
                            nextGenDirPath = genDir.absolutePath,
                            isMergeMode = false
                        )
                    )

                    // Step 1: Atomic filesystem swap (preserving previous generation)
                    if (vaultDir.exists()) {
                        Files.move(vaultDir.toPath(), prevDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    }
                    Files.move(genDir.toPath(), vaultDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    switched = true

                    // Record FS_SWAPPED state in Journal
                    VaultRestoreJournal.recordState(
                        context,
                        RestoreJournalRecord(
                            state = RestoreJournalState.FS_SWAPPED.name,
                            isDecoy = isDecoy,
                            nextGen = nextGen,
                            intentFileName = genIntentFile.name,
                            vaultDirPath = vaultDir.absolutePath,
                            backupPrevGenDirPath = prevDir.absolutePath,
                            nextGenDirPath = genDir.absolutePath,
                            isMergeMode = false
                        )
                    )

                    activeFaultHook?.invoke(RestoreFaultPhase.AFTER_FS_SWAP)

                    // Step 2: Atomic DB transaction
                    db.withTransaction {
                        db.vaultDao().deleteAllItems()
                        db.vaultDao().deleteAllFolders()
                        db.vaultPasswordDao().deleteAll()
                        db.intruderLogDao().clearLogs()

                        restoredFolders?.forEach { db.vaultDao().insertFolder(it) }
                        restoredPasswords?.forEach { db.vaultPasswordDao().insertPassword(it) }
                        logsToRestore?.forEach { db.intruderLogDao().insertLog(it) }
                        finalItemsToInsert.forEach { item ->
                            db.vaultDao().insertVaultItem(item)
                            restoredCount++
                        }
                        activeFaultHook?.invoke(RestoreFaultPhase.DURING_DB_TRANSACTION)
                    }

                    // Record DB_COMMITTED state in Journal
                    VaultRestoreJournal.recordState(
                        context,
                        RestoreJournalRecord(
                            state = RestoreJournalState.DB_COMMITTED.name,
                            isDecoy = isDecoy,
                            nextGen = nextGen,
                            intentFileName = genIntentFile.name,
                            vaultDirPath = vaultDir.absolutePath,
                            backupPrevGenDirPath = prevDir.absolutePath,
                            nextGenDirPath = genDir.absolutePath,
                            isMergeMode = false
                        )
                    )

                    // Step 3: Advance generation epoch atomically via prepared intent
                    activeFaultHook?.invoke(RestoreFaultPhase.BEFORE_GENERATION_COMMIT)
                    VaultGenerationManager.commitGeneration(context, isDecoy, nextGen, genIntentFile)
                    prevDir.deleteRecursively()
                    VaultRestoreJournal.clearJournal(context)

                    val thumbDir = File(context.cacheDir, "vault_thumbnails_encrypted")
                    try { thumbDir.deleteRecursively(); thumbDir.mkdirs() } catch (_: Exception) {}
                } catch (t: Throwable) {
                    Log.e(TAG, "Restore transaction failed, rolling back atomic generation...", t)
                    try { genIntentFile.delete() } catch (_: Throwable) {}
                    if (switched) {
                        if (vaultDir.exists()) {
                            vaultDir.deleteRecursively()
                        }
                        if (prevDir.exists()) {
                            Files.move(prevDir.toPath(), vaultDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
                        }
                    }
                    VaultRestoreJournal.clearJournal(context)
                    throw t
                }
            } else {
                // Merge Mode: Transactional Restore with Atomic Rollback of Replaced & Added Files
                val replacedFilesBackupDir = File(context.filesDir, "merge_replaced_${System.currentTimeMillis()}").apply { mkdirs() }
                val newlyAddedFiles = mutableListOf<File>()
                val replacedOriginals = mutableListOf<File>()

                try {
                    // Record PREPARED state in Journal
                    VaultRestoreJournal.recordState(
                        context,
                        RestoreJournalRecord(
                            state = RestoreJournalState.PREPARED.name,
                            isDecoy = isDecoy,
                            nextGen = nextGen,
                            intentFileName = genIntentFile.name,
                            vaultDirPath = vaultDir.absolutePath,
                            backupPrevGenDirPath = "",
                            nextGenDirPath = genDir.absolutePath,
                            isMergeMode = true,
                            replacedFilesBackupDirPath = replacedFilesBackupDir.absolutePath
                        )
                    )

                    // Preserve any existing file in vaultDir that would be overwritten
                    genDir.listFiles()?.forEach { file ->
                        val target = File(vaultDir, file.name)
                        if (target.exists()) {
                            val backupCopy = File(replacedFilesBackupDir, target.name)
                            Files.move(
                                target.toPath(),
                                backupCopy.toPath(),
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING
                            )
                            replacedOriginals.add(backupCopy)
                        }
                        Files.move(
                            file.toPath(),
                            target.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                        )
                        newlyAddedFiles.add(target)
                    }

                    // Record FS_SWAPPED state in Journal
                    VaultRestoreJournal.recordState(
                        context,
                        RestoreJournalRecord(
                            state = RestoreJournalState.FS_SWAPPED.name,
                            isDecoy = isDecoy,
                            nextGen = nextGen,
                            intentFileName = genIntentFile.name,
                            vaultDirPath = vaultDir.absolutePath,
                            backupPrevGenDirPath = "",
                            nextGenDirPath = genDir.absolutePath,
                            isMergeMode = true,
                            replacedFilesBackupDirPath = replacedFilesBackupDir.absolutePath,
                            newlyAddedFilesPaths = newlyAddedFiles.map { it.absolutePath }
                        )
                    )

                    activeFaultHook?.invoke(RestoreFaultPhase.AFTER_FS_SWAP)

                    db.withTransaction {
                        restoredFolders?.forEach { db.vaultDao().insertFolder(it) }
                        restoredPasswords?.forEach { db.vaultPasswordDao().insertPassword(it) }
                        logsToRestore?.forEach { db.intruderLogDao().insertLog(it) }
                        finalItemsToInsert.forEach { item ->
                            db.vaultDao().insertVaultItem(item)
                            restoredCount++
                        }
                        activeFaultHook?.invoke(RestoreFaultPhase.DURING_DB_TRANSACTION)
                    }

                    // Record DB_COMMITTED state in Journal
                    VaultRestoreJournal.recordState(
                        context,
                        RestoreJournalRecord(
                            state = RestoreJournalState.DB_COMMITTED.name,
                            isDecoy = isDecoy,
                            nextGen = nextGen,
                            intentFileName = genIntentFile.name,
                            vaultDirPath = vaultDir.absolutePath,
                            backupPrevGenDirPath = "",
                            nextGenDirPath = genDir.absolutePath,
                            isMergeMode = true,
                            replacedFilesBackupDirPath = replacedFilesBackupDir.absolutePath,
                            newlyAddedFilesPaths = newlyAddedFiles.map { it.absolutePath }
                        )
                    )

                    activeFaultHook?.invoke(RestoreFaultPhase.BEFORE_GENERATION_COMMIT)
                    VaultGenerationManager.commitGeneration(context, isDecoy, nextGen, genIntentFile)
                    try { replacedFilesBackupDir.deleteRecursively() } catch (_: Exception) {}
                    VaultRestoreJournal.clearJournal(context)
                } catch (t: Throwable) {
                    Log.e(TAG, "Merge restore transaction failed, rolling back newly added files and restoring replaced originals...", t)
                    try { genIntentFile.delete() } catch (_: Throwable) {}
                    newlyAddedFiles.forEach { try { it.delete() } catch (_: Exception) {} }
                    replacedOriginals.forEach { origBackup ->
                        val restoredFile = File(vaultDir, origBackup.name)
                        try {
                            Files.move(origBackup.toPath(), restoredFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        } catch (_: Exception) {}
                    }
                    try { replacedFilesBackupDir.deleteRecursively() } catch (_: Exception) {}
                    VaultRestoreJournal.clearJournal(context)
                    throw t
                }
            }

            onProgress?.invoke(restoredCount, restoredCount, "Restoration Complete ($restoredCount files)", totalBytesRestored)
            Result.success(restoredCount)
        } catch (e: BackupException) {
            Log.e(TAG, "BackupException during import: ${e.message}", e)
            Result.failure(e)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during import: ${e.message}", e)
            Result.failure(e)
        } catch (e: AEADBadTagException) {
            Log.e(TAG, "AEAD Bad Tag: Invalid master password or corrupted backup ciphertext", e)
            Result.failure(InvalidBackupPasswordException("INCORRECT_BACKUP_PASSWORD: Invalid backup password or corrupted ciphertext.", e))
        } catch (e: Exception) {
            Log.e(TAG, "Import failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            stagingDir.deleteRecursively()
            try { nextGenDir?.deleteRecursively() } catch (_: Exception) {}
            try { backupPrevGenDir?.deleteRecursively() } catch (_: Exception) {}
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
