package com.quantumvault.wkqpx.security

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class RestoreJournalState {
    PREPARED,
    FS_SWAPPED,
    DB_COMMITTED,
    COMPLETED
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class RestoreJournalRecord(
    val state: String,
    val isDecoy: Boolean,
    val nextGen: Long,
    val intentFileName: String,
    val vaultDirPath: String,
    val backupPrevGenDirPath: String,
    val nextGenDirPath: String,
    val isMergeMode: Boolean = false,
    val replacedFilesBackupDirPath: String? = null,
    val newlyAddedFilesPaths: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Crash-Consistent Write-Ahead Restore Journal.
 * Guarantees single-transaction atomicity across filesystem generation directories,
 * database contents, and generation metadata.
 *
 * If a process crash occurs during restore:
 * - At PREPARED: Clean up staging directories and intent; abort.
 * - At FS_SWAPPED (DB not committed): Rollback filesystem to previous generation; abort.
 * - At DB_COMMITTED (DB committed, gen not committed): Roll-forward generation commit; complete.
 */
object VaultRestoreJournal {
    private const val TAG = "VaultRestoreJournal"
    private const val JOURNAL_FILE = "vault_restore_journal.json"
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(RestoreJournalRecord::class.java)

    fun getJournalFile(context: Context): File = File(context.filesDir, JOURNAL_FILE)

    @Synchronized
    fun recordState(
        context: Context,
        record: RestoreJournalRecord
    ) {
        val target = getJournalFile(context)
        val temp = File(context.filesDir, "$JOURNAL_FILE.tmp")
        try {
            val json = adapter.toJson(record)
            FileOutputStream(temp).use { fos ->
                fos.write(json.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            try { temp.delete() } catch (_: Throwable) {}
            Log.e(TAG, "Failed to persist restore journal state ${record.state}", e)
            throw e
        }
    }

    @Synchronized
    fun readJournal(context: Context): RestoreJournalRecord? {
        val file = getJournalFile(context)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val json = file.readText(Charsets.UTF_8)
            adapter.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse restore journal", e)
            null
        }
    }

    @Synchronized
    fun hasPendingRestore(context: Context): Boolean {
        return readJournal(context) != null
    }

    @Synchronized
    fun clearJournal(context: Context) {
        try {
            val file = getJournalFile(context)
            if (file.exists()) file.delete()
            val temp = File(context.filesDir, "$JOURNAL_FILE.tmp")
            if (temp.exists()) temp.delete()
        } catch (_: Exception) {}
    }
}
