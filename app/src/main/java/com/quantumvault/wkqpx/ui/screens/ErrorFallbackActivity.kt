package com.quantumvault.wkqpx.ui.screens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quantumvault.wkqpx.BuildConfig
import com.quantumvault.wkqpx.MainActivity
import com.quantumvault.wkqpx.ui.theme.MyApplicationTheme
import com.quantumvault.wkqpx.util.VaultLogger

/**
 * Models defining the strict separation between Debug Diagnostics and Release Emergency Recovery.
 */
sealed interface FallbackDisplayModel {
    val recoveryId: String
    val timestamp: Long

    data class Release(
        override val recoveryId: String,
        val genericGuidance: String = "A critical system event was handled safely. Encrypted data integrity has been preserved.",
        val actionInstructions: String = "Please restart the application. If this condition persists after restart, restore your vault from a verified backup archive.",
        override val timestamp: Long = System.currentTimeMillis()
    ) : FallbackDisplayModel

    data class Debug(
        override val recoveryId: String,
        val rawErrorMessage: String,
        val stackTrace: String,
        val persistentLogs: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : FallbackDisplayModel
}

/**
 * Diagnostics & Emergency Recovery Activity.
 * Displayed when an unhandled startup or runtime exception occurs, preventing silent crashes/black screens.
 * In release mode, strictly uses ReleaseRecoveryModel to avoid leaking internal cryptographic or stack traces.
 */
class ErrorFallbackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val rawErrorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: "Unknown application error"
        val rawStackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "No stack trace available"

        val recoveryId = "QV-RECOVERY-${Math.abs(rawErrorMessage.hashCode()) % 900 + 100}"
        val isDebug = BuildConfig.DEBUG

        val model: FallbackDisplayModel = if (isDebug) {
            FallbackDisplayModel.Debug(
                recoveryId = recoveryId,
                rawErrorMessage = rawErrorMessage,
                stackTrace = rawStackTrace,
                persistentLogs = VaultLogger.readLogs(this)
            )
        } else {
            FallbackDisplayModel.Release(
                recoveryId = recoveryId
            )
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0F172A)
                ) { innerPadding ->
                    ErrorFallbackContent(
                        model = model,
                        onRestart = {
                            val restartIntent = Intent(this, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                            startActivity(restartIntent)
                            finish()
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
    }
}

@Composable
fun ErrorFallbackContent(
    model: FallbackDisplayModel,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = "Startup Error",
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Quantum Vault Recovery",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when (model) {
                is FallbackDisplayModel.Release -> model.genericGuidance
                is FallbackDisplayModel.Debug -> "A critical system event was handled safely. Encrypted data integrity has been preserved."
            },
            color = Color(0xFF94A3B8),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECOVERY STATUS",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = model.recoveryId,
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (model) {
                        is FallbackDisplayModel.Release -> model.actionInstructions
                        is FallbackDisplayModel.Debug -> model.rawErrorMessage
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (model is FallbackDisplayModel.Debug) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF090D16)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DEBUG STACK TRACE",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = model.stackTrace,
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PERSISTENT VAULT EVENTS LOG",
                        color = Color(0xFF10B981),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = model.persistentLogs,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val clipContent = when (model) {
                        is FallbackDisplayModel.Debug ->
                            "[${model.recoveryId}] ${model.rawErrorMessage}\n\n${model.stackTrace}\n\n--- VAULT LOGS ---\n${model.persistentLogs}"
                        is FallbackDisplayModel.Release ->
                            "[${model.recoveryId}] ${model.genericGuidance}\nAction: ${model.actionInstructions}"
                    }
                    clipboardManager.setText(AnnotatedString(clipContent))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Article, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text(if (model is FallbackDisplayModel.Debug) "Copy Debug Log" else "Copy Recovery ID")
            }

            Button(
                onClick = onRestart,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Restart App", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
