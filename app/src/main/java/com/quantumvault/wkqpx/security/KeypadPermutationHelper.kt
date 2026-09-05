package com.quantumvault.wkqpx.security

import java.security.SecureRandom

/**
 * Shared Cryptographically Secure Keypad Permutation Helper.
 * Used by both LockScreen UI and SecurityAuditEngine to ensure deterministic,
 * testable scrambled pinpad generation.
 */
object KeypadPermutationHelper {
    fun generateScrambledDigits(): List<String> {
        val digits = (0..9).map { it.toString() }.toMutableList()
        digits.shuffle(SecureRandom())
        return digits
    }
}
