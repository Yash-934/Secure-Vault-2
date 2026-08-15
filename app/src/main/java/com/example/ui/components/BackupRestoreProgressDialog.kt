package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.ui.BackupRestoreProgressState
import com.example.ui.BackupRestoreType

private val DialogDarkBg = Color(0xFF070E17)
private val DialogBorder = Color(0xFF1E3A5A)
private val BrightCyan = Color(0xFF00F5D4)
private val NeonPurple = Color(0xFF9D4EDD)
private val MutedText = Color(0xFF8DA4BE)
private val DarkCardBg = Color(0xFF0C1726)
private val SuccessGreen = Color(0xFF10B981)
private val ErrorRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreProgressDialog(
    state: BackupRestoreProgressState,
    onDismiss: () -> Unit
) {
    if (!state.isActive) return

    val infiniteTransition = rememberInfiniteTransition(label = "backup_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "progress_animation"
    )

    BasicAlertDialog(
        onDismissRequest = {
            if (state.isComplete) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state.isComplete,
            dismissOnClickOutside = state.isComplete
        ),
        modifier = Modifier.testTag("backup_restore_progress_dialog")
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = DialogDarkBg,
            border = BorderStroke(1.5.dp, if (state.isComplete) (if (state.isSuccess) SuccessGreen else ErrorRed) else BrightCyan.copy(alpha = 0.8f)),
            shadowElevation = 28.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Glowing Icon
                val iconColor = when {
                    state.isComplete && state.isSuccess -> SuccessGreen
                    state.isComplete && !state.isSuccess -> ErrorRed
                    else -> BrightCyan
                }

                val headerIcon = when {
                    state.isComplete && state.isSuccess -> Icons.Default.CheckCircle
                    state.isComplete && !state.isSuccess -> Icons.Default.ErrorOutline
                    state.type == BackupRestoreType.BACKUP || state.type == BackupRestoreType.STEGO_BACKUP -> Icons.Default.CloudUpload
                    else -> Icons.Default.CloudDownload
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(if (!state.isComplete) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    iconColor.copy(alpha = 0.3f),
                                    iconColor.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(1.8.dp, iconColor.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = state.title,
                        tint = iconColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Title
                Text(
                    text = state.title.ifEmpty { "VAULT OPERATION" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    letterSpacing = 1.3.sp,
                    textAlign = TextAlign.Center
                )

                // Subtitle
                Text(
                    text = state.subtitle.ifEmpty { "Zero-Knowledge Hardware-Backed Security" },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = BrightCyan.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (!state.isComplete) {
                    // Live Status Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, DialogBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "STATUS",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MutedText,
                                    letterSpacing = 1.sp
                                )

                                if (state.totalItems > 0) {
                                    Text(
                                        text = "FILE ${state.currentItemIndex} OF ${state.totalItems}",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = BrightCyan
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = state.currentStep.ifEmpty { "Processing vault cryptographic stream..." },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 18.sp
                            )

                            if (state.bytesProcessed > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Data Transferred: ${formatBytes(state.bytesProcessed)}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MutedText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Progress Bar & Percentage Row
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STREAM PROGRESS",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MutedText
                            )

                            val percent = (animatedProgress * 100).toInt()
                            Text(
                                text = "$percent%",
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrightCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Custom Cyber Glowing Linear Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF132338))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                NeonPurple,
                                                BrightCyan
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MutedText.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "AES-256-GCM Streaming • Do not close app",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MutedText.copy(alpha = 0.8f)
                        )
                    }
                } else {
                    // Result Summary Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.isSuccess) Color(0xFF071E18) else Color(0xFF220B0B)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            if (state.isSuccess) SuccessGreen.copy(alpha = 0.5f) else ErrorRed.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (state.isSuccess) "OPERATION COMPLETED" else "OPERATION FAILED",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isSuccess) SuccessGreen else ErrorRed,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = state.resultSummary ?: (if (state.isSuccess) "All files processed successfully." else "An error occurred."),
                                fontSize = 13.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("backup_restore_dismiss_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isSuccess) BrightCyan else ErrorRed
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = if (state.isSuccess) "DONE" else "CLOSE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (state.isSuccess) Color(0xFF03080F) else Color.White
                        )
                    }
                }
            }
        }
    }
}
