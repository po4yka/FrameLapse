package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.entity.CameraFacing
import com.po4yka.framelapse.domain.entity.FlashMode
import com.po4yka.framelapse.domain.service.CameraController
import com.po4yka.framelapse.domain.util.Result
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureStillImageOutput
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVVideoCodecJPEG
import platform.AVFoundation.AVVideoCodecKey
import platform.Foundation.NSFileManager
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
class CameraControllerImpl : CameraController {

    private var captureSession: AVCaptureSession? = null
    private var stillImageOutput: AVCaptureStillImageOutput? = null
    private var currentDeviceInput: AVCaptureDeviceInput? = null
    private var currentDevice: AVCaptureDevice? = null

    private var _currentFacing: CameraFacing = CameraFacing.FRONT
    private var _currentFlashMode: FlashMode = FlashMode.OFF
    private var _isPreviewing: Boolean = false

    override val currentFacing: CameraFacing
        get() = _currentFacing

    override val currentFlashMode: FlashMode
        get() = _currentFlashMode

    override val isPreviewing: Boolean
        get() = _isPreviewing

    override suspend fun captureImage(outputPath: String): Result<String> = withContext(Dispatchers.Main) {
        val output = stillImageOutput
        val session = captureSession

        if (output == null || session == null) {
            return@withContext Result.Error(
                IllegalStateException("Camera not initialized"),
                "Camera not initialized",
            )
        }

        val connection = output.connectionWithMediaType(AVMediaTypeVideo)
        if (connection == null) {
            return@withContext Result.Error(
                IllegalStateException("No video connection available"),
                "No video connection available",
            )
        }

        suspendCancellableCoroutine<Result<String>> { continuation ->
            output.captureStillImageAsynchronouslyFromConnection(connection) { buffer, error ->
                val result: Result<String> = when {
                    error != null -> Result.Error(
                        Exception(error.localizedDescription),
                        "Failed to capture: ${error.localizedDescription}",
                    )
                    buffer == null -> Result.Error(
                        IllegalStateException("No image buffer"),
                        "Failed to get image buffer",
                    )
                    else -> {
                        val imageData = AVCaptureStillImageOutput.jpegStillImageNSDataRepresentation(buffer)
                        if (imageData == null) {
                            Result.Error(
                                IllegalStateException("Failed to get JPEG data"),
                                "Failed to encode image",
                            )
                        } else {
                            val fileManager = NSFileManager.defaultManager
                            val success = fileManager.createFileAtPath(
                                path = outputPath,
                                contents = imageData,
                                attributes = null,
                            )
                            if (success) {
                                Result.Success(outputPath)
                            } else {
                                Result.Error(
                                    IllegalStateException("Failed to save photo"),
                                    "Failed to save photo",
                                )
                            }
                        }
                    }
                }
                continuation.resume(result)
            }
        }
    }

    override fun startPreview() {
        if (_isPreviewing) return

        val session = AVCaptureSession()
        session.sessionPreset = AVCaptureSessionPresetPhoto

        val device = getCamera(_currentFacing)
        if (device == null) {
            return
        }

        val input = try {
            AVCaptureDeviceInput.deviceInputWithDevice(device, null)
        } catch (_: Exception) {
            return
        }

        if (input == null || !session.canAddInput(input)) {
            return
        }

        session.addInput(input)
        currentDeviceInput = input
        currentDevice = device

        // Use AVCaptureStillImageOutput for simpler capture
        val output = AVCaptureStillImageOutput()
        output.outputSettings = mapOf<Any?, Any?>(AVVideoCodecKey to AVVideoCodecJPEG)

        if (!session.canAddOutput(output)) {
            return
        }

        session.addOutput(output)
        stillImageOutput = output

        captureSession = session
        session.startRunning()
        _isPreviewing = true
    }

    override fun stopPreview() {
        captureSession?.stopRunning()
        captureSession = null
        stillImageOutput = null
        currentDeviceInput = null
        currentDevice = null
        _isPreviewing = false
    }

    override fun setFlashMode(mode: FlashMode) {
        _currentFlashMode = mode
    }

    override fun switchCamera() {
        _currentFacing = when (_currentFacing) {
            CameraFacing.FRONT -> CameraFacing.BACK
            CameraFacing.BACK -> CameraFacing.FRONT
        }
        if (_isPreviewing) {
            reconfigureCamera()
        }
    }

    override fun setCameraFacing(facing: CameraFacing) {
        if (_currentFacing != facing) {
            _currentFacing = facing
            if (_isPreviewing) {
                reconfigureCamera()
            }
        }
    }

    override fun isCameraAvailable(facing: CameraFacing): Boolean = getCamera(facing) != null

    override fun release() {
        stopPreview()
    }

    fun getCaptureSession(): AVCaptureSession? = captureSession

    @Suppress("UNCHECKED_CAST")
    private fun getCamera(facing: CameraFacing): AVCaptureDevice? {
        val position = when (facing) {
            CameraFacing.FRONT -> AVCaptureDevicePositionFront
            CameraFacing.BACK -> AVCaptureDevicePositionBack
        }

        // Use discovery session to find the camera device
        val discoverySession = platform.AVFoundation.AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
            deviceTypes = listOf(platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera),
            mediaType = AVMediaTypeVideo,
            position = position,
        )

        return discoverySession.devices.firstOrNull() as? AVCaptureDevice
    }

    private fun reconfigureCamera() {
        val session = captureSession ?: return

        session.beginConfiguration()

        currentDeviceInput?.let { session.removeInput(it) }

        val device = getCamera(_currentFacing)
        if (device != null) {
            val input = try {
                AVCaptureDeviceInput.deviceInputWithDevice(device, null)
            } catch (_: Exception) {
                null
            }

            if (input != null && session.canAddInput(input)) {
                session.addInput(input)
                currentDeviceInput = input
                currentDevice = device
            }
        }

        session.commitConfiguration()
    }
}
