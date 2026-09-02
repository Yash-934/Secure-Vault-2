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
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.ui.theme.VaultErrorRed
import com.example.ui.theme.VaultNeonGreen
import com.example.ui.theme.VaultPrimaryCyan
import com.example.ui.theme.VaultSecondaryNeonBlue
import com.example.ui.theme.VaultSurface
import com.example.ui.theme.VaultSurfaceVariant
import com.example.ui.theme.VaultTextPrimary
import com.example.ui.theme.VaultTextSecondary

@Composable
fun MaxSecurityGuideDialog(
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
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = VaultPrimaryCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "MAX SECURITY DIRECTIVES",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Essential Rules for Maximum Protection",
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
                    .height(380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecurityDirectiveItem(
                    number = "1",
                    icon = Icons.Default.Key,
                    iconTint = VaultPrimaryCyan,
                    title = "Strong Backup Passwords (Argon2id)",
                    description = "When exporting Master Backups or Stego carrier images, always choose a complex password with 12+ characters (combining uppercase, lowercase, numbers, and symbols). AES-256-GCM encryption provides industry-standard security against brute-force attacks when strong passwords are used."
                )

                SecurityDirectiveItem(
                    number = "2",
                    icon = Icons.Default.Lock,
                    iconTint = VaultNeonGreen,
                    title = "Decoy Vault Preparation (Duress Defense)",
                    description = "Pre-populate your Decoy Vault with plausible, harmless photos. If you are ever forced or coerced into unlocking the vault, enter your Decoy PIN. The app will display convincing decoy media without revealing the existence of your real vault."
                )

                SecurityDirectiveItem(
                    number = "3",
                    icon = Icons.Default.Calculate,
                    iconTint = VaultSecondaryNeonBlue,
                    title = "Stealth Camouflage (Calculator Alias)",
                    description = "Keep Calculator Camouflage enabled. The launcher icon and app label will appear as a functional calculator. To unlock your real or decoy vault, type your PIN into the calculator and press '='."
                )

                SecurityDirectiveItem(
                    number = "4",
                    icon = Icons.Default.Image,
                    iconTint = Color(0xFFFFD166),
                    title = "Steganography Carrier Image Safety",
                    description = "Do not share carrier photos containing hidden encrypted payloads over social media or chat apps (e.g. WhatsApp) as standard image uploads, because automatic compression corrupts the hidden bytes. Always transmit them as uncompressed raw Files or Documents."
                )

                SecurityDirectiveItem(
                    number = "5",
                    icon = Icons.Default.ScreenRotation,
                    iconTint = VaultPrimaryCyan,
                    title = "Panic Flip & Screen Shielding",
                    description = "Placing your device face-down immediately locks the vault via the Panic Flip accelerometer sensor. Furthermore, Screen Protection (FLAG_SECURE) ensures screenshots, screen recordings, and app switcher previews remain strictly blacked out."
                )

                SecurityDirectiveItem(
                    number = "6",
                    icon = Icons.Default.DeleteForever,
                    iconTint = VaultErrorRed,
                    title = "Kill PIN Caution (Emergency Self-Destruct)",
                    description = "Entering your Kill PIN instantly executes a permanent, irreversible cryptographic shredding operation (zero-overwrite). Only use this feature when data destruction is necessary during critical emergencies."
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
                    text = "UNDERSTOOD (I WILL FOLLOW)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}

@Composable
private fun SecurityDirectiveItem(
    number: String,
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
                text = "$number. $title",
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
