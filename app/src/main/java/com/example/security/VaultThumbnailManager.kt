package com.example.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import com.example.data.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object VaultThumbnailManager {
    private const val TAG = "VaultThumbnailManager"

    // Memory cache: max 30MB of decoded thumbnail Bitmaps
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(1024 * 16, 1024 * 48) // 16MB - 48MB

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    /**
     * Retrieves or generates a downsampled thumbnail Bitmap for a given VaultItem.
     * Caches in-memory for 60fps smooth scrolling.
     */
    suspend fun getThumbnail(context: Context, item: VaultItem): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${item.encryptedFileName}_${item.id}"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val vaultDir = File(context.filesDir, "vault_secure_data")
        val encryptedFile = File(vaultDir, item.encryptedFileName)
        if (!encryptedFile.exists()) return@withContext null

        try {
            val bitmap = when {
                item.mimeType.startsWith("image/") -> {
                    loadDecryptedImageThumbnail(encryptedFile)
                }
                item.isVideo || item.mimeType.startsWith("video/") -> {
                    loadDecryptedVideoThumbnail(context, encryptedFile)
                }
                item.mimeType == "application/pdf" || item.originalName.endsWith(".pdf", ignoreCase = true) -> {
                    loadDecryptedPdfThumbnail(context, encryptedFile)
                }
                else -> null
            }

            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
            }
            bitmap
        } catch (e: Throwable) {
            Log.w(TAG, "Error generating thumbnail for ${item.originalName}: ${e.message}")
            null
        }
    }

    private fun loadDecryptedImageThumbnail(encryptedFile: File): Bitmap? {
        return try {
            // Decrypt stream in-memory with size limit
            FileInputStream(encryptedFile).buffered(65536).use { inStream ->
                val decryptedBytes = CryptoManager.decryptStreamToByteArray(inStream, maxSizeBytes = 40 * 1024 * 1024L)
                if (decryptedBytes != null && decryptedBytes.isNotEmpty()) {
                    // First decode bounds
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, options)

                    // Calculate inSampleSize for max 320x320 thumbnail
                    options.inSampleSize = calculateInSampleSize(options, 320, 320)
                    options.inJustDecodeBounds = false
                    options.inPreferredConfig = Bitmap.Config.RGB_565 // Low memory footprint

                    BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, options)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadDecryptedVideoThumbnail(context: Context, encryptedFile: File): Bitmap? {
        val tempThumbVideo = File(context.cacheDir, "temp_thumb_${System.currentTimeMillis()}_${encryptedFile.name}.mp4")
        return try {
            // Decrypt first 5MB of video file to extract thumbnail header/frame
            FileOutputStream(tempThumbVideo).buffered(65536).use { outStream ->
                FileInputStream(encryptedFile).buffered(65536).use { inStream ->
                    // Stream only first few chunks or full file if < 8MB
                    CryptoManager.decryptStreamToOutputStream(inStream, outStream)
                }
            }

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(tempThumbVideo.absolutePath)
            val frame = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            retriever.release()

            if (frame != null) {
                // Scale down frame to thumbnail size
                val scaled = Bitmap.createScaledBitmap(frame, 320, (320f * (frame.height.toFloat() / frame.width.toFloat())).toInt().coerceIn(180, 480), true)
                scaled
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            if (tempThumbVideo.exists()) {
                tempThumbVideo.delete()
            }
        }
    }

    private fun loadDecryptedPdfThumbnail(context: Context, encryptedFile: File): Bitmap? {
        val tempPdf = File(context.cacheDir, "temp_pdf_thumb_${System.currentTimeMillis()}.pdf")
        return try {
            FileOutputStream(tempPdf).buffered(65536).use { outStream ->
                FileInputStream(encryptedFile).buffered(65536).use { inStream ->
                    CryptoManager.decryptStreamToOutputStream(inStream, outStream)
                }
            }

            val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val width = 280
                val height = (width * (page.height.toFloat() / page.width.toFloat())).toInt().coerceIn(280, 420)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()
                bitmap
            } else {
                renderer.close()
                pfd.close()
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            if (tempPdf.exists()) {
                tempPdf.delete()
            }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}
