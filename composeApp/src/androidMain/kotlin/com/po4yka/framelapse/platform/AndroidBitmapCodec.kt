package com.po4yka.framelapse.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

internal class AndroidBitmapCodec {
    fun loadImage(path: String): Result<ImageData> {
        return try {
            val file = File(path)
            if (!file.exists()) {
                return Result.Error(
                    IllegalArgumentException("File not found: $path"),
                    "File not found",
                )
            }

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val originalBitmap = BitmapFactory.decodeFile(path, options)
                ?: return Result.Error(
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

    fun byteArrayToBitmap(bytes: ByteArray): Bitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

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
