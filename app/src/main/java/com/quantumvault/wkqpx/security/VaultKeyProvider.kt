package com.quantumvault.wkqpx.security

import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * Interface defining KeyProvider abstraction.
 * Production uses AndroidKeystoreKeyProvider (strictly hardware/Keystore backed, fail-closed).
 * Unit/JVM tests use TestKeyProvider.
 */
interface VaultKeyProvider {
    fun getKey(alias: String): SecretKey?
    fun getOrCreateKey(alias: String): SecretKey
    fun createBiometricMasterKey(alias: String): SecretKey
    fun deleteKey(alias: String): Boolean
    fun destroyAllKeys(targetAliases: List<String>): Map<String, Boolean>
}
