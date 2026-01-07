package com.po4yka.framelapse.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIImage

/**
 * iOS implementation of image loading from file path.
 * Uses UIImage to load the image and converts it to ImageBitmap.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun loadImageFromPath(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val uiImage = UIImage.imageWithContentsOfFile(path) ?: return@withContext null
        val cgImage = uiImage.CGImage ?: return@withContext null

        val width = CGImageGetWidth(cgImage).toInt()
        val height = CGImageGetHeight(cgImage).toInt()

        if (width <= 0 || height <= 0) {
            return@withContext null
        }

        // Scale down if too large
        val scale = calculateScale(width, height, MAX_SIZE, MAX_SIZE)
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()

        val bytesPerRow = scaledWidth * 4
        val colorSpace = CGColorSpaceCreateDeviceRGB()

        val context = CGBitmapContextCreate(
            data = null,
            width = scaledWidth.toULong(),
            height = scaledHeight.toULong(),
            bitsPerComponent = 8u,
            bytesPerRow = bytesPerRow.toULong(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        ) ?: return@withContext null

        CGContextDrawImage(
            context,
            CGRectMake(0.0, 0.0, scaledWidth.toDouble(), scaledHeight.toDouble()),
            cgImage,
        )

        val data = CGBitmapContextGetData(context) ?: return@withContext null
        val byteArray = ByteArray(scaledWidth * scaledHeight * 4)

        for (i in byteArray.indices) {
            @Suppress("UNCHECKED_CAST")
            byteArray[i] = (data as kotlinx.cinterop.CPointer<kotlinx.cinterop.ByteVar>)[i]
        }

        val imageInfo = ImageInfo(
            width = scaledWidth,
            height = scaledHeight,
            colorType = ColorType.RGBA_8888,
            alphaType = ColorAlphaType.PREMUL,
        )

        val skiaImage = Image.makeRaster(imageInfo, byteArray, bytesPerRow)
        skiaImage.toComposeImageBitmap()
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

private fun calculateScale(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Float {
    val widthScale = if (width > maxWidth) maxWidth.toFloat() / width else 1f
    val heightScale = if (height > maxHeight) maxHeight.toFloat() / height else 1f
    return minOf(widthScale, heightScale)
}

private const val MAX_SIZE = 1920
