package com.quantumvault.wkqpx.security

import android.util.Base64
import android.util.Log
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed class PasswordDecryptResult {
    data class Success(val plaintext: String) : PasswordDecryptResult()
    object WrongKey : PasswordDecryptResult()
    object CorruptCiphertext : PasswordDecryptResult()
    data class LegacyRecordRequiresMigration(val legacyPlaintext: String) : PasswordDecryptResult()
    object KeyUnavailable : PasswordDecryptResult()
    object VaultLocked : PasswordDecryptResult()
}

object PasswordCryptoHelper {
    private const val TAG = "PasswordCryptoHelper"
    const val FORMAT_V2_PREFIX = "QVPM2:"

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    private fun getKey(): SecretKey = VaultKeyManager.getPasswordMasterKey()

    fun encryptText(plainText: String): String {
        if (plainText.isEmpty()) return ""
        try {
            val key = getKey()
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = ByteArray(IV_LENGTH_BYTE).also { SecureRandom().nextBytes(it) }
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = iv + encryptedBytes
            return FORMAT_V2_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw SecurityException("Failed to encrypt data", e)
        }
    }

    /**
     * Decrypts an encrypted password or notes blob returning a typed result.
     * Never returns a false or corrupted password string on failure.
     */
    fun decryptPassword(cipherBlob: String): PasswordDecryptResult {
        if (cipherBlob.isEmpty()) return PasswordDecryptResult.Success("")
        if (!VaultKeyManager.isSessionAuthorized()) return PasswordDecryptResult.VaultLocked

        val key = try {
            getKey()
        } catch (e: Exception) {
            return PasswordDecryptResult.KeyUnavailable
        }

        // Format 1: Current V2 Format with explicit prefix
        if (cipherBlob.startsWith(FORMAT_V2_PREFIX)) {
            val cleanBlob = cipherBlob.removePrefix(FORMAT_V2_PREFIX)
            val combined = try {
                Base64.decode(cleanBlob, Base64.NO_WRAP)
            } catch (_: Exception) {
                return PasswordDecryptResult.CorruptCiphertext
            }
            if (combined.size < IV_LENGTH_BYTE + 16) return PasswordDecryptResult.CorruptCiphertext

            return try {
                val iv = combined.copyOfRange(0, IV_LENGTH_BYTE)
                val cipherBytes = combined.copyOfRange(IV_LENGTH_BYTE, combined.size)
                val cipher = Cipher.getInstance(ALGORITHM)
                val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
                cipher.init(Cipher.DECRYPT_MODE, key, spec)
                val decryptedBytes = cipher.doFinal(cipherBytes)
                val text = String(decryptedBytes, Charsets.UTF_8)
                decryptedBytes.fill(0)
                PasswordDecryptResult.Success(text)
            } catch (e: AEADBadTagException) {
                PasswordDecryptResult.WrongKey
            } catch (e: Exception) {
                PasswordDecryptResult.CorruptCiphertext
            }
        }

        // Format 2: Un-prefixed ciphertext (could be unmigrated V2 or legacy)
        // First try decrypting with current VRK key
        val unPrefixedBytes = try {
            Base64.decode(cipherBlob, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }

        if (unPrefixedBytes != null && unPrefixedBytes.size >= IV_LENGTH_BYTE + 16) {
            try {
                val iv = unPrefixedBytes.copyOfRange(0, IV_LENGTH_BYTE)
                val cipherBytes = unPrefixedBytes.copyOfRange(IV_LENGTH_BYTE, unPrefixedBytes.size)
                val cipher = Cipher.getInstance(ALGORITHM)
                val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
                cipher.init(Cipher.DECRYPT_MODE, key, spec)
                val decryptedBytes = cipher.doFinal(cipherBytes)
                val text = String(decryptedBytes, Charsets.UTF_8)
                decryptedBytes.fill(0)
                return PasswordDecryptResult.Success(text)
            } catch (_: AEADBadTagException) {
                // VRK key did not match, check legacy migrator below
            } catch (_: Exception) {
                // Ignore and try legacy
            }
        }

        // Format 3: Check legacy format migration
        if (LegacyPasswordMigrator.isLegacyBlob(cipherBlob)) {
            val legacyPlaintext = LegacyPasswordMigrator.decryptLegacyRecord(cipherBlob)
            if (legacyPlaintext != null) {
                return PasswordDecryptResult.LegacyRecordRequiresMigration(legacyPlaintext)
            }
        }

        return PasswordDecryptResult.CorruptCiphertext
    }

    fun decryptText(cipherBlob: String): String {
        return when (val result = decryptPassword(cipherBlob)) {
            is PasswordDecryptResult.Success -> result.plaintext
            is PasswordDecryptResult.LegacyRecordRequiresMigration -> result.legacyPlaintext
            is PasswordDecryptResult.VaultLocked -> throw SecurityException("Vault is locked")
            is PasswordDecryptResult.WrongKey -> throw SecurityException("Wrong decryption key")
            is PasswordDecryptResult.CorruptCiphertext -> throw SecurityException("Vault password record is corrupted.")
            is PasswordDecryptResult.KeyUnavailable -> throw SecurityException("Cryptographic key unavailable")
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
