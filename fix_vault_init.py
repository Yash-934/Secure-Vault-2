import re

# 1. Update VaultViewModel.kt
with open("app/src/main/java/com/example/ui/VaultViewModel.kt", "r") as f:
    content = f.read()

# Replace constructor
content = re.sub(
    r'class VaultViewModel\(\s*private val realRepository: VaultRepository,\s*private val decoyRepository: VaultRepository\s*\) : ViewModel\(\) \{',
    '''class VaultViewModel(
    private val appCtx: android.content.Context
) : ViewModel() {

    private val realRepository by lazy {
        val database = com.example.data.AppDatabase.getDatabase(appCtx)
        com.example.data.VaultRepository(database.vaultDao(), "vault")
    }

    private val decoyRepository by lazy {
        val database = com.example.data.AppDatabase.getDecoyDatabase(appCtx)
        com.example.data.VaultRepository(database.vaultDao(), "decoy_vault")
    }''',
    content
)

# Replace repository getter
content = content.replace(
    '''val repository: VaultRepository get() = if (vaultMode.value == VaultMode.DECOY) decoyRepository else realRepository''',
    '''val repository: VaultRepository get() = if (vaultMode.value == VaultMode.DECOY) decoyRepository else realRepository'''
)

# Fix vaultItems flatMapLatest
content = content.replace(
    '''    }.flatMapLatest { (mode, folder, tab) ->
        val activeRepo = if (mode == VaultMode.DECOY) decoyRepository else realRepository
        activeRepo.getItemsForFolderAndTab(folder, tab)
    }.stateIn(''',
    '''    }.flatMapLatest { (mode, folder, tab) ->
        if (mode == VaultMode.LOCKED) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            val activeRepo = if (mode == VaultMode.DECOY) decoyRepository else realRepository
            activeRepo.getItemsForFolderAndTab(folder, tab)
        }
    }.stateIn('''
)

# Fix folders flatMapLatest
content = content.replace(
    '''    val vaultFolders: StateFlow<List<com.example.data.VaultFolder>> = _vaultMode.flatMapLatest { mode ->
        val activeRepo = if (mode == VaultMode.DECOY) decoyRepository else realRepository
        activeRepo.getAllFolders()
    }.stateIn(''',
    '''    val vaultFolders: StateFlow<List<com.example.data.VaultFolder>> = _vaultMode.flatMapLatest { mode ->
        if (mode == VaultMode.LOCKED) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            val activeRepo = if (mode == VaultMode.DECOY) decoyRepository else realRepository
            activeRepo.getAllFolders()
        }
    }.stateIn('''
)

# Fix passwords flatMapLatest
content = content.replace(
    '''    val vaultPasswords: StateFlow<List<com.example.data.VaultPassword>> = _vaultMode.flatMapLatest { mode ->
        val db = if (mode == VaultMode.DECOY) com.example.data.AppDatabase.getDecoyDatabase(appCtx) else com.example.data.AppDatabase.getDatabase(appCtx)
        db.vaultPasswordDao().getAllPasswords()
    }.stateIn(''',
    '''    val vaultPasswords: StateFlow<List<com.example.data.VaultPassword>> = _vaultMode.flatMapLatest { mode ->
        if (mode == VaultMode.LOCKED) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            val db = if (mode == VaultMode.DECOY) com.example.data.AppDatabase.getDecoyDatabase(appCtx) else com.example.data.AppDatabase.getDatabase(appCtx)
            db.vaultPasswordDao().getAllPasswords()
        }
    }.stateIn('''
)

# There might be another place where passwords are created
content = re.sub(
    r'val db = if \(mode == VaultMode\.DECOY\) com\.example\.data\.AppDatabase\.getDecoyDatabase\(.*\) else com\.example\.data\.AppDatabase\.getDatabase\(.*\)\s*db\.vaultPasswordDao\(\)\.getAllPasswords\(\)',
    '''if (mode == VaultMode.LOCKED) kotlinx.coroutines.flow.flowOf(emptyList()) else { val db = if (mode == VaultMode.DECOY) com.example.data.AppDatabase.getDecoyDatabase(appCtx) else com.example.data.AppDatabase.getDatabase(appCtx); db.vaultPasswordDao().getAllPasswords() }''',
    content
)


# Fix VaultViewModel.Factory
content = re.sub(
    r'class Factory\(\s*private val realRepository: VaultRepository,\s*private val decoyRepository: VaultRepository\s*\) : ViewModelProvider\.Factory \{.*?(?=^\s*\})^\s*\}',
    '''class Factory(
        private val context: android.content.Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(VaultViewModel::class.java)) {
                return VaultViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }''',
    content,
    flags=re.DOTALL | re.MULTILINE
)

with open("app/src/main/java/com/example/ui/VaultViewModel.kt", "w") as f:
    f.write(content)


# 2. Update MainActivity.kt
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    main_content = f.read()

main_content = re.sub(
    r'private val vaultViewModel: VaultViewModel by viewModels \{.*?VaultViewModel\.Factory\(realRepository, decoyRepository\).*?^\s*\}',
    '''private val vaultViewModel: VaultViewModel by viewModels {
        VaultViewModel.Factory(applicationContext)
    }''',
    main_content,
    flags=re.DOTALL | re.MULTILINE
)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(main_content)

