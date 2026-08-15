package com.example.ui

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.VaultItem
import com.example.data.VaultRepository
import com.example.domain.model.VaultMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class VaultFilterTab { ALL, PHOTOS, VIDEOS, DOCUMENTS }

data class ImportProgressState(
    val isImporting: Boolean = false,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = "",
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val overallProgress: Float = 0f
)

class VaultViewModel(
    private val realRepository: VaultRepository,
    private val decoyRepository: VaultRepository
) : ViewModel() {

    private val _vaultMode = MutableStateFlow(VaultMode.LOCKED)
    val vaultMode: StateFlow<VaultMode> = _vaultMode.asStateFlow()

    @Volatile
    var isSystemPickerActive: Boolean = false
        private set

    fun onSystemPickerLaunched() {
        isSystemPickerActive = true
    }

    fun onSystemPickerFinished() {
        isSystemPickerActive = false
    }

    var pendingExportPassword: String? = null
    var pendingExportUri: android.net.Uri? = null

    var pendingStegoPassword: String? = null
    var pendingStegoCoverUri: android.net.Uri? = null
    var pendingStegoExtractUri: android.net.Uri? = null

    var pendingImportUri: android.net.Uri? = null

    val repository: VaultRepository get() = if (vaultMode.value == VaultMode.DECOY) decoyRepository else realRepository

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _filterTab = MutableStateFlow(VaultFilterTab.ALL)
    val filterTab: StateFlow<VaultFilterTab> = _filterTab.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String>("ALL")
    val selectedFolder: StateFlow<String> = _selectedFolder.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val folders: StateFlow<List<com.example.data.VaultFolder>> = _vaultMode.flatMapLatest { mode ->
        if (mode == VaultMode.DECOY) decoyRepository.allFolders else realRepository.allFolders
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val vaultItems: StateFlow<List<VaultItem>> = kotlinx.coroutines.flow.combine(_vaultMode, _selectedFolder, _filterTab) { mode, folder, tab ->
        Triple(mode, folder, tab)
    }.flatMapLatest { (mode, folder, tab) ->
        val activeRepo = if (mode == VaultMode.DECOY) decoyRepository else realRepository
        activeRepo.getItemsForFolderAndTab(folder, tab)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Import Live Progress State
    private val _importProgress = MutableStateFlow(ImportProgressState())
    val importProgress: StateFlow<ImportProgressState> = _importProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Import Dialog State
    private val _pendingImportUris = MutableStateFlow<List<Uri>>(emptyList())
    val pendingImportUris: StateFlow<List<Uri>> = _pendingImportUris.asStateFlow()

    // IntentSender for Android 10+ MediaStore deletion prompt
    private val _deleteIntentSender = MutableStateFlow<IntentSender?>(null)
    val deleteIntentSender: StateFlow<IntentSender?> = _deleteIntentSender.asStateFlow()

    // Active Item Viewer State ( decrypted in memory )
    private val _selectedVaultItem = MutableStateFlow<VaultItem?>(null)
    val selectedVaultItem: StateFlow<VaultItem?> = _selectedVaultItem.asStateFlow()

    private val _decryptedBytes = MutableStateFlow<ByteArray?>(null)
    val decryptedBytes: StateFlow<ByteArray?> = _decryptedBytes.asStateFlow()

    fun unlockRealVault() {
        _vaultMode.value = VaultMode.REAL
        _isUnlocked.value = true
        simulateLoading()
    }

    fun unlockDecoyVault() {
        _vaultMode.value = VaultMode.DECOY
        _isUnlocked.value = true
    }

    // Consecutive Failed Attempts Counter
    var consecutiveFailedAttempts = 0
        private set

    fun executeSelfDestruct(context: Context) {
        _isProcessing.value = true
        viewModelScope.launch {
            com.example.security.SelfDestructManager.executeNuclearSelfDestruct(context)
            lockVault()
            _isProcessing.value = false
            _statusMessage.value = "NUCLEAR SELF-DESTRUCT EXECUTED: All vault files, keys, and databases shredded!"
        }
    }

    fun authenticateWithPin(
        context: Context,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner?,
        enteredPin: String,
        settings: com.example.data.local.VaultSettings
    ): Boolean {
        // 1. Check Kill PIN
        if (settings.isKillPinEnabled && enteredPin == settings.killPin) {
            logIntruderAttempt(context, "KILL_PIN_TRIGGERED", "Nuclear Kill PIN entered! Shredding all data...")
            executeSelfDestruct(context)
            return false
        }

        // 2. Check Master PIN
        if (enteredPin == settings.masterPin) {
            unlockRealVault()
            consecutiveFailedAttempts = 0
            return true
        }

        // 3. Check Decoy PIN
        if (enteredPin == settings.decoyPin) {
            unlockDecoyVault()
            consecutiveFailedAttempts = 0
            return true
        }

        // 4. Incorrect PIN
        consecutiveFailedAttempts++
        logIntruderAttempt(context, "PIN_FAILED", "Incorrect PIN attempt #$consecutiveFailedAttempts")

        if (consecutiveFailedAttempts >= 3 && settings.isIntruderSelfieEnabled && lifecycleOwner != null) {
            com.example.security.IntruderCaptureManager.captureIntruderSelfie(
                context = context,
                lifecycleOwner = lifecycleOwner,
                attemptType = "INTRUDER_SELFIE_3X",
                details = "Captured 3 consecutive failed PIN attempts"
            )
            consecutiveFailedAttempts = 0
        }

        return false
    }

    fun hideItemInStegoCarrier(context: Context, item: VaultItem, coverInputStream: java.io.InputStream, outputStream: java.io.OutputStream) {
        _isProcessing.value = true
        viewModelScope.launch {
            val tempItemFile = java.io.File(context.cacheDir, "temp_stego_item_${System.currentTimeMillis()}.bin")
            try {
                val decryptedVaultBytes = repository.decryptFileToByteArray(context, item)
                if (decryptedVaultBytes == null) {
                    _statusMessage.value = "Failed to decrypt vault file for steganography."
                    _isProcessing.value = false
                    return@launch
                }
                tempItemFile.writeBytes(decryptedVaultBytes)
                decryptedVaultBytes.fill(0)

                tempItemFile.inputStream().buffered(65536).use { payloadIn ->
                    coverInputStream.buffered(65536).use { coverIn ->
                        outputStream.buffered(65536).use { out ->
                            com.example.security.SteganographyManager.embedPayloadStream(coverIn, payloadIn, out)
                        }
                    }
                }

                _statusMessage.value = "Vault item concealed inside carrier file via Steganography!"
            } catch (e: Exception) {
                _statusMessage.value = "Steganography embedding failed: ${e.localizedMessage}"
            } finally {
                if (tempItemFile.exists()) tempItemFile.delete()
                _isProcessing.value = false
            }
        }
    }

    fun extractItemFromStegoCarrier(context: Context, stegoInputStream: java.io.InputStream) {
        _isProcessing.value = true
        viewModelScope.launch {
            val tempStegoFile = java.io.File(context.cacheDir, "temp_stego_in_${System.currentTimeMillis()}.tmp")
            val tempExtractedFile = java.io.File(context.cacheDir, "temp_stego_out_${System.currentTimeMillis()}.bin")
            try {
                stegoInputStream.buffered(65536).use { input ->
                    tempStegoFile.outputStream().buffered(65536).use { output ->
                        input.copyTo(output, 65536)
                    }
                }

                val extractResult = tempExtractedFile.outputStream().buffered(65536).use { out ->
                    com.example.security.SteganographyManager.extractPayloadFromFile(tempStegoFile, out)
                }

                if (extractResult.isSuccess) {
                    val uri = Uri.fromFile(tempExtractedFile)
                    val result = repository.encryptAndImportFile(context, uri, true)
                    result.onSuccess {
                        _statusMessage.value = "Steganography payload extracted & secured into Vault!"
                    }.onFailure {
                        _statusMessage.value = "Failed to import extracted steganography file."
                    }
                } else {
                    _statusMessage.value = extractResult.exceptionOrNull()?.message ?: "No valid Steganography payload found in carrier file."
                }
            } catch (e: Exception) {
                _statusMessage.value = "Steganography extraction failed: ${e.localizedMessage}"
            } finally {
                if (tempStegoFile.exists()) tempStegoFile.delete()
                if (tempExtractedFile.exists()) tempExtractedFile.delete()
                _isProcessing.value = false
            }
        }
    }

    fun logIntruderAttempt(context: Context, attemptType: String, details: String = "Unauthorized access attempt blocked") {
        viewModelScope.launch {
            try {
                val db = com.example.data.AppDatabase.getDatabase(context)
                db.intruderLogDao().insertLog(
                    com.example.data.IntruderLog(
                        attemptType = attemptType,
                        details = details
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearIntruderLogs(context: Context) {
        viewModelScope.launch {
            try {
                val db = com.example.data.AppDatabase.getDatabase(context)
                db.intruderLogDao().clearLogs()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showUserFeedback(context: Context, message: String) {
        _statusMessage.value = message
        viewModelScope.launch(Dispatchers.Main) {
            try {
                android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
            } catch (_: Throwable) {}
        }
    }

    fun exportStegoBackup(context: Context, masterPassword: String, coverUri: android.net.Uri, outputUri: android.net.Uri) {
        _isProcessing.value = true
        viewModelScope.launch {
            val tempBackupFile = java.io.File(context.cacheDir, "temp_stego_backup_${System.currentTimeMillis()}.bin")
            try {
                // 1. Export encrypted vault backup into temp file
                val backupResult = java.io.FileOutputStream(tempBackupFile).buffered(65536).use { tempOut ->
                    com.example.security.VaultBackupManager.exportMasterBackup(context, masterPassword, tempOut, repository)
                }

                if (backupResult.isSuccess && tempBackupFile.length() > 0) {
                    val coverInputStream = context.contentResolver.openInputStream(coverUri)?.buffered(65536)
                    val payloadInputStream = java.io.FileInputStream(tempBackupFile).buffered(65536)
                    val rawOut = context.contentResolver.openOutputStream(outputUri, "wt") 
                        ?: context.contentResolver.openOutputStream(outputUri)
                    val outStream = rawOut?.buffered(65536)

                    if (coverInputStream != null && outStream != null) {
                        coverInputStream.use { cIn ->
                            payloadInputStream.use { pIn ->
                                outStream.use { oOut ->
                                    com.example.security.SteganographyManager.embedPayloadStream(cIn, pIn, oOut)
                                }
                            }
                        }
                        val carrierInfo = com.example.security.SteganographyManager.resolveCarrierFileInfo(context, coverUri)
                        showUserFeedback(context, "Zero-Trust Vault concealed inside ${carrierInfo.extension.uppercase()} carrier file!")
                    } else {
                        showUserFeedback(context, "Failed to access carrier or destination file.")
                    }
                } else {
                    val err = backupResult.exceptionOrNull()?.localizedMessage ?: "Vault backup creation failed."
                    showUserFeedback(context, "Backup failed: $err")
                }
            } catch (e: Exception) {
                showUserFeedback(context, "Steganography failed: ${e.message}")
            } finally {
                if (tempBackupFile.exists()) {
                    tempBackupFile.delete()
                }
                _isProcessing.value = false
            }
        }
    }

    fun importStegoBackup(context: Context, masterPassword: String, stegoUri: android.net.Uri) {
        _isProcessing.value = true
        viewModelScope.launch {
            val tempStegoFile = java.io.File(context.cacheDir, "temp_incoming_stego_${System.currentTimeMillis()}.tmp")
            val tempExtractedBackupFile = java.io.File(context.cacheDir, "temp_extracted_backup_${System.currentTimeMillis()}.bin")
            try {
                // 1. Copy incoming stego URI stream to temp cache file
                context.contentResolver.openInputStream(stegoUri)?.buffered(65536).use { input ->
                    if (input == null) throw IllegalStateException("Cannot read selected file.")
                    tempStegoFile.outputStream().buffered(65536).use { output ->
                        input.copyTo(output, 65536)
                    }
                }

                // 2. Extract payload from file using SteganographyManager
                val extractResult = tempExtractedBackupFile.outputStream().buffered(65536).use { out ->
                    com.example.security.SteganographyManager.extractPayloadFromFile(tempStegoFile, out)
                }

                if (extractResult.isSuccess && tempExtractedBackupFile.length() > 0) {
                    val result = tempExtractedBackupFile.inputStream().buffered(65536).use { inStream ->
                        com.example.security.VaultBackupManager.importMasterBackup(context, masterPassword, inStream, repository)
                    }
                    result.onSuccess { count ->
                        showUserFeedback(context, "Steganography Restore complete! Restored $count vault item(s).")
                    }.onFailure {
                        showUserFeedback(context, "Restore failed: Invalid password or corrupted payload.")
                    }
                } else {
                    showUserFeedback(context, extractResult.exceptionOrNull()?.message ?: "No steganography payload found in this file.")
                }
            } catch (e: Exception) {
                showUserFeedback(context, "Extraction failed: ${e.message}")
            } finally {
                if (tempStegoFile.exists()) tempStegoFile.delete()
                if (tempExtractedBackupFile.exists()) tempExtractedBackupFile.delete()
                _isProcessing.value = false
            }
        }
    }

    fun exportMasterBackup(context: Context, masterPassword: String, targetUri: android.net.Uri) {
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val outputStream = context.contentResolver.openOutputStream(targetUri, "wt")
                    ?: context.contentResolver.openOutputStream(targetUri)
                if (outputStream == null) {
                    showUserFeedback(context, "Failed to open destination file.")
                    _isProcessing.value = false
                    return@launch
                }
                val result = outputStream.buffered(65536).use { stream ->
                    com.example.security.VaultBackupManager.exportMasterBackup(context, masterPassword, stream, repository)
                }
                _isProcessing.value = false
                result.onSuccess {
                    showUserFeedback(context, "Master Encrypted Backup exported successfully!")
                }.onFailure { err ->
                    showUserFeedback(context, "Backup failed: ${err.localizedMessage ?: "Unknown error"}")
                }
            } catch (e: Exception) {
                _isProcessing.value = false
                showUserFeedback(context, "Backup failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun importMasterBackup(context: Context, masterPassword: String, sourceUri: android.net.Uri) {
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                if (inputStream == null) {
                    showUserFeedback(context, "Failed to open backup file.")
                    _isProcessing.value = false
                    return@launch
                }
                val result = inputStream.buffered(65536).use { stream ->
                    com.example.security.VaultBackupManager.importMasterBackup(context, masterPassword, stream, repository)
                }
                _isProcessing.value = false
                result.onSuccess { restoredCount ->
                    showUserFeedback(context, "Disaster Recovery complete! Restored $restoredCount item(s).")
                }.onFailure { err ->
                    showUserFeedback(context, "Restore failed: Invalid password or corrupt backup.")
                }
            } catch (e: Exception) {
                _isProcessing.value = false
                showUserFeedback(context, "Restore failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun lockVault() {
        _vaultMode.value = VaultMode.LOCKED
        _isUnlocked.value = false
        closeViewer()
    }

    private fun simulateLoading() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(400) // Brief shimmer transition
            _isLoading.value = false
        }
    }

    fun setFilterTab(tab: VaultFilterTab) {
        _filterTab.value = tab
    }

    fun selectFolder(folderName: String) {
        _selectedFolder.value = folderName
    }

    fun createFolder(folderName: String) {
        val trimmed = folderName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.createFolder(trimmed)
            _selectedFolder.value = trimmed
            _statusMessage.value = "Folder '$trimmed' created."
        }
    }

    fun renameFolder(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        
        viewModelScope.launch {
            repository.renameFolder(oldName, trimmed)
            if (_selectedFolder.value == oldName) {
                _selectedFolder.value = trimmed
            }
            _statusMessage.value = "Folder renamed to '$trimmed'."
        }
    }

    fun deleteFolder(folderName: String) {
        viewModelScope.launch {
            repository.deleteFolder(folderName)
            if (_selectedFolder.value == folderName) {
                _selectedFolder.value = "ALL"
            }
            _statusMessage.value = "Folder '$folderName' removed."
        }
    }

    fun moveItemToFolder(itemId: Long, destinationFolder: String) {
        viewModelScope.launch {
            repository.moveItemToFolder(itemId, destinationFolder)
            _statusMessage.value = "Moved file to '$destinationFolder'."
        }
    }

    fun copyItemToFolder(context: Context, item: VaultItem, destinationFolder: String) {
        _isProcessing.value = true
        viewModelScope.launch {
            val copied = repository.copyItemToFolder(context, item, destinationFolder)
            _isProcessing.value = false
            if (copied != null) {
                _statusMessage.value = "Copied '${item.originalName}' to '$destinationFolder'."
            } else {
                _statusMessage.value = "Failed to copy file."
            }
        }
    }

    fun onFilesSelected(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            _pendingImportUris.value = uris
        }
    }

    fun clearPendingImport() {
        _pendingImportUris.value = emptyList()
    }

    fun importPendingFiles(context: Context, deleteOriginal: Boolean) {
        val urisToImport = _pendingImportUris.value
        if (urisToImport.isEmpty()) return

        _pendingImportUris.value = emptyList()
        _isProcessing.value = true

        val targetFolder = if (_selectedFolder.value == "ALL") "Root" else _selectedFolder.value

        viewModelScope.launch {
            var successCount = 0
            var failCount = 0
            val urisToDelete = mutableListOf<android.net.Uri>()
            var fallbackIntentSender: android.content.IntentSender? = null
            val totalFiles = urisToImport.size

            _importProgress.value = ImportProgressState(
                isImporting = true,
                currentFileIndex = 1,
                totalFiles = totalFiles,
                currentFileName = "Preparing files...",
                bytesProcessed = 0L,
                totalBytes = 0L,
                overallProgress = 0f
            )

            try {
                for ((index, uri) in urisToImport.withIndex()) {
                    val fileIndex = index + 1
                    val result = repository.encryptAndImportFile(
                        context = context,
                        sourceUri = uri,
                        deleteOriginal = deleteOriginal,
                        targetFolder = targetFolder,
                        onProgress = { processed, total, fileName ->
                            val currentFileFraction = if (total > 0) processed.toFloat() / total.toFloat() else 0f
                            val totalBatchProgress = ((index.toFloat() + currentFileFraction) / totalFiles.toFloat()).coerceIn(0f, 1f)
                            _importProgress.value = ImportProgressState(
                                isImporting = true,
                                currentFileIndex = fileIndex,
                                totalFiles = totalFiles,
                                currentFileName = fileName,
                                bytesProcessed = processed,
                                totalBytes = total,
                                overallProgress = totalBatchProgress
                            )
                        }
                    )

                    result.onSuccess { importRes ->
                        successCount++
                        if (importRes.mediaStoreUriToDelete != null) {
                            urisToDelete.add(importRes.mediaStoreUriToDelete)
                        } else if (importRes.deleteIntentSender != null) {
                            fallbackIntentSender = importRes.deleteIntentSender
                        }
                    }.onFailure {
                        failCount++
                    }
                }

                if (urisToDelete.isNotEmpty() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    try {
                        val deleteRequest = android.provider.MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)
                        _deleteIntentSender.value = deleteRequest.intentSender
                    } catch (e: Exception) {
                        _deleteIntentSender.value = fallbackIntentSender
                    }
                } else if (fallbackIntentSender != null) {
                    _deleteIntentSender.value = fallbackIntentSender
                }

                if (failCount == 0) {
                    _statusMessage.value = "Successfully encrypted $successCount file(s) into vault."
                } else {
                    _statusMessage.value = "Imported $successCount file(s). $failCount failed."
                }
            } catch (e: Exception) {
                _statusMessage.value = "Import error: ${e.message}"
            } finally {
                _isProcessing.value = false
                _importProgress.value = ImportProgressState(isImporting = false)
            }
        }
    }

    fun openViewer(context: Context, item: VaultItem) {
        _selectedVaultItem.value = item

        val mime = item.mimeType.lowercase()
        val isStreamableType = item.isVideo ||
                mime.startsWith("video/") ||
                mime.startsWith("audio/") ||
                mime == "application/pdf" ||
                mime.contains("zip") ||
                item.sizeBytes > 30 * 1024 * 1024L

        if (isStreamableType) {
            // For streaming media (large videos up to multi-GB, audio, pdf), do not load in-memory byte array.
            // Dedicated streaming players handle decrypted streaming directly from sandbox storage.
            _decryptedBytes.value = null
            _isProcessing.value = false
            return
        }

        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val bytes = repository.decryptFileToByteArray(context, item)
                _isProcessing.value = false
                if (bytes != null) {
                    _decryptedBytes.value = bytes
                } else {
                    _decryptedBytes.value = null
                }
            } catch (e: Throwable) {
                _isProcessing.value = false
                _decryptedBytes.value = null
            }
        }
    }

    fun closeViewer() {
        val currentBytes = _decryptedBytes.value
        currentBytes?.fill(0)
        _decryptedBytes.value = null
        _selectedVaultItem.value = null
    }

    fun deleteVaultItem(context: Context, item: VaultItem) {
        viewModelScope.launch {
            repository.deleteVaultItem(context, item)
            if (_selectedVaultItem.value?.id == item.id) {
                closeViewer()
            }
            _statusMessage.value = "File removed from Vault."
        }
    }

    fun exportVaultItem(context: Context, item: VaultItem) {
        _isProcessing.value = true
        viewModelScope.launch {
            val exportedUri = repository.exportVaultItemToGallery(context, item)
            _isProcessing.value = false
            if (exportedUri != null) {
                showUserFeedback(context, "File decrypted and restored to gallery!")
            } else {
                showUserFeedback(context, "Export failed.")
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearDeleteIntentSender() {
        _deleteIntentSender.value = null
    }

    class Factory(
        private val realRepository: VaultRepository,
        private val decoyRepository: VaultRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VaultViewModel::class.java)) {
                return VaultViewModel(realRepository, decoyRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
