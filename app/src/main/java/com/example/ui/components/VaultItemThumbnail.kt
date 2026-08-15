package com.example.ui.components

import android.graphics.Bitmap
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.data.VaultItem
import com.example.security.VaultThumbnailManager

@Composable
fun VaultItemThumbnail(
    item: VaultItem,
    modifier: Modifier = Modifier,
    iconSize: Dp = 36.dp,
    fallbackIconColor: Color = Color(0xFF00F0FF)
) {
    val context = LocalContext.current
    var bitmap by remember(item.id, item.encryptedFileName) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(item.id, item.encryptedFileName) { mutableStateOf(true) }

    LaunchedEffect(item.id, item.encryptedFileName) {
        val loaded = VaultThumbnailManager.getThumbnail(context, item)
        bitmap = loaded
        isLoading = false
    }

    Box(
        modifier = modifier.background(Color(0xFF050E17)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.originalName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // If it's a video, add small play overlay on the thumbnail image
            if (item.isVideo || item.mimeType.startsWith("video/")) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Video",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        } else {
            val icon = getFileTypeIcon(item.mimeType, item.isVideo, item.originalName)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fallbackIconColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

private fun getFileTypeIcon(mimeType: String, isVideo: Boolean, originalName: String): ImageVector {
    val mime = mimeType.lowercase()
    val name = originalName.lowercase()
    return when {
        isVideo || mime.startsWith("video/") -> Icons.Default.PlayCircle
        mime.startsWith("image/") -> Icons.Default.Image
        mime.startsWith("audio/") -> Icons.Default.MusicNote
        mime == "application/pdf" || name.endsWith(".pdf") -> Icons.Default.PictureAsPdf
        mime.contains("zip") || mime.contains("tar") || mime.contains("rar") || name.endsWith(".zip") -> Icons.Default.FolderZip
        else -> Icons.Default.Description
    }
}
