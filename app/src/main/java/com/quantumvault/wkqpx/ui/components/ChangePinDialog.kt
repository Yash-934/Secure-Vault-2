package com.quantumvault.wkqpx.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantumvault.wkqpx.ui.theme.VaultBorder
import com.quantumvault.wkqpx.ui.theme.VaultErrorRed
import com.quantumvault.wkqpx.ui.theme.VaultPrimaryCyan
import com.quantumvault.wkqpx.ui.theme.VaultSurface
import com.quantumvault.wkqpx.ui.theme.VaultTextSecondary

@Composable
fun ChangePinDialog(
    title: String,
    subtitle: String,
    requireCurrentPin: Boolean = false,
    onDismiss: () -> Unit,
    onSavePin: (newPin: String) -> Unit,
    onSavePinWithOld: ((oldPin: String, newPin: String) -> Unit)? = null
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VaultSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Password,
                    contentDescription = null,
                    tint = VaultPrimaryCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = VaultTextSecondary
                )

                if (requireCurrentPin) {
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = {
                            if (it.length <= 8) oldPin = it
                            errorMsg = null
                        },
                        label = { Text("Current PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VaultPrimaryCyan,
                            unfocusedBorderColor = VaultBorder,
                            focusedLabelColor = VaultPrimaryCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = newPin,
                    onValueChange = {
                        if (it.length <= 8) newPin = it
                        errorMsg = null
                    },
                    label = { Text("New 4-8 Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultPrimaryCyan,
                        unfocusedBorderColor = VaultBorder,
                        focusedLabelColor = VaultPrimaryCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        if (it.length <= 8) confirmPin = it
                        errorMsg = null
                    },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VaultPrimaryCyan,
                        unfocusedBorderColor = VaultBorder,
                        focusedLabelColor = VaultPrimaryCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = VaultErrorRed,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (requireCurrentPin && oldPin.length < 4) {
                        errorMsg = "Current PIN must be at least 4 digits."
                    } else if (newPin.length < 4) {
                        errorMsg = "PIN must be at least 4 digits."
                    } else if (newPin != confirmPin) {
                        errorMsg = "PINs do not match."
                    } else {
                        if (requireCurrentPin && onSavePinWithOld != null) {
                            onSavePinWithOld(oldPin, newPin)
                        } else {
                            onSavePin(newPin)
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultPrimaryCyan,
                    contentColor = Color.Black
                )
            ) {
                Text("Save PIN", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel", color = VaultTextSecondary)
            }
        }
    )
}
