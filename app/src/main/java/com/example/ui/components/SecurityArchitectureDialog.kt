package com.example.ui.components

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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.VaultBorder
import com.example.ui.theme.VaultNeonGreen
import com.example.ui.theme.VaultPrimaryCyan
import com.example.ui.theme.VaultSecondaryNeonBlue
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceVariant
import com.example.ui.theme.VaultTextPrimary
import com.example.ui.theme.VaultTextSecondary

@Composable
fun SecurityArchitectureDialog(
    onDismiss: () -> Unit
) {
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
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = VaultPrimaryCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "SYSTEM SECURITY LEVEL",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Rating: ULTRA-HIGH (Military-Grade)",
                        fontSize = 11.sp,
                        color = VaultNeonGreen
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "This application employs state-of-the-art cryptographic defenses, ensuring complete data sovereignty and protection against advanced forensic analysis.",
                    fontSize = 12.sp,
                    color = VaultTextSecondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                ArchitectureItem(
                    icon = Icons.Default.Shield,
                    iconTint = VaultPrimaryCyan,
                    title = "AES-256-GCM Hardware Encryption",
                    description = "Every file is encrypted using an authenticated 256-bit AES-GCM cipher. Master keys are securely locked inside the Android Hardware Keystore (TEE) making remote extraction impossible."
                )

                ArchitectureItem(
                    icon = Icons.Default.Memory,
                    iconTint = VaultNeonGreen,
                    title = "Zero-Disk-Footprint (RAM Pipeline)",
                    description = "Images, videos, and carrier payloads are decrypted entirely in-memory using an instant streaming pipeline. Absolutely zero unencrypted plain-text data is ever written to the device's physical storage."
                )

                ArchitectureItem(
                    icon = Icons.Default.Warning,
                    iconTint = Color(0xFFFF4C4C),
                    title = "Anti-Malware Root Lockdown",
                    description = "Active environment monitoring instantly halts and blocks the application if compromised environments (Root, Magisk, System overlays) are detected, preventing screen scrapers and memory dumpers."
                )

                ArchitectureItem(
                    icon = Icons.Default.Password,
                    iconTint = VaultSecondaryNeonBlue,
                    title = "Scrambled Matrix Keypad",
                    description = "The PIN keypad scrambles its digit positions on every launch. This mathematically defeats touch-coordinate loggers, screen overlay attacks, and shoulder surfing."
                )

                ArchitectureItem(
                    icon = Icons.Default.DeleteForever,
                    iconTint = Color(0xFFFFD166),
                    title = "Forensic Zero-Overwrite Shredder",
                    description = "When a file is deleted or an emergency Kill-PIN is activated, the data is physically overwritten with zeroes (0x00) across the storage blocks, neutralizing forensic recovery tools (e.g. DiskDigger, Cellebrite)."
                )

                ArchitectureItem(
                    icon = Icons.Default.Code,
                    iconTint = VaultPrimaryCyan,
                    title = "PBKDF2 Hashed Master Backups",
                    description = "Portable master backups derive unique AES keys via PBKDF2 with HMAC-SHA256 (10,000 iterations) and 16-byte random salts. This grants extreme resistance against brute-force and dictionary attacks."
                )
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
                    text = "CLOSE ANALYSIS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}

@Composable
private fun ArchitectureItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VaultSurfaceVariant)
            .border(1.dp, VaultBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = VaultTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = VaultTextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}
