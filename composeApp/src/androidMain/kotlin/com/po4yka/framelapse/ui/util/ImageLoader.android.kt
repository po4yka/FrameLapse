package com.po4yka.framelapse.ui.util

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of image loading from file path.
 * Uses BitmapFactory to decode the image file.
 */
actual suspend fun loadImageFromPath(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val file = File(path)
        if (!file.exists()) {
            return@withContext null
        }

        val options = BitmapFactory.Options().apply {
            // First, decode just the bounds to check the image size
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        // Calculate sample size to avoid loading huge images
        options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT)
        options.inJustDecodeBounds = false

        val bitmap = BitmapFactory.decodeFile(path, options)
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

/**
 * Composable that remembers and loads an image from a file path.
 */
@Composable
actual fun rememberImageFromPath(path: String?): ImageLoadResult {
    var result by remember { mutableStateOf<ImageLoadResult>(ImageLoadResult.Loading) }

    LaunchedEffect(path) {
        if (path == null) {
            result = ImageLoadResult.Error("No path provided")
            return@LaunchedEffect
        }

        result = ImageLoadResult.Loading
        val image = loadImageFromPath(path)
        result = if (image != null) {
            ImageLoadResult.Success(image)
        } else {
            ImageLoadResult.Error("Failed to load image from: $path")
        }
    }

    return result
}

/**
 * Calculate appropriate sample size to fit within the target dimensions.
 */
private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height, width) = options.outHeight to options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

private const val MAX_WIDTH = 1920
private const val MAX_HEIGHT = 1920
