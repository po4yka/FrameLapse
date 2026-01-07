package com.po4yka.framelapse.ui.screens.capture

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.po4yka.framelapse.domain.entity.FlashMode
import com.po4yka.framelapse.domain.service.CameraController
import com.po4yka.framelapse.domain.service.SoundPlayer
import com.po4yka.framelapse.presentation.capture.CaptureEffect
import com.po4yka.framelapse.presentation.capture.CaptureEvent
import com.po4yka.framelapse.presentation.capture.CaptureState
import com.po4yka.framelapse.presentation.capture.CaptureViewModel
import com.po4yka.framelapse.ui.components.ConfidenceIndicator
import com.po4yka.framelapse.ui.components.GhostImageOverlay
import com.po4yka.framelapse.ui.components.GridOverlay
import com.po4yka.framelapse.ui.components.PermissionDeniedScreen
import com.po4yka.framelapse.ui.util.HandleEffects
import com.po4yka.framelapse.ui.util.ImageLoadResult
import com.po4yka.framelapse.ui.util.rememberImageFromPath
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val CONTROL_BUTTON_SIZE = 48.dp
private val CAPTURE_BUTTON_SIZE = 72.dp
private val CONTROLS_PADDING = 16.dp

/**
 * Capture screen for taking timelapse photos.
 */
@Composable
fun CaptureScreen(
    projectId: String,
    onNavigateToGallery: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaptureViewModel = koinViewModel(),
    soundPlayer: SoundPlayer = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionState = rememberCameraPermissionState()

    HandleEffects(viewModel.effect) { effect ->
        when (effect) {
            is CaptureEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            is CaptureEffect.NavigateToGallery -> onNavigateToGallery()
            is CaptureEffect.TriggerCapture -> { /* Handled by camera */ }
            is CaptureEffect.PlayCaptureSound -> soundPlayer.playCaptureSound()
        }
    }

    LaunchedEffect(projectId) {
        viewModel.onEvent(CaptureEvent.Initialize(projectId))
    }

    // Request permission on first launch
    LaunchedEffect(permissionState.hasPermission) {
        if (!permissionState.hasPermission) {
            permissionState.requestPermission()
        }
    }

    if (permissionState.hasPermission) {
        CaptureContent(
            state = state,
            snackbarHostState = snackbarHostState,
            onEvent = viewModel::onEvent,
            onCameraReady = { controller ->
                viewModel.cameraController = controller
                viewModel.onEvent(CaptureEvent.CameraReady)
            },
            onNavigateBack = onNavigateBack,
            modifier = modifier,
        )
    } else {
        PermissionDeniedScreen(
            title = "Camera Permission Required",
            description = "FrameLapse needs camera access to capture your daily photos for the timelapse.",
            onRequestPermission = permissionState.requestPermission,
            onNavigateBack = onNavigateBack,
        )
    }
}

@Composable
private fun CaptureContent(
    state: CaptureState,
    snackbarHostState: SnackbarHostState,
    onEvent: (CaptureEvent) -> Unit,
    onCameraReady: (CameraController) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Camera preview
            CameraPreview(
                cameraFacing = state.captureSettings.cameraFacing,
                flashMode = state.captureSettings.flashMode,
                onCameraReady = onCameraReady,
                modifier = Modifier.fillMaxSize(),
            )

            // Ghost image overlay for alignment
            GhostImageOverlay(
                imagePath = state.ghostImagePath,
                opacity = state.captureSettings.ghostOpacity,
                modifier = Modifier.fillMaxSize(),
            )

            // Grid overlay for composition
            GridOverlay(
                showGrid = state.captureSettings.showGrid,
                modifier = Modifier.fillMaxSize(),
            )

            // Face detection confidence indicator
            ConfidenceIndicator(
                confidence = state.faceDetectionConfidence,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = CONTROLS_PADDING),
            )

            // Top controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CONTROLS_PADDING),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }

                Row {
                    // Ghost visibility toggle (only show if ghost image exists)
                    if (state.ghostImagePath != null) {
                        IconButton(
                            onClick = {
                                val newOpacity = if (state.captureSettings.ghostOpacity > 0f) 0f else 0.3f
                                onEvent(CaptureEvent.UpdateGhostOpacity(newOpacity))
                            },
                        ) {
                            Icon(
                                imageVector = if (state.captureSettings.ghostOpacity > 0f) {
                                    Icons.Default.Visibility
                                } else {
                                    Icons.Default.VisibilityOff
                                },
                                contentDescription = "Toggle ghost image",
                                tint = if (state.captureSettings.ghostOpacity > 0f) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White
                                },
                            )
                        }
                    }

                    IconButton(onClick = { onEvent(CaptureEvent.ToggleFlash) }) {
                        Icon(
                            imageVector = if (state.captureSettings.flashMode != FlashMode.OFF) {
                                Icons.Default.FlashOn
                            } else {
                                Icons.Default.FlashOff
                            },
                            contentDescription = "Toggle flash",
                            tint = Color.White,
                        )
                    }

                    IconButton(onClick = { onEvent(CaptureEvent.ToggleGrid) }) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Toggle grid",
                            tint = if (state.captureSettings.showGrid) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.White
                            },
                        )
                    }
                }
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(CONTROLS_PADDING),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Ghost opacity slider (only show if ghost image visible)
                if (state.ghostImagePath != null && state.captureSettings.ghostOpacity > 0f) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Ghost",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = state.captureSettings.ghostOpacity,
                            onValueChange = { onEvent(CaptureEvent.UpdateGhostOpacity(it)) },
                            valueRange = 0.1f..0.8f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(state.captureSettings.ghostOpacity * 100).toInt()}%",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Frame count
                if (state.frameCount > 0) {
                    Text(
                        text = "${state.frameCount} frames",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Gallery button with thumbnail
                    BadgedBox(
                        badge = {
                            if (state.frameCount > 0) {
                                Badge { Text("${state.frameCount}") }
                            }
                        },
                    ) {
                        FloatingActionButton(
                            onClick = { onEvent(CaptureEvent.NavigateToGallery) },
                            modifier = Modifier.size(CONTROL_BUTTON_SIZE),
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            // Show thumbnail if last frame exists
                            val thumbnailPath = state.lastCapturedFrame?.alignedPath
                                ?: state.lastCapturedFrame?.originalPath

                            if (thumbnailPath != null) {
                                val imageResult = rememberImageFromPath(thumbnailPath)
                                when (imageResult) {
                                    is ImageLoadResult.Success -> {
                                        Image(
                                            bitmap = imageResult.image,
                                            contentDescription = "Last captured frame",
                                            modifier = Modifier
                                                .size(CONTROL_BUTTON_SIZE)
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = "Gallery",
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Gallery",
                                )
                            }
                        }
                    }

                    // Capture button
                    LargeFloatingActionButton(
                        onClick = { onEvent(CaptureEvent.CaptureImage) },
                        modifier = Modifier.size(CAPTURE_BUTTON_SIZE),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(CAPTURE_BUTTON_SIZE - 16.dp)
                                .background(Color.White, CircleShape),
                        )
                    }

                    // Flip camera button
                    FloatingActionButton(
                        onClick = { onEvent(CaptureEvent.FlipCamera) },
                        modifier = Modifier.size(CONTROL_BUTTON_SIZE),
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Flip camera",
                        )
                    }
                }

                Spacer(modifier = Modifier.height(CONTROLS_PADDING))
            }
        }
    }
}
