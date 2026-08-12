package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultItem
import com.example.ui.VaultFilterTab
import androidx.compose.ui.graphics.Brush
import com.example.ui.theme.CyberBackgroundGradient
import com.example.ui.theme.CyberCardGlowGradient
import com.example.ui.theme.CyberHeaderGradient
import com.example.ui.theme.CyberNeonGradient
import com.example.ui.theme.CyberPlasmaGradient
import com.example.ui.theme.VaultBorder
import com.example.ui.theme.VaultBorderGlow
import com.example.ui.theme.VaultDarkBackground
import com.example.ui.theme.VaultErrorRed
import com.example.ui.theme.VaultNeonPink
import com.example.ui.theme.VaultNeonPurple
import com.example.ui.theme.VaultPrimaryCyan
import com.example.ui.theme.VaultSecondaryBlue
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceVariant
import com.example.ui.theme.VaultTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultHomeScreen(
    vaultItems: List<VaultItem>,
    activeFilter: VaultFilterTab,
    statusMessage: String?,
    onFilterChanged: (VaultFilterTab) -> Unit,
    onFilesSelected: (List<Uri>) -> Unit,
    onItemClick: (VaultItem) -> Unit,
    onDeleteItem: (VaultItem) -> Unit,
    onExportItem: (VaultItem) -> Unit,
    onLockClick: () -> Unit,
    onClearStatusMessage: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(statusMessage)
            onClearStatusMessage()
        }
    }

    // Photo/Video gallery picker launcher
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesSelected(uris)
        }
    }

    val totalSizeBytes = vaultItems.sumOf { it.sizeBytes }

    Scaffold(
        containerColor = VaultDarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(VaultPrimaryCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = VaultPrimaryCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Secure Vault",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "AES-256-GCM Encrypted",
                                fontSize = 10.sp,
                                color = VaultPrimaryCyan
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onLockClick,
                        modifier = Modifier.testTag("lock_vault_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault",
                            tint = VaultPrimaryCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VaultDarkBackground
                )
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(CyberNeonGradient)
                    .clickable {
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .testTag("import_fab"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Files",
                        tint = Color(0xFF03070C)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Import Media",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF03070C),
                        fontSize = 14.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Vault Security Summary Card with Cyber Gradient Glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VaultSurface)
                    .border(1.5.dp, CyberNeonGradient, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberCardGlowGradient)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${vaultItems.size} Encrypted File${if (vaultItems.size != 1) "s" else ""}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Total Storage: ${formatFileSize(totalSizeBytes)}",
                            fontSize = 11.sp,
                            color = VaultTextSecondary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0A1C2A))
                            .border(1.dp, VaultPrimaryCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = VaultPrimaryCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AIR-GAPPED OFFLINE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = VaultPrimaryCyan,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Filter Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VaultFilterChip(
                    text = "All Files",
                    selected = activeFilter == VaultFilterTab.ALL,
                    onClick = { onFilterChanged(VaultFilterTab.ALL) }
                )
                VaultFilterChip(
                    text = "Photos",
                    selected = activeFilter == VaultFilterTab.PHOTOS,
                    onClick = { onFilterChanged(VaultFilterTab.PHOTOS) }
                )
                VaultFilterChip(
                    text = "Videos",
                    selected = activeFilter == VaultFilterTab.VIDEOS,
                    onClick = { onFilterChanged(VaultFilterTab.VIDEOS) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content Area
            if (vaultItems.isEmpty()) {
                EmptyVaultView(onImportClick = {
                    mediaPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                })
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(vaultItems, key = { it.id }) { item ->
                        VaultItemCard(
                            item = item,
                            onClick = { onItemClick(item) },
                            onDelete = { onDeleteItem(item) },
                            onExport = { onExportItem(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) CyberNeonGradient else Brush.horizontalGradient(listOf(VaultSurface, VaultSurface)))
            .border(1.dp, if (selected) Color.Transparent else VaultBorderGlow.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) Color(0xFF03070C) else VaultTextSecondary
        )
    }
}

@Composable
private fun VaultItemCard(
    item: VaultItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VaultSurface)
            .border(1.dp, CyberCardGlowGradient, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("vault_item_card_${item.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Media Thumbnail Placeholder Box with Cyber Gradient Accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF142030),
                                Color(0xFF0D1622)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isVideo) Icons.Default.PlayCircle else Icons.Default.Image,
                    contentDescription = null,
                    tint = if (item.isVideo) VaultNeonPurple else VaultPrimaryCyan,
                    modifier = Modifier.size(46.dp)
                )

                // AES-256 Badge Overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xE6050910))
                        .border(0.8.dp, VaultPrimaryCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = VaultPrimaryCyan,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "AES-256",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = VaultPrimaryCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Card Footer
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = VaultTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(VaultSurface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Restore to Gallery", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.FileDownload,
                                        contentDescription = null,
                                        tint = VaultPrimaryCyan
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

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatFileSize(item.sizeBytes),
                        fontSize = 10.sp,
                        color = VaultTextSecondary
                    )
                    Text(
                        text = formatDate(item.addedTimestamp),
                        fontSize = 10.sp,
                        color = VaultTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyVaultView(onImportClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(VaultSurface)
                    .border(1.dp, VaultBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = VaultTextSecondary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Your Vault is Empty",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select photos or videos from your phone to encrypt with AES-256 and store securely offline.",
                fontSize = 12.sp,
                color = VaultTextSecondary,
                textAlign = TextAlign.Center
            )
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
