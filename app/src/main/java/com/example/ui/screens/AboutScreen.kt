package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

private val DarkNavyBg = Color(0xFF03070C)
private val CardBg = Color(0xFF07111B)
private val CardBorder = Color(0xFF112538)
private val BrightCyan = Color(0xFF00D2EF)
private val SubtitleText = Color(0xFF6C7A8E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBackClick: () -> Unit) {
    Scaffold(
        containerColor = DarkNavyBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "APP INFO",
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // 1. App Identity Section
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(BrightCyan.copy(alpha = 0.15f))
                    .border(2.dp, BrightCyan.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier.size(80.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "QUANTUM VAULT",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Version 1.0.0",
                fontSize = 13.sp,
                color = SubtitleText,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // 2. Description Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Quantum Vault is a next-generation, offline-first security application designed for absolute privacy. Built on a strict 'Zero-Trust' architecture, it ensures that your personal data remains encrypted, invisible, and completely isolated from the outside world.",
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // 3. Security Architecture & Tech Stack Box (Redesigned from Screenshot)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header Row with Red Shield Icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFFFF2A4B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SECURITY ARCHITECTURE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB0BEC5),
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.6.sp
                        )
                    }

                    // Architecture Items
                    SecurityArchItem(
                        title = "AES-256-GCM",
                        subtitle = "Authenticated Symmetric Encryption"
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    SecurityArchItem(
                        title = "Argon2id KDF",
                        subtitle = "Memory-hard brute-force resistance"
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    SecurityArchItem(
                        title = "Hardware Keystore",
                        subtitle = "Biometric TEE/StrongBox attestation"
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    SecurityArchItem(
                        title = "Anti-Tamper & Root",
                        subtitle = "Detects Magisk, KernelSU, and Frida"
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    SecurityArchItem(
                        title = "Stealth Disguise",
                        subtitle = "Decoy applications & covert triggers"
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    SecurityArchItem(
                        title = "Multi-Carrier Steganography",
                        subtitle = "Conceal vault payloads in MP4, PDF & Images"
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    SecurityArchItem(
                        title = "Zero Cloud Telemetry",
                        subtitle = "Offline data storage"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // 4. Developer & Dedication Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkNavyBg), // Slightly different to make it stand out
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Developed & Engineered by: Yash Pradeep",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Dedicated to Ruem❤️Rumi",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Italic,
                        color = BrightCyan.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f, fill = false))
            Spacer(modifier = Modifier.height(40.dp))

            // 5. Footer
            Text(
                text = "© 2026 Yash Pradeep. All Rights Reserved.",
                fontSize = 10.sp,
                color = SubtitleText.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun SecurityArchItem(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(BrightCyan)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = SubtitleText,
                lineHeight = 16.sp
            )
        }
    }
}
