package com.quantumvault.wkqpx.security

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quantumvault.wkqpx.data.local.SettingsDataStore
import kotlinx.coroutines.flow.first

/**
 * Dead Man's Switch Periodic Background Worker.
 * Monitors last login timestamp; triggers nuclear self-destruct if vault remains inactive beyond configured threshold.
 */
class DeadManSwitchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val settingsDataStore = SettingsDataStore(applicationContext)
            val settings = settingsDataStore.settingsFlow.first()

            if (settings.isDeadManSwitchEnabled) {
                val deadManThresholdMs = settings.deadManDays * 24L * 60L * 60L * 1000L
                val timeSinceLastLogin = System.currentTimeMillis() - settings.lastLoginTimestamp

                if (timeSinceLastLogin >= deadManThresholdMs) {
                    SelfDestructManager.executeNuclearSelfDestruct(applicationContext)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
