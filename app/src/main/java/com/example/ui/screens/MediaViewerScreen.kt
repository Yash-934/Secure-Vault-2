package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultItem
import com.example.data.VaultRepository
import com.example.ui.components.EncryptedVideoPlayer
import com.example.ui.components.SecureImageViewer
import com.example.ui.components.SecurePdfViewer
import com.example.ui.components.SecureZipViewer
import com.example.ui.theme.VaultBorder
import com.example.ui.theme.VaultDarkBackground
import com.example.ui.theme.VaultErrorRed
import com.example.ui.theme.VaultPrimaryCyan
import com.example.ui.theme.VaultSecondaryBlue
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultTextSecondary
import java.io.File

/**
 * Unified In-App File Manager & Secure Viewer Engine.
 * 
 * Intelligently routes file types:
 * - Video/Audio -> EncryptedVideoPlayer (ExoPlayer Media3 streaming chunk decryption)
 * - Image -> SecureImageViewer (In-memory pinch-to-zoom rendering)
 * - PDF -> SecurePdfViewer (Android PdfRenderer with instant lifecycle temp-wipe)
 * - ZIP -> SecureZipViewer (Archive catalog & direct vault extraction)
 * - Text/Log -> Monospace Code/Text Viewer
 */
@Composable
fun MediaViewerScreen(
    vaultItem: VaultItem,
    decryptedBytes: ByteArray?,
    isProcessing: Boolean,
    vaultRepository: VaultRepository? = null,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current
    val encryptedFile = remember(vaultItem) {
        File(File(context.filesDir, "vault"), vaultItem.encryptedFileName)
    }

    var isVideoFullscreen by remember { mutableStateOf(false) }

    val mimeType = vaultItem.mimeType.lowercase()
    val name = vaultItem.originalName.lowercase()

    val isVideoOrAudio = vaultItem.isVideo ||
            mimeType.startsWith("video/") ||
            mimeType.startsWith("audio/") ||
            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mp3") || name.endsWith(".m4a")

    val isPdf = mimeType == "application/pdf" || name.endsWith(".pdf")

    val isZip = mimeType == "application/zip" ||
            mimeType == "application/x-zip-compressed" ||
            name.endsWith(".zip")

    val isImage = mimeType.startsWith("image/") ||
            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")

    val isText = mimeType.startsWith("text/") ||
            name.endsWith(".txt") || name.endsWith(".json") || name.endsWith(".csv") || name.endsWith(".log")

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = VaultDarkBackground
    ) {
        if (isVideoOrAudio && isVideoFullscreen) {
            // Edge-to-edge dedicated Fullscreen Video Box
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                EncryptedVideoPlayer(
                    encryptedFile = encryptedFile,
                    modifier = Modifier.fillMaxSize(),
                    isFullscreen = true,
                    onToggleFullscreen = { isVideoFullscreen = it }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(VaultSecondaryBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = VaultPrimaryCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = vaultItem.originalName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "AES-256-GCM Secure Pipeline • ${formatFileSize(vaultItem.sizeBytes)}",
                                fontSize = 11.sp,
                                color = VaultTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("close_viewer_screen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Viewer",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // Security Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VaultSurface)
                        .border(1.dp, VaultBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = VaultPrimaryCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Isolated In-App Engine • Zero Public Footprint",
                        fontSize = 10.sp,
                        color = VaultTextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Main Content Engine Routing Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(VaultSurface)
                        .border(1.dp, VaultBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isVideoOrAudio -> {
                            EncryptedVideoPlayer(
                                encryptedFile = encryptedFile,
                                modifier = Modifier.fillMaxSize(),
                                isFullscreen = false,
                                onToggleFullscreen = { isVideoFullscreen = it }
                            )
                        }

                        isPdf -> {
                            SecurePdfViewer(
                                encryptedFile = encryptedFile,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        isZip -> {
                            if (vaultRepository != null) {
                                SecureZipViewer(
                                    encryptedZipFile = encryptedFile,
                                    vaultRepository = vaultRepository,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = "Repository instance unavailable for extraction.",
                                    fontSize = 12.sp,
                                    color = VaultTextSecondary
                                )
                            }
                        }

                        isImage -> {
                            if (isProcessing || decryptedBytes == null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = VaultPrimaryCyan)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Decrypting stream with AES-256-GCM...",
                                        fontSize = 13.sp,
                                        color = VaultTextSecondary
                                    )
                                }
                            } else {
                                SecureImageViewer(
                                    decryptedBytes = decryptedBytes,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        isText -> {
                            if (isProcessing || decryptedBytes == null) {
                                CircularProgressIndicator(color = VaultPrimaryCyan)
                            } else {
                                val decryptedText = remember(decryptedBytes) {
                                    try {
                                        String(decryptedBytes, Charsets.UTF_8)
                                    } catch (e: Exception) {
                                        "Unable to parse text content."
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF030A12))
                                        .padding(12.dp)
                                        .verticalScroll(rememberScrollState())
                                        .testTag("text_viewer_box")
                                ) {
                                    Text(
                                        text = decryptedText,
                                        fontSize = 13.sp,
                                        color = Color(0xFF00D2EF),
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Document",
                                    tint = VaultPrimaryCyan,
                                    modifier = Modifier.size(72.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Encrypted Document Isolated",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Name: ${vaultItem.originalName}\nType: ${vaultItem.mimeType}\nSize: ${formatFileSize(vaultItem.sizeBytes)}",
                                    fontSize = 12.sp,
                                    color = VaultTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(VaultPrimaryCyan.copy(alpha = 0.15f))
                                        .border(1.dp, VaultPrimaryCyan, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "TAP RESTORE TO EXPORT TO DOWNLOADS",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VaultPrimaryCyan,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("delete_media_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = VaultErrorRed
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VaultErrorRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onExport,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_media_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VaultPrimaryCyan,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Restore",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore", fontWeight = FontWeight.Bold)
                    }
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
