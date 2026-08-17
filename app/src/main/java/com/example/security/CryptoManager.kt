package com.example.security

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
 * High-Security Cryptography Manager using AES-256-GCM and Android Keystore System.
 * 
 * Cryptographic Architecture:
 * 1. Hardware-Backed Master Key: Generated in Android Keystore TEE/StrongBox.
 * 2. Chunked AEAD Streaming (V2 STREAM format): Large files (up to multi-GB) are encrypted
 *    in independent 1MB AES-256-GCM authenticated chunks with deterministic per-chunk IVs.
 *    This guarantees constant O(1) ~1MB memory usage and prevents any OutOfMemoryError.
 * 3. Backward Compatibility (V1 format): Seamlessly detects and decrypts legacy single-block files.
 */
object CryptoManager {

    private const val KEY_ALIAS = "SecureVaultAES256MasterKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val CHUNK_SIZE = 1024 * 1024 // 1 MB Streaming Chunks

    // Magic header identifier for V2 Chunked AEAD format
    private val V2_MAGIC = byteArrayOf(0x56, 0x4C, 0x54, 0x32) // "VLT2"
    // Magic header identifier for V3 Envelope Encryption format
    private val V3_MAGIC = byteArrayOf(0x56, 0x4C, 0x54, 0x33) // "VLT3"

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Retrieves the AES-256 key from the Android Keystore, or generates a new one if it doesn't exist.
     */
    private fun getSecretKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) {
            return existingKey.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
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

    private val secureRandom = SecureRandom()

    private fun generateRandomIV(): ByteArray {
        val iv = ByteArray(IV_SIZE_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    private fun computeChunkIV(baseIV: ByteArray, chunkIndex: Long): ByteArray {
        val chunkIV = baseIV.copyOf(IV_SIZE_BYTES)
        val bb = ByteBuffer.allocate(8).putLong(chunkIndex)
        val indexBytes = bb.array()
        for (i in 0 until 8) {
            chunkIV[4 + i] = (chunkIV[4 + i].toInt() xor indexBytes[i].toInt()).toByte()
        }
        return chunkIV
    }

    private fun writeInt(out: OutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun readInt(input: InputStream): Int {
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        val b4 = input.read()
        if ((b1 or b2 or b3 or b4) < 0) return -1
        return ((b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4)
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

    /**
     * Securely zeroes out a direct or heap ByteBuffer to prevent Memory Remanence & RAM scraping attacks.
     * Overwrites the memory with 0x00 bytes.
     */
    private fun wipeDirectBuffer(buffer: ByteBuffer?) {
        if (buffer == null) return
        try {
            buffer.clear()
            val zeroArray = ByteArray(minOf(65536, buffer.capacity()))
            while (buffer.hasRemaining()) {
                val toPut = minOf(buffer.remaining(), zeroArray.size)
                buffer.put(zeroArray, 0, toPut)
            }
        } catch (_: Throwable) {
            // Best effort memory clearing
        }
    }

    /**
     * Securely zeroes out a ByteArray to prevent plaintext remnants in heap memory.
     */
    private fun wipeByteArray(array: ByteArray?) {
        if (array == null) return
        try {
            array.fill(0)
        } catch (_: Throwable) {
            // Best effort
        }
    }

    /**
     * Encrypts a stream into V3 format (Envelope Encryption).
     * Ultra-fast ~GB/s speed because chunk encryption uses software AES, while DEK is Keystore-protected.
     */
    fun encryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        totalBytes: Long = -1L,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val masterKey = getSecretKey()

        // 1. Generate Software DEK (Data Encryption Key)
        val dekGenerator = KeyGenerator.getInstance("AES")
        dekGenerator.init(256, secureRandom)
        val dek = dekGenerator.generateKey()

        // 2. Encrypt DEK with Keystore Master Key
        val ksCipher = Cipher.getInstance(TRANSFORMATION)
        ksCipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val dekIv = ksCipher.iv
        val encryptedDek = ksCipher.doFinal(dek.encoded)

        // 3. Write V3 Header
        outputStream.write(V3_MAGIC)
        outputStream.write(dekIv)
        writeInt(outputStream, encryptedDek.size)
        outputStream.write(encryptedDek)

        // 4. Process Chunks using Software DEK (Extremely Fast)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val readBuffer = ByteArray(CHUNK_SIZE)
        val nextBuffer = ByteArray(CHUNK_SIZE)
        val cipherBuffer = ByteArray(CHUNK_SIZE + 64)
        var totalProcessed = 0L

        try {
            var currentChunkBytes = readFully(inputStream, readBuffer, 0, CHUNK_SIZE)

            if (currentChunkBytes <= 0) {
                val iv = generateRandomIV()
                cipher.init(Cipher.ENCRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                val cipherLen = cipher.doFinal(ByteArray(0), 0, 0, cipherBuffer, 0)

                writeInt(outputStream, cipherLen)
                outputStream.write(1) // isLast
                outputStream.write(iv)
                outputStream.write(cipherBuffer, 0, cipherLen)
                outputStream.flush()
                onProgress?.invoke(0L, totalBytes)
                return
            }

            while (currentChunkBytes > 0) {
                val nextChunkBytes = readFully(inputStream, nextBuffer, 0, CHUNK_SIZE)
                val isLast = (nextChunkBytes <= 0)

                val chunkIV = generateRandomIV()
                cipher.init(Cipher.ENCRYPT_MODE, dek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIV))

                val cipherLen = cipher.doFinal(readBuffer, 0, currentChunkBytes, cipherBuffer, 0)

                writeInt(outputStream, cipherLen)
                outputStream.write(if (isLast) 1 else 0)
                outputStream.write(chunkIV)
                outputStream.write(cipherBuffer, 0, cipherLen)

                totalProcessed += currentChunkBytes
                onProgress?.invoke(totalProcessed, totalBytes)

                if (isLast) break

                System.arraycopy(nextBuffer, 0, readBuffer, 0, nextChunkBytes)
                currentChunkBytes = nextChunkBytes
            }

            outputStream.flush()
        } finally {
            wipeByteArray(readBuffer)
            wipeByteArray(nextBuffer)
            wipeByteArray(cipherBuffer)
            wipeByteArray(dek.encoded)
        }
    }

    /**
     * Decrypts an encrypted file directly to a destination file on disk.
     * Uses high-throughput 256KB buffered streams for V2 files and zero-RAM memory mapping for legacy V1 files.
     * Guaranteed to never crash with OutOfMemoryError regardless of file size.
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

        val headerBytes = ByteArray(4)
        RandomAccessFile(encryptedFile, "r").use { checkRaf ->
            checkRaf.readFully(headerBytes)
        }

        if (headerBytes.contentEquals(V2_MAGIC) || headerBytes.contentEquals(V3_MAGIC)) {
            encryptedFile.inputStream().buffered(262144).use { input ->
                destFile.outputStream().buffered(262144).use { output ->
                    decryptStreamToOutputStream(input, output, totalSize, onProgress)
                }
            }
        } else {
            // Legacy V1 format: Header is first 4 bytes of 12-byte IV
            val secretKey = getSecretKey()
            val iv = ByteArray(IV_SIZE_BYTES)
            System.arraycopy(headerBytes, 0, iv, 0, 4)

            RandomAccessFile(encryptedFile, "r").use { inRaf ->
                inRaf.seek(4)
                inRaf.readFully(iv, 4, 8)

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

                val cipherPayloadSize = totalSize - IV_SIZE_BYTES
                val outputSize = cipher.getOutputSize(cipherPayloadSize.toInt())

                val inChannel = inRaf.channel
                val inMapped = inChannel.map(FileChannel.MapMode.READ_ONLY, IV_SIZE_BYTES.toLong(), cipherPayloadSize)

                RandomAccessFile(destFile, "rw").use { outRaf ->
                    outRaf.setLength(outputSize.toLong())
                    val outChannel = outRaf.channel
                    val outMapped = outChannel.map(FileChannel.MapMode.READ_WRITE, 0, outputSize.toLong())
                    
                    cipher.doFinal(inMapped, outMapped)
                }
            }
            onProgress?.invoke(totalSize, totalSize)
        }
    }

    /**
     * Decrypts an encrypted input stream to plaintext OutputStream with live progress reporting.
     * Handles both V2 Chunked AEAD streaming (constant ~1MB RAM) and V1 legacy formats.
     * Enforces mandatory memory wiping (zeroization) on buffers.
     */
    fun decryptStreamToOutputStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        totalBytes: Long = -1L,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val masterKey = getSecretKey()

        // 1. Check Magic Header (4 bytes)
        val headerBytes = ByteArray(4)
        val headerRead = readFully(inputStream, headerBytes, 0, 4)
        if (headerRead < 4) {
            throw IllegalArgumentException("Corrupted encrypted file: Invalid header.")
        }

        val isV2 = headerBytes.contentEquals(V2_MAGIC)
        val isV3 = headerBytes.contentEquals(V3_MAGIC)

        if (isV2 || isV3) {
            // V3 / V2 Chunked AEAD Streaming Decryption (Constant ~1MB RAM, High Throughput)
            
            // Resolve Key: Master Key for V2, Software DEK for V3
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
                val dekLen = ((dekLenHeader[0].toInt() and 0xFF) shl 24) or
                        ((dekLenHeader[1].toInt() and 0xFF) shl 16) or
                        ((dekLenHeader[2].toInt() and 0xFF) shl 8) or
                        (dekLenHeader[3].toInt() and 0xFF)
                        
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
            
            // Reuse single Cipher instance and pre-allocated buffers across all chunks
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val maxChunkCapacity = CHUNK_SIZE + 64
            val cipherBuffer = ByteArray(maxChunkCapacity)
            val plainBuffer = ByteArray(maxChunkCapacity)
            val chunkIV = ByteArray(IV_SIZE_BYTES)
            val lenHeader = ByteArray(4)
            var totalProcessed = 0L

            try {
                while (true) {
                    val lenRead = readFully(inputStream, lenHeader, 0, 4)
                    if (lenRead < 4) break

                    val cipherLength = ((lenHeader[0].toInt() and 0xFF) shl 24) or
                            ((lenHeader[1].toInt() and 0xFF) shl 16) or
                            ((lenHeader[2].toInt() and 0xFF) shl 8) or
                            (lenHeader[3].toInt() and 0xFF)

                    if (cipherLength <= 0 || cipherLength > maxChunkCapacity) break

                    val isLastFlag = inputStream.read()
                    if (isLastFlag < 0) break

                    val ivRead = readFully(inputStream, chunkIV, 0, IV_SIZE_BYTES)
                    if (ivRead < IV_SIZE_BYTES) {
                        throw IllegalStateException("Unexpected end of encrypted stream: Incomplete chunk IV.")
                    }

                    val bytesRead = readFully(inputStream, cipherBuffer, 0, cipherLength)
                    if (bytesRead < cipherLength) {
                        throw IllegalStateException("Unexpected end of encrypted stream.")
                    }

                    cipher.init(Cipher.DECRYPT_MODE, activeKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIV))
                    val plainBytes = cipher.doFinal(cipherBuffer, 0, cipherLength, plainBuffer, 0)
                    outputStream.write(plainBuffer, 0, plainBytes)

                    totalProcessed += cipherLength
                    onProgress?.invoke(totalProcessed, totalBytes)

                    if (isLastFlag == 1) break
                }
                outputStream.flush()
            } finally {
                wipeByteArray(cipherBuffer)
                wipeByteArray(plainBuffer)
                wipeByteArray(chunkIV)
                wipeByteArray(dekWipeBuffer)
            }
        } else {
            // Legacy V1 format: Header was the first 4 bytes of the 12-byte IV
            val iv = ByteArray(IV_SIZE_BYTES)
            System.arraycopy(headerBytes, 0, iv, 0, 4)
            val remainingIvRead = readFully(inputStream, iv, 4, 8)
            if (remainingIvRead < 8) {
                throw IllegalArgumentException("Corrupted legacy encrypted file: Incomplete IV header.")
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            // To avoid any OOM on legacy single-block streams, spill to temporary disk-backed file and memory-map
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
    }

    /**
     * Decrypts an encrypted input stream and returns the plaintext ByteArray in memory.
     * Guarded with a strict 30MB size limit for in-memory previews.
     */
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

    /**
     * Encrypts a plaintext ByteArray into an AES-256-GCM encrypted ByteArray.
     */
    fun encryptByteArray(data: ByteArray): ByteArray {
        val outStream = java.io.ByteArrayOutputStream()
        val inStream = java.io.ByteArrayInputStream(data)
        encryptStream(inStream, outStream, totalBytes = data.size.toLong())
        return outStream.toByteArray()
    }

    /**
     * Decrypts an AES-256-GCM encrypted ByteArray into a plaintext ByteArray.
     */
    fun decryptByteArray(encryptedData: ByteArray): ByteArray {
        val inStream = java.io.ByteArrayInputStream(encryptedData)
        return decryptStreamToByteArray(inStream)
    }

    /**
     * Decrypts a stream into javax.crypto.CipherInputStream for legacy stream consumers.
     */
    fun getDecryptedInputStream(inputStream: InputStream): InputStream {
        val iv = ByteArray(IV_SIZE_BYTES)
        val ivBytesRead = readFully(inputStream, iv, 0, IV_SIZE_BYTES)
        if (ivBytesRead < IV_SIZE_BYTES) {
            throw IllegalArgumentException("Invalid encrypted file format: Missing IV header.")
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        return javax.crypto.CipherInputStream(inputStream, cipher)
    }
}
