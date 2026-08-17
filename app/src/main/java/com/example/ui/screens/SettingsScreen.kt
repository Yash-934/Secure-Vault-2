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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.local.VaultSettings
import com.example.security.AuditResult
import com.example.ui.components.AntiTamperReportDialog
import com.example.ui.components.ChangePinDialog
import com.example.ui.components.NuclearSelfDestructDialog
import com.example.ui.components.SecurityArchitectureDialog
import com.example.ui.components.SecurityEnclaveScannerDialog
import com.example.ui.components.MaxSecurityGuideDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    statusMessage: String? = null,
    auditResult: AuditResult? = null,
    isAuditing: Boolean = false,
    onRunAudit: () -> Unit = {},
    onBackClick: () -> Unit,
    onToggleBiometrics: (Boolean) -> Unit,
    onChangeMasterPinClick: () -> Unit,
    onChangeDecoyPinClick: () -> Unit,
    onTogglePanicFlip: (Boolean) -> Unit,
    onToggleThumbnails: (Boolean) -> Unit = {},
    onToggleCamouflage: (Boolean) -> Unit = {},
    onChangeCamouflageType: (String) -> Unit = {},
    onToggleScreenProtection: (Boolean) -> Unit = {},
    onExportBackupClick: () -> Unit = {},
    onImportBackupClick: () -> Unit = {},
    onViewIntruderLogsClick: () -> Unit = {},
    onOpenStealthDialog: () -> Unit = {},
    onToggleKillPin: (Boolean) -> Unit = {},
    onChangeKillPinClick: () -> Unit = {},
    onToggleIntruderSelfie: (Boolean) -> Unit = {},
    onToggleDeadManSwitch: (Boolean) -> Unit = {},
    onChangeDeadManDays: (Int) -> Unit = {},
    onExecuteSelfDestructClick: () -> Unit = {},
    onEmbedStegoClick: () -> Unit = {},
    onExtractStegoClick: () -> Unit = {},
    onNavigateToPasswords: () -> Unit = {},
    onHelpClick: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showConfirmSelfDestructDialog by remember { mutableStateOf(false) }
    var showChangeKillPinDialog by remember { mutableStateOf(false) }
    var showMaxSecurityGuideDialog by remember { mutableStateOf(false) }
    var showSecurityArchitectureDialog by remember { mutableStateOf(false) }
    var showAntiTamperDialog by remember { mutableStateOf(false) }
    var showSecurityEnclaveScannerDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val intruderLogsFlow = remember { AppDatabase.getDatabase(context).intruderLogDao().getAllLogs() }
    val intruderLogs by intruderLogsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(statusMessage)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            onToggleIntruderSelfie(true)
        } else {
            Toast.makeText(context, "Camera permission required for Intruder Selfie", Toast.LENGTH_SHORT).show()
            onToggleIntruderSelfie(false)
        }
    }
    Scaffold(
        containerColor = DarkNavyBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    softWrap = false
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

            // SECURITY ARCHITECTURE CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showSecurityArchitectureDialog = true }
                    .testTag("security_architecture_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, PassGreen.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PassGreen.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = PassGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Security Architecture Level",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Current Rating: ULTRA-HIGH (Military-Grade)",
                                fontSize = 11.sp,
                                color = PassGreen
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "View Security Details",
                        tint = PassGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // MAX SECURITY BEST PRACTICES ACTION CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showMaxSecurityGuideDialog = true }
                    .testTag("max_security_guide_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, BrightCyan.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrightCyan.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = BrightCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Max Security Directives",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "6 Essential Rules for 100% Unbreakable Security",
                                fontSize = 11.sp,
                                color = SubtitleText
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Security Directives",
                        tint = BrightCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ANTI-REVERSE ENGINEERING & TAMPER SHIELD CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showAntiTamperDialog = true }
                    .testTag("anti_tamper_shield_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, PassGreen.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PassGreen.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EnhancedEncryption,
                                contentDescription = null,
                                tint = PassGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Anti-Tamper & Anti-Recompile Shield",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Anti-Debugger • Frida & Hooking Shield • SHA-256 Sig",
                                fontSize = 11.sp,
                                color = SubtitleText
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Anti-Tamper Shield",
                        tint = PassGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ENCRYPTED PASSWORD VAULT CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onNavigateToPasswords() }
                    .testTag("settings_password_manager_btn"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, BrightCyan.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(BrightCyan.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = BrightCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Encrypted Password Vault",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "AES-256 Logins, Credit Cards & Strong Passwords",
                                fontSize = 11.sp,
                                color = SubtitleText
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open Password Manager",
                        tint = BrightCyan,
                        modifier = Modifier.size(22.dp)
                    )
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

                    HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                    // Item 4: File Thumbnail Previews
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
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = BrightCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        title = "File Thumbnail Previews",
                        subtitle = "Show image, video & PDF previews in vault (Disable for stealth)",
                        trailing = {
                            Switch(
                                checked = settings.isThumbnailsEnabled,
                                onCheckedChange = onToggleThumbnails,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkNavyBg,
                                    checkedTrackColor = BrightCyan,
                                    uncheckedThumbColor = SubtitleText,
                                    uncheckedTrackColor = CardBg,
                                    uncheckedBorderColor = CardBorder
                                ),
                                modifier = Modifier.testTag("thumbnails_switch")
                            )
                        }
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

                    HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                    // Item 3: App Icon Camouflage (Calculator / Notes Disguise)
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
                        title = "App Icon Camouflage",
                        subtitle = if (settings.isCamouflageEnabled) {
                            "Camouflage Active: ${if (settings.camouflageType == "NOTES") "Quick Notes" else "Calculator"}"
                        } else {
                            "Disguise launcher icon & entry screen"
                        },
                        trailing = {
                            Switch(
                                checked = settings.isCamouflageEnabled,
                                onCheckedChange = onToggleCamouflage,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkNavyBg,
                                    checkedTrackColor = BrightCyan,
                                    uncheckedThumbColor = SubtitleText,
                                    uncheckedTrackColor = CardBg,
                                    uncheckedBorderColor = CardBorder
                                ),
                                modifier = Modifier.testTag("camouflage_switch")
                            )
                        }
                    )

                    if (settings.isCamouflageEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onChangeCamouflageType("CALCULATOR") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (settings.camouflageType == "CALCULATOR") BrightCyan else Color(0xFF0C1D2E),
                                    contentColor = if (settings.camouflageType == "CALCULATOR") Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Calculator",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Button(
                                onClick = { onChangeCamouflageType("NOTES") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (settings.camouflageType == "NOTES") BrightCyan else Color(0xFF0C1D2E),
                                    contentColor = if (settings.camouflageType == "NOTES") Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Notes App",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                    // Item 4: Anti-Screen Capture (FLAG_SECURE)
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
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = PanicRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        title = "Anti-Screen Capture (FLAG_SECURE)",
                        subtitle = "Blocks screenshots/recording (Note: turns web preview black)",
                        trailing = {
                            Switch(
                                checked = settings.isScreenProtectionEnabled,
                                onCheckedChange = onToggleScreenProtection,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkNavyBg,
                                    checkedTrackColor = PanicRed,
                                    uncheckedThumbColor = SubtitleText,
                                    uncheckedTrackColor = CardBg,
                                    uncheckedBorderColor = CardBorder
                                ),
                                modifier = Modifier.testTag("screen_protection_switch")
                            )
                        }
                    )
                }
            }

            // SECTION 4: ANTI-FORENSICS & DISASTER RECOVERY
            SectionHeader(title = "ANTI-FORENSICS & DISASTER RECOVERY")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column {
                    // Item 1: Export Encrypted Master Backup
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
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = BrightCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        title = "Export Encrypted Master Backup",
                        subtitle = "Argon2id (64MB memory-hard) + optional Keystore hardware key binding",
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Export Backup",
                                tint = BrightCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = onExportBackupClick,
                        modifier = Modifier.testTag("export_backup_item")
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                    // Item 2: Restore Master Backup
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
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = BrightCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        title = "Restore Encrypted Master Backup",
                        subtitle = "Decrypt and restore vault files from a master backup file",
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Restore Backup",
                                tint = BrightCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = onImportBackupClick,
                        modifier = Modifier.testTag("restore_backup_item")
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                    // Item 3: View Intruder Access Logs
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
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = PanicRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        title = "Intruder Access Logs",
                        subtitle = "Review timestamp records of failed PIN/Biometric breach attempts",
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "View Intruder Logs",
                                tint = PanicRed,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = onViewIntruderLogsClick,
                        modifier = Modifier.testTag("intruder_logs_item")
                    )
                }
            }

            // SECTION 5: NUCLEAR & GHOST PROTOCOL (PHASE 5)
            SectionHeader(title = "NUCLEAR & GHOST PROTOCOL")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, PanicRed.copy(alpha = 0.5f))
            ) {
                Column {
                    // 1. Kill PIN (Self-Destruct PIN)
                    SettingRowItem(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PanicRed.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = PanicRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        title = "The 'Kill PIN' (Self-Destruct)",
                        subtitle = "Secondary PIN that instantly shreds all files, keys & databases upon input",
                        trailing = {
                            Switch(
                                checked = settings.isKillPinEnabled,
                                onCheckedChange = onToggleKillPin,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkNavyBg,
                                    checkedTrackColor = PanicRed,
                                    uncheckedThumbColor = SubtitleText,
                                    uncheckedTrackColor = CardBg,
                                    uncheckedBorderColor = CardBorder
                                ),
                                modifier = Modifier.testTag("kill_pin_switch")
                            )
                        }
                    )

                    if (settings.isKillPinEnabled) {
                        HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                        SettingRowItem(
                            icon = { Spacer(modifier = Modifier.size(36.dp)) },
                            title = "Configure Kill PIN",
                            subtitle = "Current Kill PIN: ****",
                            trailing = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Kill PIN",
                                    tint = PanicRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = { showChangeKillPinDialog = true },
                            modifier = Modifier.testTag("change_kill_pin_item")
                        )
                    }

                    HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                    // 2. Intruder Selfie (CameraX)
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
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    tint = BrightCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        title = "Intruder Selfie (CameraX)",
                        subtitle = "Silently capture front camera photo on 3 consecutive failed PIN attempts",
                        trailing = {
                            Switch(
                                checked = settings.isIntruderSelfieEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                            onToggleIntruderSelfie(true)
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    } else {
                                        onToggleIntruderSelfie(false)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkNavyBg,
                                    checkedTrackColor = BrightCyan,
                                    uncheckedThumbColor = SubtitleText,
                                    uncheckedTrackColor = CardBg,
                                    uncheckedBorderColor = CardBorder
                                ),
                                modifier = Modifier.testTag("intruder_selfie_switch")
                            )
                        }
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                    // 3. Universal Multi-Carrier Steganography (Videos, PDFs, Photos)
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
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = BrightCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        title = "Multi-Carrier Steganography",
                        subtitle = "Conceal large vault files (up to 5GB+) inside Videos (MP4), PDFs or Photos without breaking playback",
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = "Embed",
                                    color = BrightCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onEmbedStegoClick() }.padding(vertical = 8.dp)
                                )
                                Text(
                                    text = "Extract",
                                    color = PassGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onExtractStegoClick() }.padding(vertical = 8.dp)
                                )
                            }
                        }
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                    // 4. Dead Man's Switch (WorkManager)
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
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = PanicRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        title = "Dead Man's Switch",
                        subtitle = "Auto self-destruct if vault is inactive for ${settings.deadManDays} days",
                        trailing = {
                            Switch(
                                checked = settings.isDeadManSwitchEnabled,
                                onCheckedChange = onToggleDeadManSwitch,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkNavyBg,
                                    checkedTrackColor = PanicRed,
                                    uncheckedThumbColor = SubtitleText,
                                    uncheckedTrackColor = CardBg,
                                    uncheckedBorderColor = CardBorder
                                ),
                                modifier = Modifier.testTag("dead_man_switch")
                            )
                        }
                    )

                    HorizontalDivider(color = CardBorder, thickness = 0.8.dp)

                    // 5. Manual Nuclear Wipe Action
                    SettingRowItem(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PanicRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        title = "EXECUTE NUCLEAR SELF-DESTRUCT NOW",
                        subtitle = "Irreversibly shred all internal storage, keys, logs and cache immediately",
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Execute Self Destruct",
                                tint = PanicRed,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        onClick = { showConfirmSelfDestructDialog = true },
                        modifier = Modifier.testTag("nuclear_self_destruct_now_item")
                    )
                }
            }

            // SECTION 6: SUPPORT & DOCUMENTATION
            SectionHeader(title = "SUPPORT & DOCUMENTATION")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
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
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = BrightCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    title = "Help & How to Use",
                    subtitle = "Read the manual and documentation",
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "View Help",
                            tint = SubtitleText,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    onClick = onHelpClick
                )
            }

            if (showMaxSecurityGuideDialog) {
                MaxSecurityGuideDialog(
                    onDismiss = { showMaxSecurityGuideDialog = false }
                )
            }
            
            if (showSecurityArchitectureDialog) {
                SecurityArchitectureDialog(
                    onDismiss = { showSecurityArchitectureDialog = false }
                )
            }

            if (showAntiTamperDialog) {
                AntiTamperReportDialog(
                    onDismiss = { showAntiTamperDialog = false }
                )
            }

            if (showChangeKillPinDialog) {
                ChangePinDialog(
                    title = "Configure Kill PIN",
                    subtitle = "Entering this PIN on lock screen will IMMEDIATELY execute nuclear self-destruct.",
                    onDismiss = { showChangeKillPinDialog = false },
                    onSavePin = { newPin ->
                        onChangeKillPinClick()
                        showChangeKillPinDialog = false
                    }
                )
            }

            if (showConfirmSelfDestructDialog) {
                NuclearSelfDestructDialog(
                    masterPin = settings.masterPin,
                    onDismiss = { showConfirmSelfDestructDialog = false },
                    onConfirmSelfDestruct = {
                        showConfirmSelfDestructDialog = false
                        onExecuteSelfDestructClick()
                    }
                )
            }

            if (showSecurityEnclaveScannerDialog) {
                SecurityEnclaveScannerDialog(
                    auditResult = auditResult,
                    isAuditing = isAuditing,
                    intruderLogs = intruderLogs,
                    onRunAudit = onRunAudit,
                    onClearIntruderLogs = {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                AppDatabase.getDatabase(context).intruderLogDao().clearLogs()
                            } catch (_: Exception) {}
                        }
                    },
                    onDismiss = { showSecurityEnclaveScannerDialog = false }
                )
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
