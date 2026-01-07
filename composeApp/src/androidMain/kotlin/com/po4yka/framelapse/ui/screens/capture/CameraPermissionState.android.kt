package com.po4yka.framelapse.ui.screens.capture

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Android implementation of camera permission state.
 * Uses Activity Result API for permission requests.
 */
@Composable
actual fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    val shouldShowRationale = remember(context) {
        (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: false
    }

    return CameraPermissionState(
        hasPermission = hasPermission,
        shouldShowRationale = shouldShowRationale,
        requestPermission = { launcher.launch(Manifest.permission.CAMERA) },
    )
}
