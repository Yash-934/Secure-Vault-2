package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import com.example.ui.theme.CyberBackgroundGradient
import com.example.ui.theme.CyberNeonGradient
import com.example.ui.theme.CyberPlasmaGradient
import com.example.ui.theme.VaultDarkBackground
import com.example.ui.theme.VaultErrorRed
import com.example.ui.theme.VaultNeonPink
import com.example.ui.theme.VaultNeonPurple
import com.example.ui.theme.VaultPrimaryCyan

@Composable
fun LockScreen(
    onAuthenticateClick: () -> Unit,
    onPinSubmit: (pin: String) -> Unit,
    errorMessage: String? = null,
    errorTrigger: Int = 0,
    lockoutSecondsRemaining: Int = 0
) {
    var enteredPin by remember { mutableStateOf("") }
    val isLockedOut = lockoutSecondsRemaining > 0
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Generate scrambled digits once per LockScreen composition
    val scrambledDigits = remember {
        (0..9).map { it.toString() }.shuffled()
    }

    // Auto-clear PIN input when error occurs
    LaunchedEffect(errorTrigger) {
        if (errorTrigger > 0) {
            enteredPin = ""
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }

    if (isLockedOut) {
        // ULTRA-FUTURISTIC CYBERPUNK SECURITY LOCKOUT SCREEN
        FuturisticCyberLockoutScreen(
            lockoutSecondsRemaining = lockoutSecondsRemaining
        )
    } else {
        // STANDARD PIN ENTRY SCREEN
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberBackgroundGradient)
        ) {
        // Futuristic Cyber Background Grid with Ambient Radial Glow
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 44.dp.toPx()
            val gridColor = Color(0xFF00F5D4).copy(alpha = 0.05f)
            
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
                x += gridSpacing
            }
            
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
                y += gridSpacing
            }

            // Radial Center Ambient Purple/Cyan Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        VaultNeonPurple.copy(alpha = 0.25f),
                        VaultPrimaryCyan.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.35f),
                    radius = size.width * 0.65f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // 1. Top System Badge (Pill with Gradient Border)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF0B1424))
                    .border(1.5.dp, CyberNeonGradient, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00FF87)) // Matrix Emerald Green
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "QUANTUM VAULT: ENCRYPTED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VaultPrimaryCyan,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Glowing Shield Ring Logo with Concentric Mixed Gradient Rings
            Box(
                modifier = Modifier.size(108.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer Cyber Plasma Gradient Circle
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .border(2.5.dp, CyberPlasmaGradient, CircleShape)
                )
                // Inner Cyber Neon Gradient Circle
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .border(1.8.dp, CyberNeonGradient, CircleShape)
                )
                // Center Shield Icon
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Vault Shield",
                    tint = VaultPrimaryCyan,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Title & Subtitle
            Text(
                text = "QUANTUM VAULT",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "NEON PROTOCOL • AUTH REQUIRED",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = VaultNeonPurple,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 4. PIN Indicator Dots with Cyber Neon Gradient Fill
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) CyberPlasmaGradient else Brush.linearGradient(listOf(Color(0xFF0A1320), Color(0xFF0A1320)))
                            )
                            .border(
                                width = 1.5.dp,
                                brush = if (isFilled) CyberNeonGradient else Brush.linearGradient(listOf(Color(0xFF1E3148), Color(0xFF1E3148))),
                                shape = CircleShape
                            )
                    )
                }
            }

            // Error / Lockout Text Display
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isLockedOut) {
                    Text(
                        text = "LOCKOUT ACTIVE: ${lockoutSecondsRemaining}s REMAINING",
                        color = VaultErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                } else if (!errorMessage.isNullOrEmpty()) {
                    Text(
                        text = errorMessage,
                        color = VaultErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Numeric Keypad Grid (Scrambled 0-9, Fingerprint, Backspace)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Row 1
                KeypadRow {
                    for (i in 0..2) {
                        NumberKey(scrambledDigits[i], enabled = !isLockedOut, onDigitClick = { digit ->
                            if (!isLockedOut && enteredPin.length < 4) {
                                val newPin = enteredPin + digit
                                enteredPin = newPin
                                if (newPin.length == 4) {
                                    onPinSubmit(newPin)
                                }
                            }
                        })
                    }
                }

                // Row 2
                KeypadRow {
                    for (i in 3..5) {
                        NumberKey(scrambledDigits[i], enabled = !isLockedOut, onDigitClick = { digit ->
                            if (!isLockedOut && enteredPin.length < 4) {
                                val newPin = enteredPin + digit
                                enteredPin = newPin
                                if (newPin.length == 4) {
                                    onPinSubmit(newPin)
                                }
                            }
                        })
                    }
                }

                // Row 3
                KeypadRow {
                    for (i in 6..8) {
                        NumberKey(scrambledDigits[i], enabled = !isLockedOut, onDigitClick = { digit ->
                            if (!isLockedOut && enteredPin.length < 4) {
                                val newPin = enteredPin + digit
                                enteredPin = newPin
                                if (newPin.length == 4) {
                                    onPinSubmit(newPin)
                                }
                            }
                        })
                    }
                }

                // Row 4: Fingerprint, Remaining Digit, Backspace
                KeypadRow {
                    // Fingerprint Key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF091B2A), Color(0xFF11283F))))
                            .border(1.5.dp, CyberNeonGradient, RoundedCornerShape(20.dp))
                            .clickable(enabled = !isLockedOut) { onAuthenticateClick() }
                            .testTag("biometric_keypad_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Biometric Unlock",
                            tint = if (isLockedOut) Color.Gray else VaultPrimaryCyan,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Remaining Scrambled Key (Index 9)
                    NumberKey(scrambledDigits[9], enabled = !isLockedOut, onDigitClick = { digit ->
                        if (!isLockedOut && enteredPin.length < 4) {
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            if (newPin.length == 4) {
                                onPinSubmit(newPin)
                            }
                        }
                    })

                    // Backspace Key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF2E0915), Color(0xFF450D20))))
                            .border(1.5.dp, Brush.horizontalGradient(listOf(VaultErrorRed, VaultNeonPink)), RoundedCornerShape(20.dp))
                            .clickable(enabled = !isLockedOut) {
                                if (enteredPin.isNotEmpty()) {
                                    enteredPin = enteredPin.dropLast(1)
                                }
                            }
                            .testTag("backspace_keypad_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Delete",
                            tint = if (isLockedOut) Color.Gray else VaultErrorRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
}

@Composable
private fun KeypadRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        content()
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NumberKey(
    digit: String,
    enabled: Boolean = true,
    onDigitClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(68.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (enabled) Brush.verticalGradient(listOf(Color(0xFF0F1B2B), Color(0xFF0B1420)))
                else Brush.verticalGradient(listOf(Color(0xFF070C12), Color(0xFF070C12)))
            )
            .border(
                1.dp,
                if (enabled) Brush.horizontalGradient(listOf(Color(0xFF1E3852), Color(0xFF284868)))
                else Brush.horizontalGradient(listOf(Color(0xFF111C28), Color(0xFF111C28))),
                RoundedCornerShape(20.dp)
            )
            .clickable(enabled = enabled) { onDigitClick(digit) }
            .testTag("keypad_$digit"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else Color(0xFF4A5568)
        )
    }
}

@Composable
private fun FuturisticCyberLockoutScreen(
    lockoutSecondsRemaining: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cyber_lockout_anim")

    // Rotating Outer Tech Arc
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rot"
    )

    // Counter-Rotating Inner Tech Arc
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rot"
    )

    // Warning Pulse
    val warningPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "warning_pulse"
    )

    // Laser Scan Beam Line (0f to 1f)
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_beam"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF03070E),
                        Color(0xFF070E1A),
                        Color(0xFF03060B)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("cooldown_lockout_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Futuristic Cyber Background Grid with Ambient Radial Pulse
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 40.dp.toPx()
            val cols = (size.width / gridSpacing).toInt() + 1
            val rows = (size.height / gridSpacing).toInt() + 1

            // Cyber Grid Lines
            for (i in 0..cols) {
                val x = i * gridSpacing
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.04f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
            }
            for (j in 0..rows) {
                val y = j * gridSpacing
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.04f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            // Radial Warning Ambient Glow in Center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF1744).copy(alpha = 0.16f * warningPulse),
                        Color(0xFF00E5FF).copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2f, size.height * 0.35f),
                    radius = size.width * 0.75f
                )
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP STATUS TELEMETRY BANNER
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1B0C14).copy(alpha = 0.8f))
                        .border(1.dp, Color(0xFFFF1744).copy(alpha = 0.5f * warningPulse), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF1744))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SECURITY THREAT LEVEL 4 • ENCLAVE FROZEN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252),
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            // CENTER REACTOR CORE & APP TITLE
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // HOLOGRAPHIC ROTATING REACTOR LOCK CORE
                Box(
                    modifier = Modifier.size(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Canvas Rotating Tech Rings & Orbital Dashes
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radiusOuter = size.minDimension / 2f - 4.dp.toPx()
                        val radiusInner = size.minDimension / 2f - 18.dp.toPx()

                        // Outer Orbit Arc 1 (Cyan)
                        drawArc(
                            color = Color(0xFF00E5FF).copy(alpha = 0.85f),
                            startAngle = outerRotation,
                            sweepAngle = 100f,
                            useCenter = false,
                            topLeft = Offset(center.x - radiusOuter, center.y - radiusOuter),
                            size = Size(radiusOuter * 2f, radiusOuter * 2f),
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 15f), 0f)
                            )
                        )

                        // Outer Orbit Arc 2 (Crimson Warning)
                        drawArc(
                            color = Color(0xFFFF1744).copy(alpha = 0.9f),
                            startAngle = outerRotation + 180f,
                            sweepAngle = 100f,
                            useCenter = false,
                            topLeft = Offset(center.x - radiusOuter, center.y - radiusOuter),
                            size = Size(radiusOuter * 2f, radiusOuter * 2f),
                            style = Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 15f), 0f)
                            )
                        )

                        // Inner Counter-Rotating Tech Arc
                        drawArc(
                            color = Color(0xFF00E5FF).copy(alpha = 0.6f),
                            startAngle = innerRotation,
                            sweepAngle = 220f,
                            useCenter = false,
                            topLeft = Offset(center.x - radiusInner, center.y - radiusInner),
                            size = Size(radiusInner * 2f, radiusInner * 2f),
                            style = Stroke(
                                width = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )

                        // 4 Corner HUD Crosshairs
                        val crosshairLen = 8.dp.toPx()
                        val pad = 4.dp.toPx()
                        // Top-Left
                        drawLine(Color(0xFF00E5FF), Offset(pad, pad), Offset(pad + crosshairLen, pad), 2f)
                        drawLine(Color(0xFF00E5FF), Offset(pad, pad), Offset(pad, pad + crosshairLen), 2f)
                        // Top-Right
                        drawLine(Color(0xFF00E5FF), Offset(size.width - pad, pad), Offset(size.width - pad - crosshairLen, pad), 2f)
                        drawLine(Color(0xFF00E5FF), Offset(size.width - pad, pad), Offset(size.width - pad, pad + crosshairLen), 2f)
                        // Bottom-Left
                        drawLine(Color(0xFF00E5FF), Offset(pad, size.height - pad), Offset(pad + crosshairLen, size.height - pad), 2f)
                        drawLine(Color(0xFF00E5FF), Offset(pad, size.height - pad), Offset(pad, size.height - pad - crosshairLen), 2f)
                        // Bottom-Right
                        drawLine(Color(0xFF00E5FF), Offset(size.width - pad, size.height - pad), Offset(size.width - pad - crosshairLen, size.height - pad), 2f)
                        drawLine(Color(0xFF00E5FF), Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - crosshairLen), 2f)
                    }

                    // Center Glowing Core
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0xFF1E0A14),
                                        Color(0xFF0B1422)
                                    )
                                )
                            )
                            .border(
                                2.dp,
                                Brush.sweepGradient(
                                    listOf(
                                        Color(0xFFFF1744),
                                        Color(0xFF00E5FF),
                                        Color(0xFFFF1744)
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lockout",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Title
                Text(
                    text = "QUANTUM VAULT",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle
                Text(
                    text = "MILITARY-GRADE ENCRYPTED ENCLAVE",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF),
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.6.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // SCI-FI HOLOGRAPHIC COUNTDOWN CARD WITH LASER SCANNER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF140810).copy(alpha = 0.92f),
                                    Color(0xFF0A121D).copy(alpha = 0.95f)
                                )
                            )
                        )
                        .border(
                            1.5.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFFF1744).copy(alpha = 0.9f),
                                    Color(0xFF00E5FF).copy(alpha = 0.6f),
                                    Color(0xFFFF1744).copy(alpha = 0.9f)
                                )
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(20.dp)
                ) {
                    // Animated Laser Scanning Line over the HUD Card
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val currentY = size.height * scanLineY
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xFFFF5252).copy(alpha = 0.7f),
                                    Color(0xFF00E5FF).copy(alpha = 0.9f),
                                    Color(0xFFFF5252).copy(alpha = 0.7f),
                                    Color.Transparent
                                )
                            ),
                            start = Offset(0f, currentY),
                            end = Offset(size.width, currentY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Card Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFFFF1744),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TEMPORAL LOCKOUT ACTIVE",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF5252),
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.4.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // HUGE CYBER DIGITAL COUNTDOWN NUMERALS
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF060B12))
                                .border(1.dp, Color(0xFF1E354D), RoundedCornerShape(12.dp))
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "00:${lockoutSecondsRemaining.toString().padStart(2, '0')}",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SEC",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Live Dynamic Segmented Cooldown Progress Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val totalSegments = 12
                            val activeSegments = ((lockoutSecondsRemaining / 30f) * totalSegments).toInt().coerceIn(1, totalSegments)

                            for (i in 0 until totalSegments) {
                                val isActive = i < activeSegments
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            if (isActive) Color(0xFFFF1744)
                                            else Color(0xFF1A2634)
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Telemetry Log Text
                        Text(
                            text = "4th unauthorized attempt detected. Keystore bus throttled to prevent side-channel brute force.",
                            fontSize = 12.sp,
                            color = Color(0xFFB0BEC5),
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // BOTTOM HARDWARE ENCLAVE STATUS MATRIX
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CyberSpecBadge(
                    label = "AES-256-GCM",
                    activeColor = Color(0xFF00E676),
                    modifier = Modifier.weight(1f)
                )
                CyberSpecBadge(
                    label = "ARGON2ID 64M",
                    activeColor = Color(0xFF00E5FF),
                    modifier = Modifier.weight(1f)
                )
                CyberSpecBadge(
                    label = "HARDWARE TEE",
                    activeColor = Color(0xFFFFB300),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CyberSpecBadge(
    label: String,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A111A))
            .border(1.dp, Color(0xFF162536), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(activeColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFECEFF1),
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}
