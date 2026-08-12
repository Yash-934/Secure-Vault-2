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
import com.example.ui.theme.VaultDarkBackground
import com.example.ui.theme.VaultErrorRed
import com.example.ui.theme.VaultPrimaryCyan

@Composable
fun LockScreen(
    onAuthenticateClick: () -> Unit,
    onPinSubmit: (pin: String) -> Unit,
    errorMessage: String? = null
) {
    var enteredPin by remember { mutableStateOf("") }

    // Auto-clear PIN input when error occurs
    LaunchedEffect(errorMessage) {
        if (!errorMessage.isNullOrEmpty()) {
            enteredPin = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF03070C)) // Deep pitch black background
    ) {
        // Futuristic Cyber Background Grid
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 44.dp.toPx()
            val gridColor = Color(0xFF00D2EF).copy(alpha = 0.04f)
            
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
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // 1. Top System Badge (Pill)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF041C28))
                    .border(1.dp, Color(0xFF00B2D6).copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00FF66)) // Glowing green dot
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VAULT SYSTEM: ENCRYPTED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00D2EF),
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 2. Glowing Shield Ring Logo
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer Cyan Circle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color(0xFF00D2EF), CircleShape)
                )
                // Inner Cyan Circle
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color(0xFF00D2EF).copy(alpha = 0.8f), CircleShape)
                )
                // Center Shield Icon
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Vault Shield",
                    tint = Color(0xFF00D2EF),
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Title & Subtitle
            Text(
                text = "SECURE VAULT",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 2.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "AUTHENTICATION REQUIRED",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6C7A8E),
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 4. PIN Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) Color(0xFF00D2EF) else Color.Transparent
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isFilled) Color(0xFF00D2EF) else Color(0xFF1B2A3A),
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
                            .background(Color(0xFF061824))
                            .border(1.5.dp, Color(0xFF00D2EF), RoundedCornerShape(20.dp))
                            .clickable { onAuthenticateClick() }
                            .testTag("biometric_keypad_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Biometric Unlock",
                            tint = Color(0xFF00D2EF),
                            modifier = Modifier.size(30.dp)
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
                            .background(Color(0xFF420816))
                            .border(1.dp, Color(0xFF6E0D24), RoundedCornerShape(20.dp))
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
                            tint = Color(0xFFFF2A55),
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
            .background(Color(0xFF08121C))
            .border(1.dp, Color(0xFF132334), RoundedCornerShape(20.dp))
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
