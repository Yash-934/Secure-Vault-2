package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object PasswordCryptoHelper {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "QuantumVaultPasswordMasterKey"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    private val keyStore: KeyStore? = try {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
    } catch (_: Exception) {
        null
    }

    private var fallbackJvmKey: SecretKey? = null

    private fun getOrCreateKey(): SecretKey {
        if (keyStore != null) {
            try {
                val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (existingKey != null) {
                    return existingKey.secretKey
                }

                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER
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
            } catch (_: Exception) {
                // Fallback for JVM test runner environments
            }
        }

        return fallbackJvmKey ?: synchronized(this) {
            fallbackJvmKey ?: run {
                val kg = KeyGenerator.getInstance("AES")
                kg.init(256)
                val k = kg.generateKey()
                fallbackJvmKey = k
                k
            }
        }
    }

    fun encryptText(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = ByteArray(IV_LENGTH_BYTE)
            SecureRandom().nextBytes(iv)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = iv + encryptedBytes
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    fun decryptText(cipherBlob: String): String {
        if (cipherBlob.isEmpty()) return ""
        return try {
            val combined = Base64.decode(cipherBlob, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH_BYTE + 16) return ""
            val iv = combined.copyOfRange(0, IV_LENGTH_BYTE)
            val cipherBytes = combined.copyOfRange(IV_LENGTH_BYTE, combined.size)
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val decryptedBytes = cipher.doFinal(cipherBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun generatePassword(
        length: Int = 16,
        includeUpper: Boolean = true,
        includeLower: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        val upperChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowerChars = "abcdefghijklmnopqrstuvwxyz"
        val digitChars = "0123456789"
        val symbolChars = "!@#$%^&*()_+-=[]{}|;:,.<>?"

        val pool = StringBuilder()
        val guaranteed = mutableListOf<Char>()
        val random = SecureRandom()

        if (includeUpper) {
            pool.append(upperChars)
            guaranteed.add(upperChars[random.nextInt(upperChars.length)])
        }
        if (includeLower) {
            pool.append(lowerChars)
            guaranteed.add(lowerChars[random.nextInt(lowerChars.length)])
        }
        if (includeDigits) {
            pool.append(digitChars)
            guaranteed.add(digitChars[random.nextInt(digitChars.length)])
        }
        if (includeSymbols) {
            pool.append(symbolChars)
            guaranteed.add(symbolChars[random.nextInt(symbolChars.length)])
        }

        if (pool.isEmpty()) {
            pool.append(lowerChars).append(digitChars)
        }

        val poolStr = pool.toString()
        val result = ArrayList<Char>(guaranteed)
        val remaining = (length - guaranteed.size).coerceAtLeast(0)

        for (i in 0 until remaining) {
            result.add(poolStr[random.nextInt(poolStr.length)])
        }

        result.shuffle(random)
        return result.joinToString("")
    }

    fun evaluateStrength(password: String): Pair<Int, String> {
        if (password.isEmpty()) return 0 to "EMPTY"
        var score = 0

        // Length score
        score += when {
            password.length >= 16 -> 40
            password.length >= 12 -> 30
            password.length >= 8 -> 15
            else -> 5
        }

        // Diversity score
        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        if (hasLower) score += 15
        if (hasUpper) score += 15
        if (hasDigit) score += 15
        if (hasSymbol) score += 15

        val finalScore = score.coerceIn(0, 100)
        val label = when {
            finalScore >= 80 -> "VERY STRONG"
            finalScore >= 60 -> "STRONG"
            finalScore >= 40 -> "MODERATE"
            else -> "WEAK"
        }

        return finalScore to label
    }
}
