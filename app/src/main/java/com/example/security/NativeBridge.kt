package com.example.security

import java.nio.ByteBuffer

object NativeBridge {
    init {
        try {
            System.loadLibrary("native-lib")
        } catch (e: UnsatisfiedLinkError) {
            // Graceful fallback if native library is missing or architecture is unsupported
            e.printStackTrace()
        }
    }

    external fun mlockBuffer(buffer: ByteBuffer, size: Int): Boolean
    external fun munlockBuffer(buffer: ByteBuffer, size: Int): Boolean
    external fun getSecretString(id: Int): String
    external fun runObfuscatedCheck(): Int

    fun safeMlock(buffer: ByteBuffer?): Boolean {
        if (buffer == null || !buffer.isDirect) return false
        return try {
            mlockBuffer(buffer, buffer.capacity())
        } catch (e: Throwable) {
            false
        }
    }

    fun safeMunlock(buffer: ByteBuffer?): Boolean {
        if (buffer == null || !buffer.isDirect) return false
        return try {
            munlockBuffer(buffer, buffer.capacity())
        } catch (e: Throwable) {
            false
        }
    }
}