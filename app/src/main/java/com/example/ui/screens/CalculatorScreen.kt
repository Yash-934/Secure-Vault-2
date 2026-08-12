package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CalculatorScreen(
    onPinSubmit: (String) -> Unit
) {
    var display by remember { mutableStateOf("") }
    
    val buttons = listOf(
        listOf("7", "8", "9", "/"),
        listOf("4", "5", "6", "*"),
        listOf("1", "2", "3", "-"),
        listOf("C", "0", "=", "+")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF03070C))
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = display.ifEmpty { "0" },
            fontSize = 48.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            textAlign = TextAlign.End
        )
        
        for (row in buttons) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (btn in row) {
                    val isAction = btn in listOf("/", "*", "-", "+", "=")
                    val bgColor = if (isAction) Color(0xFF00D2EF) else Color(0xFF112538)
                    val textColor = if (isAction) Color(0xFF03070C) else Color.White
                    
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .clickable {
                                when (btn) {
                                    "C" -> display = ""
                                    "=" -> {
                                        onPinSubmit(display)
                                        try {
                                            // Simple evaluation for disguise
                                            val parts = display.split(Regex("(?<=[-+*/])|(?=[-+*/])"))
                                            if (parts.size == 3) {
                                                val left = parts[0].toDouble()
                                                val op = parts[1]
                                                val right = parts[2].toDouble()
                                                val result = when(op) {
                                                    "+" -> left + right
                                                    "-" -> left - right
                                                    "*" -> left * right
                                                    "/" -> if (right != 0.0) left / right else Double.NaN
                                                    else -> left
                                                }
                                                display = if (result % 1.0 == 0.0) result.toLong().toString() else result.toString()
                                            } else {
                                                display = ""
                                            }
                                        } catch (e: Exception) {
                                            display = ""
                                        }
                                    }
                                    else -> {
                                        if (display.length < 15) display += btn
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = btn,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
