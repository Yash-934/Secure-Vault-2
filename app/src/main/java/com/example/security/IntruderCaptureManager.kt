package com.example.security

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.data.AppDatabase
import com.example.data.IntruderLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Silent Intruder CameraX Selfie Engine.
 * Binds CameraX ImageCapture to front-facing camera without UI preview surface.
 */
object IntruderCaptureManager {

    private val executor = Executors.newSingleThreadExecutor()

    fun captureIntruderSelfie(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        attemptType: String,
        details: String
    ) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            saveLogWithImage(context, attemptType, details + " (No Camera Permission)", null)
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // Force camera stream active by binding a dummy ImageAnalysis use case
                val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                    .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    imageProxy.close()
                }

                // Select front camera
                var cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                if (!cameraProvider.hasCamera(cameraSelector)) {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    if (!cameraProvider.hasCamera(cameraSelector)) {
                        saveLogWithImage(context, attemptType, details + " (No camera available on device)", null)
                        return@addListener
                    }
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture, imageAnalysis)

                val selfieDir = File(context.filesDir, "intruder_selfies").apply { if (!exists()) mkdirs() }
                val selfieFile = File(selfieDir, "intruder_${System.currentTimeMillis()}.jpg")

                val outputOptions = ImageCapture.OutputFileOptions.Builder(selfieFile).build()

                // Give camera sensor time to initialize and focus before snapping
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        imageCapture.takePicture(
                            outputOptions,
                            executor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    ContextCompat.getMainExecutor(context).execute {
                                        cameraProvider.unbindAll()
                                    }
                                    // Record log with selfie imagePath in Room DB
                                    saveLogWithImage(context, attemptType, details, selfieFile.absolutePath)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    ContextCompat.getMainExecutor(context).execute {
                                        cameraProvider.unbindAll()
                                    }
                                    saveLogWithImage(context, attemptType, details + " (CameraX Error: ${exception.message})", null)
                                }
                            }
                        )
                    } catch (e: Exception) {
                        saveLogWithImage(context, attemptType, details + " (Delayed Exception: ${e.message})", null)
                    }
                }, 800)
            } catch (e: Exception) {
                saveLogWithImage(context, attemptType, details + " (Exception: ${e.message})", null)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun saveLogWithImage(context: Context, attemptType: String, details: String, imagePath: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                db.intruderLogDao().insertLog(
                    IntruderLog(
                        attemptType = attemptType,
                        details = details,
                        imagePath = imagePath
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
