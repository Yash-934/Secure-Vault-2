package com.quantumvault.wkqpx.test

import com.quantumvault.wkqpx.security.VaultKeyProvider
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * In-memory test key provider for unit and Robolectric tests.
 * Never used in release production builds.
 */
class TestKeyProvider : VaultKeyProvider {
    private val memoryKeys = ConcurrentHashMap<String, SecretKey>()

    override fun getKey(alias: String): SecretKey? {
        return memoryKeys[alias]
    }

    override fun getOrCreateKey(alias: String): SecretKey {
        return memoryKeys.getOrPut(alias) {
            val kg = KeyGenerator.getInstance("AES")
            kg.init(256, SecureRandom())
            kg.generateKey()
        }
    }

    override fun createBiometricMasterKey(alias: String): SecretKey {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256, SecureRandom())
        val key = kg.generateKey()
        memoryKeys[alias] = key
        return key
    }

    override fun deleteKey(alias: String): Boolean {
        memoryKeys.remove(alias)
        return true
    }

    override fun destroyAllKeys(targetAliases: List<String>): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        targetAliases.forEach {
            memoryKeys.remove(it)
            results[it] = true
        }
        memoryKeys.clear()
        return results
    }
}
