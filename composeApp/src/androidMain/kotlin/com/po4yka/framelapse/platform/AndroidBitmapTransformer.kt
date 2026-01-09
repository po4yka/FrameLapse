package com.po4yka.framelapse.platform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result

internal class AndroidBitmapTransformer(private val codec: AndroidBitmapCodec) {
    fun applyAffineTransform(
        image: ImageData,
        matrix: AlignmentMatrix,
        outputWidth: Int,
        outputHeight: Int,
    ): Result<ImageData> {
        return try {
            val sourceBitmap = codec.byteArrayToBitmap(image.bytes)
                ?: return Result.Error(
                    IllegalStateException("Failed to create bitmap from data"),
                    "Failed to create bitmap",
                )

            val androidMatrix = Matrix().apply {
                setValues(
                    floatArrayOf(
                        matrix.scaleX, matrix.skewX, matrix.translateX,
                        matrix.skewY, matrix.scaleY, matrix.translateY,
                        0f, 0f, 1f,
                    ),
                )
            }

            val outputBitmap = Bitmap.createBitmap(
                outputWidth,
                outputHeight,
                Bitmap.Config.ARGB_8888,
            )

            val canvas = Canvas(outputBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(sourceBitmap, androidMatrix, paint)

            val bytes = codec.bitmapToByteArray(outputBitmap)

            sourceBitmap.recycle()
            outputBitmap.recycle()

            Result.Success(
                ImageData(
                    width = outputWidth,
                    height = outputHeight,
                    bytes = bytes,
                ),
            )
        } catch (e: Exception) {
            Result.Error(e, "Failed to apply transform: ${e.message}")
        }
    }

    fun cropImage(image: ImageData, bounds: BoundingBox): Result<ImageData> {
        return try {
            val sourceBitmap = codec.byteArrayToBitmap(image.bytes)
                ?: return Result.Error(
                    IllegalStateException("Failed to create bitmap from data"),
                    "Failed to create bitmap",
                )

            val left = (bounds.left * image.width).toInt().coerceIn(0, image.width - 1)
            val top = (bounds.top * image.height).toInt().coerceIn(0, image.height - 1)
            val right = (bounds.right * image.width).toInt().coerceIn(left + 1, image.width)
            val bottom = (bounds.bottom * image.height).toInt().coerceIn(top + 1, image.height)

            val cropWidth = right - left
            val cropHeight = bottom - top

            val croppedBitmap = Bitmap.createBitmap(
                sourceBitmap,
                left,
                top,
                cropWidth,
                cropHeight,
            )

            val bytes = codec.bitmapToByteArray(croppedBitmap)

            sourceBitmap.recycle()
            if (croppedBitmap != sourceBitmap) {
                croppedBitmap.recycle()
            }

            Result.Success(
                ImageData(
                    width = cropWidth,
                    height = cropHeight,
                    bytes = bytes,
                ),
            )
        } catch (e: Exception) {
            Result.Error(e, "Failed to crop image: ${e.message}")
        }
    }

    fun resizeImage(image: ImageData, width: Int, height: Int, maintainAspectRatio: Boolean): Result<ImageData> {
        return try {
            val sourceBitmap = codec.byteArrayToBitmap(image.bytes)
                ?: return Result.Error(
                    IllegalStateException("Failed to create bitmap from data"),
                    "Failed to create bitmap",
                )

            val (targetWidth, targetHeight) = if (maintainAspectRatio) {
                calculateAspectRatioSize(image.width, image.height, width, height)
            } else {
                Pair(width, height)
            }

            val resizedBitmap = Bitmap.createScaledBitmap(
                sourceBitmap,
                targetWidth,
                targetHeight,
                true,
            )

            val bytes = codec.bitmapToByteArray(resizedBitmap)

            sourceBitmap.recycle()
            if (resizedBitmap != sourceBitmap) {
                resizedBitmap.recycle()
            }

            Result.Success(
                ImageData(
                    width = targetWidth,
                    height = targetHeight,
                    bytes = bytes,
                ),
            )
        } catch (e: Exception) {
            Result.Error(e, "Failed to resize image: ${e.message}")
        }
    }

    fun rotateImage(image: ImageData, degrees: Float): Result<ImageData> {
        return try {
            val sourceBitmap = codec.byteArrayToBitmap(image.bytes)
                ?: return Result.Error(
                    IllegalStateException("Failed to create bitmap from data"),
                    "Failed to create bitmap",
                )

            val matrix = Matrix().apply {
                postRotate(degrees)
            }

            val rotatedBitmap = Bitmap.createBitmap(
                sourceBitmap,
                0,
                0,
                sourceBitmap.width,
                sourceBitmap.height,
                matrix,
                true,
            )

            val bytes = codec.bitmapToByteArray(rotatedBitmap)

            sourceBitmap.recycle()
            if (rotatedBitmap != sourceBitmap) {
                rotatedBitmap.recycle()
            }

            Result.Success(
                ImageData(
                    width = rotatedBitmap.width,
                    height = rotatedBitmap.height,
                    bytes = bytes,
                ),
            )
        } catch (e: Exception) {
            Result.Error(e, "Failed to rotate image: ${e.message}")
        }
    }

    private fun calculateAspectRatioSize(
        srcWidth: Int,
        srcHeight: Int,
        maxWidth: Int,
        maxHeight: Int,
    ): Pair<Int, Int> {
        val aspectRatio = srcWidth.toFloat() / srcHeight.toFloat()
        return if (maxWidth.toFloat() / maxHeight.toFloat() > aspectRatio) {
            Pair((maxHeight * aspectRatio).toInt(), maxHeight)
        } else {
            Pair(maxWidth, (maxWidth / aspectRatio).toInt())
        }
    }
}
