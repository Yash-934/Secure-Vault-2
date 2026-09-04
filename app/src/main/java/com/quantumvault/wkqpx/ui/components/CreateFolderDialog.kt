package com.quantumvault.wkqpx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.quantumvault.wkqpx.ui.theme.*

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreateFolder: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(VaultSurface)
                .border(1.5.dp, CyberNeonGradient, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F2030))
                        .border(1.dp, VaultPrimaryCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "New Folder",
                        tint = VaultPrimaryCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "CREATE NEW FOLDER",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.2.sp
                )

                Text(
                    text = "Organize your encrypted photos, videos & docs",
                    fontSize = 12.sp,
                    color = VaultTextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        if (errorMessage != null) errorMessage = null
                    },
                    label = { Text("Folder Name", color = VaultTextSecondary) },
                    placeholder = { Text("e.g. Work Docs, Private Trip", color = VaultTextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultPrimaryCyan,
                        unfocusedBorderColor = VaultBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_folder_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = VaultErrorRed,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Cancel", color = VaultTextSecondary)
                    }

                    Button(
                        onClick = {
                            val name = folderName.trim()
                            if (name.isEmpty()) {
                                errorMessage = "Folder name cannot be empty"
                            } else if (name.equals("ALL", ignoreCase = true)) {
                                errorMessage = "Folder name cannot be 'ALL'"
                            } else {
                                onCreateFolder(name)
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VaultPrimaryCyan),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("confirm_create_folder_btn")
                    ) {
                        Text("Create", color = Color(0xFF03070C), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RenameFolderDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onRenameFolder: (String) -> Unit
) {
    var folderName by remember { mutableStateOf(initialName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(VaultSurface)
                .border(1.5.dp, CyberNeonGradient, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F2030))
                        .border(1.dp, VaultPrimaryCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "Rename Folder",
                        tint = VaultPrimaryCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "RENAME FOLDER",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.2.sp
                )

                Text(
                    text = "Enter a new name for your folder",
                    fontSize = 12.sp,
                    color = VaultTextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        if (errorMessage != null) errorMessage = null
                    },
                    label = { Text("Folder Name", color = VaultTextSecondary) },
                    placeholder = { Text("e.g. Work Docs, Private Trip", color = VaultTextSecondary.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultPrimaryCyan,
                        unfocusedBorderColor = VaultBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_folder_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = VaultErrorRed,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VaultBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Cancel", color = VaultTextSecondary)
                    }

                    Button(
                        onClick = {
                            val name = folderName.trim()
                            if (name.isEmpty()) {
                                errorMessage = "Folder name cannot be empty"
                            } else if (name.equals("ALL", ignoreCase = true) || name.equals("Root", ignoreCase = true)) {
                                errorMessage = "Reserved folder name"
                            } else if (name == initialName) {
                                onDismiss()
                            } else {
                                onRenameFolder(name)
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VaultPrimaryCyan),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("confirm_rename_folder_btn")
                    ) {
                        Text("Rename", color = Color(0xFF03070C), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

