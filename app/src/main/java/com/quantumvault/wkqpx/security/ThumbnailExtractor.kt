package com.quantumvault.wkqpx.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import java.io.File
import java.io.FileOutputStream

object ThumbnailExtractor {
    fun extractThumbnailFromUri(context: Context, uri: Uri, mimeType: String, isVideo: Boolean): Bitmap? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return context.contentResolver.loadThumbnail(uri, Size(320, 320), null)
            }
        } catch (e: Exception) {
            // Fallback
        }

        try {
            if (isVideo || mimeType.startsWith("video/")) {
                val retriever = MediaMetadataRetriever()
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                    } ?: retriever.setDataSource(context, uri)
                } catch (e: Exception) {
                    retriever.setDataSource(context, uri)
                }
                val frame = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: retriever.frameAtTime
                retriever.release()
                if (frame != null) {
                    val targetWidth = 320
                    val targetHeight = (targetWidth * (frame.height.toFloat() / frame.width.toFloat())).toInt().coerceIn(180, 480)
                    return Bitmap.createScaledBitmap(frame, targetWidth, targetHeight, true)
                }
            } else if (mimeType.startsWith("image/")) {
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeStream(inStream, null, options)
                    options.inSampleSize = calculateInSampleSize(options, 320, 320)
                    options.inJustDecodeBounds = false
                    
                    // re-open stream because decodeStream advances it
                    context.contentResolver.openInputStream(uri)?.use { inStream2 ->
                        return BitmapFactory.decodeStream(inStream2, null, options)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
