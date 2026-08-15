package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.ImportProgressState
import java.util.Locale

private val DialogDarkBg = Color(0xFF0A0F18)
private val DialogBorder = Color(0xFF1E334D)
private val BrightCyan = Color(0xFF00F5D4)
private val NeonPurple = Color(0xFF9D4EDD)
private val MutedText = Color(0xFF94A3B8)
private val DarkCardBg = Color(0xFF060B12)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptionProgressDialog(
    progressState: ImportProgressState
) {
    if (!progressState.isImporting) return

    val infiniteTransition = rememberInfiniteTransition(label = "shield_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    BasicAlertDialog(
        onDismissRequest = { /* Non-dismissible while encrypting to prevent corrupted state */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        modifier = Modifier.testTag("encryption_progress_dialog")
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DialogDarkBg,
            border = androidx.compose.foundation.BorderStroke(1.2.dp, DialogBorder),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glowing Animated Shield Header
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    BrightCyan.copy(alpha = 0.25f),
                                    NeonPurple.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(1.5.dp, BrightCyan.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Encrypting",
                        tint = BrightCyan,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SECURING & ENCRYPTING",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    letterSpacing = 1.2.sp
                )

                Text(
                    text = "AES-256-GCM Hardware Key Protection",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = BrightCyan.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Current File Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = DarkCardBg,
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, DialogBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "FILE ${progressState.currentFileIndex} OF ${progressState.totalFiles}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = NeonPurple
                            )

                            val percent = if (progressState.totalBytes > 0) {
                                ((progressState.bytesProcessed.toFloat() / progressState.totalBytes.toFloat()) * 100).toInt().coerceIn(0, 100)
                            } else {
                                (progressState.overallProgress * 100).toInt().coerceIn(0, 100)
                            }

                            Text(
                                text = "$percent%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = BrightCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = progressState.currentFileName.ifEmpty { "Encrypting media..." },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Linear Progress Bar
                        val progressFraction = if (progressState.totalBytes > 0) {
                            (progressState.bytesProcessed.toFloat() / progressState.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            progressState.overallProgress.coerceIn(0f, 1f)
                        }

                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = BrightCyan,
                            trackColor = DialogBorder
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatBytes(progressState.bytesProcessed)} / ${formatBytes(progressState.totalBytes)}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MutedText
                            )

                            Text(
                                text = "Zero-Leak Stream",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = BrightCyan.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Security Note
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MutedText,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Writing directly to isolated sandbox storage",
                        fontSize = 11.sp,
                        color = MutedText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
