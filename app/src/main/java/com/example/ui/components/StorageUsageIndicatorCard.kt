package com.example.ui.components

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultItem
import java.util.Locale

private val DarkCapsuleBg = Color(0xFF0C1420)
private val CapsuleBorder = Color(0xFF1B3148)
private val BrightCyan = Color(0xFF00F5D4)
private val NeonPurple = Color(0xFF9D4EDD)
private val NeonGreen = Color(0xFF00FF66)
private val AmberYellow = Color(0xFFFFB703)
private val MutedText = Color(0xFF94A3B8)

@Composable
fun StorageUsageIndicatorCard(
    vaultItems: List<VaultItem>,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val totalSizeBytes = remember(vaultItems) { vaultItems.sumOf { it.sizeBytes } }
    val photoBytes = remember(vaultItems) {
        vaultItems.filter { it.mimeType.startsWith("image/") }.sumOf { it.sizeBytes }
    }
    val videoBytes = remember(vaultItems) {
        vaultItems.filter { it.mimeType.startsWith("video/") }.sumOf { it.sizeBytes }
    }
    val docBytes = remember(vaultItems) {
        vaultItems.filter {
            !it.mimeType.startsWith("image/") &&
            !it.mimeType.startsWith("video/") &&
            !it.mimeType.startsWith("audio/")
        }.sumOf { it.sizeBytes }
    }
    val audioBytes = remember(vaultItems) {
        vaultItems.filter { it.mimeType.startsWith("audio/") }.sumOf { it.sizeBytes }
    }

    val freeDeviceBytes = remember {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            10L * 1024 * 1024 * 1024 // 10 GB fallback
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF07121E))
            .border(1.dp, Color(0xFF0E283E), RoundedCornerShape(14.dp))
            .clickable { isExpanded = !isExpanded }
            .padding(12.dp)
            .testTag("storage_usage_indicator_card")
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
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(BrightCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = BrightCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "VAULT STORAGE USAGE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrightCyan,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "${formatFileSize(totalSizeBytes)} Used (${vaultItems.size} files) • ${formatFileSize(freeDeviceBytes)} Free on Device",
                            fontSize = 10.sp,
                            color = MutedText
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = BrightCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Multi-segment storage bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF040A10))
            ) {
                if (totalSizeBytes > 0) {
                    val photoWeight = (photoBytes.toFloat() / totalSizeBytes.toFloat()).coerceIn(0f, 1f)
                    val videoWeight = (videoBytes.toFloat() / totalSizeBytes.toFloat()).coerceIn(0f, 1f)
                    val docWeight = (docBytes.toFloat() / totalSizeBytes.toFloat()).coerceIn(0f, 1f)
                    val audioWeight = (audioBytes.toFloat() / totalSizeBytes.toFloat()).coerceIn(0f, 1f)

                    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                        if (photoWeight > 0f) {
                            Box(modifier = Modifier.weight(photoWeight.coerceAtLeast(0.01f)).fillMaxHeight().background(BrightCyan))
                        }
                        if (videoWeight > 0f) {
                            Box(modifier = Modifier.weight(videoWeight.coerceAtLeast(0.01f)).fillMaxHeight().background(NeonPurple))
                        }
                        if (docWeight > 0f) {
                            Box(modifier = Modifier.weight(docWeight.coerceAtLeast(0.01f)).fillMaxHeight().background(NeonGreen))
                        }
                        if (audioWeight > 0f) {
                            Box(modifier = Modifier.weight(audioWeight.coerceAtLeast(0.01f)).fillMaxHeight().background(AmberYellow))
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight().background(Color(0xFF0D1B2A)))
                }
            }

            // Expanded Breakdown Details
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StorageCategoryRow(
                        icon = Icons.Default.Image,
                        color = BrightCyan,
                        label = "Photos",
                        size = formatFileSize(photoBytes),
                        count = vaultItems.count { it.mimeType.startsWith("image/") }
                    )
                    StorageCategoryRow(
                        icon = Icons.Default.Movie,
                        color = NeonPurple,
                        label = "Videos",
                        size = formatFileSize(videoBytes),
                        count = vaultItems.count { it.mimeType.startsWith("video/") }
                    )
                    StorageCategoryRow(
                        icon = Icons.Default.Description,
                        color = NeonGreen,
                        label = "Documents & PDFs",
                        size = formatFileSize(docBytes),
                        count = vaultItems.count {
                            !it.mimeType.startsWith("image/") &&
                            !it.mimeType.startsWith("video/") &&
                            !it.mimeType.startsWith("audio/")
                        }
                    )
                    if (audioBytes > 0) {
                        StorageCategoryRow(
                            icon = Icons.Default.MusicNote,
                            color = AmberYellow,
                            label = "Audio & Other",
                            size = formatFileSize(audioBytes),
                            count = vaultItems.count { it.mimeType.startsWith("audio/") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageCategoryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    label: String,
    size: String,
    count: Int
) {
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
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "($count)",
                fontSize = 10.sp,
                color = MutedText
            )
        }

        Text(
            text = size,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
