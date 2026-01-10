package com.po4yka.framelapse.ui.screens.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.po4yka.framelapse.domain.entity.CameraFacing
import com.po4yka.framelapse.domain.entity.FlashMode
import com.po4yka.framelapse.domain.service.CameraController
import com.po4yka.framelapse.platform.CameraControllerImpl
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView

/**
 * iOS implementation of camera preview using AVFoundation.
 * Creates a CameraControllerImpl and displays the preview via AVCaptureVideoPreviewLayer.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
@Suppress("DEPRECATION")
actual fun CameraPreview(
    cameraFacing: CameraFacing,
    flashMode: FlashMode,
    onCameraReady: (CameraController) -> Unit,
    modifier: Modifier,
) {
    // Create the camera controller
    val cameraController = remember { CameraControllerImpl() }

    // Track if camera is ready
    var isCameraReady by remember { mutableStateOf(false) }

    // Track preview layer for frame updates
    var previewLayer by remember { mutableStateOf<AVCaptureVideoPreviewLayer?>(null) }

    // Start preview and clean up on disposal
    DisposableEffect(Unit) {
        cameraController.startPreview()
        isCameraReady = true
        onDispose {
            cameraController.release()
        }
    }

    // Sync camera facing when it changes
    LaunchedEffect(cameraFacing) {
        cameraController.setCameraFacing(cameraFacing)
    }

    // Sync flash mode when it changes
    LaunchedEffect(flashMode) {
        cameraController.setFlashMode(flashMode)
    }

    // Notify when camera is ready
    LaunchedEffect(isCameraReady) {
        if (isCameraReady) {
            onCameraReady(cameraController)
        }
    }

    // Camera preview view
    UIKitView(
        factory = {
            // Create a custom UIView that handles layout
            val session = cameraController.getCaptureSession()
            val layer = if (session != null) {
                AVCaptureVideoPreviewLayer(session = session).apply {
                    videoGravity = AVLayerVideoGravityResizeAspectFill
                }
            } else {
                null
            }
            previewLayer = layer

            CameraPreviewUIView(layer)
        },
        modifier = modifier,
    )
}

/**
 * Custom UIView that properly handles preview layer frame updates on layout.
 */
@OptIn(ExperimentalForeignApi::class)
private class CameraPreviewUIView(private val previewLayer: AVCaptureVideoPreviewLayer?) :
    UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    init {
        backgroundColor = platform.UIKit.UIColor.blackColor
        previewLayer?.let { layer.addSublayer(it) }
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        // Update preview layer frame to match view bounds
        previewLayer?.frame = bounds
    }
}
