package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.VaultSettings
import com.example.security.AuditResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DarkNavyBg = Color(0xFF03070C)
private val CardBg = Color(0xFF07111B)
private val CardBorder = Color(0xFF112538)
private val BrightCyan = Color(0xFF00D2EF)
private val PanicRed = Color(0xFFFF2A55)
private val PassGreen = Color(0xFF00E676)
private val SubtitleText = Color(0xFF6C7A8E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: VaultSettings,
    auditResult: AuditResult? = null,
    isAuditing: Boolean = false,
    onRunAudit: () -> Unit = {},
    onBackClick: () -> Unit,
    onToggleBiometrics: (Boolean) -> Unit,
    onChangeMasterPinClick: () -> Unit,
    onChangeDecoyPinClick: () -> Unit,
    onTogglePanicFlip: (Boolean) -> Unit,
    onOpenStealthDialog: () -> Unit
) {
    Scaffold(
        containerColor = DarkNavyBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SECURITY MATRIX SETTINGS",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = BrightCyan
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
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // SECTION 1: SYSTEM SECURITY INTEGRITY AUDIT
            SectionHeader(title = "SYSTEM SECURITY INTEGRITY AUDIT")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("security_audit_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BrightCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = BrightCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Environment Audit Engine",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Local Zero-Trust Diagnostic Scan",
                                    fontSize = 11.sp,
                                    color = SubtitleText
                                )
                            }
                        }

                        Button(
                            onClick = onRunAudit,
                            enabled = !isAuditing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrightCyan,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("run_audit_button")
                        ) {
                            if (isAuditing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (auditResult == null) Icons.Default.PlayArrow else Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (auditResult == null) "RUN SCAN" else "RE-SCAN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (auditResult != null) {
                        HorizontalDivider(color = CardBorder, thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Status Badge Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Audit Status: ",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SubtitleText
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (auditResult.status == "PASS") PassGreen.copy(alpha = 0.18f)
                                            else PanicRed.copy(alpha = 0.18f)
                                        )
                                        .border(
                                            1.dp,
                                            if (auditResult.status == "PASS") PassGreen else PanicRed,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (auditResult.status == "PASS") Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (auditResult.status == "PASS") PassGreen else PanicRed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = auditResult.status,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (auditResult.status == "PASS") PassGreen else PanicRed,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            Text(
                                text = "Scanned: ${sdf.format(Date(auditResult.timestamp))}",
                                fontSize = 10.sp,
                                color = SubtitleText,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Checklist breakdown
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            auditResult.checkResults.forEach { (checkName, isPassed) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF030D16))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isPassed) PassGreen.copy(alpha = 0.2f) else PanicRed.copy(
                                                    alpha = 0.2f
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPassed) Icons.Default.Check else Icons.Default.Close,
                                            contentDescription = null,
                                            tint = if (isPassed) PassGreen else PanicRed,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = checkName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (isPassed) "SECURE" else "WARN",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPassed) PassGreen else PanicRed,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Execute an offline 5-point environment scan verifying network isolation, keystore integrity, AES-256-GCM crypto engine, biometric support, and private storage path security.",
                            fontSize = 11.sp,
                            color = SubtitleText,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // SECTION 2: AUTHENTICATION & ZERO-TRUST
            SectionHeader(title = "AUTHENTICATION & ZERO-TRUST")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column {
                    // Item 1: Biometric Authentication
                    SettingRowItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = BrightCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        title = "Biometric Authentication",
                        subtitle = "Require Fingerprint or Face ID scan to disarm vault lock",
                        trailing = {
                            Switch(
                                checked = settings.isBiometricsEnabled,
                                onCheckedChange = onToggleBiometrics,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkNavyBg,
                                    checkedTrackColor = BrightCyan,
                                    uncheckedThumbColor = SubtitleText,
                                    uncheckedTrackColor = CardBg,
                                    uncheckedBorderColor = CardBorder
                                ),
                                modifier = Modifier.testTag("biometrics_switch")
                            )
                        }
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                    // Item 2: Change Master PIN
                    SettingRowItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = null,
                                tint = BrightCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        title = "Change Master PIN",
                        subtitle = "Current PIN: ****",
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Master PIN",
                                tint = BrightCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = onChangeMasterPinClick,
                        modifier = Modifier.testTag("change_master_pin_item")
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                    // Item 3: Change Decoy PIN
                    SettingRowItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = BrightCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        title = "Change Decoy PIN",
                        subtitle = "Secondary PIN to deploy fake decoy vault under coercion",
                        trailing = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Decoy PIN",
                                tint = BrightCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = onChangeDecoyPinClick,
                        modifier = Modifier.testTag("change_decoy_pin_item")
                    )
                }
            }

            // SECTION 3: PANIC & COERCION DEFENSE
            SectionHeader(title = "PANIC & COERCION DEFENSE")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column {
                    // Item 1: Panic Flip Lock
                    SettingRowItem(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PanicRed.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhonelinkSetup,
                                    contentDescription = null,
                                    tint = PanicRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        title = "Panic Flip Lock",
                        subtitle = "Flip device face down to immediately seal and lock vault",
                        trailing = {
                            Switch(
                                checked = settings.isPanicFlipEnabled,
                                onCheckedChange = onTogglePanicFlip,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkNavyBg,
                                    checkedTrackColor = PanicRed,
                                    uncheckedThumbColor = SubtitleText,
                                    uncheckedTrackColor = CardBg,
                                    uncheckedBorderColor = CardBorder
                                ),
                                modifier = Modifier.testTag("panic_flip_switch")
                            )
                        }
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                    // Item 2: Stealth Protocol Info
                    SettingRowItem(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BrightCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = null,
                                    tint = BrightCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        title = "Stealth Protocol Info",
                        subtitle = "View decoy defense & offline zero-trust details",
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open Details",
                                tint = BrightCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = onOpenStealthDialog,
                        modifier = Modifier.testTag("stealth_mode_item")
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = BrightCyan,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingRowItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = SubtitleText,
                lineHeight = 15.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        trailing()
    }
}
