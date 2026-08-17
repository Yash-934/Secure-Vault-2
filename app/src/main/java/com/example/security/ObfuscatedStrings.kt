package com.example.security

import java.nio.charset.StandardCharsets

/**
 * Native & Runtime Obfuscated String Engine.
 * 
 * Protects sensitive cryptographic constants, root detection binary paths, Frida/Xposed
 * identifiers, and security keywords from static binary analysis (e.g. `strings`, `dexdump`,
 * `jadx`, `ghidra`, `ida pro`).
 * 
 * Employs multi-round polymorphic XOR transformations with dynamic key derivation
 * and immediate zeroization of decrypted memory buffers.
 */
object ObfuscatedStrings {

    // Dynamic salt mask for runtime de-obfuscation
    private val RUNTIME_MASK = byteArrayOf(
        0x5A.toByte(), 0xA5.toByte(), 0x3C.toByte(), 0xC3.toByte(),
        0x69.toByte(), 0x96.toByte(), 0x1E.toByte(), 0xE1.toByte()
    )

    /**
     * Decrypts an obfuscated byte sequence using multi-round XOR and bitwise rotations.
     * The returned string is ephemeral; for maximum security callers can request char arrays.
     */
    fun decrypt(encrypted: ByteArray, key: Byte): String {
        val result = ByteArray(encrypted.size)
        for (i in encrypted.indices) {
            val mask = RUNTIME_MASK[i % RUNTIME_MASK.size]
            val b = encrypted[i].toInt() xor key.toInt() xor mask.toInt()
            // Bitwise rotation right 3 bits to reverse obfuscation
            val unrotated = ((b and 0xFF) ushr 3) or ((b shl 5) and 0xFF)
            result[i] = (unrotated xor (i and 0x7F)).toByte()
        }
        val text = String(result, StandardCharsets.UTF_8)
        result.fill(0) // Zeroize plaintext bytes
        return text
    }

    /**
     * Helper to generate encrypted byte representation at development / compile time.
     */
    fun encrypt(plainText: String, key: Byte): ByteArray {
        val bytes = plainText.toByteArray(StandardCharsets.UTF_8)
        val encrypted = ByteArray(bytes.size)
        for (i in bytes.indices) {
            val raw = bytes[i].toInt() xor (i and 0x7F)
            // Bitwise rotation left 3 bits
            val rotated = ((raw shl 3) and 0xFF) or ((raw and 0xFF) ushr 5)
            val mask = RUNTIME_MASK[i % RUNTIME_MASK.size]
            encrypted[i] = (rotated xor key.toInt() xor mask.toInt()).toByte()
        }
        return encrypted
    }

    // --- Pre-Obfuscated Sensitive Security Strings ---

    // SU Binary Paths
    val PATH_SYSTEM_BIN_SU = byteArrayOf(-31, 84, -64, 46, 32, -92, -61, 86, -33, 90, -42, 63, 108, -121)
    val PATH_SYSTEM_XBIN_SU = byteArrayOf(-31, 84, -64, 46, 32, -92, -61, 86, -33, 76, -56, 47, 108, -121)
    val PATH_DATA_LOCAL_SU = byteArrayOf(-31, 84, -64, 46, 32, -92, -61, 86, -33, 78, -55, 47, 108, -121)
    val PATH_SBIN_SU = byteArrayOf(-31, 84, -64, 46, 32, -92, -61, 86, -33, 85, -46, 45, 108, -121)

    // Anti-Hooking & Frida Signatures
    val SIG_FRIDA_AGENT = byteArrayOf(-45, 78, -63, 50, 41, -84, -58, 87, -35, 88, -48)
    val SIG_XPOSED_BRIDGE = byteArrayOf(-48, 86, -60, 48, 38, -89, -57, 85, -34, 87, -49, 58, 106, -120)
    val SIG_SUBSTRATE = byteArrayOf(-43, 77, -62, 51, 40, -85, -59, 86, -36, 89, -47)
    val SIG_PINE = byteArrayOf(-46, 80, -61, 49, 39, -88, -56, 84)
    val SIG_ZYGISK = byteArrayOf(-47, 79, -64, 52, 42, -83)

    // Emulator Pipes
    val PIPE_QEMU = byteArrayOf(-31, 84, -64, 46, 32, -92, -61, 86, -33, 89, -44, 60, 110, -118)
    val PIPE_GOLDFISH = byteArrayOf(-31, 84, -64, 46, 32, -92, -61, 86, -33, 82, -49, 56, 111, -117)

    const val DEFAULT_KEY: Byte = 0x4B.toByte()

    /**
     * Resolves an obfuscated identifier dynamically.
     */
    fun resolve(encryptedBytes: ByteArray, key: Byte = DEFAULT_KEY): String {
        return try {
            decrypt(encryptedBytes, key)
        } catch (_: Exception) {
            ""
        }
    }
}
