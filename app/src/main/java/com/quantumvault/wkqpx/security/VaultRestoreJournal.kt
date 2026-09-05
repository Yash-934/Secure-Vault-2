package com.quantumvault.wkqpx.security

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class JournalCorruptedException(msg: String, cause: Throwable? = null) : SecurityException(msg, cause)

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
 * Crash-Consistent Authenticated Write-Ahead Restore Journal.
 * Guarantees single-transaction atomicity across filesystem generation directories,
 * database contents, and generation metadata with AES-256-GCM AEAD integrity.
 *
 * If a process crash occurs during restore:
 * - At PREPARED: Clean up staging directories and intent; abort.
 * - At FS_SWAPPED (DB not committed): Rollback filesystem to previous generation; abort.
 * - At DB_COMMITTED (DB committed, gen not committed): Roll-forward generation commit; complete.
 */
object VaultRestoreJournal {
    private const val TAG = "VaultRestoreJournal"
    private const val JOURNAL_FILE = "vault_restore_journal.bin"
    private const val LEGACY_JOURNAL_FILE = "vault_restore_journal.json"
    private val MAGIC_HEADER = "VLT_JRN1".toByteArray(Charsets.UTF_8) // 8 bytes
    private const val GCM_IV_SIZE = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(RestoreJournalRecord::class.java)

    fun getJournalFile(context: Context): File = File(context.filesDir, JOURNAL_FILE)

    private fun getJournalAuthKey(): SecretKey {
        return VaultKeyManager.getOrCreateKey(VaultKeyAliases.ALIAS_RESTORE_JOURNAL)
    }

    @Synchronized
    fun recordState(
        context: Context,
        record: RestoreJournalRecord
    ) {
        val target = getJournalFile(context)
        val temp = File(context.filesDir, "$JOURNAL_FILE.tmp")
        try {
            val json = adapter.toJson(record)
            val jsonBytes = json.toByteArray(Charsets.UTF_8)
            val key = getJournalAuthKey()

            val iv = ByteArray(GCM_IV_SIZE).also { java.security.SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.updateAAD(MAGIC_HEADER)

            val cipherTextWithTag = cipher.doFinal(jsonBytes)

            val crc = CRC32()
            crc.update(MAGIC_HEADER)
            crc.update(iv)
            crc.update(cipherTextWithTag)
            val computedCrc = crc.value.toInt()

            // Binary Envelope: Magic (8B) | IV (12B) | CRC32 (4B) | CipherText+GCMTag (NB)
            val totalSize = MAGIC_HEADER.size + iv.size + 4 + cipherTextWithTag.size
            val buffer = ByteBuffer.allocate(totalSize)
                .put(MAGIC_HEADER)
                .put(iv)
                .putInt(computedCrc)
                .put(cipherTextWithTag)
                .array()

            FileOutputStream(temp).use { fos ->
                fos.write(buffer)
                fos.flush()
                fos.fd.sync()
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            try { temp.delete() } catch (_: Throwable) {}
            Log.e(TAG, "Failed to persist authenticated restore journal state ${record.state}", e)
            throw e
        }
    }

    @Synchronized
    fun readJournal(context: Context): RestoreJournalRecord? {
        val file = getJournalFile(context)
        val legacyFile = File(context.filesDir, LEGACY_JOURNAL_FILE)

        if (!file.exists() && !legacyFile.exists()) return null
        if (file.exists() && file.length() == 0L) return null

        if (file.exists()) {
            return try {
                val bytes = file.readBytes()
                if (bytes.size < MAGIC_HEADER.size + GCM_IV_SIZE + 4 + 16) {
                    throw JournalCorruptedException(
                        "Restore journal file truncated or corrupted: size ${bytes.size} bytes. RECOVERY_REQUIRED."
                    )
                }
                val magic = bytes.copyOfRange(0, MAGIC_HEADER.size)
                if (!magic.contentEquals(MAGIC_HEADER)) {
                    throw JournalCorruptedException(
                        "Restore journal magic mismatch in '$JOURNAL_FILE'. Tamper detected. RECOVERY_REQUIRED."
                    )
                }
                val iv = bytes.copyOfRange(MAGIC_HEADER.size, MAGIC_HEADER.size + GCM_IV_SIZE)
                val recordedCrc = ByteBuffer.wrap(bytes, MAGIC_HEADER.size + GCM_IV_SIZE, 4).int
                val cipherTextWithTag = bytes.copyOfRange(MAGIC_HEADER.size + GCM_IV_SIZE + 4, bytes.size)

                val crc = CRC32()
                crc.update(magic)
                crc.update(iv)
                crc.update(cipherTextWithTag)
                if (crc.value.toInt() != recordedCrc) {
                    throw JournalCorruptedException(
                        "Restore journal CRC integrity mismatch. Recorded: $recordedCrc, computed: ${crc.value.toInt()}. RECOVERY_REQUIRED."
                    )
                }

                val key = getJournalAuthKey()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                cipher.updateAAD(MAGIC_HEADER)
                val plainBytes = cipher.doFinal(cipherTextWithTag)
                val json = plainBytes.toString(Charsets.UTF_8)
                val record = adapter.fromJson(json)
                    ?: throw JournalCorruptedException("Failed to parse restore journal JSON payload. RECOVERY_REQUIRED.")
                record
            } catch (e: JournalCorruptedException) {
                throw e
            } catch (e: Exception) {
                throw JournalCorruptedException("Restore journal AEAD decryption/verification failed. RECOVERY_REQUIRED.", e)
            }
        }

        if (legacyFile.exists() && legacyFile.length() > 0L) {
            return try {
                val json = legacyFile.readText(Charsets.UTF_8)
                val record = adapter.fromJson(json)
                    ?: throw JournalCorruptedException("Corrupt legacy restore journal. RECOVERY_REQUIRED.")
                // Upgrade to authenticated journal
                recordState(context, record)
                legacyFile.delete()
                record
            } catch (e: JournalCorruptedException) {
                throw e
            } catch (e: Exception) {
                throw JournalCorruptedException("Failed to read legacy restore journal. RECOVERY_REQUIRED.", e)
            }
        }

        return null
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
            val legacyFile = File(context.filesDir, LEGACY_JOURNAL_FILE)
            if (legacyFile.exists()) legacyFile.delete()
            val legacyTemp = File(context.filesDir, "$LEGACY_JOURNAL_FILE.tmp")
            if (legacyTemp.exists()) legacyTemp.delete()
        } catch (_: Exception) {}
    }
}
