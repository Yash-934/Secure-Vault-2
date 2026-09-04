package com.quantumvault.wkqpx.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkNavyBg = Color(0xFF07111B)
private val CardBg = Color(0xFF0E1A29)
private val PanicRed = Color(0xFFFF2A55)
private val WarningOrange = Color(0xFFFF9100)
private val SubtitleText = Color(0xFF94A3B8)
private val CardBorder = Color(0xFF2A1520)

/**
 * High-Security Nuclear Self-Destruct Confirmation Dialog.
 * Always presents a strict warning and mandates Master PIN validation before authorizing
 * any zero-overwrite shredding operations.
 */
@Composable
fun NuclearSelfDestructDialog(
    onDismiss: () -> Unit,
    onConfirmSelfDestruct: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var enteredPin by remember { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    fun attemptVerification() {
        if (enteredPin.isBlank()) {
            errorMessage = "Please enter your Master PIN to authorize."
            return
        }
        isVerifying = true
        coroutineScope.launch {
            val settingsDataStore = com.quantumvault.wkqpx.data.local.SettingsDataStore(context)
            val isValid = settingsDataStore.verifyMasterPin(enteredPin)
            isVerifying = false
            if (!isValid) {
                errorMessage = "Invalid Master PIN. Nuclear wipe aborted."
                return@launch
            }
            errorMessage = null
            onConfirmSelfDestruct()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavyBg,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(PanicRed.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Nuclear Warning",
                        tint = PanicRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "NUCLEAR SELF-DESTRUCT",
                        color = PanicRed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Irreversible Data Destruction",
                        color = SubtitleText,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PanicRed.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PanicRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "EXTREME WARNING:",
                            color = PanicRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This action will zero-overwrite and shred ALL encrypted files, databases, Keystore hardware keys, and logs. This process CANNOT be undone.",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "To authorize this operation, enter your active Master PIN below:",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = {
                        enteredPin = it
                        if (errorMessage != null) errorMessage = null
                    },
                    label = { Text("Enter Master PIN", color = SubtitleText, fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { attemptVerification() }
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = PanicRed,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { isPinVisible = !isPinVisible }) {
                            Icon(
                                imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPinVisible) "Hide PIN" else "Show PIN",
                                tint = SubtitleText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    isError = errorMessage != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PanicRed,
                        unfocusedBorderColor = Color(0xFF334155),
                        cursorColor = PanicRed,
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        errorBorderColor = PanicRed,
                        errorContainerColor = CardBg
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("self_destruct_pin_input")
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = PanicRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("self_destruct_error_message")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { attemptVerification() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PanicRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("authorize_self_destruct_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AUTHORIZE WIPE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Cancel",
                    color = SubtitleText,
                    fontSize = 12.sp
                )
            }
        }
    )
}
