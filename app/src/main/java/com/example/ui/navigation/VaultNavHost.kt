package com.example.ui.navigation

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.ui.components.ChangePinDialog
import com.example.ui.components.EncryptionProgressDialog
import com.example.ui.components.ImportPromptDialog
import com.example.ui.components.StealthModeDialog
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.HelpScreen
import com.example.ui.screens.CalculatorScreen
import com.example.ui.screens.DashboardScreen
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

    // Backup & Restore SAF dialog states
    var exportTargetUri by remember { mutableStateOf<Uri?>(null) }
    var importSourceUri by remember { mutableStateOf<Uri?>(null) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }

    var stegoCoverUri by remember { mutableStateOf<Uri?>(null) }
    var stegoOutputUri by remember { mutableStateOf<Uri?>(null) }
    var stegoExtractUri by remember { mutableStateOf<Uri?>(null) }
    var showStegoEmbedPasswordDialog by remember { mutableStateOf(false) }
    var showStegoExtractPasswordDialog by remember { mutableStateOf(false) }

    // Storage Access Framework (SAF) Launchers for Master Backup
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            exportTargetUri = uri
            showExportPasswordDialog = true
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importSourceUri = uri
            showImportPasswordDialog = true
        }
    }

    var suggestedStegoOutputName by remember { mutableStateOf("covert_carrier_backup.mp4") }
    var suggestedStegoMimeType by remember { mutableStateOf("*/*") }
    var triggerStegoOutput by remember { mutableStateOf(false) }

    val stegoOutputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(suggestedStegoMimeType)
    ) { uri ->
        if (uri != null) {
            stegoOutputUri = uri
            showStegoEmbedPasswordDialog = true
        }
    }

    val stegoCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            stegoCoverUri = uri
            val info = com.example.security.SteganographyManager.resolveCarrierFileInfo(context, uri)
            suggestedStegoOutputName = "covert_${info.baseName}.${info.extension}"
            suggestedStegoMimeType = info.mimeType
            triggerStegoOutput = true
        }
    }

    if (triggerStegoOutput) {
        androidx.compose.runtime.LaunchedEffect(triggerStegoOutput) {
            stegoOutputLauncher.launch(suggestedStegoOutputName)
            triggerStegoOutput = false
        }
    }

    val stegoExtractLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            stegoExtractUri = uri
            showStegoExtractPasswordDialog = true
        }
    }

    androidx.compose.runtime.LaunchedEffect(vaultMode, settings.isCamouflageEnabled) {
        val targetLockRoute = if (settings.isCamouflageEnabled) NavRoutes.Calculator.route else NavRoutes.Lock.route
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

    val initialTargetLockRoute = if (settings.isCamouflageEnabled) NavRoutes.Calculator.route else NavRoutes.Lock.route
    NavHost(
        navController = navController,
        startDestination = if (vaultMode == VaultMode.LOCKED) initialTargetLockRoute else NavRoutes.Dashboard.route
    ) {
        // 1. Lock Screen Destination
        composable(NavRoutes.Lock.route) {
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
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
                        pinErrorMessage = "Incorrect PIN. Please try again."
                        pinErrorTrigger++
                    }
                },
                errorMessage = pinErrorMessage,
                errorTrigger = pinErrorTrigger
            )
        }

        composable(NavRoutes.Calculator.route) {
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
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
                onCopyItem = { item, destFolder -> vaultViewModel.copyItemToFolder(context, item, destFolder) },
                onFilesSelected = { vaultViewModel.onFilesSelected(it) },
                onItemClick = { item ->
                    vaultViewModel.openViewer(context, item)
                    navController.navigate(NavRoutes.MediaViewer.createRoute(item.id))
                },
                onDeleteItem = { vaultViewModel.deleteVaultItem(context, it) },
                onExportItem = { vaultViewModel.exportVaultItem(context, it) },
                onLockClick = {
                    vaultViewModel.lockVault()
                    onLockApp()
                },
                onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) },
                onNavigateToAbout = { navController.navigate(NavRoutes.About.route) },
                onNavigateToPasswords = { navController.navigate(NavRoutes.PasswordManager.route) },
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
                onToggleCamouflage = { settingsViewModel.setCamouflageEnabled(context, it) },
                onToggleScreenProtection = { settingsViewModel.setScreenProtectionEnabled(it) },
                onExportBackupClick = { exportBackupLauncher.launch("vault_master_backup_${System.currentTimeMillis()}.bin") },
                onImportBackupClick = { importBackupLauncher.launch(arrayOf("*/*")) },
                onViewIntruderLogsClick = { navController.navigate(NavRoutes.IntruderLogs.route) },
                onOpenStealthDialog = { showStealthDialog = true },
                onToggleKillPin = { settingsViewModel.setKillPinEnabled(it) },
                onChangeKillPinClick = { showKillPinDialog = true },
                onToggleIntruderSelfie = { settingsViewModel.setIntruderSelfieEnabled(it) },
                onToggleDeadManSwitch = { settingsViewModel.setDeadManSwitchEnabled(context, it) },
                onChangeDeadManDays = { settingsViewModel.setDeadManDays(it) },
                onExecuteSelfDestructClick = { vaultViewModel.executeSelfDestruct(context) },
                onEmbedStegoClick = {
                    stegoCoverLauncher.launch(arrayOf("video/*", "application/pdf", "image/*", "audio/*", "*/*"))
                },
                onExtractStegoClick = {
                    stegoExtractLauncher.launch(arrayOf("video/*", "application/pdf", "image/*", "audio/*", "*/*"))
                },
                onNavigateToPasswords = {
                    navController.navigate(NavRoutes.PasswordManager.route)
                },
                onHelpClick = {
                    navController.navigate(NavRoutes.Help.route)
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
    }

    if (vaultMode != VaultMode.LOCKED) {
        // Export Backup Password Prompt
        if (showExportPasswordDialog && exportTargetUri != null) {
            BackupPasswordDialog(
                title = "Encrypt Master Backup",
                subtitle = "Enter a password to derive an AES-256 key via PBKDF2 for this backup.",
                onDismiss = {
                    showExportPasswordDialog = false
                    exportTargetUri = null
                },
                onConfirm = { password ->
                    showExportPasswordDialog = false
                    val uri = exportTargetUri ?: return@BackupPasswordDialog
                    exportTargetUri = null
                    vaultViewModel.exportMasterBackup(context, password, uri)
                }
            )
        }

        // Import Backup Password Prompt
        if (showImportPasswordDialog && importSourceUri != null) {
            BackupPasswordDialog(
                title = "Decrypt Master Backup",
                subtitle = "Enter the password used when creating this backup file.",
                onDismiss = {
                    showImportPasswordDialog = false
                    importSourceUri = null
                },
                onConfirm = { password ->
                    showImportPasswordDialog = false
                    val uri = importSourceUri ?: return@BackupPasswordDialog
                    importSourceUri = null
                    vaultViewModel.importMasterBackup(context, password, uri)
                }
            )
        }

        // Stego Embed Password Prompt
        if (showStegoEmbedPasswordDialog && stegoCoverUri != null && stegoOutputUri != null) {
            BackupPasswordDialog(
                title = "Encrypt Multi-Carrier Stego",
                subtitle = "Enter a password to encrypt your vault before concealing it inside the cover video, PDF, or image.",
                onDismiss = {
                    showStegoEmbedPasswordDialog = false
                    stegoCoverUri = null
                    stegoOutputUri = null
                },
                onConfirm = { password ->
                    showStegoEmbedPasswordDialog = false
                    vaultViewModel.exportStegoBackup(context, password, stegoCoverUri!!, stegoOutputUri!!)
                    stegoCoverUri = null
                    stegoOutputUri = null
                }
            )
        }

        // Stego Extract Password Prompt
        if (showStegoExtractPasswordDialog && stegoExtractUri != null) {
            BackupPasswordDialog(
                title = "Decrypt Multi-Carrier Stego",
                subtitle = "Enter the password to extract and restore the vault hidden inside this carrier file.",
                onDismiss = {
                    showStegoExtractPasswordDialog = false
                    stegoExtractUri = null
                },
                onConfirm = { password ->
                    showStegoExtractPasswordDialog = false
                    vaultViewModel.importStegoBackup(context, password, stegoExtractUri!!)
                    stegoExtractUri = null
                }
            )
        }
    }
}
