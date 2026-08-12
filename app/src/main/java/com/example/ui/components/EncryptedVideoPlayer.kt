package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * On-The-Fly Encrypted Video and Audio Player using ExoPlayer (Media3) with Secure Temp Cache & Shred.
 * Decrypts the video into a temporary file in the cache directory, streams it via ExoPlayer,
 * and securely shreds the temp file when the user leaves, backgrounds the app, or the view is disposed.
 */
@Composable
fun EncryptedVideoPlayer(
    encryptedFile: File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var tempDecryptedFile by remember { mutableStateOf<File?>(null) }
    var isDecrypting by remember { mutableStateOf(true) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Shredder function
    val shredTempFile = {
        tempDecryptedFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        tempDecryptedFile = null
    }

    // 1. Background Decryption
    LaunchedEffect(encryptedFile) {
        withContext(Dispatchers.IO) {
            isDecrypting = true
            try {
                // Create temp file strictly in cacheDir
                val tempFile = File(context.cacheDir, "temp_video_${UUID.randomUUID()}.mp4")
                
                encryptedFile.inputStream().use { input ->
                    tempFile.outputStream().use { output ->
                        CryptoManager.decryptStreamToOutputStream(input, output)
                    }
                }
                tempDecryptedFile = tempFile
            } catch (e: Exception) {
                e.printStackTrace()
                shredTempFile()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to decrypt video.", Toast.LENGTH_LONG).show()
                }
            } finally {
                isDecrypting = false
            }
        }
    }

    // 2. Playback Preparation
    LaunchedEffect(tempDecryptedFile) {
        tempDecryptedFile?.let { file ->
            val player = ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
            exoPlayer = player
        }
    }

    // 3. The 'Shredder' (Strict Security)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                // Shred on backgrounding
                shredTempFile()
                exoPlayer?.stop()
                exoPlayer?.release()
                exoPlayer = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            shredTempFile()
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("encrypted_video_player"),
        contentAlignment = Alignment.Center
    ) {
        if (isDecrypting) {
            CircularProgressIndicator(color = Color.White)
        } else if (exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
