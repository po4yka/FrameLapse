package com.po4yka.framelapse.ui.screens.capture

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.po4yka.framelapse.domain.entity.FlashMode
import com.po4yka.framelapse.presentation.capture.CaptureEffect
import com.po4yka.framelapse.presentation.capture.CaptureEvent
import com.po4yka.framelapse.presentation.capture.CaptureState
import com.po4yka.framelapse.presentation.capture.CaptureViewModel
import com.po4yka.framelapse.ui.util.HandleEffects
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleEffects(viewModel.effect) { effect ->
        when (effect) {
            is CaptureEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            is CaptureEffect.NavigateToGallery -> onNavigateToGallery()
            is CaptureEffect.TriggerCapture -> { /* Handled by camera */ }
            is CaptureEffect.PlayCaptureSound -> { /* TODO: Play sound */ }
        }
    }

    LaunchedEffect(projectId) {
        viewModel.onEvent(CaptureEvent.Initialize(projectId))
    }

    CaptureContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
private fun CaptureContent(
    state: CaptureState,
    snackbarHostState: SnackbarHostState,
    onEvent: (CaptureEvent) -> Unit,
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
                onCameraReady = { onEvent(CaptureEvent.CameraReady) },
                onCaptureRequest = { /* Capture handled by button */ },
                modifier = Modifier.fillMaxSize(),
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
                    // Gallery button
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
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Gallery",
                            )
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
