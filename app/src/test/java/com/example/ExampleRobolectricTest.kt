package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.Argon2Kdf
import com.example.security.DexProtectionEngine
import com.example.security.NativeIntegrityVerifier
import com.example.security.ObfuscatedStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Quantum Vault", appName)
  }

  @Test
  fun `verify obfuscated strings decryption`() {
    val plain = "SYSTEM_SECURITY_SALT_2026"
    val key: Byte = 0x55
    val encrypted = ObfuscatedStrings.encrypt(plain, key)
    val decrypted = ObfuscatedStrings.decrypt(encrypted, key)
    assertEquals(plain, decrypted)
  }

  @Test
  fun `verify argon2id kdf derivation`() {
    val pwd = "MilitaryGradeMasterPassword2026!".toCharArray()
    val salt = ByteArray(16) { 0x3C.toByte() }
    val secretKey = Argon2Kdf.deriveKey(pwd, salt, memoryKb = 1024, iterations = 1)
    assertNotNull(secretKey)
    assertEquals(32, secretKey.encoded.size)
  }

  @Test
  fun `verify native integrity state machine`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val code = NativeIntegrityVerifier.executeObfuscatedSecurityCheck(context)
    assertTrue(code != -1)
  }
}

