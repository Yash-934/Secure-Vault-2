package com.quantumvault.wkqpx.security

import android.util.Log
import java.nio.ByteBuffer

object NativeBridge {
    private const val TAG = "NativeBridge"

    init {
        try {
            System.loadLibrary("native-lib")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library 'native-lib' not found or unsupported ABI", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while loading native library", e)
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
        } catch (e: UnsatisfiedLinkError) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun safeMunlock(buffer: ByteBuffer?): Boolean {
        if (buffer == null || !buffer.isDirect) return false
        return try {
            munlockBuffer(buffer, buffer.capacity())
        } catch (e: UnsatisfiedLinkError) {
            false
        } catch (e: Exception) {
            false
        }
    }
}