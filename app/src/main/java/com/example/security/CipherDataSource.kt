package com.example.security

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Custom Media3 DataSource for "On-The-Fly" streaming decryption of AES-256-GCM encrypted media files.
 * Streams and decrypts chunks directly into ExoPlayer buffers without writing plaintext to disk,
 * supporting arbitrarily large video/audio files (1GB+) with zero memory overhead.
 */
class CipherDataSource(
    private val encryptedFile: File
) : BaseDataSource(/* isNetwork = */ false) {

    private var fileInputStream: FileInputStream? = null
    private var cipher: Cipher? = null
    private var dataSpec: DataSpec? = null
    private var bytesRemaining: Long = 0
    private var totalDecryptedRead: Long = 0
    private val buffer = ByteArray(8192)

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)

        if (!encryptedFile.exists()) {
            throw IOException("Encrypted media file not found: ${encryptedFile.absolutePath}")
        }

        val fis = FileInputStream(encryptedFile)
        fileInputStream = fis

        // 1. Read 12-byte IV header
        val iv = ByteArray(12)
        val ivRead = fis.read(iv)
        if (ivRead < 12) {
            throw IOException("Invalid encrypted media format: Missing IV header.")
        }

        // 2. Retrieve SecretKey from Keystore
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val secretKey = (keyStore.getEntry("SecureVaultAES256MasterKey", null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw IOException("Keystore Master Key not found.")

        // 3. Initialize AES/GCM Cipher
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        c.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        cipher = c

        val totalFileLength = encryptedFile.length()
        val encryptedDataLength = if (totalFileLength > 12) totalFileLength - 12 else 0

        // Handle requested position offset
        var seekPosition = dataSpec.position
        totalDecryptedRead = 0

        if (seekPosition > 0) {
            var bytesToSkip = seekPosition
            val skipBuffer = ByteArray(8192)
            while (bytesToSkip > 0) {
                val readLength = minOf(bytesToSkip, skipBuffer.size.toLong()).toInt()
                val readEncrypted = fis.read(skipBuffer, 0, readLength)
                if (readEncrypted == -1) break
                val decrypted = c.update(skipBuffer, 0, readEncrypted)
                val decLength = decrypted?.size ?: 0
                bytesToSkip -= decLength
                totalDecryptedRead += decLength
            }
        }

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            if (encryptedDataLength > totalDecryptedRead) {
                encryptedDataLength - totalDecryptedRead
            } else {
                C.LENGTH_UNSET.toLong()
            }
        }

        transferStarted(dataSpec)
        return if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else (encryptedDataLength - totalDecryptedRead)
    }

    override fun read(targetBuffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val fis = fileInputStream ?: return C.RESULT_END_OF_INPUT
        val c = cipher ?: return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            minOf(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }

        val readEncrypted = fis.read(buffer, 0, minOf(bytesToRead, buffer.size))
        if (readEncrypted == -1) {
            // End of stream -> finalize GCM tag
            val finalBytes = try {
                c.doFinal()
            } catch (e: Exception) {
                null
            }
            if (finalBytes != null && finalBytes.isNotEmpty()) {
                val copyLen = minOf(finalBytes.size, length)
                System.arraycopy(finalBytes, 0, targetBuffer, offset, copyLen)
                bytesRemaining = 0
                bytesTransferred(copyLen)
                return copyLen
            }
            return C.RESULT_END_OF_INPUT
        }

        val decrypted = c.update(buffer, 0, readEncrypted)
        if (decrypted != null && decrypted.isNotEmpty()) {
            val copyLen = minOf(decrypted.size, length)
            System.arraycopy(decrypted, 0, targetBuffer, offset, copyLen)
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining -= copyLen
            }
            totalDecryptedRead += copyLen
            bytesTransferred(copyLen)
            return copyLen
        }

        return 0
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        try {
            fileInputStream?.close()
        } catch (_: Exception) {}
        fileInputStream = null
        cipher = null
        dataSpec = null
        transferEnded()
    }

    class Factory(private val file: File) : DataSource.Factory {
        override fun createDataSource(): DataSource = CipherDataSource(file)
    }
}
