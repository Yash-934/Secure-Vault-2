package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * High-Security Cryptography Manager using AES-256-GCM and Android Keystore System.
 * 
 * Cryptographic Principles:
 * 1. Hardware-Backed Key Storage: Secret keys are generated inside the Android Keystore
 *    Hardware Security Module (HSM/TEE/StrongBox) and never exposed in plaintext.
 * 2. Authenticated Encryption: AES-256 in Galois/Counter Mode (GCM) guarantees both
 *    confidentiality and integrity/authenticity (tamper detection).
 * 3. Fresh Initialization Vectors: A new random 12-byte IV is generated for every encryption
 *    operation and prepended to the ciphertext stream.
 */
object CryptoManager {

    private const val KEY_ALIAS = "SecureVaultAES256MasterKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val BUFFER_SIZE = 65536

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

        // Generate a new AES-256 key inside the Android Keystore
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

    /**
     * Encrypts the provided InputStream and writes the IV (12 bytes) + Ciphertext to the OutputStream.
     * Stream-based 64KB chunk processing prevents OutOfMemory errors on large photos/videos (even multi-GB).
     * Provides live progress reporting.
     */
    fun encryptStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        totalBytes: Long = -1L,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

        // 1. Write the 12-byte randomly generated IV to the start of the output stream
        val iv = cipher.iv
        outputStream.write(iv)

        // 2. Encrypt data in 64KB streaming chunks with live progress callbacks
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var totalProcessed = 0L

        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                val encryptedChunk = cipher.update(buffer, 0, bytesRead)
                if (encryptedChunk != null && encryptedChunk.isNotEmpty()) {
                    outputStream.write(encryptedChunk)
                }
                totalProcessed += bytesRead
                onProgress?.invoke(totalProcessed, totalBytes)
            }

            val finalChunk = cipher.doFinal()
            if (finalChunk != null && finalChunk.isNotEmpty()) {
                outputStream.write(finalChunk)
            }
            outputStream.flush()
        } finally {
            buffer.fill(0)
        }
    }

    /**
     * Decrypts an encrypted input stream (reading the leading 12-byte IV) and returns the plaintext ByteArray in memory.
     * Guarded with a strict 30MB size limit to prevent OutOfMemory crashes on large files/videos.
     */
    fun decryptStreamToByteArray(inputStream: InputStream, maxSizeBytes: Long = 30 * 1024 * 1024L): ByteArray {
        // 1. Read the 12-byte IV prepended during encryption
        val iv = ByteArray(IV_SIZE_BYTES)
        val ivBytesRead = inputStream.read(iv)
        if (ivBytesRead < IV_SIZE_BYTES) {
            throw IllegalArgumentException("Invalid encrypted file format: Missing IV header.")
        }

        // 2. Initialize Cipher in DECRYPT_MODE with the GCM parameter spec
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), gcmSpec)

        // 3. Read encrypted payload into buffer and perform decryption safely
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var totalRead = 0L

        try {
            while (inputStream.read(chunk).also { bytesRead = it } != -1) {
                totalRead += bytesRead
                if (totalRead > maxSizeBytes) {
                    throw IllegalStateException("File exceeds maximum in-memory preview size (${maxSizeBytes / (1024 * 1024)}MB). Streaming player must be used.")
                }
                val decryptedChunk = cipher.update(chunk, 0, bytesRead)
                if (decryptedChunk != null && decryptedChunk.isNotEmpty()) {
                    buffer.write(decryptedChunk)
                }
            }
            val finalChunk = cipher.doFinal()
            if (finalChunk != null && finalChunk.isNotEmpty()) {
                buffer.write(finalChunk)
            }
            return buffer.toByteArray()
        } finally {
            chunk.fill(0)
        }
    }

    /**
     * Decrypts an encrypted stream and writes plaintext to an OutputStream with live progress reporting.
     */
    fun decryptStreamToOutputStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        totalBytes: Long = -1L,
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ) {
        val iv = ByteArray(IV_SIZE_BYTES)
        val ivBytesRead = inputStream.read(iv)
        if (ivBytesRead < IV_SIZE_BYTES) {
            throw IllegalArgumentException("Invalid encrypted file format: Missing IV header.")
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), gcmSpec)

        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        var totalProcessed = 0L

        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                val decryptedBytes = cipher.update(buffer, 0, bytesRead)
                if (decryptedBytes != null && decryptedBytes.isNotEmpty()) {
                    outputStream.write(decryptedBytes)
                }
                totalProcessed += bytesRead
                onProgress?.invoke(totalProcessed, totalBytes)
            }

            val finalBytes = cipher.doFinal()
            if (finalBytes != null && finalBytes.isNotEmpty()) {
                outputStream.write(finalBytes)
            }
            outputStream.flush()
        } finally {
            buffer.fill(0)
        }
    }

    /**
     * Reads the 12-byte IV header from the encrypted InputStream and returns a javax.crypto.CipherInputStream
     * configured with the AES-256-GCM cipher for streaming decryption.
     */
    fun getDecryptedInputStream(inputStream: InputStream): InputStream {
        val iv = ByteArray(IV_SIZE_BYTES)
        val ivBytesRead = inputStream.read(iv)
        if (ivBytesRead < IV_SIZE_BYTES) {
            throw IllegalArgumentException("Invalid encrypted file format: Missing IV header.")
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), gcmSpec)

        return javax.crypto.CipherInputStream(inputStream, cipher)
    }
}
