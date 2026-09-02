import re

with open("app/src/main/java/com/example/ui/VaultViewModel.kt", "r") as f:
    content = f.read()

content = content.replace(
    '''    val folders: StateFlow<List<com.example.data.VaultFolder>> = _vaultMode.flatMapLatest { mode ->
        if (mode == VaultMode.DECOY) decoyRepository.allFolders else realRepository.allFolders
    }.stateIn(''',
    '''    val folders: StateFlow<List<com.example.data.VaultFolder>> = _vaultMode.flatMapLatest { mode ->
        if (mode == VaultMode.LOCKED) kotlinx.coroutines.flow.flowOf(emptyList()) else if (mode == VaultMode.DECOY) decoyRepository.allFolders else realRepository.allFolders
    }.stateIn('''
)

with open("app/src/main/java/com/example/ui/VaultViewModel.kt", "w") as f:
    f.write(content)
