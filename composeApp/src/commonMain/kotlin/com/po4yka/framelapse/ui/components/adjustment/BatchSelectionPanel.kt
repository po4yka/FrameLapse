package com.po4yka.framelapse.ui.components.adjustment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.usecase.adjustment.BatchApplyAdjustmentUseCase
import com.po4yka.framelapse.domain.usecase.adjustment.SuggestSimilarFramesUseCase
import com.po4yka.framelapse.ui.util.ImageLoadResult
import com.po4yka.framelapse.ui.util.rememberImageFromPath
import androidx.compose.foundation.Image as ComposeImage

/**
 * Panel for batch selection and application of manual adjustments.
 *
 * Shows suggested frames, allows manual selection, and applies adjustments.
 *
 * @param frames All frames available for selection
 * @param selectedFrameIds Currently selected frame IDs
 * @param suggestions Suggested frames from similarity analysis
 * @param isApplying Whether batch apply is in progress
 * @param progress Current progress (0.0 to 1.0) during batch apply
 * @param onToggleFrame Called when a frame's selection is toggled
 * @param onSelectAllSuggested Called to select all suggested frames
 * @param onClearSelection Called to clear all selections
 * @param onApply Called to apply adjustments with selected strategy
 * @param onClose Called to close the panel
 * @param modifier Additional modifier
 */
@Composable
fun BatchSelectionPanel(
    frames: List<Frame>,
    selectedFrameIds: Set<String>,
    suggestions: List<SuggestSimilarFramesUseCase.FrameSuggestion>,
    isApplying: Boolean,
    progress: Float,
    onToggleFrame: (String) -> Unit,
    onSelectAllSuggested: () -> Unit,
    onClearSelection: () -> Unit,
    onApply: (BatchApplyAdjustmentUseCase.TransferStrategy) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header
            BatchPanelHeader(
                selectedCount = selectedFrameIds.size,
                onClose = onClose,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress indicator during batch apply
            AnimatedVisibility(
                visible = isApplying,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(
                        text = "Applying adjustments...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (!isApplying) {
                // Suggestions section
                if (suggestions.isNotEmpty()) {
                    SuggestionsSection(
                        suggestions = suggestions,
                        selectedFrameIds = selectedFrameIds,
                        onToggleFrame = onToggleFrame,
                        onSelectAll = onSelectAllSuggested,
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // All frames grid
                Text(
                    text = "All Frames",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                ) {
                    items(frames) { frame ->
                        FrameSelectionItem(
                            frame = frame,
                            isSelected = frame.id in selectedFrameIds,
                            suggestion = suggestions.find { it.frame.id == frame.id },
                            onClick = { onToggleFrame(frame.id) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                BatchActionButtons(
                    selectedCount = selectedFrameIds.size,
                    onClearSelection = onClearSelection,
                    onApply = onApply,
                )
            }
        }
    }
}

/**
 * Header with title, count, and close button.
 */
@Composable
private fun BatchPanelHeader(selectedCount: Int, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Batch Apply",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "$selectedCount frames selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
            )
        }
    }
}

/**
 * Section showing suggested frames based on similarity.
 */
@Composable
private fun SuggestionsSection(
    suggestions: List<SuggestSimilarFramesUseCase.FrameSuggestion>,
    selectedFrameIds: Set<String>,
    onToggleFrame: (String) -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Suggested Frames",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedButton(
                onClick = onSelectAll,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.SelectAll,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Select All", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Group suggestions by reason
        val groupedSuggestions = suggestions.groupBy { it.reason }

        groupedSuggestions.forEach { (reason, group) ->
            SuggestionGroup(
                reason = reason,
                suggestions = group,
                selectedFrameIds = selectedFrameIds,
                onToggleFrame = onToggleFrame,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Group of suggestions with a reason label.
 */
@Composable
private fun SuggestionGroup(
    reason: SuggestSimilarFramesUseCase.SuggestionReason,
    suggestions: List<SuggestSimilarFramesUseCase.FrameSuggestion>,
    selectedFrameIds: Set<String>,
    onToggleFrame: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Reason label
        val (label, color) = when (reason) {
            SuggestSimilarFramesUseCase.SuggestionReason.SIMILAR_LANDMARKS ->
                "Similar Position" to MaterialTheme.colorScheme.primary

            SuggestSimilarFramesUseCase.SuggestionReason.LOW_CONFIDENCE ->
                "Low Confidence" to MaterialTheme.colorScheme.tertiary

            SuggestSimilarFramesUseCase.SuggestionReason.NO_DETECTION ->
                "No Detection" to MaterialTheme.colorScheme.error
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            if (reason == SuggestSimilarFramesUseCase.SuggestionReason.NO_DETECTION) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }

        // Horizontal list of suggestions
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.take(5).forEach { suggestion ->
                FrameSelectionItem(
                    frame = suggestion.frame,
                    isSelected = suggestion.frame.id in selectedFrameIds,
                    suggestion = suggestion,
                    onClick = { onToggleFrame(suggestion.frame.id) },
                    modifier = Modifier.size(60.dp),
                )
            }
            if (suggestions.size > 5) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+${suggestions.size - 5}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/**
 * Individual frame selection item.
 */
@Composable
private fun FrameSelectionItem(
    frame: Frame,
    isSelected: Boolean,
    suggestion: SuggestSimilarFramesUseCase.FrameSuggestion?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageResult = rememberImageFromPath(frame.alignedPath ?: frame.originalPath)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        // Thumbnail
        when (imageResult) {
            is ImageLoadResult.Success -> {
                ComposeImage(
                    bitmap = imageResult.image,
                    contentDescription = "Frame thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }

        // Selection checkmark
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Similarity score badge
        suggestion?.let {
            if (it.reason == SuggestSimilarFramesUseCase.SuggestionReason.SIMILAR_LANDMARKS) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "${(it.similarityScore * 100).toInt()}%",
                        fontSize = 8.sp,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Action buttons for batch apply.
 */
@Composable
private fun BatchActionButtons(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onApply: (BatchApplyAdjustmentUseCase.TransferStrategy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Strategy selector
        Text(
            text = "Transfer Strategy",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BatchApplyAdjustmentUseCase.TransferStrategy.entries.forEach { strategy ->
                FilterChip(
                    selected = false,
                    onClick = { onApply(strategy) },
                    label = {
                        Text(
                            text = when (strategy) {
                                BatchApplyAdjustmentUseCase.TransferStrategy.EXACT -> "Exact"
                                BatchApplyAdjustmentUseCase.TransferStrategy.RELATIVE -> "Relative"
                                BatchApplyAdjustmentUseCase.TransferStrategy.SCALED -> "Scaled"
                            },
                            fontSize = 11.sp,
                        )
                    },
                    enabled = selectedCount > 0,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Strategy descriptions
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = """
                    |Exact: Copy positions directly
                    |Relative: Adjust based on detected landmarks
                    |Scaled: Adjust for size differences
                """.trimMargin(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Clear selection button
        if (selectedCount > 0) {
            OutlinedButton(
                onClick = onClearSelection,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear Selection")
            }
        }
    }
}
