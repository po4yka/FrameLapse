package com.po4yka.framelapse.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ImageProcessorImpl(private val context: Context) : ImageProcessor {

    private var isOpenCvInitialized = false

    override suspend fun loadImage(path: String): Result<ImageData> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                return@withContext Result.Error(
                    IllegalArgumentException("File not found: $path"),
                    "File not found",
                )
            }

            // Load bitmap with orientation correction
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val originalBitmap = BitmapFactory.decodeFile(path, options)
                ?: return@withContext Result.Error(
                    IllegalArgumentException("Failed to decode image: $path"),
                    "Failed to decode image",
                )

            // Handle EXIF orientation
            val bitmap = correctBitmapOrientation(originalBitmap, path)

            // Convert to byte array
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

    override suspend fun saveImage(data: ImageData, path: String, quality: Int): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = byteArrayToBitmap(data.bytes, data.width, data.height)
                    ?: return@withContext Result.Error(
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

    override suspend fun applyAffineTransform(
        image: ImageData,
        matrix: AlignmentMatrix,
        outputWidth: Int,
        outputHeight: Int,
    ): Result<ImageData> = withContext(Dispatchers.IO) {
        try {
            val sourceBitmap = byteArrayToBitmap(image.bytes, image.width, image.height)
                ?: return@withContext Result.Error(
                    IllegalStateException("Failed to create bitmap from data"),
                    "Failed to create bitmap",
                )

            // Create Android Matrix from AlignmentMatrix
            val androidMatrix = Matrix().apply {
                setValues(
                    floatArrayOf(
                        matrix.scaleX, matrix.skewX, matrix.translateX,
                        matrix.skewY, matrix.scaleY, matrix.translateY,
                        0f, 0f, 1f,
                    ),
                )
            }

            // Create output bitmap
            val outputBitmap = Bitmap.createBitmap(
                outputWidth,
                outputHeight,
                Bitmap.Config.ARGB_8888,
            )

            // Apply transformation with high-quality paint
            val canvas = Canvas(outputBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(sourceBitmap, androidMatrix, paint)

            val bytes = bitmapToByteArray(outputBitmap)

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

    override suspend fun applyHomographyTransform(
        image: ImageData,
        matrix: HomographyMatrix,
        outputWidth: Int,
        outputHeight: Int,
    ): Result<ImageData> = withContext(Dispatchers.IO) {
        try {
            ensureOpenCvInitialized()

            val sourceBitmap = byteArrayToBitmap(image.bytes, image.width, image.height)
                ?: return@withContext Result.Error(
                    IllegalStateException("Failed to create bitmap from data"),
                    "Failed to create bitmap",
                )

            // Convert bitmap to OpenCV Mat
            val sourceMat = Mat()
            Utils.bitmapToMat(sourceBitmap, sourceMat)

            // Create homography matrix (3x3) from HomographyMatrix
            val homographyMat = Mat(3, 3, CvType.CV_64FC1)
            homographyMat.put(0, 0, matrix.h11.toDouble())
            homographyMat.put(0, 1, matrix.h12.toDouble())
            homographyMat.put(0, 2, matrix.h13.toDouble())
            homographyMat.put(1, 0, matrix.h21.toDouble())
            homographyMat.put(1, 1, matrix.h22.toDouble())
            homographyMat.put(1, 2, matrix.h23.toDouble())
            homographyMat.put(2, 0, matrix.h31.toDouble())
            homographyMat.put(2, 1, matrix.h32.toDouble())
            homographyMat.put(2, 2, matrix.h33.toDouble())

            // Create output Mat
            val outputMat = Mat()
            val outputSize = Size(outputWidth.toDouble(), outputHeight.toDouble())

            // Apply perspective transformation with high-quality interpolation
            Imgproc.warpPerspective(
                sourceMat,
                outputMat,
                homographyMat,
                outputSize,
                Imgproc.INTER_LINEAR,
                org.opencv.core.Core.BORDER_CONSTANT,
            )

            // Convert output Mat back to Bitmap
            val outputBitmap = Bitmap.createBitmap(
                outputWidth,
                outputHeight,
                Bitmap.Config.ARGB_8888,
            )
            Utils.matToBitmap(outputMat, outputBitmap)

            val bytes = bitmapToByteArray(outputBitmap)

            // Release resources
            sourceBitmap.recycle()
            outputBitmap.recycle()
            sourceMat.release()
            homographyMat.release()
            outputMat.release()

            Result.Success(
                ImageData(
                    width = outputWidth,
                    height = outputHeight,
                    bytes = bytes,
                ),
            )
        } catch (e: Exception) {
            Result.Error(e, "Failed to apply homography transform: ${e.message}")
        }
    }

    override suspend fun cropImage(image: ImageData, bounds: BoundingBox): Result<ImageData> =
        withContext(Dispatchers.IO) {
            try {
                val sourceBitmap = byteArrayToBitmap(image.bytes, image.width, image.height)
                    ?: return@withContext Result.Error(
                        IllegalStateException("Failed to create bitmap from data"),
                        "Failed to create bitmap",
                    )

                // Convert normalized coordinates to pixels
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

                val bytes = bitmapToByteArray(croppedBitmap)

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

    override suspend fun resizeImage(
        image: ImageData,
        width: Int,
        height: Int,
        maintainAspectRatio: Boolean,
    ): Result<ImageData> = withContext(Dispatchers.IO) {
        try {
            val sourceBitmap = byteArrayToBitmap(image.bytes, image.width, image.height)
                ?: return@withContext Result.Error(
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
                true, // Use filter for high quality
            )

            val bytes = bitmapToByteArray(resizedBitmap)

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

    override suspend fun rotateImage(image: ImageData, degrees: Float): Result<ImageData> =
        withContext(Dispatchers.IO) {
            try {
                val sourceBitmap = byteArrayToBitmap(image.bytes, image.width, image.height)
                    ?: return@withContext Result.Error(
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

                val bytes = bitmapToByteArray(rotatedBitmap)

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

    override suspend fun getImageDimensions(path: String): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return@withContext Result.Error(
                    IllegalArgumentException("Failed to read image dimensions: $path"),
                    "Failed to read image dimensions",
                )
            }

            // Handle EXIF orientation for dimension reporting
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

    private fun correctBitmapOrientation(bitmap: Bitmap, path: String): Bitmap {
        return try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )

            val matrix = Matrix()
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

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun byteArrayToBitmap(bytes: ByteArray, width: Int, height: Int): Bitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

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

    /**
     * Ensures OpenCV is initialized before using OpenCV operations.
     * Uses initLocal (newer API) with fallback to initDebug.
     */
    private fun ensureOpenCvInitialized() {
        if (isOpenCvInitialized) return

        isOpenCvInitialized = try {
            OpenCVLoader.initLocal()
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            OpenCVLoader.initDebug()
        }

        if (!isOpenCvInitialized) {
            throw IllegalStateException("Failed to initialize OpenCV")
        }
    }
}
