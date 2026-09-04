package com.quantumvault.wkqpx.security

import java.util.Arrays

/**
 * Extension functions for secure memory zeroing.
 * Bypasses Garbage Collection delays by explicitly overwriting sensitive byte/char arrays
 * with zeros (0x00 / '\u0000') immediately after crypto/authentication operations.
 */

/**
 * Explicitly overwrites every element in this ByteArray with 0x00.
 */
fun ByteArray.zeroize() {
    Arrays.fill(this, 0.toByte())
}

/**
 * Explicitly overwrites every element in this CharArray with '\u0000'.
 */
fun CharArray.zeroize() {
    Arrays.fill(this, '\u0000')
}

/**
 * Executes [block] with this sensitive [ByteArray] and guarantees that the array is zeroized
 * in a finally block regardless of whether the execution completes normally or throws an exception.
 */
inline fun <R> ByteArray.useAndZeroize(block: (ByteArray) -> R): R {
    try {
        return block(this)
    } finally {
        this.zeroize()
    }
}

/**
 * Executes [block] with this sensitive [CharArray] and guarantees that the array is zeroized
 * in a finally block regardless of whether the execution completes normally or throws an exception.
 */
inline fun <R> CharArray.useAndZeroize(block: (CharArray) -> R): R {
    try {
        return block(this)
    } finally {
        this.zeroize()
    }
}
