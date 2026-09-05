package com.quantumvault.wkqpx.security

import android.content.Context
import com.quantumvault.wkqpx.data.VaultItem
import com.quantumvault.wkqpx.data.VaultRepository
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class SecureZipEntry(
    val name: String,
    val uncompressedSize: Long,
    val isDirectory: Boolean
)

object SecureZipManager {

    /**
     * Reads an encrypted ZIP archive on-the-fly and lists all contained entries
     * without extracting or writing anything to disk.
     */
    fun listZipEntries(encryptedZipFile: File): List<SecureZipEntry> {
        val entries = mutableListOf<SecureZipEntry>()
        if (!encryptedZipFile.exists()) return entries

        try {
            FileInputStream(encryptedZipFile).use { fis ->
                val decryptedBytes = CryptoManager.decryptStreamToByteArray(fis)
                ByteArrayInputStream(decryptedBytes).use { decryptedStream ->
                    ZipInputStream(decryptedStream).use { zipStream ->
                        var entry: ZipEntry? = zipStream.nextEntry
                        while (entry != null) {
                            entries.add(
                                SecureZipEntry(
                                    name = entry.name,
                                    uncompressedSize = if (entry.size >= 0) entry.size else 0L,
                                    isDirectory = entry.isDirectory
                                )
                            )
                            zipStream.closeEntry()
                            entry = zipStream.nextEntry
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Security", "Exception in listZipEntries", e)
        }
        return entries
    }

    /**
     * Extract a single zip entry directly into internal encrypted vault storage
     * without writing any unencrypted data to disk.
     */
    suspend fun extractEntryToVault(
        context: Context,
        encryptedZipFile: File,
        entryName: String,
        vaultRepository: VaultRepository
    ): Result<VaultItem> {
        return try {
            if (!encryptedZipFile.exists()) {
                return Result.failure(IllegalArgumentException("Zip file does not exist."))
            }

            var extractedItem: VaultItem? = null

            FileInputStream(encryptedZipFile).use { fis ->
                val decryptedBytes = CryptoManager.decryptStreamToByteArray(fis)
                ByteArrayInputStream(decryptedBytes).use { decryptedStream ->
                    ZipInputStream(decryptedStream).use { zipStream ->
                        var entry: ZipEntry? = zipStream.nextEntry
                        while (entry != null) {
                            if (entry.name == entryName && !entry.isDirectory) {
                                val cleanName = File(entry.name).name
                                val mimeType = inferMimeType(cleanName)

                                // Wrap zipStream in NonClosingInputStream to prevent CryptoManager closing zipStream
                                val entryInputStream = object : InputStream() {
                                    override fun read(): Int = zipStream.read()
                                    override fun read(b: ByteArray, off: Int, len: Int): Int = zipStream.read(b, off, len)
                                }

                                extractedItem = vaultRepository.importStreamToVault(
                                    context = context,
                                    inputStream = entryInputStream,
                                    originalName = cleanName,
                                    mimeType = mimeType,
                                    sizeBytes = if (entry.size > 0) entry.size else 0L
                                )
                                break
                            }
                            zipStream.closeEntry()
                            entry = zipStream.nextEntry
                        }
                    }
                }
            }

            if (extractedItem != null) {
                Result.success(extractedItem!!)
            } else {
                Result.failure(IllegalArgumentException("Entry '$entryName' not found in zip archive."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun inferMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif" -> "image/jpeg"
            "mp4", "mkv", "avi", "mov" -> "video/mp4"
            "mp3", "wav", "m4a" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "txt", "csv", "json", "log" -> "text/plain"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
