import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace(
    '''    private val vaultViewModel: VaultViewModel by viewModels {
        VaultViewModel.Factory(applicationContext)
    } catch (t: Throwable) {
            VaultLogger.logError(applicationContext, "MainActivity", "Error creating databases for VaultViewModel", t)
            throw t
        }
    }''',
    '''    private val vaultViewModel: VaultViewModel by viewModels {
        VaultViewModel.Factory(applicationContext)
    }'''
)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)

