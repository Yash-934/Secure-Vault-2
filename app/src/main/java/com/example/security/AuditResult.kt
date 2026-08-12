package com.example.security

/**
 * Data class representing the result of a Security Integrity Audit scan.
 *
 * @param status Overall audit status ("PASS" or "FAIL").
 * @param checkResults Map of individual security diagnostic checks to boolean pass/fail status.
 * @param timestamp System time in milliseconds when the audit was conducted.
 */
data class AuditResult(
    val status: String,
    val checkResults: Map<String, Boolean>,
    val timestamp: Long = System.currentTimeMillis()
)
