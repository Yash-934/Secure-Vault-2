package com.quantumvault.wkqpx.ui.components
import androidx.compose.material.icons.automirrored.filled.*

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.quantumvault.wkqpx.security.CryptoManager
import com.quantumvault.wkqpx.ui.theme.VaultPrimaryCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.UUID

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * High-performance Encrypted Video & Audio Player with seamless playback & fullscreen:
 * - Play / Pause
 * - Seamless Fullscreen toggle (Immersive landscape & System UI hiding without reloading)
 * - 10s Forward / Rewind
 * - Scrubbing seekbar with live duration and position
 * - Playback speed selector (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
 * - Mute / Unmute toggle
 * - Auto-hiding control HUD on idle or single tap
 * - Strict Temp Cache Shredding on Stop/Dispose
 */
@Composable
fun EncryptedVideoPlayer(
    encryptedFile: File,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }

    var tempDecryptedFile by remember { mutableStateOf<File?>(null) }
    var isDecrypting by remember { mutableStateOf(true) }
    var decryptedBytesCount by remember { mutableLongStateOf(0L) }
    var totalBytesCount by remember { mutableLongStateOf(0L) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Playback state
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // UI state
    var areControlsVisible by remember { mutableStateOf(true) }
    var isUserDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    // Secure Shredder function: Zero-overwrites file content before unlinking
    val shredTempFile = {
        tempDecryptedFile?.let { file ->
            try {
                if (file.exists()) {
                    val length = file.length()
                    if (length > 0) {
                        RandomAccessFile(file, "rws").use { raf ->
                            raf.seek(0)
                            val zeroBuf = ByteArray(minOf(65536, length.toInt().coerceAtLeast(1024)))
                            var written = 0L
                            while (written < length) {
                                val toWrite = minOf(zeroBuf.size.toLong(), length - written).toInt()
                                raf.write(zeroBuf, 0, toWrite)
                                written += toWrite
                            }
                        }
                    }
                    file.delete()
                }
            } catch (_: Throwable) {
                file.delete()
            }
        }
        tempDecryptedFile = null
    }

    // 1. Playback Preparation & Listener (Instant Streaming)
    LaunchedEffect(encryptedFile) {
        withContext(Dispatchers.IO) {
            totalBytesCount = encryptedFile.length()
        }
        val factory = com.quantumvault.wkqpx.security.CipherDataSourceFactory(encryptedFile)
        val player = ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(encryptedFile))
            val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(factory)
                .createMediaSource(mediaItem)
            
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                isDecrypting = false // Once playing, hide decryption spinner
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    totalDuration = player.duration.coerceAtLeast(0L)
                    isDecrypting = false
                }
            }
        })

        exoPlayer = player
    }

    // 3. Periodic Position Updater
    LaunchedEffect(exoPlayer, isPlaying, isUserDraggingSlider) {
        while (true) {
            exoPlayer?.let { player ->
                if (!isUserDraggingSlider) {
                    currentPosition = player.currentPosition.coerceAtLeast(0L)
                    if (player.duration > 0) {
                        totalDuration = player.duration
                    }
                }
            }
            delay(300)
        }
    }

    // 4. Auto-Hide Controls Timer
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying) {
            delay(3500)
            areControlsVisible = false
        }
    }

    // 5. Fullscreen Immersive Configuration
    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 6. Lifecycle Listener - Only destroy when genuinely backgrounded (ON_STOP) or closed (onDispose)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                shredTempFile()
                exoPlayer?.stop()
                exoPlayer?.release()
                exoPlayer = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            lifecycleOwner.lifecycle.removeObserver(observer)
            shredTempFile()
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    // Handle back button when in fullscreen mode
    BackHandler(enabled = isFullscreen) {
        onToggleFullscreen(false)
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                areControlsVisible = !areControlsVisible
            }
            .testTag("encrypted_video_player_box"),
        contentAlignment = Alignment.Center
    ) {
        if (isDecrypting) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(24.dp)
            ) {
                CircularProgressIndicator(
                    color = VaultPrimaryCyan,
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 3.5.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "DECRYPTING MEDIA STREAM",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                val progressFraction = if (totalBytesCount > 0) {
                    (decryptedBytesCount.toFloat() / totalBytesCount.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val percent = (progressFraction * 100).toInt().coerceIn(0, 100)

                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = VaultPrimaryCyan,
                    trackColor = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (totalBytesCount > 0) {
                            "${formatBytes(decryptedBytesCount)} / ${formatBytes(totalBytesCount)}"
                        } else {
                            "Initializing cipher..."
                        },
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "$percent%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VaultPrimaryCyan,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else if (exoPlayer != null) {
            // ExoPlayer View
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // Custom Jetpack Compose overlay controls used
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )

            // Buffering Spinner
            if (isBuffering) {
                CircularProgressIndicator(
                    color = VaultPrimaryCyan,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Interactive Custom Overlay Controls
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.70f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                ) {
                    // Top Bar in Video Overlay (Speed & Mute)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Playback Speed Selector
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable { showSpeedMenu = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Speed",
                                    tint = VaultPrimaryCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${playbackSpeed}x",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false }
                            ) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}x") },
                                        onClick = {
                                            playbackSpeed = speed
                                            exoPlayer?.playbackParameters = PlaybackParameters(speed)
                                            showSpeedMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Mute / Unmute Button
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                exoPlayer?.volume = if (isMuted) 0f else 1f
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = if (isMuted) Color(0xFFFF5252) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Center Controls (10s Rewind, Play/Pause, 10s Forward)
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s
                        IconButton(
                            onClick = {
                                exoPlayer?.let { player ->
                                    val newPos = (player.currentPosition - 10000L).coerceAtLeast(0L)
                                    player.seekTo(newPos)
                                    currentPosition = newPos
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .testTag("video_rewind_10s")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(28.dp))

                        // Big Play / Pause Button
                        IconButton(
                            onClick = {
                                exoPlayer?.let { player ->
                                    if (player.isPlaying) {
                                        player.pause()
                                    } else {
                                        player.play()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(VaultPrimaryCyan)
                                .testTag("video_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(28.dp))

                        // Forward 10s
                        IconButton(
                            onClick = {
                                exoPlayer?.let { player ->
                                    val newPos = (player.currentPosition + 10000L).coerceAtMost(player.duration.coerceAtLeast(0L))
                                    player.seekTo(newPos)
                                    currentPosition = newPos
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .testTag("video_forward_10s")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Bottom Control Bar (Time Labels, Scrubber Slider, Fullscreen Toggle)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Progress Slider
                        val durationFloat = totalDuration.toFloat().coerceAtLeast(1f)
                        val sliderValue = if (isUserDraggingSlider) sliderDragPosition else currentPosition.toFloat()

                        Slider(
                            value = sliderValue.coerceIn(0f, durationFloat),
                            onValueChange = { newPos ->
                                isUserDraggingSlider = true
                                sliderDragPosition = newPos
                            },
                            onValueChangeFinished = {
                                isUserDraggingSlider = false
                                exoPlayer?.seekTo(sliderDragPosition.toLong())
                                currentPosition = sliderDragPosition.toLong()
                            },
                            valueRange = 0f..durationFloat,
                            colors = SliderDefaults.colors(
                                thumbColor = VaultPrimaryCyan,
                                activeTrackColor = VaultPrimaryCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .testTag("video_progress_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Time string (Current / Total)
                            Text(
                                text = "${formatTime(currentPosition)} / ${formatTime(totalDuration)}",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )

                            // Fullscreen Toggle Button
                            IconButton(
                                onClick = { onToggleFullscreen(!isFullscreen) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .testTag("video_fullscreen_button")
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                                    tint = VaultPrimaryCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
