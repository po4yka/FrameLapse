package com.po4yka.framelapse.ui.screens.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.po4yka.framelapse.presentation.gallery.GalleryEffect
import com.po4yka.framelapse.presentation.gallery.GalleryEvent
import com.po4yka.framelapse.presentation.gallery.GalleryState
import com.po4yka.framelapse.presentation.gallery.GalleryViewModel
import com.po4yka.framelapse.ui.components.EmptyState
import com.po4yka.framelapse.ui.components.FrameGridItem
import com.po4yka.framelapse.ui.components.FrameLapseTopBar
import com.po4yka.framelapse.ui.components.ImportPreviewSheet
import com.po4yka.framelapse.ui.components.ImportProgressDialog
import com.po4yka.framelapse.ui.components.ImportResultDialog
import com.po4yka.framelapse.ui.components.LoadingIndicator
import com.po4yka.framelapse.ui.components.SelectionTopBar
import com.po4yka.framelapse.ui.util.HandleEffects
import com.po4yka.framelapse.ui.util.PhotoPickerResult
import com.po4yka.framelapse.ui.util.rememberPhotoPickerLauncher
import framelapse.composeapp.generated.resources.Res
import framelapse.composeapp.generated.resources.cd_capture_photo
import framelapse.composeapp.generated.resources.cd_export_video
import framelapse.composeapp.generated.resources.cd_import_photos
import framelapse.composeapp.generated.resources.frame_count
import framelapse.composeapp.generated.resources.gallery_capture_button
import framelapse.composeapp.generated.resources.gallery_empty_description
import framelapse.composeapp.generated.resources.gallery_empty_title
import framelapse.composeapp.generated.resources.gallery_export_button
import framelapse.composeapp.generated.resources.gallery_title
import framelapse.composeapp.generated.resources.nav_statistics
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val GRID_SPACING = 4.dp
private val CONTENT_PADDING = 8.dp
private const val GRID_COLUMNS = 3

/**
 * Gallery screen for viewing and managing frames.
 */
@Composable
fun GalleryScreen(
    projectId: String,
    onNavigateToCapture: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateBack: () -> Unit,
    onShowDeleteDialog: (Int) -> Unit = {},
    onShowFilterSheet: () -> Unit = {},
    onNavigateToManualAdjustment: (frameId: String) -> Unit = {},
    onNavigateToStatistics: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = koinViewModel<GalleryViewModel>(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Import result dialog state
    var importResultState by remember { mutableStateOf<ImportResultState?>(null) }

    // Photo picker launcher
    val photoPickerLauncher = rememberPhotoPickerLauncher { result ->
        when (result) {
            is PhotoPickerResult.Success -> {
                viewModel.onPhotosSelected(result.uris)
            }
            is PhotoPickerResult.Error -> {
                // Error will be shown via snackbar
            }
            is PhotoPickerResult.Cancelled -> {
                // User cancelled, no action needed
            }
        }
    }

    HandleEffects(viewModel.effect) { effect ->
        when (effect) {
            is GalleryEffect.NavigateToCapture -> onNavigateToCapture()
            is GalleryEffect.NavigateToExport -> onNavigateToExport()
            is GalleryEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            is GalleryEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            is GalleryEffect.OpenPhotoPicker -> photoPickerLauncher.launch(maxItems = 0)
            is GalleryEffect.ShowDeleteConfirmation -> onShowDeleteDialog(effect.count)
            is GalleryEffect.NavigateToManualAdjustment -> onNavigateToManualAdjustment(effect.frameId)
            is GalleryEffect.ShowImportResult -> {
                importResultState = ImportResultState(
                    successCount = effect.successCount,
                    failedCount = effect.failedCount,
                    thumbnailPaths = effect.thumbnailPaths,
                )
            }
        }
    }

    LaunchedEffect(projectId) {
        viewModel.onEvent(GalleryEvent.Initialize(projectId))
    }

    GalleryContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        onNavigateToStatistics = onNavigateToStatistics,
        modifier = modifier,
    )

    // Import preview sheet
    if (state.showImportPreview && state.pendingImportPaths.isNotEmpty()) {
        ImportPreviewSheet(
            photoPaths = state.pendingImportPaths,
            onConfirm = { viewModel.onEvent(GalleryEvent.ConfirmImport) },
            onDismiss = { viewModel.onEvent(GalleryEvent.DismissImportPreview) },
        )
    }

    // Import progress dialog
    val importProgress = state.importProgress
    if (state.isImporting && importProgress != null) {
        ImportProgressDialog(
            progress = importProgress,
            onCancel = { viewModel.onEvent(GalleryEvent.CancelImport) },
        )
    }

    // Import result dialog
    importResultState?.let { result ->
        ImportResultDialog(
            successCount = result.successCount,
            failedCount = result.failedCount,
            thumbnailPaths = result.thumbnailPaths,
            onDismiss = {
                importResultState = null
                viewModel.onEvent(GalleryEvent.DismissImportResult)
            },
            onRetry = {
                importResultState = null
                viewModel.onEvent(GalleryEvent.RetryFailedImports)
            },
        )
    }
}

/**
 * State for the import result dialog.
 */
private data class ImportResultState(val successCount: Int, val failedCount: Int, val thumbnailPaths: List<String>)

@Composable
private fun GalleryContent(
    state: GalleryState,
    snackbarHostState: SnackbarHostState,
    onEvent: (GalleryEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (state.isSelectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedCount,
                    onClearSelection = { onEvent(GalleryEvent.ClearSelection) },
                    onSelectAll = { onEvent(GalleryEvent.SelectAll) },
                    onDelete = { onEvent(GalleryEvent.DeleteSelected) },
                )
            } else {
                FrameLapseTopBar(
                    title = state.project?.name ?: stringResource(Res.string.gallery_title),
                    onBackClick = onNavigateBack,
                    actions = {
                        IconButton(onClick = onNavigateToStatistics) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = stringResource(Res.string.nav_statistics),
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!state.isSelectionMode) {
                FloatingActionButton(
                    onClick = { onEvent(GalleryEvent.ImportPhotos) },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.cd_import_photos),
                    )
                }
            }
        },
        bottomBar = {
            if (!state.isSelectionMode && state.frames.isNotEmpty()) {
                GalleryBottomBar(
                    frameCount = state.frames.size,
                    onCaptureClick = { onEvent(GalleryEvent.NavigateToCapture) },
                    onExportClick = { onEvent(GalleryEvent.NavigateToExport) },
                )
            }
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
                    LoadingIndicator()
                }

                state.frames.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.PhotoLibrary,
                        title = stringResource(Res.string.gallery_empty_title),
                        description = stringResource(Res.string.gallery_empty_description),
                        actionLabel = stringResource(Res.string.gallery_capture_button),
                        onAction = { onEvent(GalleryEvent.NavigateToCapture) },
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(GRID_COLUMNS),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(CONTENT_PADDING),
                        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
                        verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
                    ) {
                        items(
                            items = state.frames,
                            key = { it.id },
                        ) { frame ->
                            FrameGridItem(
                                frame = frame,
                                isSelected = frame.id in state.selectedFrameIds,
                                onClick = {
                                    if (state.isSelectionMode) {
                                        onEvent(GalleryEvent.ToggleFrameSelection(frame.id))
                                    } else {
                                        onEvent(GalleryEvent.OpenManualAdjustment(frame.id))
                                    }
                                },
                                onLongClick = {
                                    onEvent(GalleryEvent.ToggleFrameSelection(frame.id))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryBottomBar(
    frameCount: Int,
    onCaptureClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.frame_count, frameCount),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCaptureClick,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = stringResource(Res.string.cd_capture_photo),
                )
                Spacer(modifier = Modifier.padding(4.dp))
                Text(stringResource(Res.string.gallery_capture_button))
            }
            Button(
                onClick = onExportClick,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = stringResource(Res.string.cd_export_video),
                )
                Spacer(modifier = Modifier.padding(4.dp))
                Text(stringResource(Res.string.gallery_export_button))
            }
        }
    }
}
