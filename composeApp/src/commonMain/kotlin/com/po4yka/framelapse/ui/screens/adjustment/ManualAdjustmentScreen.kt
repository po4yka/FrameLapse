package com.po4yka.framelapse.ui.screens.adjustment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.po4yka.framelapse.presentation.adjustment.ManualAdjustmentEffect
import com.po4yka.framelapse.presentation.adjustment.ManualAdjustmentEvent
import com.po4yka.framelapse.presentation.adjustment.ManualAdjustmentViewModel
import com.po4yka.framelapse.ui.components.adjustment.AdjustmentOverlay
import com.po4yka.framelapse.ui.components.adjustment.BatchSelectionPanel
import com.po4yka.framelapse.ui.components.adjustment.PreviewComparison
import com.po4yka.framelapse.ui.util.HandleEffects
import com.po4yka.framelapse.ui.util.ImageLoadResult
import com.po4yka.framelapse.ui.util.rememberImageFromPath
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Manual adjustment screen for refining stabilization landmarks.
 *
 * @param frameId The ID of the frame to adjust
 * @param projectId The ID of the project containing the frame
 * @param onNavigateBack Called when user wants to go back
 * @param onNavigateToFrame Called to navigate to a different frame
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAdjustmentScreen(
    frameId: String,
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToFrame: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ManualAdjustmentViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showBatchResultDialog by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showComparisonSheet by remember { mutableStateOf(false) }

    // Initialize on first composition
    LaunchedEffect(frameId, projectId) {
        viewModel.onEvent(ManualAdjustmentEvent.Initialize(frameId, projectId))
    }

    // Handle effects
    HandleEffects(viewModel.effect) { effect ->
        when (effect) {
            is ManualAdjustmentEffect.NavigateBack -> onNavigateBack()
            is ManualAdjustmentEffect.ShowError -> {
                scope.launch {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
            is ManualAdjustmentEffect.ShowSuccess -> {
                scope.launch {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
            is ManualAdjustmentEffect.NavigateToFrame -> onNavigateToFrame(effect.frameId)
            is ManualAdjustmentEffect.ShowDiscardConfirmation -> showDiscardDialog = true
            is ManualAdjustmentEffect.ShowBatchResult -> {
                showBatchResultDialog = effect.successCount to effect.failedCount
            }
            is ManualAdjustmentEffect.HapticFeedback -> {
                // Platform-specific haptic feedback would be handled here
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ManualAdjustmentTopBar(
                title = "Manual Adjustment",
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                undoDescription = state.undoDescription,
                redoDescription = state.redoDescription,
                onBack = { viewModel.onEvent(ManualAdjustmentEvent.DiscardChanges) },
                onUndo = { viewModel.onEvent(ManualAdjustmentEvent.Undo) },
                onRedo = { viewModel.onEvent(ManualAdjustmentEvent.Redo) },
            )
        },
        bottomBar = {
            ManualAdjustmentBottomBar(
                canSave = state.currentAdjustment != null,
                isSaving = state.isSaving,
                onSave = { viewModel.onEvent(ManualAdjustmentEvent.SaveAdjustment) },
                onRevert = { viewModel.onEvent(ManualAdjustmentEvent.RevertToAutoDetected) },
                onBatchMode = { viewModel.onEvent(ManualAdjustmentEvent.EnterBatchMode) },
                onCompare = { showComparisonSheet = true },
                onZoomIn = { viewModel.onEvent(ManualAdjustmentEvent.SetZoom(state.zoomLevel + 0.5f)) },
                onZoomOut = { viewModel.onEvent(ManualAdjustmentEvent.SetZoom(state.zoomLevel - 0.5f)) },
                onPrevFrame = { viewModel.onEvent(ManualAdjustmentEvent.NavigateToPreviousFrame) },
                onNextFrame = { viewModel.onEvent(ManualAdjustmentEvent.NavigateToNextFrame) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.frame != null -> {
                    AdjustmentContent(
                        imagePath = state.frame!!.originalPath,
                        adjustment = state.currentAdjustment,
                        contentType = state.contentType,
                        activeDragPoint = state.activeDragPoint,
                        zoomLevel = state.zoomLevel,
                        panOffset = state.panOffset,
                        onDragStart = { viewModel.onEvent(ManualAdjustmentEvent.StartDrag(it)) },
                        onDrag = { viewModel.onEvent(ManualAdjustmentEvent.UpdateDrag(it)) },
                        onDragEnd = { viewModel.onEvent(ManualAdjustmentEvent.EndDrag) },
                        onZoomChange = { viewModel.onEvent(ManualAdjustmentEvent.SetZoom(it)) },
                        onPanChange = { viewModel.onEvent(ManualAdjustmentEvent.SetPanOffset(it)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Preview generation indicator
            if (state.isGeneratingPreview) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }

            // Batch mode panel
            AnimatedVisibility(
                visible = state.isBatchMode,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BatchSelectionPanel(
                    frames = state.allFrames,
                    selectedFrameIds = state.selectedFrameIds,
                    suggestions = state.suggestedFrames,
                    isApplying = state.isBatchApplying,
                    progress = state.batchProgress,
                    onToggleFrame = { viewModel.onEvent(ManualAdjustmentEvent.ToggleFrameForBatch(it)) },
                    onSelectAllSuggested = { viewModel.onEvent(ManualAdjustmentEvent.SelectAllSuggested) },
                    onClearSelection = { viewModel.onEvent(ManualAdjustmentEvent.ClearBatchSelection) },
                    onApply = { viewModel.onEvent(ManualAdjustmentEvent.ApplyToBatch(it)) },
                    onClose = { viewModel.onEvent(ManualAdjustmentEvent.ExitBatchMode) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Comparison bottom sheet
    if (showComparisonSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showComparisonSheet = false },
            sheetState = sheetState,
        ) {
            state.frame?.let { frame ->
                PreviewComparison(
                    originalPath = frame.originalPath,
                    previewPath = state.previewImagePath,
                    mode = state.comparisonMode,
                    onModeChange = { viewModel.onEvent(ManualAdjustmentEvent.SetComparisonMode(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .padding(16.dp),
                )
            }
        }
    }

    // Discard confirmation dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.confirmDiscard()
                    },
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Batch result dialog
    showBatchResultDialog?.let { (success, failed) ->
        AlertDialog(
            onDismissRequest = { showBatchResultDialog = null },
            title = { Text("Batch Apply Complete") },
            text = {
                Text(
                    if (failed == 0) {
                        "Successfully applied adjustments to $success frames."
                    } else {
                        "Applied to $success frames. $failed frames failed."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { showBatchResultDialog = null }) {
                    Text("OK")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualAdjustmentTopBar(
    title: String,
    canUndo: Boolean,
    canRedo: Boolean,
    undoDescription: String?,
    redoDescription: String?,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        actions = {
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = undoDescription ?: "Undo",
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = redoDescription ?: "Redo",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun ManualAdjustmentBottomBar(
    canSave: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    onRevert: () -> Unit,
    onBatchMode: () -> Unit,
    onCompare: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onPrevFrame: () -> Unit,
    onNextFrame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomAppBar(modifier = modifier) {
        // Navigation between frames
        IconButton(onClick = onPrevFrame) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                contentDescription = "Previous frame",
            )
        }
        IconButton(onClick = onNextFrame) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = "Next frame",
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Zoom controls
        IconButton(onClick = onZoomOut) {
            Icon(
                imageVector = Icons.Default.ZoomOut,
                contentDescription = "Zoom out",
            )
        }
        IconButton(onClick = onZoomIn) {
            Icon(
                imageVector = Icons.Default.ZoomIn,
                contentDescription = "Zoom in",
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Compare button
        IconButton(onClick = onCompare) {
            Icon(
                imageVector = Icons.Default.Compare,
                contentDescription = "Compare",
            )
        }

        // Batch mode button
        IconButton(onClick = onBatchMode) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = "Batch apply",
            )
        }

        // Revert button
        IconButton(onClick = onRevert) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Revert to auto-detected",
            )
        }

        // Save button
        IconButton(
            onClick = onSave,
            enabled = canSave && !isSaving,
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(8.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save",
                )
            }
        }
    }
}

@Composable
private fun AdjustmentContent(
    imagePath: String,
    adjustment: com.po4yka.framelapse.domain.entity.ManualAdjustment?,
    contentType: com.po4yka.framelapse.domain.entity.ContentType,
    activeDragPoint: com.po4yka.framelapse.domain.entity.AdjustmentPointType?,
    zoomLevel: Float,
    panOffset: Offset,
    onDragStart: (com.po4yka.framelapse.domain.entity.AdjustmentPointType) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onPanChange: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageResult = rememberImageFromPath(imagePath)
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onZoomChange(zoomLevel * zoom)
                    onPanChange(Offset(panOffset.x + pan.x, panOffset.y + pan.y))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (imageResult) {
            is ImageLoadResult.Success -> {
                val image = imageResult.image
                val imageWidth = containerSize.width.toFloat()
                val imageHeight = containerSize.height.toFloat()

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = zoomLevel
                            scaleY = zoomLevel
                            translationX = panOffset.x
                            translationY = panOffset.y
                        },
                ) {
                    // Base image
                    Image(
                        bitmap = image,
                        contentDescription = "Frame image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )

                    // Adjustment overlay with drag handles
                    AdjustmentOverlay(
                        contentType = contentType,
                        adjustment = adjustment,
                        activeDragPoint = activeDragPoint,
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        imageWidth = imageWidth,
                        imageHeight = imageHeight,
                    )
                }
            }
            is ImageLoadResult.Loading -> {
                CircularProgressIndicator()
            }
            is ImageLoadResult.Error -> {
                Text(
                    text = "Failed to load image",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
