package com.quantumvault.wkqpx.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IntruderLogDao {
    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<IntruderLog>>

    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsSync(): List<IntruderLog>

    @Insert
    suspend fun insertLog(log: IntruderLog)

    @Query("DELETE FROM intruder_logs")
    suspend fun clearLogs()
}
