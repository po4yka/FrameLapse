package com.po4yka.framelapse.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.ui.util.ImageLoadResult
import com.po4yka.framelapse.ui.util.rememberImageFromPath
import kotlinx.coroutines.delay

/**
 * A slideshow component that previews frames as a simulated video.
 *
 * @param frames List of frames to display in the slideshow
 * @param fps Target frames per second for the slideshow
 * @param autoPlay Whether to start playing automatically
 * @param modifier Modifier for the component
 */
@Composable
fun FrameSlideshow(frames: List<Frame>, fps: Int = 15, autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    if (frames.isEmpty()) {
        EmptySlideshow(modifier)
        return
    }

    var currentFrameIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(autoPlay) }

    // Auto-advance when playing
    LaunchedEffect(isPlaying, fps) {
        if (isPlaying) {
            while (isPlaying) {
                delay((1000L / fps))
                currentFrameIndex = (currentFrameIndex + 1) % frames.size
            }
        }
    }

    // Reset when frames change
    DisposableEffect(frames.size) {
        currentFrameIndex = 0
        onDispose { }
    }

    val currentFrame = frames.getOrNull(currentFrameIndex)

    Column(modifier = modifier) {
        // Preview area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f) // Portrait aspect ratio
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (currentFrame != null) {
                val imagePath = currentFrame.alignedPath ?: currentFrame.originalPath
                val imageResult = rememberImageFromPath(imagePath)

                AnimatedContent(
                    targetState = currentFrameIndex,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "frame_transition",
                ) { _ ->
                    when (imageResult) {
                        is ImageLoadResult.Success -> {
                            Image(
                                bitmap = imageResult.image,
                                contentDescription = "Frame ${currentFrameIndex + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        is ImageLoadResult.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = Color.White,
                            )
                        }
                        is ImageLoadResult.Error -> {
                            Text(
                                text = "Failed to load frame",
                                color = Color.White,
                            )
                        }
                    }
                }

                // Frame counter overlay
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                ) {
                    Text(
                        text = "${currentFrameIndex + 1}/${frames.size}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Timeline slider
        Slider(
            value = currentFrameIndex.toFloat(),
            onValueChange = { newValue ->
                currentFrameIndex = newValue.toInt().coerceIn(0, frames.lastIndex)
                isPlaying = false
            },
            valueRange = 0f..(frames.size - 1).toFloat().coerceAtLeast(0f),
            steps = (frames.size - 2).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Playback controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Previous frame
            IconButton(
                onClick = {
                    isPlaying = false
                    currentFrameIndex = (currentFrameIndex - 1).coerceAtLeast(0)
                },
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous frame",
                )
            }

            // Play/Pause button
            Surface(
                onClick = { isPlaying = !isPlaying },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            // Next frame
            IconButton(
                onClick = {
                    isPlaying = false
                    currentFrameIndex = (currentFrameIndex + 1).coerceAtMost(frames.lastIndex)
                },
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next frame",
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Duration estimate
        val estimatedDuration = frames.size.toFloat() / fps
        Text(
            text = "Preview duration: ${formatDuration(estimatedDuration)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun EmptySlideshow(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No frames to preview",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Formats duration in seconds to a readable string (e.g., "1m 30s" or "45s").
 */
private fun formatDuration(seconds: Float): String {
    val totalSeconds = seconds.toInt()
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60

    return if (minutes > 0) {
        "${minutes}m ${remainingSeconds}s"
    } else {
        "${remainingSeconds}s"
    }
}
