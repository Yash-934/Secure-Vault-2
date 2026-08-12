package com.example.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Result data holder for import operations, carrying optional delete IntentSender for Android 10+
 */
data class ImportResult(
    val vaultItem: VaultItem,
    val originalDeleted: Boolean,
    val deleteIntentSender: android.content.IntentSender? = null
)

class VaultRepository(private val vaultDao: VaultDao, private val vaultDirName: String = "vault") {

    val allVaultItems: Flow<List<VaultItem>> = vaultDao.getAllVaultItems()
    val photos: Flow<List<VaultItem>> = vaultDao.getPhotos()
    val videos: Flow<List<VaultItem>> = vaultDao.getVideos()
    val documents: Flow<List<VaultItem>> = vaultDao.getDocuments()

    val allFolders: Flow<List<VaultFolder>> = vaultDao.getAllFolders()

    suspend fun createFolder(name: String, iconType: String = "FOLDER") = withContext(Dispatchers.IO) {
        vaultDao.insertFolder(VaultFolder(name = name, iconType = iconType))
    }

    suspend fun deleteFolder(name: String) = withContext(Dispatchers.IO) {
        vaultDao.resetItemsInFolderToRoot(name)
        vaultDao.deleteFolder(name)
    }

    suspend fun moveItemToFolder(itemId: Long, destinationFolder: String) = withContext(Dispatchers.IO) {
        vaultDao.updateItemFolder(itemId, destinationFolder)
    }

    suspend fun copyItemToFolder(context: Context, item: VaultItem, destinationFolder: String): VaultItem? = withContext(Dispatchers.IO) {
        try {
            val vaultDir = File(context.filesDir, vaultDirName)
            val sourceEncryptedFile = File(vaultDir, item.encryptedFileName)
            if (!sourceEncryptedFile.exists()) return@withContext null

            val newEncryptedFileName = "enc_${UUID.randomUUID()}.bin"
            val targetEncryptedFile = File(vaultDir, newEncryptedFileName)

            sourceEncryptedFile.copyTo(targetEncryptedFile, overwrite = true)

            val newItem = VaultItem(
                originalName = "Copy_${item.originalName}",
                encryptedFileName = newEncryptedFileName,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                isVideo = item.isVideo,
                folderName = destinationFolder
            )

            val generatedId = vaultDao.insertVaultItem(newItem)
            newItem.copy(id = generatedId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getItemsForFolderAndTab(folderName: String, tab: com.example.ui.VaultFilterTab): Flow<List<VaultItem>> {
        return if (folderName == "ALL" || folderName.isEmpty()) {
            when (tab) {
                com.example.ui.VaultFilterTab.ALL -> vaultDao.getAllVaultItems()
                com.example.ui.VaultFilterTab.PHOTOS -> vaultDao.getPhotos()
                com.example.ui.VaultFilterTab.VIDEOS -> vaultDao.getVideos()
                com.example.ui.VaultFilterTab.DOCUMENTS -> vaultDao.getDocuments()
            }
        } else {
            when (tab) {
                com.example.ui.VaultFilterTab.ALL -> vaultDao.getItemsByFolder(folderName)
                com.example.ui.VaultFilterTab.PHOTOS -> vaultDao.getPhotosByFolder(folderName)
                com.example.ui.VaultFilterTab.VIDEOS -> vaultDao.getVideosByFolder(folderName)
                com.example.ui.VaultFilterTab.DOCUMENTS -> vaultDao.getDocumentsByFolder(folderName)
            }
        }
    }

    /**
     * Encrypts the user-selected file from public gallery and stores it in app-private storage (filesDir/vault/).
     * Option to delete original source Uri using MediaStore APIs.
     */
    suspend fun encryptAndImportFile(
        context: Context,
        sourceUri: Uri,
        deleteOriginal: Boolean,
        targetFolder: String = "Root"
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver

            // 1. Resolve file metadata (name, mimeType, size)
            var originalName = "media_${System.currentTimeMillis()}"
            var mimeType = "image/jpeg"
            var sizeBytes = 0L

            contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)

                if (cursor.moveToFirst()) {
                    if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                        originalName = cursor.getString(nameIndex)
                    }
                    if (mimeIndex != -1 && !cursor.isNull(mimeIndex)) {
                        mimeType = cursor.getString(mimeIndex) ?: "image/jpeg"
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }

            // Fallback MIME check if unspecified or generic
            val typeFromResolver = contentResolver.getType(sourceUri)
            if (typeFromResolver != null) {
                mimeType = typeFromResolver
            }
            if (mimeType == "image/jpeg" || mimeType.isEmpty() || mimeType == "application/octet-stream") {
                val ext = originalName.substringAfterLast('.', "").lowercase()
                mimeType = when (ext) {
                    "pdf" -> "application/pdf"
                    "doc", "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "xls", "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "ppt", "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    "txt", "csv", "log" -> "text/plain"
                    "zip", "rar", "7z" -> "application/zip"
                    "mp3", "wav", "m4a", "aac" -> "audio/mpeg"
                    "mp4", "mkv", "avi", "mov" -> "video/mp4"
                    "jpg", "jpeg", "png", "webp", "gif" -> "image/jpeg"
                    else -> mimeType.ifEmpty { "application/octet-stream" }
                }
            }

            val isVideo = mimeType.startsWith("video/")

            // 2. Prepare internal vault destination file inside Context.filesDir
            val vaultDir = File(context.filesDir, vaultDirName).apply { if (!exists()) mkdirs() }
            val encryptedFileName = "enc_${UUID.randomUUID()}.bin"
            val targetEncryptedFile = File(vaultDir, encryptedFileName)

            // 3. Encrypt file using AES-256-GCM via CryptoManager
            contentResolver.openInputStream(sourceUri).use { inputStream ->
                requireNotNull(inputStream) { "Unable to open input stream from selected file." }
                FileOutputStream(targetEncryptedFile).use { outputStream ->
                    CryptoManager.encryptStream(inputStream, outputStream)
                }
            }

            if (sizeBytes == 0L) {
                sizeBytes = targetEncryptedFile.length()
            }

            // 4. Save metadata to Room Database
            val vaultItem = VaultItem(
                originalName = originalName,
                encryptedFileName = encryptedFileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                isVideo = isVideo,
                folderName = targetFolder
            )
            val generatedId = vaultDao.insertVaultItem(vaultItem)
            val savedVaultItem = vaultItem.copy(id = generatedId)

            // 5. Handle deletion of original file if requested
            var originalDeleted = false
            var deleteIntentSender: android.content.IntentSender? = null

            if (deleteOriginal) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val rowsDeleted = contentResolver.delete(sourceUri, null, null)
                            if (rowsDeleted > 0) {
                                originalDeleted = true
                            }
                        } catch (securityException: SecurityException) {
                            val deleteRequest = MediaStore.createDeleteRequest(contentResolver, listOf(sourceUri))
                            deleteIntentSender = deleteRequest.intentSender
                        }
                    } else {
                        try {
                            val rowsDeleted = contentResolver.delete(sourceUri, null, null)
                            if (rowsDeleted > 0) {
                                originalDeleted = true
                            }
                        } catch (securityException: SecurityException) {
                            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                                val recoverableException = securityException as? android.app.RecoverableSecurityException
                                deleteIntentSender = recoverableException?.userAction?.actionIntent?.intentSender
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VaultRepository", "Failed to delete original file (Picker/Document URI restriction): ${e.message}")
                    // We don't fail the import just because delete failed.
                }

                // Fallback for PhotoPicker or SAF URIs where direct delete throws exception and fails
                if (!originalDeleted && deleteIntentSender == null) {
                    try {
                        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
                        val selectionArgs = arrayOf(originalName, sizeBytes.toString())
                        
                        contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), selection, selectionArgs, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val id = cursor.getLong(0)
                                val mediaStoreUri = android.content.ContentUris.withAppendedId(collection, id)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    try {
                                        val rows = contentResolver.delete(mediaStoreUri, null, null)
                                        if (rows > 0) originalDeleted = true
                                    } catch (se: SecurityException) {
                                        val deleteRequest = MediaStore.createDeleteRequest(contentResolver, listOf(mediaStoreUri))
                                        deleteIntentSender = deleteRequest.intentSender
                                    }
                                } else {
                                    val rows = contentResolver.delete(mediaStoreUri, null, null)
                                    if (rows > 0) originalDeleted = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("VaultRepository", "Fallback MediaStore query/delete failed: ${e.message}")
                    }
                }
            }

            Result.success(ImportResult(savedVaultItem, originalDeleted, deleteIntentSender))
        } catch (e: Exception) {
            android.util.Log.e("VaultRepository", "Import failed for URI: $sourceUri - ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Imports an unencrypted stream directly into encrypted vault storage and Room DB.
     * Used for extracted ZIP entries, camera captures, and secure file operations.
     */
    suspend fun importStreamToVault(
        context: Context,
        inputStream: InputStream,
        originalName: String,
        mimeType: String,
        sizeBytes: Long
    ): VaultItem = withContext(Dispatchers.IO) {
        val vaultDir = File(context.filesDir, vaultDirName).apply { if (!exists()) mkdirs() }
        val encryptedFileName = "enc_${UUID.randomUUID()}.bin"
        val targetEncryptedFile = File(vaultDir, encryptedFileName)

        FileOutputStream(targetEncryptedFile).use { outputStream ->
            CryptoManager.encryptStream(inputStream, outputStream)
        }

        val actualSize = if (sizeBytes > 0) sizeBytes else targetEncryptedFile.length()
        val isVideo = mimeType.startsWith("video/")

        val vaultItem = VaultItem(
            originalName = originalName,
            encryptedFileName = encryptedFileName,
            mimeType = mimeType,
            sizeBytes = actualSize,
            isVideo = isVideo
        )

        val generatedId = vaultDao.insertVaultItem(vaultItem)
        vaultItem.copy(id = generatedId)
    }

    /**
     * Inserts a restored VaultItem into Room DB (for Disaster Recovery / Master Backup import).
     */
    suspend fun insertRestoredVaultItem(item: VaultItem): Long = withContext(Dispatchers.IO) {
        vaultDao.insertVaultItem(item)
    }

    /**
     * Decrypts encrypted vault file directly in-memory to a ByteArray for viewing.
     * Keeps decrypted content transient in memory.
     */
    suspend fun decryptFileToByteArray(context: Context, item: VaultItem): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val vaultDir = File(context.filesDir, vaultDirName)
            val encryptedFile = File(vaultDir, item.encryptedFileName)
            if (!encryptedFile.exists()) return@withContext null

            FileInputStream(encryptedFile).use { inputStream ->
                CryptoManager.decryptStreamToByteArray(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Removes file from internal vault storage and deletes its Room record.
     */
    suspend fun deleteVaultItem(context: Context, item: VaultItem) = withContext(Dispatchers.IO) {
        val vaultDir = File(context.filesDir, vaultDirName)
        val encryptedFile = File(vaultDir, item.encryptedFileName)
        if (encryptedFile.exists()) {
            encryptedFile.delete()
        }
        vaultDao.deleteVaultItem(item)
    }

    /**
     * Decrypts vault item and restores it back to public gallery (Downloads/Vault or Pictures/Vault).
     */
    suspend fun exportVaultItemToGallery(context: Context, item: VaultItem): Uri? = withContext(Dispatchers.IO) {
        try {
            val vaultDir = File(context.filesDir, vaultDirName)
            val encryptedFile = File(vaultDir, item.encryptedFileName)
            if (!encryptedFile.exists()) return@withContext null

            val contentResolver = context.contentResolver
            val contentUri = when {
                item.mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                item.mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                item.mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Files.getContentUri("external")
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "restored_${item.originalName}")
                put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relativePath = when {
                        item.mimeType.startsWith("video/") -> Environment.DIRECTORY_MOVIES
                        item.mimeType.startsWith("image/") -> Environment.DIRECTORY_PICTURES
                        item.mimeType.startsWith("audio/") -> Environment.DIRECTORY_MUSIC
                        else -> Environment.DIRECTORY_DOWNLOADS
                    }
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativePath/SecureVault")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = contentResolver.insert(contentUri, values) ?: return@withContext null

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(encryptedFile).use { inputStream ->
                    CryptoManager.decryptStreamToOutputStream(inputStream, outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }

            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
