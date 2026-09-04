package com.quantumvault.wkqpx.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

@Composable
fun CalculatorScreen(
    onPinSubmit: (String) -> Unit
) {
    var displayValue by remember { mutableStateOf("0") }
    var expressionHistory by remember { mutableStateOf("") }
    var rawInputSequence by remember { mutableStateOf("") }
    var pendingOperator by remember { mutableStateOf<String?>(null) }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var isNewNumber by remember { mutableStateOf(true) }

    val haptic = LocalHapticFeedback.current

    // Number format for clean display
    val df = remember { DecimalFormat("#,###.########") }

    fun clearAll() {
        displayValue = "0"
        expressionHistory = ""
        rawInputSequence = ""
        pendingOperator = null
        operand1 = null
        isNewNumber = true
    }

    fun onDigitPress(digit: String) {
        rawInputSequence += digit
        if (isNewNumber || displayValue == "0") {
            displayValue = digit
            isNewNumber = false
        } else {
            if (displayValue.length < 14) {
                displayValue += digit
            }
        }
    }

    fun onDecimalPress() {
        rawInputSequence += "."
        if (isNewNumber) {
            displayValue = "0."
            isNewNumber = false
        } else if (!displayValue.contains(".")) {
            displayValue += "."
        }
    }

    fun onOperatorPress(op: String) {
        val current = displayValue.toDoubleOrNull() ?: 0.0
        if (operand1 == null) {
            operand1 = current
        } else if (pendingOperator != null && !isNewNumber) {
            val result = calculateResult(operand1!!, current, pendingOperator!!)
            operand1 = result
            displayValue = formatNumber(result, df)
        }
        pendingOperator = op
        expressionHistory = "${formatNumber(operand1!!, df)} $op"
        isNewNumber = true
    }

    fun onEqualsPress() {
        // First, submit the raw digits entered or current display as PIN attempt for covert vault trigger
        if (rawInputSequence.isNotBlank()) {
            onPinSubmit(rawInputSequence)
        }
        onPinSubmit(displayValue)

        // Then execute genuine calculator calculation
        val current = displayValue.toDoubleOrNull()
        if (operand1 != null && pendingOperator != null && current != null) {
            val result = calculateResult(operand1!!, current, pendingOperator!!)
            expressionHistory = "${formatNumber(operand1!!, df)} $pendingOperator ${formatNumber(current, df)} ="
            displayValue = formatNumber(result, df)
            operand1 = null
            pendingOperator = null
            isNewNumber = true
        }
    }

    fun onToggleSignPress() {
        val current = displayValue.toDoubleOrNull() ?: return
        val toggled = current * -1
        displayValue = formatNumber(toggled, df)
    }

    fun onPercentagePress() {
        val current = displayValue.toDoubleOrNull() ?: return
        val percent = current / 100.0
        displayValue = formatNumber(percent, df)
        isNewNumber = true
    }

    fun onBackspacePress() {
        if (!isNewNumber && displayValue.isNotEmpty() && displayValue != "0") {
            displayValue = displayValue.dropLast(1)
            if (displayValue.isEmpty() || displayValue == "-") {
                displayValue = "0"
                isNewNumber = true
            }
        }
        if (rawInputSequence.isNotEmpty()) {
            rawInputSequence = rawInputSequence.dropLast(1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14171D))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("modern_calculator_screen"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP APP BAR (As in Screenshot)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Amber Calculator Icon Badge
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFB300)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+−\n×=",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF332000),
                        lineHeight = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Calculator",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    letterSpacing = 0.3.sp
                )
            }

            // Unit/Converter swap icon (Right Header)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { /* decorative unit swap action */ },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = "Unit Conversion",
                    tint = Color(0xFFB0BEC5),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // SLATE MATTE DISPLAY BOX (As in Screenshot)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF282E38))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                // Expression History
                Text(
                    text = expressionHistory,
                    fontSize = 18.sp,
                    color = Color(0xFF90A4AE),
                    maxLines = 2,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )

                // Current Active Value
                Text(
                    text = displayValue,
                    fontSize = if (displayValue.length > 9) 36.sp else 48.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // KEYPAD MATRIX (5 Rows x 4 Columns)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: AC, +/-, %, ÷
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalculatorKey(
                    label = if (displayValue == "0" && expressionHistory.isEmpty()) "AC" else "C",
                    bgColor = Color(0xFF4A5562),
                    textColor = Color(0xFFFF7A66),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        clearAll()
                    }
                )
                CalculatorKey(
                    label = "+/-",
                    bgColor = Color(0xFF4A5562),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onToggleSignPress()
                    }
                )
                CalculatorKey(
                    label = "%",
                    bgColor = Color(0xFF4A5562),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onPercentagePress()
                    }
                )
                CalculatorKey(
                    label = "÷",
                    bgColor = Color(0xFFFF5238),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onOperatorPress("÷")
                    }
                )
            }

            // Row 2: 7, 8, 9, ×
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalculatorKey(
                    label = "7",
                    bgColor = Color(0xFF2C3540),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onDigitPress("7")
                    }
                )
                CalculatorKey(
                    label = "8",
                    bgColor = Color(0xFF2C3540),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onDigitPress("8")
                    }
                )
                CalculatorKey(
                    label = "9",
                    bgColor = Color(0xFF2C3540),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onDigitPress("9")
                    }
                )
                CalculatorKey(
                    label = "×",
                    bgColor = Color(0xFFFF5238),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onOperatorPress("×")
                    }
                )
            }

            // Row 3: 4, 5, 6, -
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalculatorKey(
                    label = "4",
                    bgColor = Color(0xFF2C3540),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onDigitPress("4")
                    }
                )
                CalculatorKey(
                    label = "5",
                    bgColor = Color(0xFF2C3540),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onDigitPress("5")
                    }
                )
                CalculatorKey(
                    label = "6",
                    bgColor = Color(0xFF2C3540),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onDigitPress("6")
                    }
                )
                CalculatorKey(
                    label = "−",
                    bgColor = Color(0xFFFF5238),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onOperatorPress("−")
                    }
                )
            }

            // Row 4: 1, 2, 3, +
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalculatorKey(
                    label = "1",
                    bgColor = Color(0xFF2C3540),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onDigitPress("1")
                    }
                )
                CalculatorKey(
                    label = "2",
                    bgColor = Color(0xFF2C3540),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onDigitPress("2")
                    }
                )
                CalculatorKey(
                    label = "3",
                    bgColor = Color(0xFF2C3540),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onDigitPress("3")
                    }
                )
                CalculatorKey(
                    label = "+",
                    bgColor = Color(0xFFFF5238),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onOperatorPress("+")
                    }
                )
            }

            // Row 5: 0, ., Backspace, =
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalculatorKey(
                    label = "0",
                    bgColor = Color(0xFF2C3540),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onDigitPress("0")
                    }
                )
                CalculatorKey(
                    label = ".",
                    bgColor = Color(0xFF2C3540),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onDecimalPress()
                    }
                )
                CalculatorKey(
                    label = null,
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    bgColor = Color(0xFF4A5562),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onBackspacePress()
                    }
                )
                CalculatorKey(
                    label = "=",
                    bgColor = Color(0xFF00E676),
                    textColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onEqualsPress()
                    }
                )
            }
        }
    }
}

@Composable
private fun CalculatorKey(
    label: String?,
    icon: (@Composable () -> Unit)? = null,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f, label = "btn_scale")

    Box(
        modifier = modifier
            .aspectRatio(1.22f)
            .scale(scale)
            .clip(RoundedCornerShape(32.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (label != null) {
            Text(
                text = label,
                fontSize = if (label.length > 2) 20.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )
        } else if (icon != null) {
            icon()
        }
    }
}

private fun calculateResult(a: Double, b: Double, op: String): Double {
    return when (op) {
        "+" -> a + b
        "−", "-" -> a - b
        "×", "*" -> a * b
        "÷", "/" -> if (b != 0.0) a / b else Double.NaN
        else -> b
    }
}

private fun formatNumber(value: Double, df: DecimalFormat): String {
    if (value.isNaN() || value.isInfinite()) return "Error"
    return if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e12) {
        value.toLong().toString()
    } else {
        df.format(value)
    }
}
