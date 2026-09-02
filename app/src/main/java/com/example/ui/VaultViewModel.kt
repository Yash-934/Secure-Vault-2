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
import com.example.security.ScreenCaptureDetector
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

enum class BackupRestoreType {
    BACKUP,
    RESTORE,
    STEGO_BACKUP,
    STEGO_RESTORE
}

data class BackupRestoreProgressState(
    val isActive: Boolean = false,
    val type: BackupRestoreType = BackupRestoreType.BACKUP,
    val title: String = "",
    val subtitle: String = "",
    val currentStep: String = "",
    val currentItemIndex: Int = 0,
    val totalItems: Int = 0,
    val bytesProcessed: Long = 0L,
    val progress: Float = 0f,
    val isComplete: Boolean = false,
    val isSuccess: Boolean = true,
    val resultSummary: String? = null
)

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
    private val appCtx: android.content.Context
) : ViewModel() {

    private val realRepository by lazy {
        val database = com.example.data.AppDatabase.getDatabase(appCtx)
        com.example.data.VaultRepository(database.vaultDao(), "vault")
    }

    private val decoyRepository by lazy {
        val database = com.example.data.AppDatabase.getDecoyDatabase(appCtx)
        com.example.data.VaultRepository(database.vaultDao(), "decoy_vault")
    }

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

    private val _isScreenRecordingWarningVisible = MutableStateFlow(false)
    val isScreenRecordingWarningVisible: StateFlow<Boolean> = _isScreenRecordingWarningVisible.asStateFlow()

    fun dismissScreenRecordingWarning() {
        _isScreenRecordingWarningVisible.value = false
    }

    fun checkAndEnforceScreenRecordingProtection(context: Context) {
        if (_isUnlocked.value && ScreenCaptureDetector.auditScreenCapture(context).isCaptureActive) {
            lockVault()
            _isScreenRecordingWarningVisible.value = true
            logIntruderAttempt(
                context,
                "SCREEN_RECORDING_DETECTED",
                "Screen recording or virtual mirroring display detected while vault was unlocked!"
            )
        }
    }

    private val _filterTab = MutableStateFlow(VaultFilterTab.ALL)
    val filterTab: StateFlow<VaultFilterTab> = _filterTab.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String>("ALL")
    val selectedFolder: StateFlow<String> = _selectedFolder.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val folders: StateFlow<List<com.example.data.VaultFolder>> = _vaultMode.flatMapLatest { mode ->
        if (mode == VaultMode.LOCKED) kotlinx.coroutines.flow.flowOf(emptyList()) else if (mode == VaultMode.DECOY) decoyRepository.allFolders else realRepository.allFolders
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
        if (mode == VaultMode.LOCKED) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            val activeRepo = if (mode == VaultMode.DECOY) decoyRepository else realRepository
            activeRepo.getItemsForFolderAndTab(folder, tab)
        }
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

    // Backup & Restore Live Progress State
    private val _backupRestoreProgress = MutableStateFlow(BackupRestoreProgressState())
    val backupRestoreProgress: StateFlow<BackupRestoreProgressState> = _backupRestoreProgress.asStateFlow()

    fun dismissBackupRestoreProgress() {
        _backupRestoreProgress.value = BackupRestoreProgressState(isActive = false)
    }

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

    // Consecutive Failed Attempts Counter & Lockout Timer
    var consecutiveFailedAttempts = 0
        private set

    private val _lockoutSecondsRemaining = MutableStateFlow(0)
    val lockoutSecondsRemaining: StateFlow<Int> = _lockoutSecondsRemaining.asStateFlow()

    private var lockoutJob: kotlinx.coroutines.Job? = null

    private fun startLockoutCountdown(seconds: Int) {
        lockoutJob?.cancel()
        _lockoutSecondsRemaining.value = seconds
        lockoutJob = viewModelScope.launch {
            while (_lockoutSecondsRemaining.value > 0) {
                delay(1000)
                _lockoutSecondsRemaining.value -= 1
            }
        }
    }

    var pendingExportIsDeviceLocked: Boolean = false

    fun executeSelfDestruct(context: Context) {
        _isProcessing.value = true
        viewModelScope.launch {
            com.example.security.SelfDestructManager.executeNuclearSelfDestruct(context)
            lockVault()
            _isProcessing.value = false
            _statusMessage.value = "NUCLEAR SELF-DESTRUCT EXECUTED: All vault files, keys, and databases shredded!"
        }
    }

    suspend fun initializeCredentials(context: Context, masterPin: String) {
        val settingsDataStore = com.example.data.local.SettingsDataStore(context)
        settingsDataStore.initializeCredentials(masterPin)
        com.example.security.VaultKeyManager.initializeVrkWithPin(context, masterPin, isDecoy = false)
        com.example.security.VaultKeyManager.authorizeWithPin(context, masterPin, isDecoy = false)
        unlockRealVault()
        consecutiveFailedAttempts = 0
        _lockoutSecondsRemaining.value = 0
        settingsDataStore.resetFailedAttempts()
    }

    suspend fun authenticateWithPin(
        context: Context,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner?,
        enteredPin: String,
        settings: com.example.data.local.VaultSettings
    ): Boolean {
        val settingsDataStore = com.example.data.local.SettingsDataStore(context)
        val remainingLockout = settingsDataStore.getLockoutSecondsRemaining()
        if (remainingLockout > 0) {
            _lockoutSecondsRemaining.value = remainingLockout
            return false
        }

        // 1. Check Kill PIN
        if (settings.isKillPinEnabled && settingsDataStore.verifyKillPin(enteredPin)) {
            logIntruderAttempt(context, "KILL_PIN_TRIGGERED", "Nuclear Kill PIN entered! Shredding all data...")
            executeSelfDestruct(context)
            return false
        }

        // 2. Check Master PIN
        if (settingsDataStore.verifyMasterPin(enteredPin)) {
            if (com.example.security.VaultKeyManager.authorizeWithPin(context, enteredPin, false)) {
                unlockRealVault()
                consecutiveFailedAttempts = 0
            _lockoutSecondsRemaining.value = 0
            lockoutJob?.cancel()
            settingsDataStore.resetFailedAttempts()
            return true
            }
        }

        // 3. Check Decoy PIN
        if (settingsDataStore.verifyDecoyPin(enteredPin)) {
            if (com.example.security.VaultKeyManager.authorizeWithPin(context, enteredPin, true)) {
                unlockDecoyVault()
                consecutiveFailedAttempts = 0
            _lockoutSecondsRemaining.value = 0
            lockoutJob?.cancel()
            settingsDataStore.resetFailedAttempts()
            return true
            }
        }

        // 4. Incorrect PIN
        val failedCount = settingsDataStore.recordFailedAttempt()
        consecutiveFailedAttempts = failedCount
        logIntruderAttempt(context, "PIN_FAILED", "Incorrect PIN attempt #$failedCount")

        if (failedCount >= 10) {
            logIntruderAttempt(context, "NUCLEAR_AUTO_WIPE", "10 consecutive failed PIN attempts exceeded. Self-destructing.")
            executeSelfDestruct(context)
            return false
        }

        if (failedCount >= 3 && settings.isIntruderSelfieEnabled && lifecycleOwner != null) {
            com.example.security.IntruderCaptureManager.captureIntruderSelfie(
                context = context,
                lifecycleOwner = lifecycleOwner,
                attemptType = "INTRUDER_SELFIE_3X",
                details = "Captured on 3rd consecutive failed PIN attempt"
            )
        }

        if (failedCount == 5) {
            val lockoutSec = 30
            settingsDataStore.setLockoutExpiration(System.currentTimeMillis() + (lockoutSec * 1000L))
            startLockoutCountdown(lockoutSec)
        } else if (failedCount in 6..9) {
            val lockoutSec = 30 + (failedCount - 5) * 15
            settingsDataStore.setLockoutExpiration(System.currentTimeMillis() + (lockoutSec * 1000L))
            startLockoutCountdown(lockoutSec)
        }

        return false
    }

    fun hideItemInStegoCarrier(context: Context, item: VaultItem, coverInputStream: java.io.InputStream, outputStream: java.io.OutputStream) {
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val decryptedVaultBytes = repository.decryptFileToByteArray(context, item)
                if (decryptedVaultBytes == null) {
                    _statusMessage.value = "Failed to decrypt vault file for steganography."
                    _isProcessing.value = false
                    return@launch
                }
                
                try {
                    java.io.ByteArrayInputStream(decryptedVaultBytes).buffered(65536).use { payloadIn ->
                        coverInputStream.buffered(65536).use { coverIn ->
                            outputStream.buffered(65536).use { out ->
                                com.example.security.SteganographyManager.embedPayloadStream(coverIn, payloadIn, out)
                            }
                        }
                    }
                    _statusMessage.value = "Vault item concealed inside carrier file via Steganography!"
                } finally {
                    decryptedVaultBytes.fill(0)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Steganography embedding failed: ${e.localizedMessage}"
            } finally {
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
                if (tempStegoFile.exists()) securelyShredFile(tempStegoFile)
                securelyShredFile(tempExtractedFile)
                _isProcessing.value = false
            }
        }
    }
    
    private fun securelyShredFile(file: java.io.File) {
        if (!file.exists()) return
        try {
            val length = file.length()
            if (length > 0) {
                java.io.RandomAccessFile(file, "rws").use { raf ->
                    val zeroBuf = ByteArray(minOf(65536, length.toInt()))
                    var written = 0L
                    while (written < length) {
                        val toWrite = minOf(zeroBuf.size.toLong(), length - written).toInt()
                        raf.write(zeroBuf, 0, toWrite)
                        written += toWrite
                    }
                }
            }
        } catch (_: Throwable) {} finally {
            file.delete()
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
                android.util.Log.e("Security", "Exception caught")
            }
        }
    }

    fun clearIntruderLogs(context: Context) {
        viewModelScope.launch {
            try {
                val db = com.example.data.AppDatabase.getDatabase(context)
                db.intruderLogDao().clearLogs()
            } catch (e: Exception) {
                android.util.Log.e("Security", "Exception caught")
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
        _backupRestoreProgress.value = BackupRestoreProgressState(
            isActive = true,
            type = BackupRestoreType.STEGO_BACKUP,
            title = "STEGANOGRAPHY BACKUP",
            subtitle = "Concealing Zero-Knowledge Archive in Carrier",
            currentStep = "Streaming cover carrier media...",
            progress = 0.1f
        )
        viewModelScope.launch {
            try {
                // 1. Open destination SAF document stream
                val outputStream = try {
                    context.contentResolver.openOutputStream(outputUri, "w")
                } catch (_: Throwable) {
                    context.contentResolver.openOutputStream(outputUri)
                }

                if (outputStream == null) {
                    _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                        isComplete = true,
                        isSuccess = false,
                        resultSummary = "Failed to open destination file."
                    )
                    _isProcessing.value = false
                    return@launch
                }

                val coverInputStream = context.contentResolver.openInputStream(coverUri)
                if (coverInputStream == null) {
                    _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                        isComplete = true,
                        isSuccess = false,
                        resultSummary = "Failed to read cover carrier file."
                    )
                    _isProcessing.value = false
                    return@launch
                }

                var totalBytesWritten = 0L

                outputStream.buffered(65536).use { outStream ->
                    // 2. Stream cover carrier file first
                    coverInputStream.buffered(65536).use { cIn ->
                        val buffer = ByteArray(65536)
                        var read: Int
                        while (cIn.read(buffer).also { read = it } != -1) {
                            outStream.write(buffer, 0, read)
                            totalBytesWritten += read
                        }
                    }

                    _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                        currentStep = "Embedding encrypted vault payload...",
                        bytesProcessed = totalBytesWritten,
                        progress = 0.25f
                    )

                    // 3. Directly stream encrypted backup payload into destination
                    var payloadSize = 0L
                    val payloadCountingStream = object : java.io.OutputStream() {
                        override fun write(b: Int) {
                            outStream.write(b)
                            payloadSize++
                        }
                        override fun write(b: ByteArray, off: Int, len: Int) {
                            outStream.write(b, off, len)
                            payloadSize += len
                        }
                        override fun flush() {
                            outStream.flush()
                        }
                    }

                    val backupResult = com.example.security.VaultBackupManager.exportMasterBackup(
                        context,
                        masterPassword,
                        payloadCountingStream,
                        repository,
                        onProgress = { current, total, name, bytes ->
                            val subProg = if (total > 0) (current.toFloat() / total.toFloat()) else 0.5f
                            val scaledProg = 0.25f + (subProg * 0.65f)
                            _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                                currentStep = name,
                                currentItemIndex = current,
                                totalItems = total,
                                bytesProcessed = totalBytesWritten + bytes,
                                progress = scaledProg
                            )
                        }
                    )

                    if (!backupResult.isSuccess || payloadSize <= 0) {
                        val err = backupResult.exceptionOrNull()?.localizedMessage ?: "Vault backup creation failed."
                        _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                            isComplete = true,
                            isSuccess = false,
                            resultSummary = "Backup failed: $err"
                        )
                        _isProcessing.value = false
                        return@launch
                    }

                    // 4. Append 8-byte payload size header + VAULT_STEGO_V2 delimiter
                    val sizeHeader = java.nio.ByteBuffer.allocate(8).putLong(payloadSize).array()
                    outStream.write(sizeHeader)
                    outStream.write("VAULT_STEGO_V2".toByteArray(Charsets.UTF_8))
                    outStream.flush()

                    totalBytesWritten += payloadSize + 8 + "VAULT_STEGO_V2".toByteArray(Charsets.UTF_8).size
                }

                val carrierInfo = com.example.security.SteganographyManager.resolveCarrierFileInfo(context, coverUri)
                val sizeKb = (totalBytesWritten + 1023) / 1024
                _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                    isComplete = true,
                    isSuccess = true,
                    progress = 1f,
                    currentStep = "Steganography Export Complete!",
                    resultSummary = "Concealed encrypted vault payload inside ${carrierInfo.extension.uppercase()} carrier ($sizeKb KB)."
                )
                showUserFeedback(context, "Zero-Trust Vault concealed inside ${carrierInfo.extension.uppercase()} carrier! ($sizeKb KB)")
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Unknown error"
                _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                    isComplete = true,
                    isSuccess = false,
                    resultSummary = "Steganography failed: $err"
                )
                showUserFeedback(context, "Steganography failed: $err")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun importStegoBackup(context: Context, masterPassword: String, stegoUri: android.net.Uri, isReplaceMode: Boolean = false) {
        _isProcessing.value = true
        _backupRestoreProgress.value = BackupRestoreProgressState(
            isActive = true,
            type = BackupRestoreType.STEGO_RESTORE,
            title = "STEGO DISASTER RECOVERY",
            subtitle = "Extracting Concealed Vault Payload",
            currentStep = if (isReplaceMode) "Wiping current vault..." else "Reading cover file stream...",
            progress = 0.1f
        )
        viewModelScope.launch {
            val tempStegoFile = java.io.File(context.cacheDir, "temp_incoming_stego_${System.currentTimeMillis()}.tmp")
            val tempExtractedBackupFile = java.io.File(context.cacheDir, "temp_extracted_backup_${System.currentTimeMillis()}.bin")
            try {
                // 1. Copy incoming stego URI stream to temp cache file for reverse extraction
                context.contentResolver.openInputStream(stegoUri)?.buffered(65536).use { input ->
                    if (input == null) throw IllegalStateException("Cannot read selected file.")
                    tempStegoFile.outputStream().buffered(65536).use { output ->
                        input.copyTo(output, 65536)
                    }
                }

                _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                    currentStep = "Extracting hidden cryptographic payload...",
                    progress = 0.35f
                )

                // 2. Extract payload from file using SteganographyManager
                val extractResult = tempExtractedBackupFile.outputStream().buffered(65536).use { out ->
                    com.example.security.SteganographyManager.extractPayloadFromFile(tempStegoFile, out)
                }

                if (extractResult.isSuccess && tempExtractedBackupFile.length() > 0) {
                    _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                        currentStep = "Decrypting & restoring vault files...",
                        progress = 0.5f
                    )
                    val result = tempExtractedBackupFile.inputStream().buffered(65536).use { inStream ->
                        com.example.security.VaultBackupManager.importMasterBackup(
                            context,
                            masterPassword,
                            inStream,
                            repository,
                            isReplaceMode,
                            onProgress = { current, total, name, bytes ->
                                val subProg = if (total > 0) (current.toFloat() / total.toFloat()) else 0.5f
                                val scaledProg = 0.5f + (subProg * 0.45f)
                                _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                                    currentStep = name,
                                    currentItemIndex = current,
                                    totalItems = total,
                                    bytesProcessed = bytes,
                                    progress = scaledProg
                                )
                            }
                        )
                    }
                    result.onSuccess { count ->
                        _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                            isComplete = true,
                            isSuccess = true,
                            progress = 1f,
                            currentStep = "Restore Completed!",
                            resultSummary = "Steganography Restore complete! Restored $count vault item(s)."
                        )
                        showUserFeedback(context, "Steganography Restore complete! Restored $count vault item(s).")
                    }.onFailure {
                        _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                            isComplete = true,
                            isSuccess = false,
                            resultSummary = "Restore failed: Invalid password or corrupted payload."
                        )
                        showUserFeedback(context, "Restore failed: Invalid password or corrupted payload.")
                    }
                } else {
                    val errMsg = extractResult.exceptionOrNull()?.message ?: "No steganography payload found in this file."
                    _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                        isComplete = true,
                        isSuccess = false,
                        resultSummary = errMsg
                    )
                    showUserFeedback(context, errMsg)
                }
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Unknown error"
                _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                    isComplete = true,
                    isSuccess = false,
                    resultSummary = "Extraction failed: $err"
                )
                showUserFeedback(context, "Extraction failed: $err")
            } finally {
                if (tempStegoFile.exists()) securelyShredFile(tempStegoFile)
                securelyShredFile(tempExtractedBackupFile)
                _isProcessing.value = false
            }
        }
    }

    fun exportMasterBackup(
        context: Context,
        masterPassword: String,
        targetUri: android.net.Uri,
        isDeviceLocked: Boolean = false
    ) {
        _isProcessing.value = true
        _backupRestoreProgress.value = BackupRestoreProgressState(
            isActive = true,
            type = BackupRestoreType.BACKUP,
            title = if (isDeviceLocked) "DEVICE-LOCKED MASTER BACKUP" else "MASTER ENCRYPTED BACKUP",
            subtitle = if (isDeviceLocked) "Argon2id + Keystore Hardware Wrapping" else "Zero-Knowledge Argon2id Streaming",
            currentStep = "Initializing master backup...",
            progress = 0f
        )
        viewModelScope.launch {
            try {
                val outputStream = try {
                    context.contentResolver.openOutputStream(targetUri, "w")
                } catch (_: Throwable) {
                    context.contentResolver.openOutputStream(targetUri)
                }

                if (outputStream == null) {
                    _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                        isComplete = true,
                        isSuccess = false,
                        resultSummary = "Failed to open destination file."
                    )
                    _isProcessing.value = false
                    return@launch
                }

                val backupResult = outputStream.buffered(65536).use { safeOutStream ->
                    com.example.security.VaultBackupManager.exportMasterBackup(
                        context,
                        masterPassword,
                        safeOutStream,
                        repository,
                        isDeviceLocked = isDeviceLocked,
                        onProgress = { current, total, name, bytes ->
                            val prog = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                            _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                                currentStep = name,
                                currentItemIndex = current,
                                totalItems = total,
                                bytesProcessed = bytes,
                                progress = prog
                            )
                        }
                    )
                }

                backupResult.onSuccess { totalBytesWritten ->
                    val sizeKb = (totalBytesWritten + 1023) / 1024
                    _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                        isComplete = true,
                        isSuccess = true,
                        progress = 1f,
                        currentStep = "Master Backup Complete!",
                        resultSummary = "Master Encrypted Backup exported successfully! ($sizeKb KB)"
                    )
                    showUserFeedback(context, "Master Encrypted Backup exported successfully! ($sizeKb KB)")
                }.onFailure { err ->
                    val msg = err.localizedMessage ?: "Unknown error"
                    _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                        isComplete = true,
                        isSuccess = false,
                        resultSummary = "Export error: $msg"
                    )
                    showUserFeedback(context, "Export error: $msg")
                }
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Unknown error"
                _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                    isComplete = true,
                    isSuccess = false,
                    resultSummary = "Export error: $msg"
                )
                showUserFeedback(context, "Export error: $msg")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun importMasterBackup(context: Context, masterPassword: String, sourceUri: android.net.Uri, isReplaceMode: Boolean = false) {
        _isProcessing.value = true
        _backupRestoreProgress.value = BackupRestoreProgressState(
            isActive = true,
            type = BackupRestoreType.RESTORE,
            title = "DISASTER RECOVERY RESTORE",
            subtitle = "Decrypting & Rebuilding Vault Database",
            currentStep = if (isReplaceMode) "Wiping current vault..." else "Validating backup archive header...",
            progress = 0f
        )
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                if (inputStream == null) {
                    _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                        isComplete = true,
                        isSuccess = false,
                        resultSummary = "Failed to open backup file."
                    )
                    _isProcessing.value = false
                    return@launch
                }
                val result = inputStream.buffered(65536).use { stream ->
                    com.example.security.VaultBackupManager.importMasterBackup(
                        context,
                        masterPassword,
                        stream,
                        repository,
                        isReplaceMode,
                        onProgress = { current, total, name, bytes ->
                            val prog = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                            _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                                currentStep = name,
                                currentItemIndex = current,
                                totalItems = total,
                                bytesProcessed = bytes,
                                progress = prog
                            )
                        }
                    )
                }
                _isProcessing.value = false
                result.onSuccess { restoredCount ->
                    _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                        isComplete = true,
                        isSuccess = true,
                        progress = 1f,
                        currentStep = "Restoration Complete!",
                        resultSummary = "Disaster Recovery complete! Restored $restoredCount item(s)."
                    )
                    showUserFeedback(context, "Disaster Recovery complete! Restored $restoredCount item(s).")
                }.onFailure { err ->
                    _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                        isComplete = true,
                        isSuccess = false,
                        resultSummary = "Restore failed: Invalid password or corrupt backup."
                    )
                    showUserFeedback(context, "Restore failed: Invalid password or corrupt backup.")
                }
            } catch (e: Exception) {
                _isProcessing.value = false
                val msg = e.localizedMessage ?: "Unknown error"
                _backupRestoreProgress.value = _backupRestoreProgress.value.copy(
                    isComplete = true,
                    isSuccess = false,
                    resultSummary = "Restore failed: $msg"
                )
                showUserFeedback(context, "Restore failed: $msg")
            }
        }
    }

    fun lockVault() {
        com.example.security.VaultKeyManager.clearAuthorizedSessionKey()
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

    fun moveItemsToFolder(itemIds: List<Long>, destinationFolder: String) {
        viewModelScope.launch {
            itemIds.forEach { id ->
                repository.moveItemToFolder(id, destinationFolder)
            }
            _statusMessage.value = "Moved ${itemIds.size} file(s) to '$destinationFolder'."
        }
    }

    @JvmName("moveVaultItemsToFolder")
    fun moveItemsToFolder(items: List<VaultItem>, destinationFolder: String) {
        moveItemsToFolder(items.map { it.id }, destinationFolder)
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

    fun deleteVaultItems(context: Context, items: List<VaultItem>) {
        viewModelScope.launch {
            items.forEach { item ->
                repository.deleteVaultItem(context, item)
                if (_selectedVaultItem.value?.id == item.id) {
                    closeViewer()
                }
            }
            _statusMessage.value = "Removed ${items.size} file(s) from Vault."
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

    fun exportVaultItems(context: Context, items: List<VaultItem>) {
        _isProcessing.value = true
        viewModelScope.launch {
            var exportedCount = 0
            items.forEach { item ->
                val uri = repository.exportVaultItemToGallery(context, item)
                if (uri != null) exportedCount++
            }
            _isProcessing.value = false
            if (exportedCount > 0) {
                showUserFeedback(context, "$exportedCount file(s) decrypted and restored to gallery!")
            } else {
                showUserFeedback(context, "Batch export failed.")
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
        private val context: android.content.Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VaultViewModel::class.java)) {
                return VaultViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
