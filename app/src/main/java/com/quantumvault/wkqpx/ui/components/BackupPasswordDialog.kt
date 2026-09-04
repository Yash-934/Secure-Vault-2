package com.quantumvault.wkqpx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantumvault.wkqpx.security.PasswordCryptoHelper

private val DarkNavyBg = Color(0xFF07111B)
private val BrightCyan = Color(0xFF00D2EF)
private val SubtitleText = Color(0xFF6C7A8E)
private val CardBorder = Color(0xFF132B44)
private val GreenAccent = Color(0xFF00FF87)
private val WarningYellow = Color(0xFFFFB703)

@Composable
fun BackupPasswordDialog(
    title: String,
    subtitle: String,
    isExportMode: Boolean = false,
    showRestoreModeOptions: Boolean = false,
    isInitialSetupMode: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (password: String, isDeviceLocked: Boolean, isReplaceMode: Boolean) -> Unit = { _, _, _ -> },
    onConfirmWithSetupPin: ((password: String, isReplaceMode: Boolean, newMasterPin: String) -> Unit)? = null
) {
    var password by remember { mutableStateOf("") }
    var setupNewPin by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isDeviceLocked by remember { mutableStateOf(false) }
    var isReplaceMode by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val (strengthScore, strengthLabel) = remember(password) {
        PasswordCryptoHelper.evaluateStrength(password)
    }

    val strengthColor = when {
        strengthScore >= 80 -> GreenAccent
        strengthScore >= 60 -> BrightCyan
        strengthScore >= 40 -> WarningYellow
        else -> Color(0xFFFF4D6D)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavyBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = BrightCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = SubtitleText,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Master Password (Argon2id)", color = SubtitleText, fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                tint = SubtitleText
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrightCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_password_input")
                )

                // Argon2id Password Strength Meter
                if (password.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STRENGTH: $strengthLabel",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = strengthColor,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "$strengthScore / 100",
                            fontSize = 10.sp,
                            color = SubtitleText,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { strengthScore / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = strengthColor,
                        trackColor = Color(0xFF112538),
                    )
                }

                if (isExportMode) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0B1927))
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                            .clickable { isDeviceLocked = !isDeviceLocked }
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isDeviceLocked,
                                onCheckedChange = { isDeviceLocked = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = BrightCyan,
                                    uncheckedColor = SubtitleText,
                                    checkmarkColor = Color.Black
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Device Hardware Key Binding",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDeviceLocked) BrightCyan else Color.White
                                )
                                Text(
                                    text = "Wraps key with Android Keystore TEE. Can only be decrypted on this device.",
                                    fontSize = 10.sp,
                                    color = SubtitleText,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                } else if (showRestoreModeOptions) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Restore Mode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrightCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Merge Option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isReplaceMode = false }
                            .padding(vertical = 6.dp)
                    ) {
                        RadioButton(
                            selected = !isReplaceMode,
                            onClick = { isReplaceMode = false },
                            colors = RadioButtonDefaults.colors(selectedColor = BrightCyan)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(text = "Merge with current", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            Text(text = "Adds backup items to your existing vault.", fontSize = 11.sp, color = SubtitleText)
                        }
                    }
                    
                    // Replace Option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isReplaceMode = true }
                            .padding(vertical = 6.dp)
                    ) {
                        RadioButton(
                            selected = isReplaceMode,
                            onClick = { isReplaceMode = true },
                            colors = RadioButtonDefaults.colors(selectedColor = BrightCyan)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(text = "Replace current", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            Text(text = "Wipes your existing vault before restoring.", fontSize = 11.sp, color = Color(0xFFFF4D6D))
                        }
                    }
                }

                if (isInitialSetupMode) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "NEW MASTER PIN FOR VAULT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrightCyan,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = setupNewPin,
                        onValueChange = { if (it.length <= 8 && it.all { char -> char.isDigit() }) setupNewPin = it },
                        label = { Text("Enter New 4-8 Digit PIN", color = SubtitleText, fontSize = 12.sp) },
                        placeholder = {
                            Text(
                                if (password.length in 4..8 && password.all { it.isDigit() })
                                    "Uses backup PIN ($password) if blank"
                                else
                                    "Choose 4 to 8 digits",
                                color = SubtitleText.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrightCyan,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_setup_pin_input")
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        fontSize = 11.sp,
                        color = Color(0xFFFF2A55)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.length < 4 && password.isNotEmpty()) {
                        errorMessage = "Password must be at least 4 characters."
                    } else if (isInitialSetupMode) {
                        val finalPin = if (setupNewPin.length in 4..8) {
                            setupNewPin
                        } else if (password.length in 4..8 && password.all { it.isDigit() }) {
                            password
                        } else {
                            null
                        }

                        if (finalPin == null) {
                            errorMessage = "Please enter a 4-8 digit new Master PIN."
                        } else {
                            if (onConfirmWithSetupPin != null) {
                                onConfirmWithSetupPin(password, true, finalPin)
                            } else {
                                onConfirm(password, false, true)
                            }
                        }
                    } else {
                        onConfirm(password, isDeviceLocked, isReplaceMode)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrightCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("backup_password_confirm_button")
            ) {
                Text(text = "CONFIRM", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("backup_password_cancel_button")
            ) {
                Text(text = "CANCEL", color = SubtitleText, fontFamily = FontFamily.Monospace)
            }
        }
    )
}
