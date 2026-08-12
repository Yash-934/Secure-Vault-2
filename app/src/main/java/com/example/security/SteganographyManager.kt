package com.example.security

import java.nio.ByteBuffer

/**
 * Steganography (File Hiding) Engine.
 * Appends AES-256-GCM encrypted payload to a standard JPEG cover image without altering visual rendering.
 */
object SteganographyManager {

    private val MAGIC_DELIMITER = "VAULT_STEGO_V1".toByteArray(Charsets.UTF_8)
    private val DELIMITER_LEN = MAGIC_DELIMITER.size

    /**
     * Embeds payload bytes into a JPEG byte array.
     */
    fun embedPayloadInJpeg(coverJpegBytes: ByteArray, payloadBytes: ByteArray): ByteArray {
        val payloadSize = payloadBytes.size
        val sizeHeader = ByteBuffer.allocate(4).putInt(payloadSize).array()

        // Structure: [Cover JPEG Bytes] + [Payload Bytes] + [4-byte Payload Size] + [MAGIC_DELIMITER]
        val totalSize = coverJpegBytes.size + payloadBytes.size + 4 + DELIMITER_LEN
        val result = ByteArray(totalSize)

        var pos = 0
        System.arraycopy(coverJpegBytes, 0, result, pos, coverJpegBytes.size)
        pos += coverJpegBytes.size

        System.arraycopy(payloadBytes, 0, result, pos, payloadBytes.size)
        pos += payloadBytes.size

        System.arraycopy(sizeHeader, 0, result, pos, 4)
        pos += 4

        System.arraycopy(MAGIC_DELIMITER, 0, result, pos, DELIMITER_LEN)

        return result
    }

    /**
     * Extracts embedded payload bytes from a steganographic JPEG file.
     */
    fun extractPayloadFromJpeg(stegoJpegBytes: ByteArray): ByteArray? {
        if (stegoJpegBytes.size < DELIMITER_LEN + 4) return null

        // Verify Magic Delimiter at EOF
        val trailingDelimiter = ByteArray(DELIMITER_LEN)
        System.arraycopy(stegoJpegBytes, stegoJpegBytes.size - DELIMITER_LEN, trailingDelimiter, 0, DELIMITER_LEN)

        if (!trailingDelimiter.contentEquals(MAGIC_DELIMITER)) {
            return null
        }

        // Read 4-byte size header before delimiter
        val sizeOffset = stegoJpegBytes.size - DELIMITER_LEN - 4
        val sizeBuffer = ByteBuffer.wrap(stegoJpegBytes, sizeOffset, 4)
        val payloadSize = sizeBuffer.int

        if (payloadSize <= 0 || payloadSize > sizeOffset) {
            return null
        }

        // Extract payload bytes
        val payloadOffset = sizeOffset - payloadSize
        val payload = ByteArray(payloadSize)
        System.arraycopy(stegoJpegBytes, payloadOffset, payload, 0, payloadSize)

        return payload
    }
}
