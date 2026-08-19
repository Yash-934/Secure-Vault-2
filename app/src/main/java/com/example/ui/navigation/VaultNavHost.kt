package com.example.ui.navigation

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.data.AppDatabase
import com.example.data.local.VaultSettings
import com.example.domain.model.VaultMode
import com.example.ui.VaultViewModel
import com.example.ui.components.BackupPasswordDialog
import com.example.ui.components.BackupRestoreProgressDialog
import com.example.ui.components.ChangePinDialog
import com.example.ui.components.EncryptionProgressDialog
import com.example.ui.components.ImportPromptDialog
import com.example.ui.components.StealthModeDialog
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.HelpScreen
import com.example.ui.screens.CalculatorScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EncryptionInspectorScreen
import com.example.ui.screens.IntruderLogsScreen
import com.example.ui.screens.LockScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.PasswordManagerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.SettingsViewModel

@Composable
fun VaultNavHost(
    navController: NavHostController,
    context: Context,
    vaultViewModel: VaultViewModel,
    settingsViewModel: SettingsViewModel,
    onTriggerBiometrics: () -> Unit,
    onLockApp: () -> Unit
) {
    val vaultMode by vaultViewModel.vaultMode.collectAsStateWithLifecycle()
    val vaultItems by vaultViewModel.vaultItems.collectAsStateWithLifecycle()
    val filterTab by vaultViewModel.filterTab.collectAsStateWithLifecycle()
    val isProcessing by vaultViewModel.isProcessing.collectAsStateWithLifecycle()
    val importProgress by vaultViewModel.importProgress.collectAsStateWithLifecycle()
    val backupRestoreProgress by vaultViewModel.backupRestoreProgress.collectAsStateWithLifecycle()
    val isLoading by vaultViewModel.isLoading.collectAsStateWithLifecycle()
    val statusMessage by vaultViewModel.statusMessage.collectAsStateWithLifecycle()
    val pendingImportUris by vaultViewModel.pendingImportUris.collectAsStateWithLifecycle()
    val selectedVaultItem by vaultViewModel.selectedVaultItem.collectAsStateWithLifecycle()
    val decryptedBytes by vaultViewModel.decryptedBytes.collectAsStateWithLifecycle()

    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    var showMasterPinDialog by remember { mutableStateOf(false) }
    var showDecoyPinDialog by remember { mutableStateOf(false) }
    var showKillPinDialog by remember { mutableStateOf(false) }
    var showStealthDialog by remember { mutableStateOf(false) }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }
    var pinErrorTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }

    var showStegoEmbedPasswordDialog by remember { mutableStateOf(false) }
    var showStegoExtractPasswordDialog by remember { mutableStateOf(false) }

    var suggestedStegoOutputName by remember { mutableStateOf("covert_carrier_backup.mp4") }
    var suggestedStegoMimeType by remember { mutableStateOf("*/*") }

    // Storage Access Framework (SAF) Launchers for Master Backup
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        vaultViewModel.onSystemPickerFinished()
        val pwd = vaultViewModel.pendingExportPassword
        vaultViewModel.pendingExportPassword = null
        if (uri != null) {
            if (!pwd.isNullOrBlank()) {
                val isDeviceLocked = vaultViewModel.pendingExportIsDeviceLocked
                vaultViewModel.exportMasterBackup(context, pwd, uri, isDeviceLocked)
            } else {
                vaultViewModel.pendingExportUri = uri
                showExportPasswordDialog = true
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        vaultViewModel.onSystemPickerFinished()
        if (uri != null) {
            vaultViewModel.pendingImportUri = uri
            showImportPasswordDialog = true
        }
    }

    val stegoOutputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(suggestedStegoMimeType)
    ) { uri ->
        vaultViewModel.onSystemPickerFinished()
        val pwd = vaultViewModel.pendingStegoPassword
        val coverUri = vaultViewModel.pendingStegoCoverUri
        vaultViewModel.pendingStegoPassword = null
        vaultViewModel.pendingStegoCoverUri = null
        if (uri != null && coverUri != null && !pwd.isNullOrBlank()) {
            vaultViewModel.exportStegoBackup(context, pwd, coverUri, uri)
        }
    }

    val stegoCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vaultViewModel.pendingStegoCoverUri = uri
            val info = com.example.security.SteganographyManager.resolveCarrierFileInfo(context, uri)
            suggestedStegoOutputName = "covert_${info.baseName}.${info.extension}"
            suggestedStegoMimeType = info.mimeType
            vaultViewModel.onSystemPickerLaunched()
            stegoOutputLauncher.launch(suggestedStegoOutputName)
        } else {
            vaultViewModel.onSystemPickerFinished()
            vaultViewModel.pendingStegoPassword = null
        }
    }

    val stegoExtractLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        vaultViewModel.onSystemPickerFinished()
        if (uri != null) {
            vaultViewModel.pendingStegoExtractUri = uri
            showStegoExtractPasswordDialog = true
        }
    }

    androidx.compose.runtime.LaunchedEffect(vaultMode, settings.isCamouflageEnabled, settings.camouflageType) {
        val targetLockRoute = if (settings.isCamouflageEnabled) {
            if (settings.camouflageType == "NOTES") NavRoutes.Notes.route else NavRoutes.Calculator.route
        } else {
            NavRoutes.Lock.route
        }
        when (vaultMode) {
            VaultMode.REAL -> {
                navController.navigate(NavRoutes.Dashboard.route) {
                    popUpTo(targetLockRoute) { inclusive = true }
                }
            }
            VaultMode.DECOY -> {
                navController.navigate(NavRoutes.Dashboard.route) {
                    popUpTo(targetLockRoute) { inclusive = true }
                }
            }
            VaultMode.LOCKED -> {
                if (navController.currentDestination?.route != targetLockRoute) {
                    navController.navigate(targetLockRoute) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    val initialTargetLockRoute = if (settings.isCamouflageEnabled) {
        if (settings.camouflageType == "NOTES") NavRoutes.Notes.route else NavRoutes.Calculator.route
    } else {
        NavRoutes.Lock.route
    }
    NavHost(
        navController = navController,
        startDestination = if (vaultMode == VaultMode.LOCKED) initialTargetLockRoute else NavRoutes.Dashboard.route
    ) {
        // 1. Lock Screen Destination
        composable(NavRoutes.Lock.route) {
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            val lockoutSecondsRemaining by vaultViewModel.lockoutSecondsRemaining.collectAsStateWithLifecycle()
            LockScreen(
                onAuthenticateClick = onTriggerBiometrics,
                onPinSubmit = { enteredPin ->
                    val success = vaultViewModel.authenticateWithPin(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        enteredPin = enteredPin,
                        settings = settings
                    )
                    if (success) {
                        pinErrorMessage = null
                        if (vaultViewModel.vaultMode.value == VaultMode.DECOY) {
                            vaultViewModel.logIntruderAttempt(context, "DECOY_TRIGGERED", "Coercion Decoy PIN entered")
                            navController.navigate(NavRoutes.Dashboard.route) {
                                popUpTo(NavRoutes.Lock.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(NavRoutes.Dashboard.route) {
                                popUpTo(NavRoutes.Lock.route) { inclusive = true }
                            }
                        }
                    } else {
                        if (vaultViewModel.lockoutSecondsRemaining.value > 0) {
                            pinErrorMessage = "Too many failed attempts! Cooldown active."
                        } else {
                            pinErrorMessage = "Incorrect PIN. Please try again."
                        }
                        pinErrorTrigger++
                    }
                },
                errorMessage = pinErrorMessage,
                errorTrigger = pinErrorTrigger,
                lockoutSecondsRemaining = lockoutSecondsRemaining
            )
        }

        composable(NavRoutes.Calculator.route) {
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            CalculatorScreen(
                onPinSubmit = { enteredPin ->
                    val success = vaultViewModel.authenticateWithPin(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        enteredPin = enteredPin,
                        settings = settings
                    )
                    if (success) {
                        pinErrorMessage = null
                        if (vaultViewModel.vaultMode.value == VaultMode.DECOY) {
                            vaultViewModel.logIntruderAttempt(context, "DECOY_TRIGGERED", "Coercion Decoy PIN entered")
                            navController.navigate(NavRoutes.Dashboard.route) {
                                popUpTo(NavRoutes.Calculator.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(NavRoutes.Dashboard.route) {
                                popUpTo(NavRoutes.Calculator.route) { inclusive = true }
                            }
                        }
                    } else {
                        // Silent failure for calculator to maintain stealth
                    }
                }
            )
        }

        composable(NavRoutes.Notes.route) {
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            com.example.ui.screens.NotesScreen(
                onPinSubmit = { enteredPin ->
                    val success = vaultViewModel.authenticateWithPin(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        enteredPin = enteredPin,
                        settings = settings
                    )
                    if (success) {
                        pinErrorMessage = null
                        if (vaultViewModel.vaultMode.value == VaultMode.DECOY) {
                            vaultViewModel.logIntruderAttempt(context, "DECOY_TRIGGERED", "Coercion Decoy PIN entered")
                            navController.navigate(NavRoutes.Dashboard.route) {
                                popUpTo(NavRoutes.Notes.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(NavRoutes.Dashboard.route) {
                                popUpTo(NavRoutes.Notes.route) { inclusive = true }
                            }
                        }
                    }
                }
            )
        }

        // 2. Real Vault Dashboard
        composable(NavRoutes.Dashboard.route) {
            val selectedFolder by vaultViewModel.selectedFolder.collectAsStateWithLifecycle()
            val folders by vaultViewModel.folders.collectAsStateWithLifecycle()

            DashboardScreen(
                vaultItems = vaultItems,
                activeFilter = filterTab,
                selectedFolder = selectedFolder,
                folders = folders,
                statusMessage = statusMessage,
                isLoading = isLoading,
                onFilterChanged = { vaultViewModel.setFilterTab(it) },
                onSelectFolder = { vaultViewModel.selectFolder(it) },
                onCreateFolder = { vaultViewModel.createFolder(it) },
                onDeleteFolder = { vaultViewModel.deleteFolder(it) },
                onRenameFolder = { oldName, newName -> vaultViewModel.renameFolder(oldName, newName) },
                onMoveItem = { item, destFolder -> vaultViewModel.moveItemToFolder(item.id, destFolder) },
                onMoveItems = { items, destFolder -> vaultViewModel.moveItemsToFolder(items, destFolder) },
                onCopyItem = { item, destFolder -> vaultViewModel.copyItemToFolder(context, item, destFolder) },
                onFilesSelected = { vaultViewModel.onFilesSelected(it) },
                onItemClick = { item ->
                    vaultViewModel.openViewer(context, item)
                    navController.navigate(NavRoutes.MediaViewer.createRoute(item.id))
                },
                onDeleteItem = { vaultViewModel.deleteVaultItem(context, it) },
                onDeleteItems = { vaultViewModel.deleteVaultItems(context, it) },
                onExportItem = { vaultViewModel.exportVaultItem(context, it) },
                onExportItems = { vaultViewModel.exportVaultItems(context, it) },
                onLockClick = {
                    vaultViewModel.lockVault()
                    onLockApp()
                },
                onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) },
                onNavigateToAbout = { navController.navigate(NavRoutes.About.route) },
                onNavigateToPasswords = { navController.navigate(NavRoutes.PasswordManager.route) },
                isThumbnailsEnabled = settings.isThumbnailsEnabled,
                onToggleThumbnails = { settingsViewModel.setThumbnailsEnabled(!settings.isThumbnailsEnabled, context) },
                onPickerLaunched = { vaultViewModel.onSystemPickerLaunched() },
                onPickerFinished = { vaultViewModel.onSystemPickerFinished() },
                onClearStatusMessage = { vaultViewModel.clearStatusMessage() }
            )

            // Import Prompt Dialog
            if (pendingImportUris.isNotEmpty()) {
                ImportPromptDialog(
                    selectedUris = pendingImportUris,
                    onDismiss = { vaultViewModel.clearPendingImport() },
                    onConfirmImport = { deleteOriginal ->
                        vaultViewModel.importPendingFiles(context, deleteOriginal)
                    }
                )
            }

            // Real-Time Hardware Encryption Progress HUD
            if (importProgress.isImporting) {
                EncryptionProgressDialog(progressState = importProgress)
            }
        }

        // 4. Settings Screen
        composable(NavRoutes.Settings.route) {
            val auditResult by settingsViewModel.auditResult.collectAsStateWithLifecycle()
            val isAuditing by settingsViewModel.isAuditing.collectAsStateWithLifecycle()

            SettingsScreen(
                settings = settings,
                statusMessage = statusMessage,
                auditResult = auditResult,
                isAuditing = isAuditing,
                onRunAudit = { settingsViewModel.runSecurityAudit() },
                onBackClick = { navController.popBackStack() },
                onToggleBiometrics = { settingsViewModel.setBiometricsEnabled(it) },
                onChangeMasterPinClick = { showMasterPinDialog = true },
                onChangeDecoyPinClick = { showDecoyPinDialog = true },
                onTogglePanicFlip = { settingsViewModel.setPanicFlipEnabled(it) },
                onToggleThumbnails = { settingsViewModel.setThumbnailsEnabled(it, context) },
                onToggleCamouflage = { settingsViewModel.setCamouflageEnabled(context, it) },
                onChangeCamouflageType = { settingsViewModel.setCamouflageType(it) },
                onToggleScreenProtection = { settingsViewModel.setScreenProtectionEnabled(it) },
                onExportBackupClick = { showExportPasswordDialog = true },
                onImportBackupClick = {
                    vaultViewModel.onSystemPickerLaunched()
                    importBackupLauncher.launch(arrayOf("*/*"))
                },
                onViewIntruderLogsClick = { navController.navigate(NavRoutes.IntruderLogs.route) },
                onOpenStealthDialog = { showStealthDialog = true },
                onToggleKillPin = { settingsViewModel.setKillPinEnabled(it) },
                onChangeKillPinClick = { showKillPinDialog = true },
                onToggleIntruderSelfie = { settingsViewModel.setIntruderSelfieEnabled(it) },
                onToggleDeadManSwitch = { settingsViewModel.setDeadManSwitchEnabled(context, it) },
                onChangeDeadManDays = { settingsViewModel.setDeadManDays(it) },
                onExecuteSelfDestructClick = { vaultViewModel.executeSelfDestruct(context) },
                onEmbedStegoClick = { showStegoEmbedPasswordDialog = true },
                onExtractStegoClick = {
                    vaultViewModel.onSystemPickerLaunched()
                    stegoExtractLauncher.launch(arrayOf("video/*", "application/pdf", "image/*", "audio/*", "*/*"))
                },
                onNavigateToPasswords = {
                    navController.navigate(NavRoutes.PasswordManager.route)
                },
                onNavigateToEncryptionInspector = {
                    navController.navigate(NavRoutes.EncryptionInspector.route)
                },
                onHelpClick = {
                    navController.navigate(NavRoutes.Help.route)
                },
                onAboutClick = {
                    navController.navigate(NavRoutes.About.route)
                }
            )

            if (showMasterPinDialog) {
                ChangePinDialog(
                    title = "Change Master PIN",
                    subtitle = "This PIN unlocks your primary encrypted vault.",
                    onDismiss = { showMasterPinDialog = false },
                    onSavePin = {
                        settingsViewModel.updateMasterPin(it)
                        showMasterPinDialog = false
                    }
                )
            }

            if (showDecoyPinDialog) {
                ChangePinDialog(
                    title = "Configure Decoy PIN",
                    subtitle = "This PIN opens the fake decoy vault to protect you under duress.",
                    onDismiss = { showDecoyPinDialog = false },
                    onSavePin = {
                        settingsViewModel.updateDecoyPin(it)
                        showDecoyPinDialog = false
                    }
                )
            }

            if (showKillPinDialog) {
                ChangePinDialog(
                    title = "Configure Kill PIN",
                    subtitle = "Entering this PIN on the lock screen will IMMEDIATELY execute nuclear self-destruct.",
                    onDismiss = { showKillPinDialog = false },
                    onSavePin = {
                        settingsViewModel.updateKillPin(it)
                        showKillPinDialog = false
                    }
                )
            }

            if (showStealthDialog) {
                StealthModeDialog(
                    isEnabled = settings.isCamouflageEnabled,
                    onDismiss = { showStealthDialog = false },
                    onToggleStealth = {
                        settingsViewModel.setCamouflageEnabled(context, it)
                        showStealthDialog = false
                    }
                )
            }
        }

        // 5. Intruder Logs Screen
        composable(NavRoutes.IntruderLogs.route) {
            val logsFlow = remember { AppDatabase.getDatabase(context).intruderLogDao().getAllLogs() }
            val logs by logsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

            IntruderLogsScreen(
                logs = logs,
                onBackClick = { navController.popBackStack() },
                onClearLogsClick = { vaultViewModel.clearIntruderLogs(context) }
            )
        }

        // 6. About Screen
        composable(NavRoutes.About.route) {
            AboutScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // 7. Help Screen
        composable(NavRoutes.Help.route) {
            HelpScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // 8. Media Viewer Screen
        composable(
            route = NavRoutes.MediaViewer.route,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
            val currentItem = selectedVaultItem ?: vaultItems.find { it.id == itemId }

            if (currentItem != null) {
                MediaViewerScreen(
                    vaultItem = currentItem,
                    decryptedBytes = decryptedBytes,
                    isProcessing = isProcessing,
                    vaultRepository = vaultViewModel.repository,
                    onClose = {
                        vaultViewModel.closeViewer()
                        navController.popBackStack()
                    },
                    onDelete = {
                        vaultViewModel.deleteVaultItem(context, currentItem)
                        navController.popBackStack()
                    },
                    onExport = {
                        vaultViewModel.exportVaultItem(context, currentItem)
                    }
                )
            }
        }

        // 9. Password Manager Screen
        composable(NavRoutes.PasswordManager.route) {
            PasswordManagerScreen(
                isDecoy = vaultMode == VaultMode.DECOY,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 10. Encryption Inspector Screen
        composable(NavRoutes.EncryptionInspector.route) {
            EncryptionInspectorScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }

    if (vaultMode != VaultMode.LOCKED) {
        // Export Backup Password Prompt
        if (showExportPasswordDialog) {
            BackupPasswordDialog(
                title = "Encrypt Master Backup",
                subtitle = "Derives memory-hard key via Argon2id. Optional hardware wrapping binds backup to this device.",
                isExportMode = true,
                onDismiss = {
                    showExportPasswordDialog = false
                    vaultViewModel.pendingExportPassword = null
                    vaultViewModel.pendingExportUri = null
                },
                onConfirm = { password, isDeviceLocked, _ ->
                    showExportPasswordDialog = false
                    vaultViewModel.pendingExportIsDeviceLocked = isDeviceLocked
                    val pendingUri = vaultViewModel.pendingExportUri
                    if (pendingUri != null) {
                        vaultViewModel.pendingExportUri = null
                        vaultViewModel.exportMasterBackup(context, password, pendingUri, isDeviceLocked)
                    } else {
                        vaultViewModel.pendingExportPassword = password
                        vaultViewModel.onSystemPickerLaunched()
                        exportBackupLauncher.launch("vault_master_backup_${System.currentTimeMillis()}.bin")
                    }
                }
            )
        }

        // Import Backup Password Prompt
        if (showImportPasswordDialog && vaultViewModel.pendingImportUri != null) {
            BackupPasswordDialog(
                title = "Decrypt Master Backup",
                subtitle = "Enter the password used when creating this backup file (V3 Argon2id / V2).",
                isExportMode = false,
                showRestoreModeOptions = true,
                onDismiss = {
                    showImportPasswordDialog = false
                    vaultViewModel.pendingImportUri = null
                },
                onConfirm = { password, _, isReplaceMode ->
                    showImportPasswordDialog = false
                    val uri = vaultViewModel.pendingImportUri ?: return@BackupPasswordDialog
                    vaultViewModel.pendingImportUri = null
                    vaultViewModel.importMasterBackup(context, password, uri, isReplaceMode)
                }
            )
        }

        // Stego Embed Password Prompt
        if (showStegoEmbedPasswordDialog) {
            BackupPasswordDialog(
                title = "Encrypt Multi-Carrier Stego",
                subtitle = "Enter a password to encrypt your vault before concealing it inside the cover video, PDF, or image.",
                isExportMode = false,
                onDismiss = {
                    showStegoEmbedPasswordDialog = false
                    vaultViewModel.pendingStegoPassword = null
                    vaultViewModel.pendingStegoCoverUri = null
                },
                onConfirm = { password, _, _ ->
                    showStegoEmbedPasswordDialog = false
                    vaultViewModel.pendingStegoPassword = password
                    vaultViewModel.onSystemPickerLaunched()
                    stegoCoverLauncher.launch(arrayOf("video/*", "application/pdf", "image/*", "audio/*", "*/*"))
                }
            )
        }

        // Stego Extract Password Prompt
        if (showStegoExtractPasswordDialog && vaultViewModel.pendingStegoExtractUri != null) {
            BackupPasswordDialog(
                title = "Decrypt Multi-Carrier Stego",
                subtitle = "Enter the password to extract and restore the vault hidden inside this carrier file.",
                isExportMode = false,
                showRestoreModeOptions = true,
                onDismiss = {
                    showStegoExtractPasswordDialog = false
                    vaultViewModel.pendingStegoExtractUri = null
                },
                onConfirm = { password, _, isReplaceMode ->
                    showStegoExtractPasswordDialog = false
                    val uri = vaultViewModel.pendingStegoExtractUri ?: return@BackupPasswordDialog
                    vaultViewModel.pendingStegoExtractUri = null
                    vaultViewModel.importStegoBackup(context, password, uri, isReplaceMode)
                }
            )
        }

        // Master Backup & Disaster Recovery Live Progress Bar HUD
        if (backupRestoreProgress.isActive) {
            BackupRestoreProgressDialog(
                state = backupRestoreProgress,
                onDismiss = { vaultViewModel.dismissBackupRestoreProgress() }
            )
        }

        // Screen Recording / Capture Warning Alert Dialog
        val isScreenRecordingWarningVisible by vaultViewModel.isScreenRecordingWarningVisible.collectAsStateWithLifecycle()
        if (isScreenRecordingWarningVisible) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { vaultViewModel.dismissScreenRecordingWarning() },
                icon = {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                        contentDescription = "Screen Recording Detected",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    androidx.compose.material3.Text(
                        "Screen Recording Detected!",
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                },
                text = {
                    androidx.compose.material3.Text(
                        "Active screen recording, screen mirroring, or virtual display capture was detected while the vault was active. The vault has been automatically locked and encrypted in memory to protect your confidential files.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = { vaultViewModel.dismissScreenRecordingWarning() }
                    ) {
                        androidx.compose.material3.Text("Acknowledge & Dismiss")
                    }
                }
            )
        }
    }
}
