package com.po4yka.framelapse.ui.screens.capture

import androidx.compose.runtime.Composable

/**
 * State holder for camera permission.
 *
 * @param hasPermission Whether the camera permission is granted
 * @param shouldShowRationale Whether to show rationale for the permission request
 * @param requestPermission Function to request the camera permission
 */
data class CameraPermissionState(
    val hasPermission: Boolean,
    val shouldShowRationale: Boolean,
    val requestPermission: () -> Unit,
)

/**
 * Creates and remembers a [CameraPermissionState] for camera permission management.
 *
 * @return The current camera permission state
 */
@Composable
expect fun rememberCameraPermissionState(): CameraPermissionState
