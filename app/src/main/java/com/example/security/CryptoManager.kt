package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
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

    private fun generateRandomIV(): ByteArray {
        val iv = ByteArray(IV_SIZE_BYTES)
        SecureRandom().nextBytes(iv)
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
     * Encrypts the provided InputStream using Chunked AES-256-GCM streaming.
     * Peak memory consumption is capped at ~1MB regardless of whether the file is 100MB or 10GB.
     */
    fun encryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        totalBytes: Long = -1L,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val secretKey = getSecretKey()
        val baseIV = generateRandomIV()

        // 1. Write Header: Magic + Base IV
        outputStream.write(V2_MAGIC)
        outputStream.write(baseIV)

        val readBuffer = ByteArray(CHUNK_SIZE)
        var chunkIndex = 0L
        var totalProcessed = 0L

        // We use a lookahead mechanism to determine if the current chunk is the last one
        var currentChunkBytes = readFully(inputStream, readBuffer, 0, CHUNK_SIZE)

        while (currentChunkBytes > 0) {
            val nextBuffer = ByteArray(CHUNK_SIZE)
            val nextChunkBytes = readFully(inputStream, nextBuffer, 0, CHUNK_SIZE)
            val isLast = (nextChunkBytes <= 0)

            // Encrypt current chunk with GCM
            val chunkIV = computeChunkIV(baseIV, chunkIndex)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIV))

            val cipherText = cipher.doFinal(readBuffer, 0, currentChunkBytes)

            // Write chunk header: length (4 bytes) + isLast flag (1 byte)
            writeInt(outputStream, cipherText.size)
            outputStream.write(if (isLast) 1 else 0)
            outputStream.write(cipherText)

            totalProcessed += currentChunkBytes
            onProgress?.invoke(totalProcessed, totalBytes)

            if (isLast) break

            System.arraycopy(nextBuffer, 0, readBuffer, 0, nextChunkBytes)
            currentChunkBytes = nextChunkBytes
            chunkIndex++
        }

        outputStream.flush()
    }

    /**
     * Decrypts an encrypted input stream to plaintext OutputStream with live progress reporting.
     * Handles both V2 Chunked AEAD streaming (constant ~1MB RAM) and V1 legacy formats.
     */
    fun decryptStreamToOutputStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        totalBytes: Long = -1L,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val secretKey = getSecretKey()

        // 1. Check Magic Header (4 bytes)
        val headerBytes = ByteArray(4)
        val headerRead = readFully(inputStream, headerBytes, 0, 4)
        if (headerRead < 4) {
            throw IllegalArgumentException("Corrupted encrypted file: Invalid header.")
        }

        val isV2 = headerBytes.contentEquals(V2_MAGIC)

        if (isV2) {
            // V2 Chunked AEAD Streaming Decryption
            val baseIV = ByteArray(IV_SIZE_BYTES)
            val ivRead = readFully(inputStream, baseIV, 0, IV_SIZE_BYTES)
            if (ivRead < IV_SIZE_BYTES) {
                throw IllegalArgumentException("Corrupted encrypted file: Missing Base IV.")
            }

            var chunkIndex = 0L
            var totalProcessed = 0L

            while (true) {
                val cipherLength = readInt(inputStream)
                if (cipherLength <= 0) break

                val isLastFlag = inputStream.read()
                if (isLastFlag < 0) break

                val cipherBuffer = ByteArray(cipherLength)
                val bytesRead = readFully(inputStream, cipherBuffer, 0, cipherLength)
                if (bytesRead < cipherLength) {
                    throw IllegalStateException("Unexpected end of encrypted stream in chunk $chunkIndex.")
                }

                val chunkIV = computeChunkIV(baseIV, chunkIndex)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkIV))

                val plainChunk = cipher.doFinal(cipherBuffer, 0, cipherLength)
                outputStream.write(plainChunk)

                totalProcessed += cipherLength
                onProgress?.invoke(totalProcessed, totalBytes)

                if (isLastFlag == 1) break
                chunkIndex++
            }
            outputStream.flush()
        } else {
            // Legacy V1 format: Header was the first 4 bytes of the 12-byte IV
            val iv = ByteArray(IV_SIZE_BYTES)
            System.arraycopy(headerBytes, 0, iv, 0, 4)
            val remainingIvRead = readFully(inputStream, iv, 4, 8)
            if (remainingIvRead < 8) {
                throw IllegalArgumentException("Corrupted legacy encrypted file: Incomplete IV header.")
            }

            // Legacy V1 format: Stream chunk by chunk using cipher.update to avoid huge heap allocations
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

            val chunkBuffer = ByteArray(65536)
            var bytesRead: Int
            var totalProcessed = 0L
            while (inputStream.read(chunkBuffer).also { bytesRead = it } != -1) {
                val decrypted = cipher.update(chunkBuffer, 0, bytesRead)
                if (decrypted != null && decrypted.isNotEmpty()) {
                    outputStream.write(decrypted)
                }
                totalProcessed += bytesRead
                onProgress?.invoke(totalProcessed, totalBytes)
            }
            val finalBytes = cipher.doFinal()
            if (finalBytes != null && finalBytes.isNotEmpty()) {
                outputStream.write(finalBytes)
            }
            outputStream.flush()
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
