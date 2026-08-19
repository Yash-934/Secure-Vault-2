package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.EncryptionComponentReport
import com.example.security.EncryptionInspectorEngine
import com.example.security.EncryptionInspectorReport
import com.example.security.EncryptionSelfTestResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Futuristic Cyberpunk Color Palette
private val PitchBlackBg = Color(0xFF03070E)
private val CardNavyBg = Color(0xFF071424)
private val CardBorderColor = Color(0xFF0E253A)
private val NeonCyan = Color(0xFF00F5D4)
private val NeonGreen = Color(0xFF00FF66)
private val NeonPurple = Color(0xFF9D4EDD)
private val PanicRed = Color(0xFFFF2A55)
private val MutedSlate = Color(0xFF6C7E93)
private val LightText = Color(0xFFE2E8F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptionInspectorScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var report by remember { mutableStateOf<EncryptionInspectorReport?>(null) }
    var selfTestResult by remember { mutableStateOf<EncryptionSelfTestResult?>(null) }
    var isRunningSelfTest by remember { mutableStateOf(false) }
    var expandedComponentId by remember { mutableStateOf<String?>("database") }
    var showTerminalLogs by remember { mutableStateOf(true) }

    // Load initial report on launch
    LaunchedEffect(Unit) {
        report = EncryptionInspectorEngine.inspectAll(context)
    }

    val pulseTransition = rememberInfiniteTransition(label = "inspector_pulse")
    val beaconAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_anim"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.15f))
                                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EnhancedEncryption,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ENCRYPTION INSPECTOR",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = LightText,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "ZERO-KNOWLEDGE ARCHITECTURE TELEMETRY",
                                fontSize = 8.5.sp,
                                color = NeonCyan,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("encryption_inspector_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = LightText
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                report = EncryptionInspectorEngine.inspectAll(context)
                            }
                        },
                        modifier = Modifier.testTag("encryption_inspector_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Telemetry",
                            tint = NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PitchBlackBg.copy(alpha = 0.95f)
                )
            )
        },
        containerColor = PitchBlackBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
        ) {
            // 1. HERO CRYPTOGRAPHIC TELEMETRY CARD
            item {
                HeroTelemetryCard(
                    report = report,
                    isRunningSelfTest = isRunningSelfTest,
                    beaconAlpha = beaconAlpha,
                    onRunSelfTest = {
                        coroutineScope.launch {
                            isRunningSelfTest = true
                            val result = EncryptionInspectorEngine.runSelfTest(context)
                            selfTestResult = result
                            report = EncryptionInspectorEngine.inspectAll(context)
                            isRunningSelfTest = false
                        }
                    }
                )
            }

            // 2. DYNAMIC SELF-TEST TELEMETRY HUD (If executed)
            if (selfTestResult != null || isRunningSelfTest) {
                item {
                    SelfTestTerminalCard(
                        result = selfTestResult,
                        isRunning = isRunningSelfTest,
                        showLogs = showTerminalLogs,
                        onToggleLogs = { showTerminalLogs = !showTerminalLogs }
                    )
                }
            }

            // 3. SECTION HEADER: 6 CRYPTOGRAPHIC SUBSYSTEMS
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NeonGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ACTIVE ENCRYPTION SUBSYSTEMS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MutedSlate,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "6 ENGINES MONITORED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 4. THE 6 DETAILED ENCRYPTION COMPONENT CARDS
            val components = report?.components ?: emptyList()
            items(components, key = { it.componentId }) { comp ->
                val isExpanded = expandedComponentId == comp.componentId
                EncryptionComponentCard(
                    component = comp,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedComponentId = if (isExpanded) null else comp.componentId
                    }
                )
            }

            // 5. SECURITY & ZERO-LEAKAGE ASSURANCE BANNER
            item {
                SecurityAssuranceBanner()
            }
        }
    }
}

/**
 * Top Hero Card featuring radar telemetry, status badges, and the Run Self-Test action.
 */
@Composable
private fun HeroTelemetryCard(
    report: EncryptionInspectorReport?,
    isRunningSelfTest: Boolean,
    beaconAlpha: Float,
    onRunSelfTest: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val lastVerified = remember(report?.timestamp) {
        report?.timestamp?.let { sdf.format(Date(it)) } ?: "Verifying..."
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("encryption_hero_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardNavyBg),
        border = BorderStroke(1.dp, CardBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(NeonGreen)
                            .alpha(beaconAlpha)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ZERO-TRUST CRYPTO ENCLAVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonGreen.copy(alpha = 0.15f))
                        .border(1.dp, NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "AES-256 / ARGON2",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Status Metrics Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PitchBlackBg.copy(alpha = 0.6f))
                    .border(0.8.dp, CardBorderColor, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PROTECTION LEVEL",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MutedSlate,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "MILITARY GRADE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(modifier = Modifier.width(1.dp).height(24.dp).background(CardBorderColor))

                Column {
                    Text(
                        text = "ISOLATION MODE",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MutedSlate,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "100% OFFLINE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(modifier = Modifier.width(1.dp).height(24.dp).background(CardBorderColor))

                Column {
                    Text(
                        text = "KEY REMANENCE",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MutedSlate,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ZERO-DISK",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = LightText,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Last Verified row + Self-Test Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LAST VERIFIED",
                        fontSize = 8.5.sp,
                        color = MutedSlate,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = lastVerified,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LightText,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Button(
                    onClick = onRunSelfTest,
                    enabled = !isRunningSelfTest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("run_encryption_self_test_button")
                ) {
                    if (isRunningSelfTest) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TESTING...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "RUN SELF-TEST",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

/**
 * Self-Test HUD Terminal showing execution metrics and real-time pass/fail indicators.
 */
@Composable
private fun SelfTestTerminalCard(
    result: EncryptionSelfTestResult?,
    isRunning: Boolean,
    showLogs: Boolean,
    onToggleLogs: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("self_test_terminal_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF040C16)),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRunning) "DYNAMIC SELF-TEST RUNNING..." else "CRYPTOGRAPHIC SELF-TEST RESULTS",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (!isRunning && result != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (result.isSuccess) NeonGreen.copy(alpha = 0.2f) else PanicRed.copy(alpha = 0.2f))
                            .border(1.dp, if (result.isSuccess) NeonGreen else PanicRed, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (result.isSuccess) "PASS" else "FAIL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (result.isSuccess) NeonGreen else PanicRed,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if (result != null) {
                // Test result chips grid
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelfTestResultRow(
                        name = "AES-256-GCM Roundtrip",
                        passed = result.aesGcmRoundtripPass,
                        detail = "256-byte payload (${result.aesGcmExecutionTimeMs}ms) • 16B Tag Verified"
                    )
                    SelfTestResultRow(
                        name = "Argon2id Memory-Hard KDF",
                        passed = result.argon2KdfPass,
                        detail = "64 MiB RAM Derivation (${result.argon2KdfExecutionTimeMs}ms)"
                    )
                    SelfTestResultRow(
                        name = "Database Keystore Unwrapping",
                        passed = result.databaseKeyVerificationPass,
                        detail = "Hardware TEE Key (${result.databaseKeyExecutionTimeMs}ms) • SQLCipher Active"
                    )
                    SelfTestResultRow(
                        name = "Zero Plaintext Disk Remanence",
                        passed = result.zeroDiskLeakPass,
                        detail = "0 unencrypted media/cache artifacts on storage"
                    )
                    SelfTestResultRow(
                        name = "Thumbnail Encryption Container",
                        passed = result.thumbnailFormatIntegrityPass,
                        detail = "All thumbnails conform to .thumb_aes256"
                    )
                }

                HorizontalDivider(color = CardBorderColor, thickness = 0.6.dp)

                // Expandable Raw Telemetry Stream
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleLogs() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DIAGNOSTIC LOG STREAM (${result.telemetryLogs.size} ENTRIES)",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedSlate,
                        fontFamily = FontFamily.Monospace
                    )
                    Icon(
                        imageVector = if (showLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MutedSlate,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(
                    visible = showLogs,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF01050A))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        result.telemetryLogs.forEach { log ->
                            Text(
                                text = log,
                                fontSize = 8.5.sp,
                                color = if (log.contains("PASS")) NeonGreen else if (log.contains("FAIL") || log.contains("ERROR")) PanicRed else NeonCyan,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelfTestResultRow(
    name: String,
    passed: Boolean,
    detail: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF071829))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (passed) NeonGreen else PanicRed,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = name,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = detail,
                    fontSize = 8.sp,
                    color = MutedSlate,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Text(
            text = if (passed) "PASS" else "FAIL",
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Black,
            color = if (passed) NeonGreen else PanicRed,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Individual Component Card displaying Status, Engine, Algorithm, and expandable architectural specs.
 */
@Composable
private fun EncryptionComponentCard(
    component: EncryptionComponentReport,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val icon = getComponentIcon(component.componentId)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("encryption_card_${component.componentId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardNavyBg),
        border = BorderStroke(
            1.dp,
            if (component.diagnosticCheckPassed) CardBorderColor else PanicRed.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Icon + Title + Status Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeonCyan.copy(alpha = 0.12f))
                            .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = component.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${component.libraryOrEngine} • ${component.algorithm}",
                            fontSize = 9.5.sp,
                            color = MutedSlate,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Badge with Green Shield
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (component.diagnosticCheckPassed) NeonGreen.copy(alpha = 0.15f) else PanicRed.copy(alpha = 0.15f))
                            .border(0.8.dp, if (component.diagnosticCheckPassed) NeonGreen else PanicRed, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (component.diagnosticCheckPassed) Icons.Default.Shield else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (component.diagnosticCheckPassed) NeonGreen else PanicRed,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = component.status,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (component.diagnosticCheckPassed) NeonGreen else PanicRed,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse Details" else "Expand Details",
                        tint = MutedSlate,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Key Protection Highlight
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF030B14))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Key Protection: ${component.keyProtection}",
                    fontSize = 8.5.sp,
                    color = LightText,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Diagnostic Summary Line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (component.diagnosticCheckPassed) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (component.diagnosticCheckPassed) NeonGreen else PanicRed,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = component.diagnosticDetails,
                    fontSize = 8.5.sp,
                    color = if (component.diagnosticCheckPassed) NeonGreen.copy(alpha = 0.85f) else PanicRed,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 12.sp
                )
            }

            // Expandable Architectural Specifications Matrix
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "SPECIFICATIONS & PARAMETERS",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    )

                    component.specs.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF040F1D))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontSize = 8.5.sp,
                                color = MutedSlate,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = value,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LightText,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Security & Zero-Knowledge Assurance Banner at bottom.
 */
@Composable
private fun SecurityAssuranceBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF071829),
                        PitchBlackBg
                    )
                )
            )
            .border(1.dp, CardBorderColor, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(NeonGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "ZERO-PLAINTEXT / ZERO-KEY LEAK GUARANTEE",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Encryption Inspector runs exclusively offline with zero network connectivity. All checks operate via cryptographic probes and metadata analysis. Raw keys, passphrases, and unencrypted file payloads are never read, buffered, or exposed.",
                    fontSize = 8.5.sp,
                    color = MutedSlate,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

private fun getComponentIcon(componentId: String): ImageVector {
    return when (componentId) {
        "database" -> Icons.Default.Storage
        "vault_files" -> Icons.Default.Lock
        "thumbnails" -> Icons.Default.Image
        "media_streaming" -> Icons.Default.Movie
        "steganography" -> Icons.Default.Visibility
        "backups" -> Icons.Default.FolderZip
        else -> Icons.Default.Security
    }
}
