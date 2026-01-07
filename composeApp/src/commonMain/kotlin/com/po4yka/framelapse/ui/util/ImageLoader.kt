package com.po4yka.framelapse.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Result of loading an image from a file path.
 */
sealed class ImageLoadResult {
    data class Success(val image: ImageBitmap) : ImageLoadResult()
    data class Error(val message: String) : ImageLoadResult()
    data object Loading : ImageLoadResult()
}

/**
 * Loads an image from a file path and returns it as an ImageBitmap.
 * This is a platform-specific implementation.
 *
 * @param path The file path to the image
 * @return The loaded ImageBitmap or null if loading fails
 */
expect suspend fun loadImageFromPath(path: String): ImageBitmap?

/**
 * Remembers and loads an image from a file path asynchronously.
 * Returns the current loading state.
 *
 * @param path The file path to the image, or null to clear the image
 * @return The current image load result
 */
@Composable
expect fun rememberImageFromPath(path: String?): ImageLoadResult
