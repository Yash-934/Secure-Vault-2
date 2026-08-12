package com.example

import android.app.Activity.RESULT_OK
import android.content.IntentSender
import android.os.Bundle
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.data.AppDatabase
import com.example.data.VaultRepository
import com.example.data.local.SettingsDataStore
import com.example.security.BiometricPromptManager
import com.example.security.PanicSensorManager
import com.example.ui.VaultViewModel
import com.example.ui.navigation.VaultNavHost
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.SettingsViewModel

class MainActivity : FragmentActivity() {

    private lateinit var biometricPromptManager: BiometricPromptManager
    private lateinit var panicSensorManager: PanicSensorManager

    private val vaultViewModel: VaultViewModel by viewModels {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = VaultRepository(database.vaultDao())
        VaultViewModel.Factory(repository)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        val dataStore = SettingsDataStore(applicationContext)
        val auditEngine = com.example.security.SecurityAuditEngine(applicationContext)
        SettingsViewModel.Factory(dataStore, auditEngine)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        biometricPromptManager = BiometricPromptManager(this)
        panicSensorManager = PanicSensorManager(this)

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val deleteIntentSender by vaultViewModel.deleteIntentSender.collectAsStateWithLifecycle()
                val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
                val isUnlocked by vaultViewModel.isUnlocked.collectAsStateWithLifecycle()

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

                // Lifecycle Observer: Locks vault when app is backgrounded
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                vaultViewModel.lockVault()
                            }
                            Lifecycle.Event.ON_RESUME -> {
                                if (!vaultViewModel.isUnlocked.value && settings.isBiometricsEnabled) {
                                    triggerBiometricAuth()
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
                        onLockApp = {
                            moveTaskToBack(false)
                        }
                    )
                }
            }
        }
    }

    private fun triggerBiometricAuth() {
        if (biometricPromptManager.canAuthenticate()) {
            biometricPromptManager.showBiometricPrompt(
                activity = this,
                title = "Secure Vault Unlock",
                subtitle = "Authenticate with Fingerprint, Face ID or PIN to unlock"
            ) { result ->
                when (result) {
                    is BiometricPromptManager.AuthResult.Success -> {
                        vaultViewModel.unlockRealVault()
                    }
                    is BiometricPromptManager.AuthResult.Error -> {
                        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        } else {
            // Devices without biometric hardware configured stay on the Lock Screen
        }
    }
}
