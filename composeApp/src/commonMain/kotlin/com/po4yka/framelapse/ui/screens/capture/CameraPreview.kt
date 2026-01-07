package com.po4yka.framelapse.ui.screens.capture

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific camera preview composable.
 *
 * @param onCameraReady Callback when camera is initialized and ready
 * @param onCaptureRequest Callback to trigger image capture
 * @param modifier Modifier for the preview
 */
@Composable
expect fun CameraPreview(onCameraReady: () -> Unit, onCaptureRequest: () -> Unit, modifier: Modifier = Modifier)
