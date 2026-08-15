package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultRepository
import com.example.security.SecureZipEntry
import com.example.security.SecureZipManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Interactive Encrypted ZIP Archive Viewer & Extractor Component.
 * Lists all contents of an encrypted ZIP archive and extracts entries directly into the Vault's internal
 * encrypted storage without unencrypted disk footprint.
 */
@Composable
fun SecureZipViewer(
    encryptedZipFile: File,
    vaultRepository: VaultRepository,
    onExtractionComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var zipEntries by remember { mutableStateOf<List<SecureZipEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var extractingEntryName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(encryptedZipFile) {
        isLoading = true
        withContext(Dispatchers.IO) {
            zipEntries = SecureZipManager.listZipEntries(encryptedZipFile)
        }
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030A12))
            .padding(12.dp)
            .testTag("secure_zip_viewer")
    ) {
        // Header card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0B1724))
                .border(1.dp, Color(0xFF1E2D3D), RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF00D2EF).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = null,
                    tint = Color(0xFF00D2EF),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Encrypted ZIP Container",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Direct stream extraction into vault storage",
                    fontSize = 11.sp,
                    color = Color(0xFF90A4AE)
                )
            }

            if (zipEntries.isNotEmpty()) {
                Button(
                    onClick = {
                        scope.launch {
                            val nonDirEntries = zipEntries.filter { !it.isDirectory }
                            var successCount = 0
                            for (entry in nonDirEntries) {
                                extractingEntryName = entry.name
                                val res = SecureZipManager.extractEntryToVault(
                                    context,
                                    encryptedZipFile,
                                    entry.name,
                                    vaultRepository
                                )
                                if (res.isSuccess) successCount++
                            }
                            extractingEntryName = null
                            Toast.makeText(
                                context,
                                "Extracted $successCount files directly into Vault!",
                                Toast.LENGTH_SHORT
                            ).show()
                            onExtractionComplete()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00D2EF),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = extractingEntryName == null,
                    modifier = Modifier.testTag("extract_all_zip_button")
                ) {
                    Text(
                        text = "EXTRACT ALL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF00D2EF))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Reading encrypted archive catalog...",
                        fontSize = 12.sp,
                        color = Color(0xFF90A4AE)
                    )
                }
            }
        } else if (zipEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No entries found in ZIP archive.",
                    fontSize = 13.sp,
                    color = Color(0xFF90A4AE)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(zipEntries) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF07121E))
                            .border(1.dp, Color(0xFF162536), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = if (entry.isDirectory) Color(0xFFFFB300) else Color(0xFF00D2EF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            if (!entry.isDirectory) {
                                Text(
                                    text = formatBytes(entry.uncompressedSize),
                                    fontSize = 10.sp,
                                    color = Color(0xFF90A4AE),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (!entry.isDirectory) {
                            if (extractingEntryName == entry.name) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF00D2EF),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            extractingEntryName = entry.name
                                            val res = SecureZipManager.extractEntryToVault(
                                                context,
                                                encryptedZipFile,
                                                entry.name,
                                                vaultRepository
                                            )
                                            extractingEntryName = null
                                            if (res.isSuccess) {
                                                Toast.makeText(
                                                    context,
                                                    "Extracted '${entry.name}' into Vault!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onExtractionComplete()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Extraction failed.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("extract_entry_${entry.name}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Extract to Vault",
                                        tint = Color(0xFF00D2EF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
