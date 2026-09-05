package com.quantumvault.wkqpx

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import com.quantumvault.wkqpx.data.AppDatabase
import com.quantumvault.wkqpx.data.VaultRepository
import com.quantumvault.wkqpx.data.local.SettingsDataStore
import com.quantumvault.wkqpx.security.BiometricPromptManager
import com.quantumvault.wkqpx.security.PanicSensorManager
import com.quantumvault.wkqpx.ui.VaultViewModel
import com.quantumvault.wkqpx.ui.navigation.VaultNavHost
import com.quantumvault.wkqpx.ui.screens.ErrorFallbackActivity
import com.quantumvault.wkqpx.ui.theme.MyApplicationTheme
import com.quantumvault.wkqpx.ui.viewmodel.SettingsViewModel
import com.quantumvault.wkqpx.util.VaultLogger

class MainActivity : FragmentActivity() {

    private lateinit var biometricPromptManager: BiometricPromptManager
    private lateinit var panicSensorManager: PanicSensorManager

    private val vaultViewModel: VaultViewModel by viewModels {
        VaultViewModel.Factory(applicationContext)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        val dataStore = SettingsDataStore(applicationContext)
        val auditEngine = com.quantumvault.wkqpx.security.SecurityAuditEngine(applicationContext)
        SettingsViewModel.Factory(dataStore, auditEngine)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Anti-Tamper: Immediately kill process if memory or code integrity is compromised in production
        com.quantumvault.wkqpx.security.NativeIntegrityVerifier.failClosedIfTampered(this)
        
        // Anti-Malware Killswitch Evaluation
        val isRooted = try {
            com.quantumvault.wkqpx.security.RootDetectionManager.isDeviceRooted(applicationContext) && !com.quantumvault.wkqpx.security.RootDetectionManager.isEmulator()
        } catch (e: Exception) {
            false
        }

        if (VaultApplicationState.isRecoveryRequired) {
            val reason = VaultApplicationState.recoveryFailureReason ?: "State Recovery Required"
            val intent = Intent(this, ErrorFallbackActivity::class.java).apply {
                putExtra(ErrorFallbackActivity.EXTRA_ERROR_MESSAGE, reason)
                putExtra(
                    ErrorFallbackActivity.EXTRA_STACK_TRACE,
                    VaultApplicationState.startupError?.let { Log.getStackTraceString(it) } ?: "Startup disaster recovery check failed."
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            finish()
            return
        }

        enableEdgeToEdge()

        biometricPromptManager = BiometricPromptManager(this)
        panicSensorManager = PanicSensorManager(this)

        // Validate biometric enrollment invariant on startup
        lifecycleScope.launch {
            com.quantumvault.wkqpx.security.VaultKeyManager.validateBiometricEnrollmentState(
                applicationContext,
                com.quantumvault.wkqpx.data.local.SettingsDataStore(applicationContext)
            )
        }

        // Clear any leftover temporary files from previous sessions
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            cleanCacheDirectory(applicationContext)
        }

        setContent {
            MyApplicationTheme {
                if (isRooted) {
                    com.quantumvault.wkqpx.ui.screens.RootWarningScreen()
                    return@MyApplicationTheme
                }

                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                val deleteIntentSender by vaultViewModel.deleteIntentSender.collectAsStateWithLifecycle()
                val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
                val isUnlocked by vaultViewModel.isUnlocked.collectAsStateWithLifecycle()

                // Dynamically update Anti-Screen Capture (FLAG_SECURE)
                // In production, FLAG_SECURE is always enforced. It is bypassed only on BuildConfig.DEBUG or Emulator.
                LaunchedEffect(settings.isScreenProtectionEnabled, currentRoute, isUnlocked) {
                    val forceSecure = currentRoute in listOf("auth", "root_warning", "encryption_inspector") || !isUnlocked
                    val isDebugOrEmulator = BuildConfig.DEBUG || com.quantumvault.wkqpx.security.RootDetectionManager.isEmulator()
                    if (!isDebugOrEmulator) {
                        if (forceSecure || settings.isScreenProtectionEnabled) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    } else {
                        // Bypassed in DEBUG/Emulator to allow browser streaming preview
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                // MediaStore delete permission launcher for Android 10+
                val deleteIntentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        Toast.makeText(this, "Original deleted from gallery.", Toast.LENGTH_SHORT).show()
                    }
                    vaultViewModel.clearDeleteIntentSender()
                }

                LaunchedEffect(deleteIntentSender) {
                    deleteIntentSender?.let { sender ->
                        val request = IntentSenderRequest.Builder(sender).build()
                        deleteIntentLauncher.launch(request)
                    }
                }

                // Active Screen Capture Protection & Virtual Display Monitor
                DisposableEffect(isUnlocked) {
                    var displayListener: android.hardware.display.DisplayManager.DisplayListener? = null
                    if (isUnlocked) {
                        vaultViewModel.checkAndEnforceScreenRecordingProtection(applicationContext)
                        displayListener = com.quantumvault.wkqpx.security.ScreenCaptureDetector.registerDisplayListener(applicationContext) {
                            runOnUiThread {
                                vaultViewModel.checkAndEnforceScreenRecordingProtection(applicationContext)
                            }
                        }
                    }
                    onDispose {
                        com.quantumvault.wkqpx.security.ScreenCaptureDetector.unregisterDisplayListener(applicationContext, displayListener)
                    }
                }

                // Panic Flip Sensor Listener: Locks vault & exits app if flipped face-down
                DisposableEffect(isUnlocked, settings.isPanicFlipEnabled) {
                    if (isUnlocked && settings.isPanicFlipEnabled) {
                        panicSensorManager.startListening {
                            runOnUiThread {
                                vaultViewModel.lockVault()
                                Toast.makeText(applicationContext, "Panic Flip triggered. Vault locked.", Toast.LENGTH_SHORT).show()
                                moveTaskToBack(true)
                            }
                        }
                    } else {
                        panicSensorManager.stopListening()
                    }
                    onDispose {
                        panicSensorManager.stopListening()
                    }
                }

                // Lifecycle Observer: Locks vault when app is backgrounded (ignoring transient system pickers and biometric prompts)
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                if (!vaultViewModel.isSystemPickerActive && !vaultViewModel.isBiometricPromptActive) {
                                    vaultViewModel.lockVault()
                                }
                            }
                            else -> {}
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose {
                        lifecycle.removeObserver(observer)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    VaultNavHost(
                        navController = navController,
                        context = this,
                        vaultViewModel = vaultViewModel,
                        settingsViewModel = settingsViewModel,
                        onTriggerBiometrics = { triggerBiometricAuth() },
                        onEnrollBiometrics = { triggerBiometricEnroll() },
                        onDisableBiometrics = {
                            com.quantumvault.wkqpx.security.VaultKeyManager.removeBiometricEnvelope(this)
                            settingsViewModel.setBiometricsEnabled(false)
                            settingsViewModel.setTwoFactorAuthEnabled(false)
                        },
                        onLockApp = {
                            moveTaskToBack(false)
                        }
                    )
                }
            }
        }
    }

    
    private fun triggerBiometricAuth() {
        val is2FA = settingsViewModel.settings.value.isTwoFactorAuthEnabled
        if (is2FA && !vaultViewModel.isTwoFactorAwaitingBiometric.value) {
            Toast.makeText(this, "2FA Active: Please enter Master PIN first.", Toast.LENGTH_SHORT).show()
            return
        }

        vaultViewModel.onBiometricPromptLaunched()
        biometricPromptManager.showBiometricUnlockPrompt(this) { result ->
            vaultViewModel.onBiometricPromptFinished()
            when (result) {
                is BiometricPromptManager.AuthResult.Success -> {
                    vaultViewModel.resetTwoFactorState()
                    vaultViewModel.unlockRealVault()
                    if (is2FA) {
                        Toast.makeText(this, "2FA Verified • Quantum Vault Unlocked", Toast.LENGTH_SHORT).show()
                    }
                }
                is BiometricPromptManager.AuthResult.EnvelopeMissing -> {
                    settingsViewModel.setBiometricsEnabled(false)
                    settingsViewModel.setTwoFactorAuthEnabled(false)
                    vaultViewModel.triggerBiometricSetupPrompt()
                }
                is BiometricPromptManager.AuthResult.KeyInvalidated -> {
                    Toast.makeText(this, "Biometric enrollment changed. Please unlock with PIN to re-enroll.", Toast.LENGTH_LONG).show()
                    com.quantumvault.wkqpx.security.VaultKeyManager.removeBiometricEnvelope(this)
                    settingsViewModel.setBiometricsEnabled(false)
                    settingsViewModel.setTwoFactorAuthEnabled(false)
                }
                is BiometricPromptManager.AuthResult.Cancelled -> {
                    if (is2FA) {
                        Toast.makeText(this, "2FA Verification Paused. Tap fingerprint icon to complete.", Toast.LENGTH_SHORT).show()
                    }
                }
                is BiometricPromptManager.AuthResult.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    vaultViewModel.logIntruderAttempt(applicationContext, "BIOMETRIC_FAILED", result.message)
                }
                else -> {}
            }
        }
    }

    private fun triggerBiometricEnroll() {
        vaultViewModel.onBiometricPromptLaunched()
        biometricPromptManager.showBiometricEnrollPrompt(this) { result ->
            vaultViewModel.onBiometricPromptFinished()
            when (result) {
                is BiometricPromptManager.AuthResult.Success -> {
                    settingsViewModel.setBiometricsEnabled(true)
                    Toast.makeText(this, "Biometric unlock enrolled successfully.", Toast.LENGTH_SHORT).show()
                }
                is BiometricPromptManager.AuthResult.AuthenticationRequired -> {
                    settingsViewModel.setBiometricsEnabled(false)
                    vaultViewModel.triggerBiometricSetupPrompt()
                }
                is BiometricPromptManager.AuthResult.Error -> {
                    settingsViewModel.setBiometricsEnabled(false)
                    val msg = result.message
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
                is BiometricPromptManager.AuthResult.KeyInvalidated -> {
                    settingsViewModel.setBiometricsEnabled(false)
                    Toast.makeText(this, "Biometric hardware state invalidated.", Toast.LENGTH_LONG).show()
                }
                else -> {
                    settingsViewModel.setBiometricsEnabled(false)
                }
            }
        }
    }
    companion object {
        fun cleanCacheDirectory(context: android.content.Context) {
            try {
                val cacheDir = context.cacheDir ?: return
                cacheDir.listFiles()?.forEach { file ->
                    try {
                        if (file.isDirectory) {
                            file.deleteRecursively()
                        } else {
                            file.delete()
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }
    }
}
