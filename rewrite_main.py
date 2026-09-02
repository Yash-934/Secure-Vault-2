import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Make a backup
with open("app/src/main/java/com/example/MainActivity.kt.bak", "w") as f:
    f.write(content)

# Add onEnrollBiometrics and onDisableBiometrics
content = content.replace(
"""                    VaultNavHost(
                        navController = navController,
                        context = this,
                        vaultViewModel = vaultViewModel,
                        settingsViewModel = settingsViewModel,
                        onTriggerBiometrics = { triggerBiometricAuth() },
                        onLockApp = {
                            moveTaskToBack(false)
                        }
                    )""",
"""                    VaultNavHost(
                        navController = navController,
                        context = this,
                        vaultViewModel = vaultViewModel,
                        settingsViewModel = settingsViewModel,
                        onTriggerBiometrics = { triggerBiometricAuth() },
                        onEnrollBiometrics = { triggerBiometricEnroll() },
                        onDisableBiometrics = {
                            com.example.security.VaultKeyManager.removeBiometricEnvelope(this)
                            settingsViewModel.setBiometricsEnabled(false)
                        },
                        onLockApp = {
                            moveTaskToBack(false)
                        }
                    )"""
)

# Rewrite triggerBiometricAuth
new_trigger_auth = """
    private fun triggerBiometricAuth() {
        biometricPromptManager.showBiometricUnlockPrompt(this) { result ->
            when (result) {
                is BiometricPromptManager.AuthResult.Success -> {
                    vaultViewModel.unlockRealVault()
                }
                is BiometricPromptManager.AuthResult.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    vaultViewModel.logIntruderAttempt(applicationContext, "BIOMETRIC_FAILED", result.message)
                }
                is BiometricPromptManager.AuthResult.KeyInvalidated -> {
                    Toast.makeText(this, "Biometric enrollment changed. Re-enroll Quantum Vault biometric unlock.", Toast.LENGTH_LONG).show()
                    com.example.security.VaultKeyManager.removeBiometricEnvelope(this)
                    settingsViewModel.setBiometricsEnabled(false)
                }
                else -> {}
            }
        }
    }

    private fun triggerBiometricEnroll() {
        biometricPromptManager.showBiometricEnrollPrompt(this) { result ->
            when (result) {
                is BiometricPromptManager.AuthResult.Success -> {
                    settingsViewModel.setBiometricsEnabled(true)
                    Toast.makeText(this, "Biometric unlock enrolled successfully.", Toast.LENGTH_SHORT).show()
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
"""

content = re.sub(
    r'private fun triggerBiometricAuth\(\) \{.*?(?=^    \w+)',
    new_trigger_auth,
    content,
    flags=re.DOTALL | re.MULTILINE
)

# if triggerBiometricAuth matches until the end of the file, we need a different regex
if "triggerBiometricEnroll" not in content:
    content = re.sub(
        r'private fun triggerBiometricAuth\(\) \{.*$',
        new_trigger_auth + "\n}\n",
        content,
        flags=re.DOTALL | re.MULTILINE
    )

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
