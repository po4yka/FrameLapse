package com.po4yka.framelapse.ui.screens.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType

/**
 * iOS implementation of camera permission state.
 * Uses AVFoundation authorization APIs.
 */
@Composable
actual fun rememberCameraPermissionState(): CameraPermissionState {
    var hasPermission by remember { mutableStateOf(false) }

    // Check initial permission status
    LaunchedEffect(Unit) {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        hasPermission = status == AVAuthorizationStatusAuthorized
    }

    return CameraPermissionState(
        hasPermission = hasPermission,
        shouldShowRationale = false, // iOS doesn't have rationale concept
        requestPermission = {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                hasPermission = granted
            }
        },
    )
}
