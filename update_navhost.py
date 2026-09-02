import re

with open("app/src/main/java/com/example/ui/navigation/VaultNavHost.kt", "r") as f:
    content = f.read()

content = content.replace(
"""fun VaultNavHost(
    navController: NavHostController,
    context: Context,
    vaultViewModel: VaultViewModel,
    settingsViewModel: SettingsViewModel,
    onTriggerBiometrics: () -> Unit,
    onLockApp: () -> Unit
)""",
"""fun VaultNavHost(
    navController: NavHostController,
    context: Context,
    vaultViewModel: VaultViewModel,
    settingsViewModel: SettingsViewModel,
    onTriggerBiometrics: () -> Unit,
    onEnrollBiometrics: () -> Unit,
    onDisableBiometrics: () -> Unit,
    onLockApp: () -> Unit
)"""
)

content = content.replace(
"""                onToggleBiometrics = { settingsViewModel.setBiometricsEnabled(it) },""",
"""                onToggleBiometrics = { 
                    if (it) onEnrollBiometrics() else onDisableBiometrics()
                },"""
)

with open("app/src/main/java/com/example/ui/navigation/VaultNavHost.kt", "w") as f:
    f.write(content)
