package com.po4yka.framelapse.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import com.po4yka.framelapse.ui.util.ImageLoadResult
import com.po4yka.framelapse.ui.util.rememberImageFromPath

/**
 * Overlay component that displays a ghost image from a previous capture.
 * Used to help users align their face for consistent timelapse frames.
 *
 * @param imagePath The file path to the ghost image, or null to hide the overlay
 * @param opacity The opacity of the ghost image (0.0 to 1.0)
 * @param modifier Modifier for the overlay
 */
@Composable
fun GhostImageOverlay(imagePath: String?, opacity: Float, modifier: Modifier = Modifier) {
    if (imagePath == null || opacity <= 0f) {
        return
    }

    val imageResult = rememberImageFromPath(imagePath)

    when (imageResult) {
        is ImageLoadResult.Success -> {
            Image(
                bitmap = imageResult.image,
                contentDescription = "Ghost image overlay for alignment",
                modifier = modifier
                    .fillMaxSize()
                    .alpha(opacity.coerceIn(0f, 1f)),
                contentScale = ContentScale.Crop,
            )
        }
        is ImageLoadResult.Loading -> {
            // Don't show anything while loading to avoid flicker
        }
        is ImageLoadResult.Error -> {
            // Don't show anything on error - silently fail
        }
    }
}
