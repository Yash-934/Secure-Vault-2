package com.example.ui.navigation

import android.content.Context
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
import com.example.data.local.VaultSettings
import com.example.domain.model.VaultMode
import com.example.ui.VaultViewModel
import com.example.ui.components.ChangePinDialog
import com.example.ui.components.ImportPromptDialog
import com.example.ui.components.StealthModeDialog
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DecoyVaultScreen
import com.example.ui.screens.LockScreen
import com.example.ui.screens.MediaViewerScreen
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
    val isLoading by vaultViewModel.isLoading.collectAsStateWithLifecycle()
    val statusMessage by vaultViewModel.statusMessage.collectAsStateWithLifecycle()
    val pendingImportUris by vaultViewModel.pendingImportUris.collectAsStateWithLifecycle()
    val selectedVaultItem by vaultViewModel.selectedVaultItem.collectAsStateWithLifecycle()
    val decryptedBytes by vaultViewModel.decryptedBytes.collectAsStateWithLifecycle()

    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    var showMasterPinDialog by remember { mutableStateOf(false) }
    var showDecoyPinDialog by remember { mutableStateOf(false) }
    var showStealthDialog by remember { mutableStateOf(false) }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(vaultMode) {
        when (vaultMode) {
            VaultMode.REAL -> {
                navController.navigate(NavRoutes.Dashboard.route) {
                    popUpTo(NavRoutes.Lock.route) { inclusive = true }
                }
            }
            VaultMode.DECOY -> {
                navController.navigate(NavRoutes.DecoyVault.route) {
                    popUpTo(NavRoutes.Lock.route) { inclusive = true }
                }
            }
            VaultMode.LOCKED -> {
                if (navController.currentDestination?.route != NavRoutes.Lock.route) {
                    navController.navigate(NavRoutes.Lock.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (vaultMode == VaultMode.LOCKED) NavRoutes.Lock.route else NavRoutes.Dashboard.route
    ) {
        // 1. Lock Screen Destination
        composable(NavRoutes.Lock.route) {
            LockScreen(
                onAuthenticateClick = onTriggerBiometrics,
                onPinSubmit = { enteredPin ->
                    val success = vaultViewModel.authenticateWithPin(
                        enteredPin = enteredPin,
                        masterPin = settings.masterPin,
                        decoyPin = settings.decoyPin
                    )
                    if (success) {
                        pinErrorMessage = null
                        if (vaultViewModel.vaultMode.value == VaultMode.DECOY) {
                            navController.navigate(NavRoutes.DecoyVault.route) {
                                popUpTo(NavRoutes.Lock.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(NavRoutes.Dashboard.route) {
                                popUpTo(NavRoutes.Lock.route) { inclusive = true }
                            }
                        }
                    } else {
                        pinErrorMessage = "Incorrect PIN. Please try again."
                    }
                },
                errorMessage = pinErrorMessage
            )
        }

        // 2. Real Vault Dashboard
        composable(NavRoutes.Dashboard.route) {
            DashboardScreen(
                vaultItems = vaultItems,
                activeFilter = filterTab,
                statusMessage = statusMessage,
                isLoading = isLoading,
                onFilterChanged = { vaultViewModel.setFilterTab(it) },
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
        }

        // 3. Decoy Vault Destination
        composable(NavRoutes.DecoyVault.route) {
            DecoyVaultScreen(
                onLockClick = {
                    vaultViewModel.lockVault()
                    onLockApp()
                }
            )
        }

        // 4. Settings Screen
        composable(NavRoutes.Settings.route) {
            val auditResult by settingsViewModel.auditResult.collectAsStateWithLifecycle()
            val isAuditing by settingsViewModel.isAuditing.collectAsStateWithLifecycle()

            SettingsScreen(
                settings = settings,
                auditResult = auditResult,
                isAuditing = isAuditing,
                onRunAudit = { settingsViewModel.runSecurityAudit() },
                onBackClick = { navController.popBackStack() },
                onToggleBiometrics = { settingsViewModel.setBiometricsEnabled(it) },
                onChangeMasterPinClick = { showMasterPinDialog = true },
                onChangeDecoyPinClick = { showDecoyPinDialog = true },
                onTogglePanicFlip = { settingsViewModel.setPanicFlipEnabled(it) },
                onOpenStealthDialog = { showStealthDialog = true }
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

            if (showStealthDialog) {
                StealthModeDialog(
                    isEnabled = settings.isStealthModeEnabled,
                    onDismiss = { showStealthDialog = false },
                    onToggleStealth = {
                        settingsViewModel.setStealthModeEnabled(it)
                        showStealthDialog = false
                    }
                )
            }
        }

        // 5. About Screen
        composable(NavRoutes.About.route) {
            AboutScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // 6. Media Viewer Screen
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
    }
}
