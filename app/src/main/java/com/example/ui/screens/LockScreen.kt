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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
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
    errorTrigger: Int = 0
) {
    var enteredPin by remember { mutableStateOf("") }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Auto-clear PIN input when error occurs
    LaunchedEffect(errorTrigger) {
        if (errorTrigger > 0) {
            enteredPin = ""
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        }
    }

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

            // Error Text Display
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (!errorMessage.isNullOrEmpty()) {
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

            // 5. Numeric Keypad Grid (1-9, Fingerprint, 0, Backspace)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Row 1: 1, 2, 3
                KeypadRow {
                    NumberKey("1", onDigitClick = { digit ->
                        if (enteredPin.length < 4) {
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            if (newPin.length == 4) {
                                onPinSubmit(newPin)
                            }
                        }
                    })
                    NumberKey("2", onDigitClick = { digit ->
                        if (enteredPin.length < 4) {
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            if (newPin.length == 4) {
                                onPinSubmit(newPin)
                            }
                        }
                    })
                    NumberKey("3", onDigitClick = { digit ->
                        if (enteredPin.length < 4) {
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            if (newPin.length == 4) {
                                onPinSubmit(newPin)
                            }
                        }
                    })
                }

                // Row 2: 4, 5, 6
                KeypadRow {
                    NumberKey("4", onDigitClick = { digit ->
                        if (enteredPin.length < 4) {
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            if (newPin.length == 4) {
                                onPinSubmit(newPin)
                            }
                        }
                    })
                    NumberKey("5", onDigitClick = { digit ->
                        if (enteredPin.length < 4) {
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            if (newPin.length == 4) {
                                onPinSubmit(newPin)
                            }
                        }
                    })
                    NumberKey("6", onDigitClick = { digit ->
                        if (enteredPin.length < 4) {
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            if (newPin.length == 4) {
                                onPinSubmit(newPin)
                            }
                        }
                    })
                }

                // Row 3: 7, 8, 9
                KeypadRow {
                    NumberKey("7", onDigitClick = { digit ->
                        if (enteredPin.length < 4) {
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            if (newPin.length == 4) {
                                onPinSubmit(newPin)
                            }
                        }
                    })
                    NumberKey("8", onDigitClick = { digit ->
                        if (enteredPin.length < 4) {
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            if (newPin.length == 4) {
                                onPinSubmit(newPin)
                            }
                        }
                    })
                    NumberKey("9", onDigitClick = { digit ->
                        if (enteredPin.length < 4) {
                            val newPin = enteredPin + digit
                            enteredPin = newPin
                            if (newPin.length == 4) {
                                onPinSubmit(newPin)
                            }
                        }
                    })
                }

                // Row 4: Fingerprint, 0, Backspace
                KeypadRow {
                    // Fingerprint Key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF091B2A), Color(0xFF11283F))))
                            .border(1.5.dp, CyberNeonGradient, RoundedCornerShape(20.dp))
                            .clickable { onAuthenticateClick() }
                            .testTag("biometric_keypad_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Biometric Unlock",
                            tint = VaultPrimaryCyan,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Zero Key
                    NumberKey("0", onDigitClick = { digit ->
                        if (enteredPin.length < 4) {
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
                            .clickable {
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
                            tint = VaultErrorRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
    onDigitClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(68.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF0F1B2B), Color(0xFF0B1420))))
            .border(1.dp, Brush.horizontalGradient(listOf(Color(0xFF1E3852), Color(0xFF284868))), RoundedCornerShape(20.dp))
            .clickable { onDigitClick(digit) }
            .testTag("keypad_$digit"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
