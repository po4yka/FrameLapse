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
            val containerView = UIView()
            containerView.backgroundColor = platform.UIKit.UIColor.blackColor

            // Create and configure preview layer
            val session = cameraController.getCaptureSession()
            if (session != null) {
                val layer = AVCaptureVideoPreviewLayer(session = session)
                layer.videoGravity = AVLayerVideoGravityResizeAspectFill
                containerView.layer.addSublayer(layer)
                previewLayer = layer
            }

            containerView
        },
        modifier = modifier,
        onResize = { view, size ->
            // Update preview layer frame to match view size
            previewLayer?.frame = CGRectMake(
                x = 0.0,
                y = 0.0,
                width = size.width.toDouble(),
                height = size.height.toDouble(),
            )
        },
    )
}
