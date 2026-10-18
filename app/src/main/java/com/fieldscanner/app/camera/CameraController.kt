package com.fieldscanner.app.camera

import android.content.Context
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.fieldscanner.app.data.camera.temporaryPhotoDirectory
import com.fieldscanner.app.data.diagnostics.DiagnosticsLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** Owns CameraX binding and temporary scan-photo creation. */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val isPreviewVisible: () -> Boolean,
    private val onUnavailable: () -> Unit,
    private val onRearCameraUnavailable: () -> Unit,
    private val onPhotoCaptured: (File) -> Unit,
    private val diagnostics: DiagnosticsLog = DiagnosticsLog.NONE
) {
    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    fun start() {
        if (camera != null) return
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val cameraProvider = runCatching { providerFuture.get() }.getOrElse { error -> diagnostics.error("CameraController", "Camera provider unavailable", error); onUnavailable(); return@addListener }
            if (isPreviewVisible()) runCatching { bind(cameraProvider) }.onFailure { error -> diagnostics.error("CameraController", "Rear camera binding failed", error); onRearCameraUnavailable() }
            else cameraProvider.unbindAll()
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        provider?.unbindAll()
        camera = null
        imageCapture = null
    }

    fun takePhoto(onNotReady: () -> Unit, onSaveFailed: () -> Unit) {
        val capture = imageCapture ?: run { onNotReady(); return }
        val directory = temporaryPhotoDirectory(context)
        if (!directory.exists() && !directory.mkdirs()) { onSaveFailed(); return }
        capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val photo = File(directory, "SCAN_$name.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(photo).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onPhotoCaptured(photo)
                override fun onError(exception: ImageCaptureException) { diagnostics.error("CameraController", "Photo capture failed", exception); onSaveFailed() }
            }
        )
    }

    private fun bind(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
            .build()
        cameraProvider.unbindAll()
        provider = cameraProvider
        camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
        imageCapture = capture
    }
}
