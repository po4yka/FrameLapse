package com.po4yka.framelapse.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

internal class AndroidBitmapCodec {
    /**
     * Loads an image from the given path.
     *
     * Uses ImageDecoder (Android 9+) for modern format support (HEIC, AVIF),
     * with fallback to BitmapFactory for older Android versions.
     */
    fun loadImage(path: String): Result<ImageData> {
        return try {
            val file = File(path)
            if (!file.exists()) {
                return Result.Error(
                    IllegalArgumentException("File not found: $path"),
                    "File not found",
                )
            }

            val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Use ImageDecoder for Android 9+ (supports HEIC, AVIF)
                loadWithImageDecoder(file)
            } else {
                // Fallback to BitmapFactory for older Android
                loadWithBitmapFactory(path)
            } ?: return Result.Error(
                IllegalArgumentException("Failed to decode image: $path"),
                "Failed to decode image",
            )

            val bitmap = correctBitmapOrientation(originalBitmap, path)
            val bytes = bitmapToByteArray(bitmap)

            if (bitmap != originalBitmap) {
                originalBitmap.recycle()
            }

            Result.Success(
                ImageData(
                    width = bitmap.width,
                    height = bitmap.height,
                    bytes = bytes,
                ),
            )
        } catch (e: Exception) {
            Result.Error(e, "Failed to load image: ${e.message}")
        }
    }

    /**
     * Loads an image using ImageDecoder (Android 9+).
     * Supports modern formats like HEIC and AVIF with hardware codec support.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun loadWithImageDecoder(file: File): Bitmap? = try {
        val source = ImageDecoder.createSource(file)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            // Use software allocator for mutable bitmaps and compatibility
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // Request ARGB_8888 for consistency with the rest of the pipeline
            decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
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
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, options)
    }

    fun saveImage(data: ImageData, path: String, quality: Int): Result<String> {
        return try {
            val bitmap = byteArrayToBitmap(data.bytes)
                ?: return Result.Error(
                    IllegalStateException("Failed to create bitmap from data"),
                    "Failed to create bitmap",
                )

            val file = File(path)
            file.parentFile?.mkdirs()

            FileOutputStream(file).use { outputStream ->
                val format = if (path.endsWith(".png", ignoreCase = true)) {
                    Bitmap.CompressFormat.PNG
                } else {
                    Bitmap.CompressFormat.JPEG
                }
                val validQuality = quality.coerceIn(0, 100)
                bitmap.compress(format, validQuality, outputStream)
            }

            bitmap.recycle()
            Result.Success(path)
        } catch (e: Exception) {
            Result.Error(e, "Failed to save image: ${e.message}")
        }
    }

    fun getImageDimensions(path: String): Result<Pair<Int, Int>> {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return Result.Error(
                    IllegalArgumentException("Failed to read image dimensions: $path"),
                    "Failed to read image dimensions",
                )
            }

            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )

            val (width, height) = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSPOSE,
                ExifInterface.ORIENTATION_TRANSVERSE,
                -> Pair(options.outHeight, options.outWidth)
                else -> Pair(options.outWidth, options.outHeight)
            }

            Result.Success(Pair(width, height))
        } catch (e: Exception) {
            Result.Error(e, "Failed to get image dimensions: ${e.message}")
        }
    }

    fun byteArrayToBitmap(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun correctBitmapOrientation(bitmap: Bitmap, path: String): Bitmap {
        return try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )

            val matrix = android.graphics.Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.preScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(-90f)
                    matrix.preScale(-1f, 1f)
                }
                else -> return bitmap
            }

            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }
}
