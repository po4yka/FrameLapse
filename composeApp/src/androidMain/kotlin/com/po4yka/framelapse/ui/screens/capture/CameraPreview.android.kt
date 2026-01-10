package com.po4yka.framelapse.ui.screens.capture

import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.po4yka.framelapse.domain.entity.CameraFacing
import com.po4yka.framelapse.domain.entity.FlashMode
import com.po4yka.framelapse.domain.service.CameraController
import com.po4yka.framelapse.platform.CameraControllerImpl

/**
 * Android implementation of camera preview using CameraX.
 * Creates a CameraControllerImpl and binds it to a PreviewView.
 */
@Composable
actual fun CameraPreview(
    cameraFacing: CameraFacing,
    flashMode: FlashMode,
    onCameraReady: (CameraController) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Create the camera controller
    val cameraController = remember(lifecycleOwner) {
        CameraControllerImpl(context, lifecycleOwner)
    }

    // Track if camera is ready
    var isCameraReady by remember { mutableStateOf(false) }

    // Clean up on disposal
    DisposableEffect(lifecycleOwner) {
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
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier,
        update = { previewView ->
            // Set up the preview surface provider and start preview
            cameraController.setPreviewSurfaceProvider(previewView.surfaceProvider)
            if (!cameraController.isPreviewing) {
                cameraController.startPreview()
                // Mark as ready after starting preview
                isCameraReady = true
            }
        },
    )
}
