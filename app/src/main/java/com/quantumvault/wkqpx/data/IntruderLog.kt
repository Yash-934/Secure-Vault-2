package com.quantumvault.wkqpx.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intruder_logs")
data class IntruderLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val attemptType: String, // "PIN_FAILED", "BIOMETRIC_FAILED", "DECOY_TRIGGERED", "KILL_PIN_TRIGGERED"
    val details: String = "Unauthorized access attempt recorded",
    val imagePath: String? = null
)
