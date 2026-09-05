package com.quantumvault.wkqpx.data

import android.content.Context
import com.quantumvault.wkqpx.security.DatabaseKeyManager
import java.io.File

/**
 * Single authoritative source of truth for vault initialization, freshness,
 * and recovery state across the entire application.
 */
object VaultStateRepository {
    private const val VRK_PIN_WRAP_REAL = "vrk_pin_wrap.bin"
    private const val VRK_PIN_WRAP_DECOY = "decoy_vrk_pin_wrap.bin"
    private const val LEGACY_VRK_WRAP = "vrk_wrap.bin"

    private const val SENTINEL_REAL = "vault_sentinel.bin"
    private const val SENTINEL_DECOY = "decoy_vault_sentinel.bin"

    private const val DB_REAL = "secure_vault_db"
    private const val DB_DECOY = "secure_vault_decoy_db"
    private const val DB_LEGACY = "vault_database"

    data class VaultArtifactStatus(
        val hasRealVrkWrap: Boolean,
        val hasDecoyVrkWrap: Boolean,
        val hasLegacyVrkWrap: Boolean,
        val hasRealSentinel: Boolean,
        val hasDecoySentinel: Boolean,
        val hasRealDbw2: Boolean,
        val hasDecoyDbw2: Boolean,
        val hasRealDb: Boolean,
        val hasDecoyDb: Boolean,
        val hasLegacyDb: Boolean,
        val hasLegacyDbPrefs: Boolean
    ) {
        val hasAnyRealArtifact: Boolean
            get() = hasRealVrkWrap || hasLegacyVrkWrap || hasRealSentinel || hasRealDbw2 || hasRealDb || hasLegacyDb || hasLegacyDbPrefs

        val hasAnyDecoyArtifact: Boolean
            get() = hasDecoyVrkWrap || hasDecoySentinel || hasDecoyDbw2 || hasDecoyDb

        val isGenuinelyFresh: Boolean
            get() = !hasAnyRealArtifact && !hasAnyDecoyArtifact

        val isCorruptOrPartial: Boolean
            get() {
                // If some critical artifacts exist but mandatory counterparts are missing
                val realCount = listOf(hasRealVrkWrap || hasLegacyVrkWrap, hasRealSentinel, hasRealDb || hasRealDbw2).count { it }
                return realCount in 1..2
            }
    }

    fun inspectArtifacts(context: Context): VaultArtifactStatus {
        val filesDir = context.filesDir
        val hasRealVrkWrap = File(filesDir, VRK_PIN_WRAP_REAL).exists()
        val hasDecoyVrkWrap = File(filesDir, VRK_PIN_WRAP_DECOY).exists()
        val hasLegacyVrkWrap = File(filesDir, LEGACY_VRK_WRAP).exists()

        val hasRealSentinel = File(filesDir, SENTINEL_REAL).exists()
        val hasDecoySentinel = File(filesDir, SENTINEL_DECOY).exists()

        val hasRealDbw2 = File(filesDir, DatabaseKeyManager.DBW2_FILE_REAL).exists()
        val hasDecoyDbw2 = File(filesDir, DatabaseKeyManager.DBW2_FILE_DECOY).exists()

        val hasRealDb = context.getDatabasePath(DB_REAL).exists()
        val hasDecoyDb = context.getDatabasePath(DB_DECOY).exists()
        val hasLegacyDb = context.getDatabasePath(DB_LEGACY).exists()

        val dbPrefs = context.getSharedPreferences("DBKeyPrefs", Context.MODE_PRIVATE)
        val hasLegacyDbPrefs = dbPrefs.contains("encrypted_db_passphrase_b64") || dbPrefs.contains("encrypted_db_passphrase_real_b64")

        return VaultArtifactStatus(
            hasRealVrkWrap = hasRealVrkWrap,
            hasDecoyVrkWrap = hasDecoyVrkWrap,
            hasLegacyVrkWrap = hasLegacyVrkWrap,
            hasRealSentinel = hasRealSentinel,
            hasDecoySentinel = hasDecoySentinel,
            hasRealDbw2 = hasRealDbw2,
            hasDecoyDbw2 = hasDecoyDbw2,
            hasRealDb = hasRealDb,
            hasDecoyDb = hasDecoyDb,
            hasLegacyDb = hasLegacyDb,
            hasLegacyDbPrefs = hasLegacyDbPrefs
        )
    }

    fun isVaultGenuinelyFresh(context: Context): Boolean {
        return inspectArtifacts(context).isGenuinelyFresh
    }

    fun isRealVaultArtifactPresent(context: Context): Boolean {
        return inspectArtifacts(context).hasAnyRealArtifact
    }

    fun isDecoyVaultArtifactPresent(context: Context): Boolean {
        return inspectArtifacts(context).hasAnyDecoyArtifact
    }
}
