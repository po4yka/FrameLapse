package com.po4yka.framelapse.platform

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.po4yka.framelapse.domain.entity.CameraFacing
import com.po4yka.framelapse.domain.entity.FlashMode
import com.po4yka.framelapse.domain.service.CameraController
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class CameraControllerImpl(private val context: Context, private val lifecycleOwner: LifecycleOwner) :
    CameraController {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var _currentFacing: CameraFacing = CameraFacing.FRONT
    private var _currentFlashMode: FlashMode = FlashMode.OFF
    private var _isPreviewing: Boolean = false

    override val currentFacing: CameraFacing
        get() = _currentFacing

    override val currentFlashMode: FlashMode
        get() = _currentFlashMode

    override val isPreviewing: Boolean
        get() = _isPreviewing

    override suspend fun captureImage(outputPath: String): Result<String> =
        suspendCancellableCoroutine { continuation ->
            val capture = imageCapture
            if (capture == null) {
                continuation.resume(
                    Result.Error(
                        IllegalStateException("Camera not initialized"),
                        "Camera not initialized",
                    ),
                )
                return@suspendCancellableCoroutine
            }

            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        continuation.resume(Result.Success(outputPath))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resume(
                            Result.Error(
                                exception,
                                "Failed to capture image: ${exception.message}",
                            ),
                        )
                    }
                },
            )
        }

    override fun startPreview() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
            _isPreviewing = true
        }, ContextCompat.getMainExecutor(context))
    }

    override fun stopPreview() {
        cameraProvider?.unbindAll()
        _isPreviewing = false
    }

    override fun setFlashMode(mode: FlashMode) {
        _currentFlashMode = mode
        imageCapture?.flashMode = when (mode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    override fun switchCamera() {
        _currentFacing = when (_currentFacing) {
            CameraFacing.FRONT -> CameraFacing.BACK
            CameraFacing.BACK -> CameraFacing.FRONT
        }
        if (_isPreviewing) {
            bindCameraUseCases()
        }
    }

    override fun setCameraFacing(facing: CameraFacing) {
        if (_currentFacing != facing) {
            _currentFacing = facing
            if (_isPreviewing) {
                bindCameraUseCases()
            }
        }
    }

    override fun isCameraAvailable(facing: CameraFacing): Boolean {
        return try {
            val provider = cameraProvider ?: return false
            val selector = when (facing) {
                CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            }
            provider.hasCamera(selector)
        } catch (e: Exception) {
            false
        }
    }

    override fun release() {
        stopPreview()
        cameraExecutor.shutdown()
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return

        val cameraSelector = when (_currentFacing) {
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        preview = Preview.Builder()
            .build()

        val flashMode = when (_currentFlashMode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(flashMode)
            .setTargetRotation(android.view.Surface.ROTATION_0)
            .build()

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
            )
        } catch (e: Exception) {
            // Handle camera binding failure
        }
    }

    fun getPreview(): Preview? = preview

    fun setPreviewSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
        preview?.setSurfaceProvider(surfaceProvider)
    }
}
