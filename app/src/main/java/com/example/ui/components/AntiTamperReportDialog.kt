package com.example.ui.components

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.AntiTamperManager
import com.example.ui.theme.VaultBorder
import com.example.ui.theme.VaultErrorRed
import com.example.ui.theme.VaultNeonGreen
import com.example.ui.theme.VaultPrimaryCyan
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceVariant
import com.example.ui.theme.VaultTextPrimary
import com.example.ui.theme.VaultTextSecondary

@Composable
fun AntiTamperReportDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val report = remember { AntiTamperManager.inspectIntegrity(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VaultPrimaryCyan.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EnhancedEncryption,
                        contentDescription = null,
                        tint = VaultPrimaryCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "ANTI-REVERSE ENGINEERING",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Real-time Tamper & Hooking Inspection",
                        fontSize = 11.sp,
                        color = VaultTextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(390.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Overall summary badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (report.isSafeEnvironment) VaultNeonGreen.copy(alpha = 0.12f)
                            else VaultErrorRed.copy(alpha = 0.12f)
                        )
                        .border(
                            1.dp,
                            if (report.isSafeEnvironment) VaultNeonGreen.copy(alpha = 0.4f)
                            else VaultErrorRed.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "APP INTEGRITY STATUS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (report.isSafeEnvironment) VaultNeonGreen else VaultErrorRed
                        )
                        Text(
                            text = if (report.isSafeEnvironment) "Zero Hooking & No Debuggers Detected" else "Potential Tampering Detected",
                            fontSize = 10.sp,
                            color = VaultTextSecondary
                        )
                    }
                    Text(
                        text = if (report.isSafeEnvironment) "SECURE" else "ALERT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = if (report.isSafeEnvironment) VaultNeonGreen else VaultErrorRed
                    )
                }

                TamperCheckRow(
                    title = "Anti-Debugger & Ptrace Check",
                    subtitle = "Blocks JDWP, GDB/LLDB, & TracerPid hooks",
                    isPassed = !report.isDebuggerAttached,
                    icon = Icons.Default.BugReport
                )

                TamperCheckRow(
                    title = "Frida & Dynamic Hooking Shield",
                    subtitle = "Scans Frida ports (27042), sockets & temp files",
                    isPassed = !report.isHookFrameworkDetected,
                    icon = Icons.Default.Code
                )

                TamperCheckRow(
                    title = "In-Memory DEX & Checksum Guard",
                    subtitle = "Verifies classes.dex and dynamic memory loader",
                    isPassed = report.isDexIntegrityValid,
                    icon = Icons.Default.EnhancedEncryption
                )

                TamperCheckRow(
                    title = "Native Code & Memory Self-Check",
                    subtitle = "Flattened control flow & .text self-verification",
                    isPassed = report.isNativeIntegrityValid,
                    icon = Icons.Default.Shield
                )

                TamperCheckRow(
                    title = "Screen Recording & Capture Defense",
                    subtitle = "Detects virtual displays & screenrecord processes",
                    isPassed = !report.isScreenRecordingDetected,
                    icon = Icons.Default.EnhancedEncryption
                )

                TamperCheckRow(
                    title = "Memory Maps (.so Injection Guard)",
                    subtitle = "Verifies /proc/self/maps against injected modules",
                    isPassed = !report.isMemoryTampered,
                    icon = Icons.Default.Memory
                )

                TamperCheckRow(
                    title = "APK Signature & Certificate Hash",
                    subtitle = "Validates OS cryptographic developer signature",
                    isPassed = report.isSignatureValid,
                    icon = Icons.Default.Fingerprint
                )

                // Certificate Fingerprint Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(VaultSurfaceVariant)
                        .border(1.dp, VaultBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "SHA-256 SIGNING CERTIFICATE HASH",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = VaultPrimaryCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = report.signatureFingerprint,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = VaultTextSecondary,
                        lineHeight = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "If an attacker decompiles and re-signs this APK, Android's Hardware Keystore isolates existing master keys, preventing any unauthorized decryption.",
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        lineHeight = 14.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultPrimaryCyan,
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "CLOSE REPORT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}

@Composable
private fun TamperCheckRow(
    title: String,
    subtitle: String,
    isPassed: Boolean,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VaultSurfaceVariant)
            .border(1.dp, VaultBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (isPassed) VaultNeonGreen.copy(alpha = 0.15f)
                    else VaultErrorRed.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPassed) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (isPassed) VaultNeonGreen else VaultErrorRed,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = VaultTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = VaultTextSecondary
            )
        }

        Text(
            text = if (isPassed) "PASS" else "FAIL",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (isPassed) VaultNeonGreen else VaultErrorRed
        )
    }
}
