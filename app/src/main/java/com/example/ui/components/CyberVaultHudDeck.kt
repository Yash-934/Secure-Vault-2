package com.example.ui.components

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultItem
import java.util.Locale

private val DarkDeckBg = Color(0xFF06111C)
private val DeckBorder = Color(0xFF0F2B44)
private val BrightCyan = Color(0xFF00F5D4)
private val NeonPurple = Color(0xFF9D4EDD)
private val NeonGreen = Color(0xFF00FF66)
private val AmberYellow = Color(0xFFFFB703)
private val MutedText = Color(0xFF8DA4BE)

@Composable
fun CyberVaultHudDeck(
    vaultItems: List<VaultItem>,
    isScanning: Boolean,
    scanProgress: Int,
    onStartScan: () -> Unit,
    onNavigateToPasswords: () -> Unit,
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
            10L * 1024 * 1024 * 1024
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF071422),
                        Color(0xFF040B13)
                    )
                )
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        BrightCyan.copy(alpha = 0.4f),
                        DeckBorder,
                        BrightCyan.copy(alpha = 0.2f)
                    )
                ),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag("cyber_vault_hud_deck")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Main Top Bar: Status + Action Micro-Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Active Matrix Beacon & Title (clickable to expand telemetry)
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { isExpanded = !isExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isScanning) BrightCyan else NeonGreen)
                            .alpha(pulseAlpha)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isScanning) "SCANNING ($scanProgress%)" else "ZERO-TRUST MATRIX",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isScanning) BrightCyan else Color.White,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BrightCyan.copy(alpha = 0.12f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "AES-256",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrightCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Right: Compact Action Capsules
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Scan Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BrightCyan.copy(alpha = 0.15f))
                            .border(1.dp, BrightCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .clickable { onStartScan() }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                            .testTag("cyber_hud_scan_chip"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = BrightCyan,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "SCAN",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrightCyan,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Passwords & Secrets Launcher Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonGreen.copy(alpha = 0.12f))
                            .border(1.dp, NeonGreen.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .clickable { onNavigateToPasswords() }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                            .testTag("cyber_hud_passwords_chip"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "PASSWORDS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreen,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Streamlined Storage Telemetry Row & Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                // Storage Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF030A11))
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
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight().background(BrightCyan.copy(alpha = 0.2f)))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Telemetry summary readout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VAULT: ${formatFileSize(totalSizeBytes)} (${vaultItems.size} files)",
                        fontSize = 9.sp,
                        color = MutedText,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "FREE: ${formatFileSize(freeDeviceBytes)}",
                            fontSize = 9.sp,
                            color = BrightCyan.copy(alpha = 0.85f),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MutedText,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            // Expandable Deep Security & Storage Inspector
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = DeckBorder, thickness = 0.8.dp)

                    // Security metrics grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "SECURITY ENCLAVE", fontSize = 8.sp, color = MutedText, fontFamily = FontFamily.Monospace)
                            Text(text = "TEE / STRONGBOX", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                        Column {
                            Text(text = "AUTH CIPHER", fontSize = 8.sp, color = MutedText, fontFamily = FontFamily.Monospace)
                            Text(text = "GCM 256-BIT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonGreen, fontFamily = FontFamily.Monospace)
                        }
                        Column {
                            Text(text = "DURESS DEFENSE", fontSize = 8.sp, color = MutedText, fontFamily = FontFamily.Monospace)
                            Text(text = "ARMED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BrightCyan, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Storage category details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CompactStoragePill(label = "Photos", size = formatFileSize(photoBytes), color = BrightCyan)
                        CompactStoragePill(label = "Videos", size = formatFileSize(videoBytes), color = NeonPurple)
                        CompactStoragePill(label = "Docs", size = formatFileSize(docBytes), color = NeonGreen)
                        if (audioBytes > 0) {
                            CompactStoragePill(label = "Audio", size = formatFileSize(audioBytes), color = AmberYellow)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactStoragePill(label: String, size: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF030A12))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label: $size",
            fontSize = 8.5.sp,
            color = Color.White,
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
