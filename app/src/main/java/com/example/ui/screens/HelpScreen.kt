package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkNavyBg = Color(0xFF03070C)
private val CardBg = Color(0xFF07111B)
private val CardBorder = Color(0xFF112538)
private val BrightCyan = Color(0xFF00D2EF)
private val SubtitleText = Color(0xFF6C7A8E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBackClick: () -> Unit) {
    Scaffold(
        containerColor = DarkNavyBg,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "HELP & HOW TO USE",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavyBg)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "QUANTUM VAULT MANUAL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BrightCyan,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
            )

            HelpExpandableCard(
                title = "1. The Basics (Importing & Exporting)",
                content = "Importing Files:\nTap the '+' button in the vault to securely import photos, videos, and documents from your public gallery.\n\nDelete Original from Gallery:\nWhen prompted, you can choose to automatically delete the original unencrypted file from your public gallery after a successful import. This requires granting a one-time permission.\n\nExporting / Restoring:\nSelect any item in your vault and tap the 'Export' icon to decrypt it and restore it back to your public gallery.\n\nMilitary-Grade Encryption:\nEvery file is individually encrypted using AES-256-GCM before it is written to the vault. Your raw files do not exist anywhere on the storage."
            )

            HelpExpandableCard(
                title = "2. Stealth Disguise Mode (The Calculator)",
                content = "Enable Disguise:\nGo to Settings -> Stealth & Disguise Mode, and enable the Calculator camouflage. Your app icon and name will change to 'Calculator', and launching the app will open a fully functional fake calculator.\n\nOpening the Vault:\nTo bypass the calculator and open your vault:\n1. Type your Master PIN into the calculator.\n2. Press the '=' (Equals) button.\n\nThe app will silently authenticate you and transition to the secure vault."
            )

            HelpExpandableCard(
                title = "3. The Decoy Vault (Fake Vault)",
                content = "Master PIN vs. Decoy PIN:\nYour Master PIN opens your real vault. Your Decoy PIN opens an entirely separate, empty 'Fake' vault.\n\nUnder Duress:\nIf you are ever forced to open the app, enter your Decoy PIN on the lock screen (or in the calculator). It will open the Decoy Vault, keeping your real files completely hidden.\n\nBest Practice:\nImport some normal, harmless photos into your Decoy Vault so that it looks authentic and convincing to whoever is watching you."
            )

            HelpExpandableCard(
                title = "4. Secure Media Viewing",
                content = "On-the-Fly Decryption:\nWhen you view a photo or video in the vault, it is decrypted instantly in memory (RAM). The decrypted file is NEVER written to the disk.\n\nZero Traces:\nThe moment you close the file, exit the viewer, or minimize the app, the temporary decrypted buffer is completely shredded and zeroed out from memory, leaving zero traces."
            )

            HelpExpandableCard(
                title = "5. Anti-Screen Capture",
                content = "Ultimate Privacy:\nBy default, the Android system's FLAG_SECURE is applied to all sensitive screens within the vault.\n\nBlocked Actions:\nScreenshots and screen recordings are strictly blocked. If someone attempts to record your screen while the vault is open, the output will just be a black screen."
            )

            HelpExpandableCard(
                title = "6. Maximum Security Directives & Best Practices",
                content = "1. Strong Backup Passwords (PBKDF2):\nAlways use 12+ characters with mixed case, numbers, and symbols when creating backups or steganography exports. Never use simple PINs or dictionary words.\n\n2. Decoy Vault Preparation:\nKeep some normal harmless photos in your Decoy Vault so that if someone coerces you to open the app, entering the Decoy PIN displays convincing decoy media without revealing your real vault.\n\n3. Steganography Image Safety:\nDo NOT share stego carrier images over WhatsApp or chat apps that compress photos, as compression corrupts hidden payloads. Share them as uncompressed raw documents/files.\n\n4. Panic Flip & Screen Shielding:\nKeep Panic Flip and Screen Protection enabled. Placing your phone face down instantly locks the vault.\n\n5. Kill PIN Caution:\nEntering your Kill PIN immediately triggers a permanent, irreversible nuclear self-destruct (zero-overwrite shredding). Only use it during genuine emergencies.\n\n6. Keystore & APK Integrity:\nYour vault uses hardware-backed Android Keystore keys. Modified APKs cannot access your data due to OS certificate isolation."
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun HelpExpandableCard(title: String, content: String) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = SubtitleText,
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = CardBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = content,
                        fontSize = 13.sp,
                        color = SubtitleText,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
