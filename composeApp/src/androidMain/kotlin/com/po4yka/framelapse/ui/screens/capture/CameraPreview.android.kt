package com.po4yka.framelapse.ui.screens.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Android camera preview stub.
 * Real CameraX implementation will be added in Phase 7.
 */
@Composable
actual fun CameraPreview(onCameraReady: () -> Unit, onCaptureRequest: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Camera Preview (Android)",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
    }

    LaunchedEffect(Unit) {
        onCameraReady()
    }
}
