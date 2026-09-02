package com.example.security

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Universal Multi-Carrier Steganography Engine.
 *
 * Conceals encrypted zero-trust vault payloads inside:
 * 1. Large Videos (MP4, MKV, MOV, AVI) - Ideal for concealing hundreds of MBs or GBs without suspicion.
 * 2. Documents & PDFs (PDF, ZIP, DOCX) - PDF readers ignore data appended after '%%EOF'.
 * 3. Audio Files (MP3, M4A, FLAC, WAV).
 * 4. High-Res Images (JPEG, PNG, WEBP).
 *
 * Architecture:
 * - Uses streaming 64KB I/O buffers to handle multi-gigabyte files without OutOfMemoryError.
 * - Non-destructive: Carrier files remain fully playable and viewable in standard media/doc viewers.
 * - Protocol V2: [Carrier Bytes] + [Payload Bytes] + [8-byte Long Payload Length] + [VAULT_STEGO_V2 delimiter].
 * - Backward compatibility with Protocol V1 (4-byte Int header).
 */
object SteganographyManager {

    private val MAGIC_DELIMITER_V2 = "VAULT_STEGO_V2".toByteArray(Charsets.UTF_8)
    private val DELIMITER_LEN_V2 = MAGIC_DELIMITER_V2.size

    private val MAGIC_DELIMITER_V1 = "VAULT_STEGO_V1".toByteArray(Charsets.UTF_8)
    private val DELIMITER_LEN_V1 = MAGIC_DELIMITER_V1.size

    private const val BUFFER_SIZE = 65536 // 64 KB stream buffer

    /**
     * Embeds payload into a carrier file using streams (memory safe for GB-sized files).
     */
    fun embedPayloadStream(
        coverInputStream: InputStream,
        payloadInputStream: InputStream,
        outputStream: OutputStream
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int

        // 1. Stream cover carrier file to output
        var coverBytesWritten = 0L
        while (coverInputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            coverBytesWritten += bytesRead
        }

        // 2. Stream payload to output and calculate exact payload size
        var payloadBytesWritten = 0L
        while (payloadInputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            payloadBytesWritten += bytesRead
        }

        // 3. Write 8-byte Long size header
        val sizeHeader = ByteBuffer.allocate(8).putLong(payloadBytesWritten).array()
        outputStream.write(sizeHeader)

        // 4. Write Magic Delimiter V2
        outputStream.write(MAGIC_DELIMITER_V2)
        outputStream.flush()

        return coverBytesWritten + payloadBytesWritten + 8 + DELIMITER_LEN_V2
    }

    /**
     * Extracts payload from a stego carrier file directly into an output stream.
     */
    fun extractPayloadFromFile(stegoFile: File, outputStream: OutputStream): Result<Long> {
        return try {
            val fileLength = stegoFile.length()
            if (fileLength < DELIMITER_LEN_V2 + 8) {
                return Result.failure(IllegalArgumentException("File is too small to contain a steganography payload."))
            }

            RandomAccessFile(stegoFile, "r").use { raf ->
                // Check V2 Protocol
                raf.seek(fileLength - DELIMITER_LEN_V2)
                val trailingDelimiterV2 = ByteArray(DELIMITER_LEN_V2)
                raf.readFully(trailingDelimiterV2)

                var isV2 = trailingDelimiterV2.contentEquals(MAGIC_DELIMITER_V2)
                var payloadSize = 0L
                var payloadOffset = 0L

                if (isV2) {
                    raf.seek(fileLength - DELIMITER_LEN_V2 - 8)
                    val sizeBytes = ByteArray(8)
                    raf.readFully(sizeBytes)
                    payloadSize = ByteBuffer.wrap(sizeBytes).long

                    if (payloadSize <= 0 || payloadSize > (fileLength - DELIMITER_LEN_V2 - 8)) {
                        return Result.failure(IllegalStateException("Corrupted steganography size header."))
                    }
                    payloadOffset = fileLength - DELIMITER_LEN_V2 - 8 - payloadSize
                } else {
                    // Fallback to V1 Protocol (4-byte header)
                    if (fileLength < DELIMITER_LEN_V1 + 4) {
                        return Result.failure(IllegalStateException("No valid steganography signature found."))
                    }
                    raf.seek(fileLength - DELIMITER_LEN_V1)
                    val trailingDelimiterV1 = ByteArray(DELIMITER_LEN_V1)
                    raf.readFully(trailingDelimiterV1)

                    if (!trailingDelimiterV1.contentEquals(MAGIC_DELIMITER_V1)) {
                        return Result.failure(IllegalStateException("No steganography hidden vault payload found in this file."))
                    }

                    raf.seek(fileLength - DELIMITER_LEN_V1 - 4)
                    val sizeBytes = ByteArray(4)
                    raf.readFully(sizeBytes)
                    val intSize = ByteBuffer.wrap(sizeBytes).int
                    payloadSize = intSize.toLong()

                    if (payloadSize <= 0 || payloadSize > (fileLength - DELIMITER_LEN_V1 - 4)) {
                        return Result.failure(IllegalStateException("Corrupted V1 steganography size header."))
                    }
                    payloadOffset = fileLength - DELIMITER_LEN_V1 - 4 - payloadSize
                }

                // Stream out payload to destination
                raf.seek(payloadOffset)
                val buffer = ByteArray(BUFFER_SIZE)
                var remaining = payloadSize
                while (remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read == -1) break
                    outputStream.write(buffer, 0, read)
                    remaining -= read
                }
                outputStream.flush()

                Result.success(payloadSize)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolves file extension and suggested filename based on cover file URI.
     */
    fun resolveCarrierFileInfo(context: Context, uri: Uri): CarrierInfo {
        var fileName = "carrier_file"
        var extension = "mp4" // Default to versatile mp4
        var mimeType = "video/mp4"

        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        fileName = it.getString(nameIdx)
                    }
                }
            }

            val type = context.contentResolver.getType(uri)
            if (type != null) {
                mimeType = type
                val extFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
                if (!extFromMime.isNullOrEmpty()) {
                    extension = extFromMime
                }
            } else if (fileName.contains(".")) {
                extension = fileName.substringAfterLast(".")
            }
        } catch (e: Exception) {
            android.util.Log.e("Security", "Exception caught")
        }

        val baseName = if (fileName.contains(".")) fileName.substringBeforeLast(".") else fileName
        return CarrierInfo(
            displayName = fileName,
            baseName = baseName,
            extension = extension.lowercase(),
            mimeType = mimeType
        )
    }

    data class CarrierInfo(
        val displayName: String,
        val baseName: String,
        val extension: String,
        val mimeType: String
    )
}
