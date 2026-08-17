package com.example.security

data class SecurityCheckItem(
    val name: String,
    val category: String,
    val passed: Boolean,
    val weight: Int,
    val description: String
)

/**
 * Enhanced Security Integrity Audit Result with weighted scoring and comprehensive threat telemetry.
 *
 * @param status Overall audit status ("PASS" or "FAIL").
 * @param score Weighted security score from 0 to 100.
 * @param securityGrade Formatted security tier (e.g., "9.9/10 - MILITARY HARDENED").
 * @param checkResults Map of individual security diagnostic checks to boolean pass/fail status.
 * @param checkItems Detailed list of security check items with categories and weights.
 * @param timestamp System time in milliseconds when the audit was conducted.
 */
data class AuditResult(
    val status: String,
    val score: Int,
    val securityGrade: String,
    val checkResults: Map<String, Boolean>,
    val checkItems: List<SecurityCheckItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
