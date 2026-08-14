package com.example.ui.screens

import android.app.Activity
import android.content.IntentSender
import androidx.activity.result.IntentSenderRequest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultFolder
import com.example.data.VaultItem
import com.example.ui.VaultFilterTab
import com.example.ui.components.CreateFolderDialog
import com.example.ui.components.MoveCopyDialog
import com.example.ui.components.PlaceholderLoadingCard
import com.example.ui.theme.VaultErrorRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PitchBlackBg = Color(0xFF06090E)
private val DarkCapsuleBg = Color(0xFF0C1420)
private val CapsuleBorder = Color(0xFF1B3148)
private val BrightCyan = Color(0xFF00F5D4)
private val NeonPurple = Color(0xFF9D4EDD)
private val MutedText = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vaultItems: List<VaultItem>,
    activeFilter: VaultFilterTab,
    selectedFolder: String = "ALL",
    folders: List<VaultFolder> = emptyList(),
    statusMessage: String?,
    isLoading: Boolean,
    onFilterChanged: (VaultFilterTab) -> Unit,
    onSelectFolder: (String) -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
    onDeleteFolder: (String) -> Unit = {},
    onRenameFolder: (oldName: String, newName: String) -> Unit = { _, _ -> },
    onMoveItem: (VaultItem, String) -> Unit = { _, _ -> },
    onCopyItem: (VaultItem, String) -> Unit = { _, _ -> },
    onFilesSelected: (List<Uri>) -> Unit,
    onItemClick: (VaultItem) -> Unit,
    onDeleteItem: (VaultItem) -> Unit,
    onExportItem: (VaultItem) -> Unit,
    onLockClick: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPasswords: () -> Unit = {},
    onClearStatusMessage: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }
    var isScanningVault by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(100) }
    var isHudExpanded by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var renameFolderTarget by remember { mutableStateOf<String?>(null) }
    var moveCopyTargetItem by remember { mutableStateOf<VaultItem?>(null) }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(statusMessage)
            onClearStatusMessage()
        }
    }

    LaunchedEffect(isScanningVault) {
        if (isScanningVault) {
            scanProgress = 0
            while (scanProgress < 100) {
                kotlinx.coroutines.delay(20)
                scanProgress += 5
            }
            isScanningVault = false
            snackbarHostState.showSnackbar("VAULT INTEGRITY VERIFIED: ZERO LEAKS DETECTED")
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesSelected(uris)
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesSelected(uris)
        }
    }

    val filteredItems = remember(vaultItems, activeFilter, searchQuery) {
        vaultItems.filter { item ->
            val matchesFilter = when (activeFilter) {
                VaultFilterTab.ALL -> true
                VaultFilterTab.PHOTOS -> item.mimeType.startsWith("image/")
                VaultFilterTab.VIDEOS -> item.mimeType.startsWith("video/")
                VaultFilterTab.DOCUMENTS -> !item.mimeType.startsWith("image/") && !item.mimeType.startsWith("video/")
            }
            val matchesSearch = searchQuery.isEmpty() ||
                    item.originalName.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchBlackBg)
    ) {
        // Cyber Ambient Grid Overlay Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 40.dp.toPx()
            val gridColor = Color(0xFF00D2EF).copy(alpha = 0.03f)

            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += gridSpacing
            }

            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += gridSpacing
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (filteredItems.isNotEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF041824))
                                .border(2.dp, BrightCyan, CircleShape)
                                .clickable { showImportDialog = true }
                                .testTag("import_fab_floating"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(BrightCyan.copy(alpha = 0.2f))
                                    .border(1.dp, BrightCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Encrypt File",
                                    tint = BrightCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Text(
                            text = "ENCRYPT FILE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrightCyan,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "QUANTUM VAULT",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrightCyan,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = BrightCyan
                            )
                        }

                        IconButton(
                            onClick = { isGridView = !isGridView },
                            modifier = Modifier.testTag("toggle_grid_list_button")
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                                contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                                tint = BrightCyan
                            )
                        }

                        IconButton(
                            onClick = onNavigateToPasswords,
                            modifier = Modifier.testTag("password_manager_nav_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Password Manager",
                                tint = BrightCyan
                            )
                        }

                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.testTag("settings_nav_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = BrightCyan
                            )
                        }

                        IconButton(
                            onClick = onNavigateToAbout,
                            modifier = Modifier.testTag("about_nav_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "About",
                                tint = BrightCyan
                            )
                        }

                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DarkCapsuleBg)
                                .border(1.5.dp, BrightCyan, CircleShape)
                                .clickable { onLockClick() }
                                .testTag("lock_vault_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Vault",
                                tint = BrightCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PitchBlackBg.copy(alpha = 0.95f))
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
            ) {
                // Cyberpunk Telemetry HUD Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF06121D))
                        .border(1.dp, Color(0xFF0F2C46), RoundedCornerShape(14.dp))
                        .clickable { isHudExpanded = !isHudExpanded }
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isScanningVault) BrightCyan else Color(0xFF00FF66))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isScanningVault) "SCANNING VAULT ($scanProgress%)..." else "ZERO-TRUST MATRIX ACTIVE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrightCyan,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BrightCyan.copy(alpha = 0.15f))
                                    .clickable { isScanningVault = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "SCAN",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrightCyan,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (isHudExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "ENCRYPTED ITEMS",
                                        fontSize = 9.sp,
                                        color = MutedText,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "${vaultItems.size} FILES",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Column {
                                    Text(
                                        text = "PROTECTION LEVEL",
                                        fontSize = 9.sp,
                                        color = MutedText,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "AES-256 GCM",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00FF66)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "DURESS DEFENSE",
                                        fontSize = 9.sp,
                                        color = MutedText,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "READY",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrightCyan
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Password Vault Quick-Access Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0A1420))
                        .border(1.dp, BrightCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable { onNavigateToPasswords() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("dashboard_password_vault_banner")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BrightCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = BrightCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "PASSWORD & SECRET VAULT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Store Logins, Bank Details & Strong Keys",
                                    fontSize = 9.5.sp,
                                    color = MutedText
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BrightCyan.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "OPEN",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrightCyan,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedVisibility(visible = isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search encrypted files...", fontSize = 12.sp, color = MutedText) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = BrightCyan)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrightCyan,
                            unfocusedBorderColor = CapsuleBorder,
                            focusedContainerColor = DarkCapsuleBg,
                            unfocusedContainerColor = DarkCapsuleBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                }

                // Folder Management Header & Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VAULT FOLDERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrightCyan,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrightCyan.copy(alpha = 0.15f))
                            .border(0.8.dp, BrightCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { showCreateFolderDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("add_folder_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Folder",
                            tint = BrightCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "NEW FOLDER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrightCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Scrollable Folder Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FolderChipItem(
                            title = "📁 ALL",
                            isSelected = selectedFolder == "ALL",
                            onClick = { onSelectFolder("ALL") }
                        )
                    }
                    item {
                        FolderChipItem(
                            title = "📂 Root",
                            isSelected = selectedFolder == "Root",
                            onClick = { onSelectFolder("Root") }
                        )
                    }
                    items(folders) { folder ->
                        if (folder.name != "ALL" && folder.name != "Root") {
                            FolderChipItem(
                                title = "📂 ${folder.name}",
                                isSelected = selectedFolder == folder.name,
                                onClick = { onSelectFolder(folder.name) },
                                onDelete = { onDeleteFolder(folder.name) },
                                onRename = { renameFolderTarget = folder.name }
                            )
                        }
                    }
                }

                // Capsule Filter Tab Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .background(DarkCapsuleBg)
                        .border(1.dp, CapsuleBorder, RoundedCornerShape(30.dp))
                        .padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CapsuleFilterTabItem(
                            text = "ALL ITEMS",
                            isSelected = activeFilter == VaultFilterTab.ALL,
                            onClick = { onFilterChanged(VaultFilterTab.ALL) },
                            modifier = Modifier.weight(1f)
                        )
                        CapsuleFilterTabItem(
                            text = "PHOTOS",
                            isSelected = activeFilter == VaultFilterTab.PHOTOS,
                            onClick = { onFilterChanged(VaultFilterTab.PHOTOS) },
                            modifier = Modifier.weight(1f)
                        )
                        CapsuleFilterTabItem(
                            text = "VIDEOS",
                            isSelected = activeFilter == VaultFilterTab.VIDEOS,
                            onClick = { onFilterChanged(VaultFilterTab.VIDEOS) },
                            modifier = Modifier.weight(1f)
                        )
                        CapsuleFilterTabItem(
                            text = "DOCS",
                            isSelected = activeFilter == VaultFilterTab.DOCUMENTS,
                            onClick = { onFilterChanged(VaultFilterTab.DOCUMENTS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(6) {
                            PlaceholderLoadingCard()
                        }
                    }
                } else if (filteredItems.isEmpty()) {
                    EmptyVaultHologramView(onImportClick = { showImportDialog = true })
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (isGridView) 2 else 1),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 90.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            if (isGridView) {
                                VaultGridCard(
                                    item = item,
                                    onClick = { onItemClick(item) },
                                    onDelete = { onDeleteItem(item) },
                                    onExport = { onExportItem(item) },
                                    onMoveCopy = { moveCopyTargetItem = item }
                                )
                            } else {
                                VaultListCard(
                                    item = item,
                                    onClick = { onItemClick(item) },
                                    onDelete = { onDeleteItem(item) },
                                    onExport = { onExportItem(item) },
                                    onMoveCopy = { moveCopyTargetItem = item }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showCreateFolderDialog) {
            CreateFolderDialog(
                onDismiss = { showCreateFolderDialog = false },
                onCreateFolder = { name ->
                    onCreateFolder(name)
                    showCreateFolderDialog = false
                }
            )
        }
        
        renameFolderTarget?.let { targetFolder ->
            com.example.ui.components.RenameFolderDialog(
                initialName = targetFolder,
                onDismiss = { renameFolderTarget = null },
                onRenameFolder = { newName ->
                    onRenameFolder(targetFolder, newName)
                    renameFolderTarget = null
                }
            )
        }

        if (moveCopyTargetItem != null) {
            MoveCopyDialog(
                item = moveCopyTargetItem!!,
                availableFolders = folders,
                onDismiss = { moveCopyTargetItem = null },
                onMove = { dest ->
                    onMoveItem(moveCopyTargetItem!!, dest)
                    moveCopyTargetItem = null
                },
                onCopy = { dest ->
                    onCopyItem(moveCopyTargetItem!!, dest)
                    moveCopyTargetItem = null
                },
                onCreateNewFolder = { name ->
                    onCreateFolder(name)
                }
            )
        }

        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                containerColor = DarkCapsuleBg,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = BrightCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ENCRYPT FILE",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        // Option 1: Photos & Videos
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF041422))
                                .border(1.dp, CapsuleBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    showImportDialog = false
                                    mediaPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                }
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(BrightCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = BrightCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Photos & Videos",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Select media from gallery",
                                        fontSize = 11.sp,
                                        color = MutedText
                                    )
                                }
                            }
                        }

                        // Option 2: Documents & All Files
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF041422))
                                .border(1.dp, CapsuleBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    showImportDialog = false
                                    docPickerLauncher.launch(arrayOf("*/*"))
                                }
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(BrightCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = BrightCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Documents & All Files",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "PDF, Word, Excel, TXT, Audio, ZIP & more",
                                        fontSize = 11.sp,
                                        color = MutedText
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("CANCEL", color = MutedText, fontFamily = FontFamily.Monospace)
                    }
                }
            )
        }
    }
}

@Composable
private fun CapsuleFilterTabItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) BrightCyan.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) BrightCyan else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) BrightCyan else MutedText,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun EmptyVaultHologramView(onImportClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(175.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF060F18))
                    .border(1.5.dp, Color(0xFF132B42), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 2.dp.toPx()
                    val cornerLen = 16.dp.toPx()
                    val color = BrightCyan

                    drawPath(
                        path = Path().apply {
                            moveTo(10.dp.toPx(), 10.dp.toPx() + cornerLen)
                            lineTo(10.dp.toPx(), 10.dp.toPx())
                            lineTo(10.dp.toPx() + cornerLen, 10.dp.toPx())
                        },
                        color = color,
                        style = Stroke(stroke)
                    )

                    drawPath(
                        path = Path().apply {
                            moveTo(size.width - 10.dp.toPx() - cornerLen, 10.dp.toPx())
                            lineTo(size.width - 10.dp.toPx(), 10.dp.toPx())
                            lineTo(size.width - 10.dp.toPx(), 10.dp.toPx() + cornerLen)
                        },
                        color = color,
                        style = Stroke(stroke)
                    )

                    drawPath(
                        path = Path().apply {
                            moveTo(10.dp.toPx(), size.height - 10.dp.toPx() - cornerLen)
                            lineTo(10.dp.toPx(), size.height - 10.dp.toPx())
                            lineTo(10.dp.toPx() + cornerLen, size.height - 10.dp.toPx())
                        },
                        color = color,
                        style = Stroke(stroke)
                    )

                    drawPath(
                        path = Path().apply {
                            moveTo(size.width - 10.dp.toPx() - cornerLen, size.height - 10.dp.toPx())
                            lineTo(size.width - 10.dp.toPx(), size.height - 10.dp.toPx())
                            lineTo(size.width - 10.dp.toPx(), size.height - 10.dp.toPx() - cornerLen)
                        },
                        color = color,
                        style = Stroke(stroke)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        BrightCyan.copy(alpha = 0.35f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(1.5.dp, BrightCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Holographic Lock",
                            tint = BrightCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .width(115.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0B1E2E))
                            .border(1.dp, BrightCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00FF66))
                            )
                            Text(
                                text = "AES-256 CHIP",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrightCyan,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "VAULT IS SECURE & EMPTY",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "Tap 'ENCRYPT FILE' to import and isolate photos\nand videos inside zero-trust AES-256 storage.",
                fontSize = 10.5.sp,
                color = MutedText,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF041824))
                        .border(1.5.dp, BrightCyan, CircleShape)
                        .clickable { onImportClick() }
                        .testTag("import_fab"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(BrightCyan.copy(alpha = 0.2f))
                            .border(1.dp, BrightCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypt Media",
                            tint = BrightCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = "ENCRYPT FILE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrightCyan,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun VaultGridCard(
    item: VaultItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onMoveCopy: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val (itemIcon, itemBadge) = remember(item.mimeType, item.isVideo) {
        getVaultItemIconAndBadge(item.mimeType, item.isVideo)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("vault_item_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkCapsuleBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, CapsuleBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(Color(0xFF050E17)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = itemIcon,
                    contentDescription = null,
                    tint = BrightCyan,
                    modifier = Modifier.size(42.dp)
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PitchBlackBg.copy(alpha = 0.85f))
                            .border(0.8.dp, BrightCyan, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = itemBadge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrightCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (item.folderName.isNotEmpty() && item.folderName != "Root") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0E2235))
                                .border(0.8.dp, BrightCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "📂 ${item.folderName}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrightCyan
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.originalName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MutedText,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(DarkCapsuleBg)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Move / Copy File", fontSize = 12.sp, color = Color.White) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DriveFileMove,
                                        contentDescription = null,
                                        tint = BrightCyan
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onMoveCopy()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Restore to Gallery", fontSize = 12.sp, color = Color.White) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.FileDownload,
                                        contentDescription = null,
                                        tint = BrightCyan
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onExport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete from Vault", fontSize = 12.sp, color = VaultErrorRed) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = VaultErrorRed
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatFileSize(item.sizeBytes),
                        fontSize = 10.sp,
                        color = MutedText
                    )
                    Text(
                        text = formatDate(item.addedTimestamp),
                        fontSize = 10.sp,
                        color = MutedText
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultListCard(
    item: VaultItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onMoveCopy: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val (itemIcon, itemBadge) = remember(item.mimeType, item.isVideo) {
        getVaultItemIconAndBadge(item.mimeType, item.isVideo)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("vault_item_list_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkCapsuleBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, CapsuleBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF050E17)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = itemIcon,
                    contentDescription = null,
                    tint = BrightCyan,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.originalName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.folderName.isNotEmpty() && item.folderName != "Root") {
                        Text(
                            text = "📂 ${item.folderName}",
                            fontSize = 10.sp,
                            color = BrightCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = formatFileSize(item.sizeBytes),
                        fontSize = 10.sp,
                        color = MutedText
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MutedText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(DarkCapsuleBg)
                ) {
                    DropdownMenuItem(
                        text = { Text("Move / Copy File", fontSize = 12.sp, color = Color.White) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DriveFileMove,
                                contentDescription = null,
                                tint = BrightCyan
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onMoveCopy()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Restore to Gallery", fontSize = 12.sp, color = Color.White) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = BrightCyan
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onExport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete from Vault", fontSize = 12.sp, color = VaultErrorRed) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = VaultErrorRed
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun getVaultItemIconAndBadge(mimeType: String, isVideo: Boolean): Pair<androidx.compose.ui.graphics.vector.ImageVector, String> {
    return when {
        isVideo || mimeType.startsWith("video/") -> Icons.Default.PlayCircle to "VIDEO"
        mimeType.startsWith("image/") -> Icons.Default.Image to "PHOTO"
        mimeType == "application/pdf" -> Icons.Default.Description to "PDF"
        mimeType.startsWith("text/") -> Icons.Default.Description to "TXT"
        mimeType.startsWith("audio/") -> Icons.Default.Description to "AUDIO"
        else -> Icons.Default.InsertDriveFile to "DOC"
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FolderChipItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) BrightCyan else DarkCapsuleBg)
            .border(1.dp, if (isSelected) BrightCyan else CapsuleBorder, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (onDelete != null || onRename != null) showMenu = true }
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag("folder_chip_${title.replace(" ", "_")}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) Color(0xFF03070C) else Color.White
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (onRename != null) {
                DropdownMenuItem(
                    text = { Text("Rename Folder") },
                    onClick = {
                        showMenu = false
                        onRename()
                    }
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Delete Folder") },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}
