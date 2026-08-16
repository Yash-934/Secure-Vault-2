package com.example.security

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.concurrent.thread

/**
 * Custom Media3 DataSource for "On-The-Fly" streaming decryption of encrypted media files.
 * Streams and decrypts chunks directly into ExoPlayer buffers without writing plaintext to disk.
 * Supports seeking by intelligently skipping decrypted bytes.
 */
class CipherDataSource(
    private val encryptedFile: File
) : BaseDataSource(/* isNetwork = */ false) {

    private var pipedInputStream: PipedInputStream? = null
    private var decryptThread: Thread? = null
    private var dataSpec: DataSpec? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var isOpened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        transferInitializing(dataSpec)

        if (!encryptedFile.exists()) {
            throw IOException("Encrypted media file not found: ${encryptedFile.absolutePath}")
        }

        val pos = dataSpec.position
        bytesRemaining = dataSpec.length

        val posInputStream = PipedInputStream(1024 * 1024) // 1MB buffer
        val posOutputStream = PipedOutputStream(posInputStream)
        pipedInputStream = posInputStream

        isOpened = true

        decryptThread = thread {
            try {
                // We use a custom OutputStream that skips 'pos' bytes before writing to posOutputStream
                val skippingStream = object : OutputStream() {
                    var skipped = 0L
                    var closed = false
                    override fun write(b: Int) {
                        if (closed) return
                        if (skipped < pos) {
                            skipped++
                        } else {
                            posOutputStream.write(b)
                        }
                    }

                    override fun write(b: ByteArray, off: Int, len: Int) {
                        if (closed) return
                        if (skipped < pos) {
                            val toSkip = minOf(pos - skipped, len.toLong()).toInt()
                            skipped += toSkip
                            if (toSkip < len) {
                                posOutputStream.write(b, off + toSkip, len - toSkip)
                            }
                        } else {
                            posOutputStream.write(b, off, len)
                        }
                    }

                    override fun close() {
                        closed = true
                        posOutputStream.close()
                    }
                }

                encryptedFile.inputStream().buffered(65536).use { fileIn ->
                    CryptoManager.decryptStreamToOutputStream(fileIn, skippingStream)
                }
                skippingStream.close()
            } catch (e: Exception) {
                try { posOutputStream.close() } catch (ignored: Exception) {}
            }
        }

        transferStarted(dataSpec)
        
        // Return LENGTH_UNSET because we don't know the exact plaintext length quickly for V2
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            minOf(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }

        val read = pipedInputStream?.read(buffer, offset, bytesToRead) ?: -1
        if (read == -1) {
            if (bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining > 0) {
                // Expected more bytes but reached EOF
                return C.RESULT_END_OF_INPUT
            }
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= read
        }
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = Uri.fromFile(encryptedFile)

    override fun close() {
        isOpened = false
        try {
            pipedInputStream?.close()
        } catch (e: Exception) {
            // Ignore
        }
        pipedInputStream = null
        
        decryptThread?.interrupt()
        decryptThread = null
        
        transferEnded()
    }
}
