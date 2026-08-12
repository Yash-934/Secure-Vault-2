package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.VaultFolder
import com.example.data.VaultItem
import com.example.ui.theme.*

@Composable
fun MoveCopyDialog(
    item: VaultItem,
    availableFolders: List<VaultFolder>,
    onDismiss: () -> Unit,
    onMove: (destinationFolder: String) -> Unit,
    onCopy: (destinationFolder: String) -> Unit,
    onCreateNewFolder: (folderName: String) -> Unit
) {
    var selectedFolder by remember { mutableStateOf(item.folderName.ifEmpty { "Root" }) }
    var showCreateFolderInside by remember { mutableStateOf(false) }

    // Combine default "Root" with custom created folders
    val folderList = remember(availableFolders) {
        val list = mutableListOf("Root")
        availableFolders.map { it.name }.forEach { name ->
            if (!list.contains(name) && name != "ALL") {
                list.add(name)
            }
        }
        list
    }

    if (showCreateFolderInside) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderInside = false },
            onCreateFolder = { newName ->
                onCreateNewFolder(newName)
                selectedFolder = newName
                showCreateFolderInside = false
            }
        )
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(VaultSurface)
                .border(1.5.dp, CyberNeonGradient, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0C1D2D))
                            .border(1.dp, VaultPrimaryCyan, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DriveFileMove,
                            contentDescription = "Move/Copy",
                            tint = VaultPrimaryCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MOVE / COPY FILE",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = item.originalName,
                            fontSize = 12.sp,
                            color = VaultTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = VaultBorder, thickness = 1.dp)

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECT DESTINATION FOLDER:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VaultPrimaryCyan,
                        letterSpacing = 1.sp
                    )

                    TextButton(
                        onClick = { showCreateFolderInside = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = VaultPrimaryCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Folder", fontSize = 12.sp, color = VaultPrimaryCyan, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Folder List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    items(folderList) { folderName ->
                        val isSelected = selectedFolder == folderName
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF0F263A) else Color(0xFF08121C))
                                .border(1.dp, if (isSelected) VaultPrimaryCyan else VaultBorder, RoundedCornerShape(12.dp))
                                .clickable { selectedFolder = folderName }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) VaultPrimaryCyan else VaultTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = folderName,
                                    color = if (isSelected) Color.White else VaultTextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = VaultPrimaryCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: MOVE or COPY
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // COPY BUTTON
                    OutlinedButton(
                        onClick = {
                            onCopy(selectedFolder)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VaultPrimaryCyan),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0A1C2A)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("action_copy_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = VaultPrimaryCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Here", color = VaultPrimaryCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // MOVE BUTTON
                    Button(
                        onClick = {
                            onMove(selectedFolder)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VaultPrimaryCyan),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("action_move_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.DriveFileMove, contentDescription = "Move", tint = Color(0xFF03070C), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Move Here", color = Color(0xFF03070C), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
