import re

with open("app/src/main/java/com/example/ui/VaultViewModel.kt", "r") as f:
    content = f.read()

content = content.replace(
"""            lockoutJob?.cancel()
            settingsDataStore.resetFailedAttempts()
            return true
        }

        // 4. Incorrect PIN""",
"""            lockoutJob?.cancel()
            settingsDataStore.resetFailedAttempts()
            return true
            }
        }

        // 4. Incorrect PIN"""
)

with open("app/src/main/java/com/example/ui/VaultViewModel.kt", "w") as f:
    f.write(content)
