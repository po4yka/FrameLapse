package com.po4yka.framelapse.ui.screens.capture

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.po4yka.framelapse.domain.entity.CameraFacing
import com.po4yka.framelapse.domain.entity.FlashMode
import com.po4yka.framelapse.domain.service.CameraController

/**
 * Platform-specific camera preview composable.
 *
 * @param cameraFacing The camera direction (front/back)
 * @param flashMode The flash mode (off/on/auto)
 * @param onCameraReady Callback when camera is initialized and ready, provides the CameraController
 * @param modifier Modifier for the preview
 */
@Composable
expect fun CameraPreview(
    cameraFacing: CameraFacing,
    flashMode: FlashMode,
    onCameraReady: (CameraController) -> Unit,
    modifier: Modifier = Modifier,
)
