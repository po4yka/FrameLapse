package com.po4yka.framelapse.ui.components.adjustment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.po4yka.framelapse.presentation.adjustment.ComparisonMode
import com.po4yka.framelapse.ui.util.ImageLoadResult
import com.po4yka.framelapse.ui.util.rememberImageFromPath

/**
 * Preview comparison component for before/after comparison.
 *
 * Supports three modes:
 * - SLIDER: Horizontal slider to reveal before/after
 * - SIDE_BY_SIDE: Split screen side by side
 * - TOGGLE: Tap to toggle between before and after
 *
 * @param originalPath Path to the original image
 * @param previewPath Path to the preview image (after adjustment)
 * @param mode Comparison mode
 * @param onModeChange Called when mode changes
 * @param modifier Additional modifier
 */
@Composable
fun PreviewComparison(
    originalPath: String,
    previewPath: String?,
    mode: ComparisonMode,
    onModeChange: (ComparisonMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val originalResult = rememberImageFromPath(originalPath)
    val previewResult = rememberImageFromPath(previewPath)

    Column(modifier = modifier) {
        // Mode selector
        ComparisonModeSelector(
            currentMode = mode,
            onModeChange = onModeChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        )

        // Comparison content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val originalImage = (originalResult as? ImageLoadResult.Success)?.image
            val previewImage = (previewResult as? ImageLoadResult.Success)?.image

            if (originalImage != null) {
                when (mode) {
                    ComparisonMode.SLIDER -> {
                        SliderComparison(
                            originalImage = originalImage,
                            previewImage = previewImage,
                        )
                    }
                    ComparisonMode.SIDE_BY_SIDE -> {
                        SideBySideComparison(
                            originalImage = originalImage,
                            previewImage = previewImage,
                        )
                    }
                    ComparisonMode.TOGGLE -> {
                        ToggleComparison(
                            originalImage = originalImage,
                            previewImage = previewImage,
                        )
                    }
                }
            } else {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Mode selector chips for comparison mode.
 */
@Composable
private fun ComparisonModeSelector(
    currentMode: ComparisonMode,
    onModeChange: (ComparisonMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        ComparisonMode.entries.forEach { mode ->
            FilterChip(
                selected = currentMode == mode,
                onClick = { onModeChange(mode) },
                label = {
                    Text(
                        text = when (mode) {
                            ComparisonMode.SLIDER -> "Slider"
                            ComparisonMode.SIDE_BY_SIDE -> "Side by Side"
                            ComparisonMode.TOGGLE -> "Toggle"
                        },
                        fontSize = 12.sp,
                    )
                },
                leadingIcon = if (currentMode == mode) {
                    {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = null,
                            modifier = Modifier.height(16.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Slider comparison - drag horizontal slider to reveal before/after.
 */
@Composable
private fun SliderComparison(originalImage: ImageBitmap, previewImage: ImageBitmap?, modifier: Modifier = Modifier) {
    var sliderPosition by remember { mutableFloatStateOf(0.5f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .clipToBounds()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val x = change.position.x
                    sliderPosition = (x / containerSize.width).coerceIn(0f, 1f)
                }
            },
    ) {
        // Preview image (full size, behind)
        if (previewImage != null) {
            Image(
                bitmap = previewImage,
                contentDescription = "Preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        // Original image (clipped to slider position)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(sliderPosition)
                .clipToBounds(),
        ) {
            Image(
                bitmap = originalImage,
                contentDescription = "Original",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        // Slider line
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .padding(start = (containerSize.width * sliderPosition).dp - 2.dp)
                .background(Color.White.copy(alpha = 0.8f)),
        )

        // Labels
        LabelOverlay(
            leftLabel = "Original",
            rightLabel = "Preview",
            sliderPosition = sliderPosition,
        )
    }
}

/**
 * Side by side comparison - split screen.
 */
@Composable
private fun SideBySideComparison(
    originalImage: ImageBitmap,
    previewImage: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Original
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Image(
                bitmap = originalImage,
                contentDescription = "Original",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            ImageLabel(
                text = "Original",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
        }

        // Preview
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            if (previewImage != null) {
                Image(
                    bitmap = previewImage,
                    contentDescription = "Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Preview not available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ImageLabel(
                text = "Preview",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            )
        }
    }
}

/**
 * Toggle comparison - tap to switch between images.
 */
@Composable
private fun ToggleComparison(originalImage: ImageBitmap, previewImage: ImageBitmap?, modifier: Modifier = Modifier) {
    var showOriginal by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { showOriginal = !showOriginal },
    ) {
        AnimatedVisibility(
            visible = showOriginal,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Image(
                bitmap = originalImage,
                contentDescription = "Original",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        AnimatedVisibility(
            visible = !showOriginal && previewImage != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            if (previewImage != null) {
                Image(
                    bitmap = previewImage,
                    contentDescription = "Preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        // Label
        ImageLabel(
            text = if (showOriginal) "Original (tap to toggle)" else "Preview (tap to toggle)",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp),
        )
    }
}

/**
 * Label overlay for slider mode.
 */
@Composable
private fun LabelOverlay(leftLabel: String, rightLabel: String, sliderPosition: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        // Left label (visible when slider > 0.2)
        AnimatedVisibility(
            visible = sliderPosition > 0.2f,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ImageLabel(text = leftLabel)
        }

        // Right label (visible when slider < 0.8)
        AnimatedVisibility(
            visible = sliderPosition < 0.8f,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ImageLabel(text = rightLabel)
        }
    }
}

/**
 * Small label badge for image identification.
 */
@Composable
private fun ImageLabel(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = Color.Black.copy(alpha = 0.6f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
