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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class VaultFilterTab { ALL, PHOTOS, VIDEOS, DOCUMENTS }

class VaultViewModel(val repository: VaultRepository) : ViewModel() {

    private val _vaultMode = MutableStateFlow(VaultMode.LOCKED)
    val vaultMode: StateFlow<VaultMode> = _vaultMode.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _filterTab = MutableStateFlow(VaultFilterTab.ALL)
    val filterTab: StateFlow<VaultFilterTab> = _filterTab.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val vaultItems: StateFlow<List<VaultItem>> = _filterTab.flatMapLatest { tab ->
        when (tab) {
            VaultFilterTab.ALL -> repository.allVaultItems
            VaultFilterTab.PHOTOS -> repository.photos
            VaultFilterTab.VIDEOS -> repository.videos
            VaultFilterTab.DOCUMENTS -> repository.documents
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

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

    fun hideItemInStegoJpeg(context: Context, item: VaultItem, coverJpegInputStream: java.io.InputStream, outputStream: java.io.OutputStream) {
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val decryptedVaultBytes = repository.decryptFileToByteArray(context, item)
                if (decryptedVaultBytes == null) {
                    _statusMessage.value = "Failed to decrypt vault file for steganography embedding."
                    _isProcessing.value = false
                    return@launch
                }

                val coverBytes = coverJpegInputStream.readBytes()
                val stegoBytes = com.example.security.SteganographyManager.embedPayloadInJpeg(coverBytes, decryptedVaultBytes)
                outputStream.write(stegoBytes)
                outputStream.flush()
                outputStream.close()

                _statusMessage.value = "Vault item embedded inside JPEG via Steganography!"
            } catch (e: Exception) {
                _statusMessage.value = "Steganography embedding failed: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun extractItemFromStegoJpeg(context: Context, stegoJpegInputStream: java.io.InputStream) {
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val stegoBytes = stegoJpegInputStream.readBytes()
                val extractedBytes = com.example.security.SteganographyManager.extractPayloadFromJpeg(stegoBytes)

                if (extractedBytes != null) {
                    val tempFile = java.io.File(context.cacheDir, "stego_extracted_${System.currentTimeMillis()}.bin")
                    tempFile.writeBytes(extractedBytes)
                    val uri = Uri.fromFile(tempFile)

                    val result = repository.encryptAndImportFile(context, uri, true)
                    result.onSuccess {
                        _statusMessage.value = "Steganography payload extracted & imported into Vault!"
                    }.onFailure {
                        _statusMessage.value = "Failed to import extracted steganography payload."
                    }
                } else {
                    _statusMessage.value = "No valid Steganography payload found in selected JPEG image."
                }
            } catch (e: Exception) {
                _statusMessage.value = "Steganography extraction failed: ${e.localizedMessage}"
            } finally {
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

    fun exportMasterBackup(context: Context, masterPassword: String, outputStream: java.io.OutputStream) {
        _isProcessing.value = true
        viewModelScope.launch {
            val result = com.example.security.VaultBackupManager.exportMasterBackup(context, masterPassword, outputStream, repository)
            _isProcessing.value = false
            result.onSuccess {
                _statusMessage.value = "Master Encrypted Backup exported successfully!"
            }.onFailure { err ->
                _statusMessage.value = "Backup failed: ${err.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun importMasterBackup(context: Context, masterPassword: String, inputStream: java.io.InputStream) {
        _isProcessing.value = true
        viewModelScope.launch {
            val result = com.example.security.VaultBackupManager.importMasterBackup(context, masterPassword, inputStream, repository)
            _isProcessing.value = false
            result.onSuccess { restoredCount ->
                _statusMessage.value = "Disaster Recovery complete! Restored $restoredCount item(s)."
            }.onFailure { err ->
                _statusMessage.value = "Restore failed: Invalid password or corrupt backup."
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

        viewModelScope.launch {
            var successCount = 0
            var failCount = 0

            for (uri in urisToImport) {
                val result = repository.encryptAndImportFile(context, uri, deleteOriginal)
                result.onSuccess { importRes ->
                    successCount++
                    if (importRes.deleteIntentSender != null) {
                        _deleteIntentSender.value = importRes.deleteIntentSender
                    }
                }.onFailure {
                    failCount++
                }
            }

            _isProcessing.value = false
            if (failCount == 0) {
                _statusMessage.value = "Successfully encrypted $successCount file(s) into vault."
            } else {
                _statusMessage.value = "Imported $successCount file(s). $failCount failed."
            }
        }
    }

    fun openViewer(context: Context, item: VaultItem) {
        _selectedVaultItem.value = item
        _isProcessing.value = true

        viewModelScope.launch {
            val bytes = repository.decryptFileToByteArray(context, item)
            _isProcessing.value = false
            if (bytes != null) {
                _decryptedBytes.value = bytes
            } else {
                _statusMessage.value = "Failed to decrypt file."
                _selectedVaultItem.value = null
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
                _statusMessage.value = "File decrypted and restored to gallery!"
            } else {
                _statusMessage.value = "Export failed."
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearDeleteIntentSender() {
        _deleteIntentSender.value = null
    }

    class Factory(private val repository: VaultRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VaultViewModel::class.java)) {
                return VaultViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
