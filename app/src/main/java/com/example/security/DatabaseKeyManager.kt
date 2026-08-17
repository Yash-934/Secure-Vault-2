package com.example.security

import android.content.Context
import java.security.SecureRandom

object DatabaseKeyManager {
    private const val PREF_NAME = "DBKeyPrefs"
    private const val PREF_KEY_HEX = "persistent_db_passphrase_hex"

    fun getDatabasePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var hex = prefs.getString(PREF_KEY_HEX, null)
        if (hex.isNullOrEmpty()) {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            hex = bytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(PREF_KEY_HEX, hex).apply()
        }
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

