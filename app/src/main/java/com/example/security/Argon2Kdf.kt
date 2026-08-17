package com.example.security

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Military-Grade Argon2id Key Derivation Function (KDF).
 * 
 * Configured with extreme memory-hardness (64 MiB RAM, 3 iterations) to resist
 * GPU, FPGA, and ASIC brute-force password cracking attacks.
 */
object Argon2Kdf {

    const val DEFAULT_MEMORY_KIB = 64 * 1024 // 64 MiB
    const val DEFAULT_ITERATIONS = 3
    const val DEFAULT_PARALLELISM = 1
    const val SALT_LENGTH_BYTES = 16
    const val KEY_LENGTH_BYTES = 32 // 256 bits

    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically secure 16-byte random salt.
     */
    fun generateSalt(size: Int = SALT_LENGTH_BYTES): ByteArray {
        val salt = ByteArray(size)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Derives a 256-bit AES SecretKey from a password using Argon2id.
     * Memory-hardness guarantees maximum resistance against offline dictionary attacks.
     */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        memoryKb: Int = DEFAULT_MEMORY_KIB,
        iterations: Int = DEFAULT_ITERATIONS,
        parallelism: Int = DEFAULT_PARALLELISM,
        keyLengthBytes: Int = KEY_LENGTH_BYTES
    ): SecretKey {
        val passwordBytes = CharArrayToByteArray(password)
        val outputKeyBytes = ByteArray(keyLengthBytes)

        try {
            val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(memoryKb)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(salt)

            val generator = Argon2BytesGenerator()
            generator.init(builder.build())
            generator.generateBytes(passwordBytes, outputKeyBytes, 0, outputKeyBytes.size)

            return SecretKeySpec(outputKeyBytes, "AES")
        } finally {
            // Zeroize sensitive plaintext password bytes from memory
            passwordBytes.fill(0)
        }
    }

    /**
     * Helper to safely convert CharArray to UTF-8 ByteArray with zeroization capability.
     */
    private fun CharArrayToByteArray(chars: CharArray): ByteArray {
        val charBuffer = java.nio.CharBuffer.wrap(chars)
        val byteBuffer = java.nio.charset.StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        return bytes
    }
}
