package com.po4yka.framelapse.ui.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi
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
 *
 * Uses ImageDecoder (Android 9+) for modern format support (HEIC, AVIF),
 * with fallback to BitmapFactory for older Android versions.
 */
actual suspend fun loadImageFromPath(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val file = File(path)
        if (!file.exists()) {
            return@withContext null
        }

        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Use ImageDecoder for Android 9+ (supports HEIC, AVIF)
            loadWithImageDecoder(file)
        } else {
            // Fallback to BitmapFactory for older Android
            loadWithBitmapFactory(path)
        }

        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

/**
 * Loads an image using ImageDecoder (Android 9+).
 * Supports modern formats like HEIC and AVIF with hardware codec support.
 */
@RequiresApi(Build.VERSION_CODES.P)
private fun loadWithImageDecoder(file: File): Bitmap? = try {
    val source = ImageDecoder.createSource(file)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        // Apply size limit to avoid OOM
        val (width, height) = info.size.width to info.size.height
        if (width > MAX_WIDTH || height > MAX_HEIGHT) {
            val scale = minOf(MAX_WIDTH.toFloat() / width, MAX_HEIGHT.toFloat() / height)
            val targetWidth = (width * scale).toInt()
            val targetHeight = (height * scale).toInt()
            decoder.setTargetSize(targetWidth, targetHeight)
        }
        // Use software rendering for compatibility
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
} catch (e: Exception) {
    // If ImageDecoder fails (e.g., codec unavailable), try BitmapFactory
    loadWithBitmapFactory(file.absolutePath)
}

/**
 * Loads an image using BitmapFactory.
 * Works on all Android versions but limited format support.
 */
private fun loadWithBitmapFactory(path: String): Bitmap? {
    val options = BitmapFactory.Options().apply {
        // First, decode just the bounds to check the image size
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(path, options)

    // Calculate sample size to avoid loading huge images
    options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT)
    options.inJustDecodeBounds = false

    return BitmapFactory.decodeFile(path, options)
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
