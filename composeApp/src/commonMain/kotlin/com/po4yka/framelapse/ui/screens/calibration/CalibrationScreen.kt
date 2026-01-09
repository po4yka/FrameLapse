package com.po4yka.framelapse.ui.screens.calibration

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.po4yka.framelapse.domain.entity.AdjustmentPointType
import com.po4yka.framelapse.domain.entity.CameraFacing
import com.po4yka.framelapse.domain.entity.FlashMode
import com.po4yka.framelapse.domain.entity.FaceManualAdjustment
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.service.CameraController
import com.po4yka.framelapse.domain.service.SoundPlayer
import com.po4yka.framelapse.platform.currentTimeMillis
import com.po4yka.framelapse.presentation.calibration.CalibrationEffect
import com.po4yka.framelapse.presentation.calibration.CalibrationEvent
import com.po4yka.framelapse.presentation.calibration.CalibrationPhase
import com.po4yka.framelapse.presentation.calibration.CalibrationState
import com.po4yka.framelapse.presentation.calibration.CalibrationViewModel
import com.po4yka.framelapse.ui.components.PermissionDeniedScreen
import com.po4yka.framelapse.ui.components.adjustment.FaceDragHandles
import com.po4yka.framelapse.ui.components.calibration.CalibrationOffsetControls
import com.po4yka.framelapse.ui.screens.capture.CameraPreview
import com.po4yka.framelapse.ui.screens.capture.rememberCameraPermissionState
import com.po4yka.framelapse.ui.util.HandleEffects
import com.po4yka.framelapse.ui.util.ImageLoadResult
import com.po4yka.framelapse.ui.util.rememberImageFromPath
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val CONTROLS_PADDING = 16.dp
private val CAPTURE_BUTTON_SIZE = 72.dp

/**
 * Calibration screen for setting up face alignment reference.
 *
 * Two-phase flow:
 * 1. CAPTURE: Take a reference photo
 * 2. ADJUST: Adjust eye markers and offset values
 */
@Composable
fun CalibrationScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalibrationViewModel = koinViewModel<CalibrationViewModel>(),
    soundPlayer: SoundPlayer = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionState = rememberCameraPermissionState()

    HandleEffects(viewModel.effect) { effect ->
        when (effect) {
            is CalibrationEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            is CalibrationEffect.ShowSuccess -> snackbarHostState.showSnackbar(effect.message)
            is CalibrationEffect.NavigateBack -> onNavigateBack()
            is CalibrationEffect.TriggerCapture -> { /* Handled by camera */ }
            is CalibrationEffect.PlayCaptureSound -> soundPlayer.playCaptureSound()
        }
    }

    LaunchedEffect(projectId) {
        viewModel.onEvent(CalibrationEvent.Initialize(projectId))
    }

    // Request permission on first launch (only for capture phase)
    LaunchedEffect(permissionState.hasPermission, state.phase) {
        if (state.phase == CalibrationPhase.CAPTURE && !permissionState.hasPermission) {
            permissionState.requestPermission()
        }
    }

    when (state.phase) {
        CalibrationPhase.CAPTURE -> {
            if (permissionState.hasPermission) {
                CalibrationCaptureContent(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onEvent = viewModel::onEvent,
                    onCameraReady = { controller ->
                        viewModel.cameraController = controller
                        viewModel.onEvent(CalibrationEvent.CameraReady)
                    },
                    onNavigateBack = onNavigateBack,
                    modifier = modifier,
                )
            } else {
                PermissionDeniedScreen(
                    title = "Camera Permission Required",
                    description = "Camera permission is needed to capture a calibration reference photo.",
                    onRequestPermission = permissionState.requestPermission,
                    onNavigateBack = onNavigateBack,
                )
            }
        }
        CalibrationPhase.ADJUST -> {
            CalibrationAdjustContent(
                state = state,
                snackbarHostState = snackbarHostState,
                onEvent = viewModel::onEvent,
                onNavigateBack = onNavigateBack,
                modifier = modifier,
            )
        }
    }
}

/**
 * Capture phase content - camera preview with capture button.
 */
@Composable
private fun CalibrationCaptureContent(
    state: CalibrationState,
    snackbarHostState: SnackbarHostState,
    onEvent: (CalibrationEvent) -> Unit,
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
                cameraFacing = CameraFacing.FRONT,
                flashMode = FlashMode.OFF,
                onCameraReady = onCameraReady,
                modifier = Modifier.fillMaxSize(),
            )

            // Top bar
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

                Text(
                    text = "Calibration",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )

                // Spacer for centering title
                Spacer(modifier = Modifier.size(48.dp))
            }

            // Instructions
            Text(
                text = "Position your face in the frame and take a reference photo",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium,
                    )
                    .padding(16.dp),
            )

            // Bottom controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Capture button
                FloatingActionButton(
                    onClick = { onEvent(CalibrationEvent.CaptureReference) },
                    modifier = Modifier.size(CAPTURE_BUTTON_SIZE),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color.White,
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Capture Reference",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Take Reference Photo",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * Adjust phase content - reference image with eye markers and offset controls.
 */
@Composable
private fun CalibrationAdjustContent(
    state: CalibrationState,
    snackbarHostState: SnackbarHostState,
    onEvent: (CalibrationEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var imageSize = remember { IntSize.Zero }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CONTROLS_PADDING),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onEvent(CalibrationEvent.Cancel) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }

                Text(
                    text = "Adjust Calibration",
                    style = MaterialTheme.typography.titleMedium,
                )

                // Clear calibration button (only if has existing)
                if (state.hasExistingCalibration) {
                    IconButton(onClick = { onEvent(CalibrationEvent.ClearCalibration) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Calibration",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            // Reference image with eye markers
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = CONTROLS_PADDING),
            ) {
                val imageResult = rememberImageFromPath(state.referenceImagePath)

                when (imageResult) {
                    is ImageLoadResult.Success -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { imageSize = it },
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = imageResult.bitmap,
                                contentDescription = "Calibration Reference",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.medium),
                            )

                            // Eye marker handles
                            val leftEye = state.adjustedLeftEye
                            val rightEye = state.adjustedRightEye
                            if (leftEye != null && rightEye != null && imageSize.width > 0) {
                                // Create a FaceManualAdjustment for the drag handles
                                val adjustment = FaceManualAdjustment(
                                    id = "calibration",
                                    timestamp = currentTimeMillis(),
                                    leftEyeCenter = leftEye,
                                    rightEyeCenter = rightEye,
                                )

                                FaceDragHandles(
                                    adjustment = adjustment,
                                    imageWidth = imageSize.width.toFloat(),
                                    imageHeight = imageSize.height.toFloat(),
                                    activeDragPoint = state.activeDragPoint,
                                    onDragStart = { pointType ->
                                        onEvent(CalibrationEvent.StartDrag(pointType))
                                    },
                                    onDrag = { delta ->
                                        onEvent(CalibrationEvent.UpdateDrag(delta))
                                    },
                                    onDragEnd = { onEvent(CalibrationEvent.EndDrag) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    is ImageLoadResult.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is ImageLoadResult.Error, ImageLoadResult.Empty -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Failed to load reference image",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // Offset controls
            CalibrationOffsetControls(
                offsetX = state.offsetX,
                offsetY = state.offsetY,
                onOffsetChange = { x, y -> onEvent(CalibrationEvent.UpdateOffset(x, y)) },
                modifier = Modifier.padding(CONTROLS_PADDING),
                enabled = !state.isProcessing,
            )

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CONTROLS_PADDING),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Retake button
                OutlinedButton(
                    onClick = { onEvent(CalibrationEvent.RetakeReference) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isProcessing,
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Retake")
                }

                // Save button
                Button(
                    onClick = { onEvent(CalibrationEvent.SaveCalibration) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Save")
                }
            }
        }
    }
}
