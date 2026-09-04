package com.quantumvault.wkqpx.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quantumvault.wkqpx.data.VaultItem
import com.quantumvault.wkqpx.security.VaultThumbnailManager

@Composable
fun VaultItemThumbnail(
    item: VaultItem,
    modifier: Modifier = Modifier,
    showThumbnails: Boolean = true,
    iconSize: Dp = 36.dp,
    fallbackIconColor: Color = Color(0xFF00F0FF)
) {
    val context = LocalContext.current
    var bitmap by remember(item.id, item.encryptedFileName, showThumbnails) { mutableStateOf<Bitmap?>(null) }
    var hasLoaded by remember(item.id, item.encryptedFileName, showThumbnails) { mutableStateOf(false) }

    LaunchedEffect(item.id, item.encryptedFileName, showThumbnails) {
        if (showThumbnails) {
            val loaded = VaultThumbnailManager.getThumbnail(context, item)
            bitmap = loaded
        } else {
            bitmap = null
        }
        hasLoaded = true
    }

    Box(
        modifier = modifier.background(Color(0xFF050E17)),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = bitmap,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "thumbnail_crossfade",
            modifier = Modifier.fillMaxSize()
        ) { currentBitmap ->
            if (currentBitmap != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = item.originalName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // If it's a video, add small play overlay on the thumbnail image
                    if (item.isVideo || item.mimeType.startsWith("video/")) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.28f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "Video",
                                tint = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }
                }
            } else {
                val icon = getFileTypeIcon(item.mimeType, item.isVideo, item.originalName)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = fallbackIconColor.copy(alpha = if (hasLoaded) 0.85f else 0.45f),
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

private fun getFileTypeIcon(mimeType: String, isVideo: Boolean, originalName: String): ImageVector {
    val mime = mimeType.lowercase()
    val name = originalName.lowercase()
    return when {
        isVideo || mime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov") -> Icons.Default.PlayCircle
        mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") -> Icons.Default.Image
        mime.startsWith("audio/") || name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a") -> Icons.Default.MusicNote
        mime == "application/pdf" || name.endsWith(".pdf") -> Icons.Default.PictureAsPdf
        mime.contains("zip") || mime.contains("tar") || mime.contains("rar") || name.endsWith(".zip") -> Icons.Default.FolderZip
        else -> Icons.Default.Description
    }
}
