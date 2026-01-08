package com.po4yka.framelapse.ui.screens.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.po4yka.framelapse.domain.entity.ExportQuality
import com.po4yka.framelapse.domain.entity.Resolution
import com.po4yka.framelapse.domain.entity.VideoCodec
import com.po4yka.framelapse.domain.service.ShareHandler
import com.po4yka.framelapse.presentation.export.ExportEffect
import com.po4yka.framelapse.presentation.export.ExportEvent
import com.po4yka.framelapse.presentation.export.ExportState
import com.po4yka.framelapse.presentation.export.ExportViewModel
import com.po4yka.framelapse.ui.components.ExportCompleteCard
import com.po4yka.framelapse.ui.components.ExportProgressCard
import com.po4yka.framelapse.ui.components.FrameLapseTopBar
import com.po4yka.framelapse.ui.components.SettingsDropdown
import com.po4yka.framelapse.ui.components.SettingsSection
import com.po4yka.framelapse.ui.components.SettingsSlider
import com.po4yka.framelapse.ui.util.HandleEffects
import framelapse.composeapp.generated.resources.Res
import framelapse.composeapp.generated.resources.duration_minutes_seconds
import framelapse.composeapp.generated.resources.duration_seconds
import framelapse.composeapp.generated.resources.export_button
import framelapse.composeapp.generated.resources.export_codec
import framelapse.composeapp.generated.resources.export_duration
import framelapse.composeapp.generated.resources.export_duration_at_fps
import framelapse.composeapp.generated.resources.export_frame_rate
import framelapse.composeapp.generated.resources.export_quality
import framelapse.composeapp.generated.resources.export_resolution
import framelapse.composeapp.generated.resources.export_title
import framelapse.composeapp.generated.resources.export_video_settings
import framelapse.composeapp.generated.resources.fps_label
import framelapse.composeapp.generated.resources.frame_count
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val CONTENT_PADDING = 16.dp
private val SECTION_SPACING = 24.dp
private const val MIN_FPS = 1f
private const val MAX_FPS = 60f
private const val FPS_STEPS = 59

/**
 * Export screen for configuring and exporting video.
 */
@Composable
fun ExportScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExportViewModel = koinViewModel<ExportViewModel>(),
    shareHandler: ShareHandler = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    HandleEffects(viewModel.effect) { effect ->
        when (effect) {
            is ExportEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            is ExportEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            is ExportEffect.ShareVideo -> {
                scope.launch {
                    shareHandler.shareFile(effect.path, VIDEO_MIME_TYPE)
                }
            }
            is ExportEffect.OpenVideo -> {
                scope.launch {
                    shareHandler.openFile(effect.path, VIDEO_MIME_TYPE)
                }
            }
            is ExportEffect.ExportComplete -> { /* Handled by UI state */ }
        }
    }

    LaunchedEffect(projectId) {
        viewModel.onEvent(ExportEvent.Initialize(projectId))
    }

    ExportContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
private fun ExportContent(
    state: ExportState,
    snackbarHostState: SnackbarHostState,
    onEvent: (ExportEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FrameLapseTopBar(
                title = stringResource(Res.string.export_title),
                onBackClick = onNavigateBack,
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
                state.isExporting -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(CONTENT_PADDING),
                        contentAlignment = Alignment.Center,
                    ) {
                        ExportProgressCard(
                            progress = state.exportProgress,
                            onCancel = { onEvent(ExportEvent.CancelExport) },
                        )
                    }
                }

                state.exportedVideoPath != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(CONTENT_PADDING),
                        contentAlignment = Alignment.Center,
                    ) {
                        ExportCompleteCard(
                            onShare = { onEvent(ExportEvent.ShareVideo) },
                            onDismiss = { onEvent(ExportEvent.DismissResult) },
                        )
                    }
                }

                else -> {
                    ExportSettingsForm(
                        state = state,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportSettingsForm(state: ExportState, onEvent: (ExportEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(CONTENT_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        // Preview card
        ExportPreviewCard(
            frameCount = state.frameCount,
            estimatedDuration = state.estimatedDuration,
            fps = state.exportSettings.fps,
        )

        // Video settings
        SettingsSection(title = stringResource(Res.string.export_video_settings)) {
            SettingsDropdown(
                title = stringResource(Res.string.export_resolution),
                selectedValue = state.exportSettings.resolution,
                options = Resolution.entries,
                onSelect = { onEvent(ExportEvent.UpdateResolution(it)) },
                valueLabel = { it.displayName },
            )

            SettingsSlider(
                title = stringResource(Res.string.export_frame_rate),
                value = state.exportSettings.fps.toFloat(),
                onValueChange = { onEvent(ExportEvent.UpdateFps(it.toInt())) },
                valueRange = MIN_FPS..MAX_FPS,
                steps = FPS_STEPS,
                valueLabel = { stringResource(Res.string.fps_label, it.toInt()) },
            )

            SettingsDropdown(
                title = stringResource(Res.string.export_codec),
                selectedValue = state.exportSettings.codec,
                options = VideoCodec.entries,
                onSelect = { onEvent(ExportEvent.UpdateCodec(it)) },
                valueLabel = { it.displayName },
            )

            SettingsDropdown(
                title = stringResource(Res.string.export_quality),
                selectedValue = state.exportSettings.quality,
                options = ExportQuality.entries,
                onSelect = { onEvent(ExportEvent.UpdateQuality(it)) },
                valueLabel = { it.displayName },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Export button
        Button(
            onClick = { onEvent(ExportEvent.StartExport) },
            enabled = state.canExport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.export_button))
        }
    }
}

@Composable
private fun ExportPreviewCard(frameCount: Int, estimatedDuration: Float, fps: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CONTENT_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.frame_count, frameCount),
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.export_duration, formatDuration(estimatedDuration)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(Res.string.export_duration_at_fps, fps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun formatDuration(seconds: Float): String {
    val totalSeconds = seconds.toInt()
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val remainingSeconds = totalSeconds % SECONDS_PER_MINUTE
    return if (minutes > 0) {
        stringResource(Res.string.duration_minutes_seconds, minutes, remainingSeconds)
    } else {
        stringResource(Res.string.duration_seconds, remainingSeconds)
    }
}

private const val SECONDS_PER_MINUTE = 60
private const val VIDEO_MIME_TYPE = "video/mp4"
