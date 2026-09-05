package com.quantumvault.wkqpx.security

enum class AuditCheckStatus {
    PASS,
    FAIL,
    UNKNOWN,
    NOT_APPLICABLE
}

data class SecurityCheckItem(
    val name: String,
    val category: String,
    val passed: Boolean,
    val status: AuditCheckStatus = if (passed) AuditCheckStatus.PASS else AuditCheckStatus.FAIL,
    val weight: Int,
    val description: String,
    val terminalOutput: String = "",
    val evidence: String = ""
)

/**
 * Enhanced Security Integrity Audit Result with weighted scoring and comprehensive threat telemetry.
 *
 * @param status Overall audit status ("PASS" or "FAIL").
 * @param score Weighted security score from 0 to 100.
 * @param securityGrade Formatted security tier (e.g., "HARDENED ENCLAVE").
 * @param checkResults Map of individual security diagnostic checks to boolean pass/fail status.
 * @param checkItems Detailed list of security check items with categories and weights.
 * @param timestamp System time in milliseconds when the audit was conducted.
 */
data class AuditResult(
    val status: String,
    val score: Int,
    val scoreOutOfTen: Double = score / 10.0,
    val securityGrade: String,
    val checkResults: Map<String, Boolean>,
    val checkItems: List<SecurityCheckItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

