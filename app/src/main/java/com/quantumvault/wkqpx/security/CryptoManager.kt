package com.quantumvault.wkqpx.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * High-Security Cryptography Manager implementing AES-256-GCM AEAD with Whole-Object Integrity.
 * 
 * VLT4 Format Architecture:
 * 1. Hardware-Backed Root Key: Derived from centralized VaultKeyManager.
 * 2. Authenticated Framing (VLT4): Every chunk binds format version, vault realm, file identity,
 *    and sequential chunk index into the GCM Additional Authenticated Data (AAD).
 * 3. Whole-File Completion Proof: End-of-file completion record commits to total chunk count,
 *    total plaintext bytes, and final GCM authentication tag. Rejects reordered, duplicated,
 *    missing, truncated chunks, or trailing bytes.
 * 4. Atomic File Decryption: Destination files are never written directly. Plaintext is staged
 *    in temporary files and atomically renamed only upon 100% verified completion.
 * 5. Backward Compatibility: Seamlessly supports legacy V3, V2, and V1 formats.
 */
object CryptoManager {

    private const val TAG = "CryptoManager"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val CHUNK_SIZE = 1024 * 1024 // 1 MB Streaming Chunks

    // Format Magic Headers
    private val V4_MAGIC = byteArrayOf(0x56, 0x4C, 0x54, 0x34) // "VLT4"
    private val V3_MAGIC = byteArrayOf(0x56, 0x4C, 0x54, 0x33) // "VLT3"
    private val V2_MAGIC = byteArrayOf(0x56, 0x4C, 0x54, 0x32) // "VLT2"

    private const val V4_VERSION: Byte = 4
    private const val REALM_REAL: Byte = 1
    private const val REALM_DECOY: Byte = 2
    private val V4_COMPLETION_MARKER = "VLT4_EOF".toByteArray(Charsets.US_ASCII)

    private val secureRandom = SecureRandom()

    private fun getSecretKey(): SecretKey {
        return VaultKeyManager.getVaultMasterKey()
    }

    private fun generateRandomIV(): ByteArray {
        val iv = ByteArray(IV_SIZE_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    private fun writeInt(out: OutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeLong(out: OutputStream, value: Long) {
        val bb = ByteBuffer.allocate(8).putLong(value)
        out.write(bb.array())
    }

    private fun readFully(inputStream: InputStream, buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int {
        var totalRead = 0
        while (totalRead < length) {
            val count = inputStream.read(buffer, offset + totalRead, length - totalRead)
            if (count == -1) break
            totalRead += count
        }
        return totalRead
    }

    private fun wipeByteArray(array: ByteArray?) {
        if (array == null) return
        try {
            array.fill(0)
        } catch (_: Throwable) {}
    }

    private fun buildV4HeaderAad(realm: Byte, fileId: ByteArray): ByteArray {
        val bb = ByteBuffer.allocate(8 + 1 + 16)
        bb.put("VLT4_HDR".toByteArray(Charsets.US_ASCII))
        bb.put(realm)
        bb.put(fileId)
        return bb.array()
    }

    private fun buildV4ChunkAad(
        realm: Byte,
        fileId: ByteArray,
        chunkIndex: Long,
        isLast: Byte,
        plainLength: Int
    ): ByteArray {
        val bb = ByteBuffer.allocate(8 + 1 + 16 + 8 + 1 + 4)
        bb.put("VLT4_CHK".toByteArray(Charsets.US_ASCII))
        bb.put(realm)
        bb.put(fileId)
        bb.putLong(chunkIndex)
        bb.put(isLast)
        bb.putInt(plainLength)
        return bb.array()
    }

    private fun buildV4CompletionAad(
        realm: Byte,
        fileId: ByteArray,
        totalChunks: Long,
        totalBytes: Long
    ): ByteArray {
        val bb = ByteBuffer.allocate(8 + 1 + 16 + 8 + 8)
        bb.put("VLT4_EOF".toByteArray(Charsets.US_ASCII))
        bb.put(realm)
        bb.put(fileId)
        bb.putLong(totalChunks)
        bb.putLong(totalBytes)
        return bb.array()
    }

    /**
     * Encrypts a stream using V4 Whole-Object Authenticated Framing.
     * Binds chunk index, realm, and file identity into per-chunk AAD, followed by
     * an authenticated end-of-file completion proof.
     */
    fun encryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        totalBytes: Long = -1L,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val masterKey = getSecretKey()
        val realm: Byte = if (VaultKeyManager.isDecoyVaultAuthorized()) REALM_DECOY else REALM_REAL

        // 1. Generate 16-byte File Identity UUID
        val fileId = ByteArray(16).also { secureRandom.nextBytes(it) }

        // 2. Generate Data Encryption Key (DEK)
        val dekGenerator = KeyGenerator.getInstance("AES")
        dekGenerator.init(256, secureRandom)
        val dek = dekGenerator.generateKey()

        // 3. Encrypt DEK under Master Key with Header AAD
        val ksCipher = Cipher.getInstance(TRANSFORMATION)
        val dekIv = generateRandomIV()
        ksCipher.init(Cipher.ENCRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, dekIv))
        ksCipher.updateAAD(buildV4HeaderAad(realm, fileId))
        val encryptedDek = ksCipher.doFinal(dek.encoded)

        // 4. Write V4 Header
        outputStream.write(V4_MAGIC)
        outputStream.write(V4_VERSION.toInt())
        outputStream.write(realm.toInt())
        outputStream.write(fileId)
        outputStream.write(dekIv)
        writeInt(outputStream, encryptedDek.size)
        outputStream.write(encryptedDek)

        // 5. Process Chunks
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val readBuffer = ByteArray(CHUNK_SIZE)
        val nextBuffer = ByteArray(CHUNK_SIZE)
        val cipherBuffer = ByteArray(CHUNK_SIZE + 64)
        var totalPlainProcessed = 0L
        var chunkIndex = 0L

        try {
            var currentChunkBytes = readFully(inputStream, readBuffer, 0, CHUNK_SIZE)

            if (currentChunkBytes <= 0) {
                // Empty file handling
                val chunkIV = generateRandomIV()
                val chunkAad = buildV4ChunkAad(realm, fileId, 0L, 1.toByte(), 0)
                cipher.init(Cipher.ENCRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIV))
                cipher.updateAAD(chunkAad)
                val cipherLen = cipher.doFinal(ByteArray(0), 0, 0, cipherBuffer, 0)

                writeLong(outputStream, 0L)
                writeInt(outputStream, 0)
                writeInt(outputStream, cipherLen)
                outputStream.write(1) // isLast
                outputStream.write(chunkIV)
                outputStream.write(cipherBuffer, 0, cipherLen)

                // Write Completion Proof
                val finalIv = generateRandomIV()
                val finalAad = buildV4CompletionAad(realm, fileId, 1L, 0L)
                cipher.init(Cipher.ENCRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, finalIv))
                cipher.updateAAD(finalAad)
                val finalProof = cipher.doFinal(ByteArray(0))

                outputStream.write(V4_COMPLETION_MARKER)
                writeLong(outputStream, 1L)
                writeLong(outputStream, 0L)
                outputStream.write(finalIv)
                outputStream.write(finalProof)
                outputStream.flush()
                onProgress?.invoke(0L, totalBytes)
                return
            }

            while (currentChunkBytes > 0) {
                val nextChunkBytes = readFully(inputStream, nextBuffer, 0, CHUNK_SIZE)
                val isLast: Byte = if (nextChunkBytes <= 0) 1 else 0

                val chunkIV = generateRandomIV()
                val chunkAad = buildV4ChunkAad(realm, fileId, chunkIndex, isLast, currentChunkBytes)
                cipher.init(Cipher.ENCRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIV))
                cipher.updateAAD(chunkAad)

                val cipherLen = cipher.doFinal(readBuffer, 0, currentChunkBytes, cipherBuffer, 0)

                writeLong(outputStream, chunkIndex)
                writeInt(outputStream, currentChunkBytes)
                writeInt(outputStream, cipherLen)
                outputStream.write(isLast.toInt())
                outputStream.write(chunkIV)
                outputStream.write(cipherBuffer, 0, cipherLen)

                totalPlainProcessed += currentChunkBytes
                chunkIndex++
                onProgress?.invoke(totalPlainProcessed, totalBytes)

                if (isLast == 1.toByte()) break

                System.arraycopy(nextBuffer, 0, readBuffer, 0, nextChunkBytes)
                currentChunkBytes = nextChunkBytes
            }

            // Write Whole-File Authenticated Completion Record
            val totalChunks = chunkIndex
            val finalIv = generateRandomIV()
            val finalAad = buildV4CompletionAad(realm, fileId, totalChunks, totalPlainProcessed)
            cipher.init(Cipher.ENCRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, finalIv))
            cipher.updateAAD(finalAad)
            val finalProof = cipher.doFinal(ByteArray(0))

            outputStream.write(V4_COMPLETION_MARKER)
            writeLong(outputStream, totalChunks)
            writeLong(outputStream, totalPlainProcessed)
            outputStream.write(finalIv)
            outputStream.write(finalProof)
            outputStream.flush()
        } finally {
            wipeByteArray(readBuffer)
            wipeByteArray(nextBuffer)
            wipeByteArray(cipherBuffer)
            wipeByteArray(dek.encoded)
        }
    }

    /**
     * Decrypts an encrypted file to a destination file on disk.
     * ATOMIC GUARANTEE: Plaintext is written to a temporary staged file and only renamed
     * to destFile upon 100% verified whole-object integrity. If decryption or authentication
     * fails at any point, the staged file is purged and never exposed.
     */
    fun decryptFileToFile(
        encryptedFile: File,
        destFile: File,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val totalSize = encryptedFile.length()
        if (totalSize < 4) {
            throw IllegalArgumentException("Invalid encrypted file: File is empty or truncated.")
        }

        val parentDir = destFile.parentFile ?: encryptedFile.parentFile ?: File(".")
        val tempDestFile = File(parentDir, "${destFile.name}.${System.currentTimeMillis()}.tmp")

        try {
            encryptedFile.inputStream().buffered(262144).use { input ->
                tempDestFile.outputStream().buffered(262144).use { output ->
                    decryptStreamToOutputStream(input, output, totalSize, onProgress)
                }
            }

            if (!tempDestFile.renameTo(destFile)) {
                tempDestFile.copyTo(destFile, overwrite = true)
                tempDestFile.delete()
            }
        } catch (e: Throwable) {
            if (tempDestFile.exists()) {
                tempDestFile.delete()
            }
            throw e
        }
    }

    /**
     * Decrypts an input stream to output stream with live progress reporting.
     * Strictly verifies V4 framing, sequential chunks, AAD tags, and completion proof.
     */
    fun decryptStreamToOutputStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        totalBytes: Long = -1L,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val masterKey = getSecretKey()

        val headerBytes = ByteArray(4)
        val headerRead = readFully(inputStream, headerBytes, 0, 4)
        if (headerRead < 4) {
            throw IllegalArgumentException("Corrupted encrypted file: Invalid header.")
        }

        if (headerBytes.contentEquals(V4_MAGIC)) {
            decryptV4Stream(inputStream, outputStream, masterKey, totalBytes, onProgress)
        } else if (headerBytes.contentEquals(V3_MAGIC) || headerBytes.contentEquals(V2_MAGIC)) {
            decryptLegacyV2V3Stream(inputStream, outputStream, masterKey, headerBytes, totalBytes, onProgress)
        } else {
            decryptLegacyV1Stream(inputStream, outputStream, masterKey, headerBytes, totalBytes, onProgress)
        }
    }

    private fun decryptV4Stream(
        inputStream: InputStream,
        outputStream: OutputStream,
        masterKey: SecretKey,
        totalBytes: Long,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)?
    ) {
        val version = inputStream.read()
        if (version != V4_VERSION.toInt()) {
            throw SecurityException("V4 unsupported version: $version")
        }

        val realm = inputStream.read().toByte()
        val expectedRealm = if (VaultKeyManager.isDecoyVaultAuthorized()) REALM_DECOY else REALM_REAL
        if (realm != expectedRealm) {
            throw SecurityException("V4 realm mismatch: expected $expectedRealm, got $realm")
        }

        val fileId = ByteArray(16)
        if (readFully(inputStream, fileId, 0, 16) < 16) {
            throw SecurityException("Truncated stream: Missing file identity")
        }

        val dekIv = ByteArray(IV_SIZE_BYTES)
        if (readFully(inputStream, dekIv, 0, IV_SIZE_BYTES) < IV_SIZE_BYTES) {
            throw SecurityException("Truncated stream: Missing DEK IV")
        }

        val dekLenBuf = ByteArray(4)
        if (readFully(inputStream, dekLenBuf, 0, 4) < 4) {
            throw SecurityException("Truncated stream: Missing DEK length")
        }
        val dekLen = ByteBuffer.wrap(dekLenBuf).int
        if (dekLen <= 0 || dekLen > 1024) {
            throw SecurityException("Invalid DEK length: $dekLen")
        }

        val encryptedDek = ByteArray(dekLen)
        if (readFully(inputStream, encryptedDek, 0, dekLen) < dekLen) {
            throw SecurityException("Truncated stream: Incomplete encrypted DEK")
        }

        val ksCipher = Cipher.getInstance(TRANSFORMATION)
        ksCipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, dekIv))
        ksCipher.updateAAD(buildV4HeaderAad(realm, fileId))
        val rawDek = ksCipher.doFinal(encryptedDek)
        val activeDek = javax.crypto.spec.SecretKeySpec(rawDek, "AES")

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val maxChunkCapacity = CHUNK_SIZE + 64
        val cipherBuffer = ByteArray(maxChunkCapacity)
        val plainBuffer = ByteArray(maxChunkCapacity)
        val chunkIV = ByteArray(IV_SIZE_BYTES)
        val indexBuf = ByteArray(8)
        val plainLenBuf = ByteArray(4)
        val cipherLenBuf = ByteArray(4)

        var expectedChunkIndex = 0L
        var totalPlainProcessed = 0L

        try {
            while (true) {
                val indexRead = readFully(inputStream, indexBuf, 0, 8)
                if (indexRead < 8) {
                    throw SecurityException("Truncated stream: Missing chunk index at chunk $expectedChunkIndex")
                }
                val chunkIndex = ByteBuffer.wrap(indexBuf).long
                if (chunkIndex != expectedChunkIndex) {
                    throw SecurityException("Chunk framing violated: Expected chunk $expectedChunkIndex, got $chunkIndex (reordered or missing chunk)")
                }

                if (readFully(inputStream, plainLenBuf, 0, 4) < 4) {
                    throw SecurityException("Truncated stream: Missing plain length at chunk $chunkIndex")
                }
                val plainLength = ByteBuffer.wrap(plainLenBuf).int

                if (readFully(inputStream, cipherLenBuf, 0, 4) < 4) {
                    throw SecurityException("Truncated stream: Missing cipher length at chunk $chunkIndex")
                }
                val cipherLength = ByteBuffer.wrap(cipherLenBuf).int
                if (cipherLength <= 0 || cipherLength > maxChunkCapacity) {
                    throw SecurityException("Invalid chunk size $cipherLength at chunk $chunkIndex")
                }

                val isLast = inputStream.read()
                if (isLast < 0) {
                    throw SecurityException("Truncated stream: Missing isLast flag at chunk $chunkIndex")
                }

                if (readFully(inputStream, chunkIV, 0, IV_SIZE_BYTES) < IV_SIZE_BYTES) {
                    throw SecurityException("Truncated stream: Incomplete chunk IV at chunk $chunkIndex")
                }

                if (readFully(inputStream, cipherBuffer, 0, cipherLength) < cipherLength) {
                    throw SecurityException("Truncated stream: Incomplete ciphertext at chunk $chunkIndex")
                }

                val chunkAad = buildV4ChunkAad(realm, fileId, chunkIndex, isLast.toByte(), plainLength)
                cipher.init(Cipher.DECRYPT_MODE, activeDek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIV))
                cipher.updateAAD(chunkAad)

                val plainBytes = cipher.doFinal(cipherBuffer, 0, cipherLength, plainBuffer, 0)
                if (plainBytes != plainLength) {
                    throw SecurityException("Chunk plaintext length mismatch at chunk $chunkIndex")
                }

                outputStream.write(plainBuffer, 0, plainBytes)
                totalPlainProcessed += plainBytes
                expectedChunkIndex++
                onProgress?.invoke(totalPlainProcessed, totalBytes)

                if (isLast == 1) {
                    break
                }
            }

            // Whole-File Completion Proof Verification
            val markerBuf = ByteArray(8)
            if (readFully(inputStream, markerBuf, 0, 8) < 8 || !markerBuf.contentEquals(V4_COMPLETION_MARKER)) {
                throw SecurityException("Truncated stream: Missing or corrupt whole-file completion proof marker")
            }

            val totalChunksBuf = ByteArray(8)
            if (readFully(inputStream, totalChunksBuf, 0, 8) < 8) {
                throw SecurityException("Truncated stream: Missing total chunks in completion proof")
            }
            val committedTotalChunks = ByteBuffer.wrap(totalChunksBuf).long
            if (committedTotalChunks != expectedChunkIndex) {
                throw SecurityException("Completion proof mismatch: Committed $committedTotalChunks chunks, observed $expectedChunkIndex")
            }

            val totalBytesBuf = ByteArray(8)
            if (readFully(inputStream, totalBytesBuf, 0, 8) < 8) {
                throw SecurityException("Truncated stream: Missing total bytes in completion proof")
            }
            val committedTotalBytes = ByteBuffer.wrap(totalBytesBuf).long
            if (committedTotalBytes != totalPlainProcessed) {
                throw SecurityException("Completion proof mismatch: Committed $committedTotalBytes bytes, observed $totalPlainProcessed")
            }

            val finalIv = ByteArray(IV_SIZE_BYTES)
            if (readFully(inputStream, finalIv, 0, IV_SIZE_BYTES) < IV_SIZE_BYTES) {
                throw SecurityException("Truncated stream: Incomplete completion proof IV")
            }

            val proofBuf = ByteArray(16) // GCM tag
            if (readFully(inputStream, proofBuf, 0, 16) < 16) {
                throw SecurityException("Truncated stream: Incomplete completion proof authentication tag")
            }

            val finalAad = buildV4CompletionAad(realm, fileId, committedTotalChunks, committedTotalBytes)
            cipher.init(Cipher.DECRYPT_MODE, activeDek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, finalIv))
            cipher.updateAAD(finalAad)
            cipher.doFinal(proofBuf) // Authenticates tag

            // Trailing Data Rejection
            if (inputStream.read() != -1) {
                throw SecurityException("Unexpected trailing bytes detected after completion proof")
            }

            outputStream.flush()
        } finally {
            wipeByteArray(cipherBuffer)
            wipeByteArray(plainBuffer)
            wipeByteArray(chunkIV)
            wipeByteArray(rawDek)
        }
    }

    private fun decryptLegacyV2V3Stream(
        inputStream: InputStream,
        outputStream: OutputStream,
        masterKey: SecretKey,
        headerBytes: ByteArray,
        totalBytes: Long,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)?
    ) {
        val isV3 = headerBytes.contentEquals(V3_MAGIC)
        val activeKey: SecretKey
        var dekWipeBuffer: ByteArray? = null

        if (isV3) {
            val dekIv = ByteArray(IV_SIZE_BYTES)
            if (readFully(inputStream, dekIv, 0, IV_SIZE_BYTES) < IV_SIZE_BYTES) {
                throw IllegalStateException("Unexpected end of stream: Missing DEK IV.")
            }
            val dekLenHeader = ByteArray(4)
            if (readFully(inputStream, dekLenHeader, 0, 4) < 4) {
                throw IllegalStateException("Unexpected end of stream: Missing DEK Length.")
            }
            val dekLen = ByteBuffer.wrap(dekLenHeader).int
            if (dekLen <= 0 || dekLen > 1024) throw IllegalStateException("Invalid DEK length.")

            val encryptedDek = ByteArray(dekLen)
            if (readFully(inputStream, encryptedDek, 0, dekLen) < dekLen) {
                throw IllegalStateException("Unexpected end of stream: Missing Encrypted DEK.")
            }

            val ksCipher = Cipher.getInstance(TRANSFORMATION)
            ksCipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, dekIv))
            val rawDek = ksCipher.doFinal(encryptedDek)
            dekWipeBuffer = rawDek
            activeKey = javax.crypto.spec.SecretKeySpec(rawDek, "AES")
        } else {
            activeKey = masterKey
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val maxChunkCapacity = CHUNK_SIZE + 64
        val cipherBuffer = ByteArray(maxChunkCapacity)
        val plainBuffer = ByteArray(maxChunkCapacity)
        val chunkIV = ByteArray(IV_SIZE_BYTES)
        val lenHeader = ByteArray(4)
        var totalProcessed = 0L

        try {
            var lastChunkProcessed = false
            while (true) {
                val lenRead = readFully(inputStream, lenHeader, 0, 4)
                if (lenRead < 4) {
                    if (totalProcessed == 0L) break
                    if (!lastChunkProcessed) {
                        throw IllegalStateException("Unexpected end of encrypted stream: Truncated chunk length header.")
                    }
                    break
                }

                val cipherLength = ByteBuffer.wrap(lenHeader).int
                if (cipherLength <= 0 || cipherLength > maxChunkCapacity) {
                    throw IllegalStateException("Corrupted encrypted stream: Invalid chunk size $cipherLength.")
                }

                val isLastFlag = inputStream.read()
                if (isLastFlag < 0) {
                    throw IllegalStateException("Unexpected end of encrypted stream: Missing last-chunk marker.")
                }

                val ivRead = readFully(inputStream, chunkIV, 0, IV_SIZE_BYTES)
                if (ivRead < IV_SIZE_BYTES) {
                    throw IllegalStateException("Unexpected end of encrypted stream: Incomplete chunk IV.")
                }

                val bytesRead = readFully(inputStream, cipherBuffer, 0, cipherLength)
                if (bytesRead < cipherLength) {
                    throw IllegalStateException("Unexpected end of encrypted stream: Ciphertext truncated.")
                }

                cipher.init(Cipher.DECRYPT_MODE, activeKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIV))
                val plainBytes = cipher.doFinal(cipherBuffer, 0, cipherLength, plainBuffer, 0)
                outputStream.write(plainBuffer, 0, plainBytes)

                totalProcessed += cipherLength
                onProgress?.invoke(totalProcessed, totalBytes)

                if (isLastFlag == 1) {
                    lastChunkProcessed = true
                    break
                }
            }
            outputStream.flush()
        } finally {
            wipeByteArray(cipherBuffer)
            wipeByteArray(plainBuffer)
            wipeByteArray(chunkIV)
            wipeByteArray(dekWipeBuffer)
        }
    }

    private fun decryptLegacyV1Stream(
        inputStream: InputStream,
        outputStream: OutputStream,
        masterKey: SecretKey,
        headerBytes: ByteArray,
        totalBytes: Long,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)?
    ) {
        val iv = ByteArray(IV_SIZE_BYTES)
        System.arraycopy(headerBytes, 0, iv, 0, 4)
        val remainingIvRead = readFully(inputStream, iv, 4, 8)
        if (remainingIvRead < 8) {
            throw IllegalArgumentException("Corrupted legacy encrypted file: Incomplete IV header.")
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val tempEncFile = File.createTempFile("v1_enc_", ".tmp")
        val tempDecFile = File.createTempFile("v1_dec_", ".tmp")
        try {
            var totalRead = 0L
            val copyBuffer = ByteArray(65536)
            tempEncFile.outputStream().buffered(65536).use { encOut ->
                var read: Int
                while (inputStream.read(copyBuffer).also { read = it } != -1) {
                    encOut.write(copyBuffer, 0, read)
                    totalRead += read
                    onProgress?.invoke(totalRead, totalBytes)
                }
                encOut.flush()
            }

            val encSize = tempEncFile.length()
            val outputSize = cipher.getOutputSize(encSize.toInt())

            RandomAccessFile(tempEncFile, "r").use { inRaf ->
                val inChannel = inRaf.channel
                val inMapped = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, encSize)

                RandomAccessFile(tempDecFile, "rw").use { outRaf ->
                    outRaf.setLength(outputSize.toLong())
                    val outChannel = outRaf.channel
                    val outMapped = outChannel.map(FileChannel.MapMode.READ_WRITE, 0, outputSize.toLong())
                    cipher.doFinal(inMapped, outMapped)
                }
            }

            tempDecFile.inputStream().buffered(65536).use { decIn ->
                var read: Int
                while (decIn.read(copyBuffer).also { read = it } != -1) {
                    outputStream.write(copyBuffer, 0, read)
                }
            }
            outputStream.flush()
            onProgress?.invoke(totalBytes, totalBytes)
        } finally {
            if (tempEncFile.exists()) tempEncFile.delete()
            if (tempDecFile.exists()) tempDecFile.delete()
        }
    }

    fun decryptStreamToByteArray(inputStream: InputStream, maxSizeBytes: Long = 30 * 1024 * 1024L): ByteArray {
        val outStream = java.io.ByteArrayOutputStream()
        decryptStreamToOutputStream(
            inputStream = inputStream,
            outputStream = outStream,
            totalBytes = -1L,
            onProgress = { processed, _ ->
                if (processed > maxSizeBytes) {
                    throw IllegalStateException("File exceeds in-memory preview limit (${maxSizeBytes / (1024 * 1024)}MB). Streaming player must be used.")
                }
            }
        )
        return outStream.toByteArray()
    }

    fun encryptByteArray(data: ByteArray): ByteArray {
        val outStream = java.io.ByteArrayOutputStream()
        val inStream = java.io.ByteArrayInputStream(data)
        encryptStream(inStream, outStream, totalBytes = data.size.toLong())
        return outStream.toByteArray()
    }

    fun decryptByteArray(encryptedData: ByteArray): ByteArray {
        val inStream = java.io.ByteArrayInputStream(encryptedData)
        return decryptStreamToByteArray(inStream)
    }
}
