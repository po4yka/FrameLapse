package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.util.Result
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGAffineTransformMake
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGInterpolationHigh
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class ImageProcessorImpl : ImageProcessor {

    override suspend fun loadImage(path: String): Result<ImageData> = withContext(Dispatchers.IO) {
        try {
            val fileManager = NSFileManager.defaultManager
            if (!fileManager.fileExistsAtPath(path)) {
                return@withContext Result.Error(
                    IllegalArgumentException("File not found: $path"),
                    "File not found",
                )
            }

            val image = UIImage.imageWithContentsOfFile(path)
                ?: return@withContext Result.Error(
                    IllegalArgumentException("Failed to load image: $path"),
                    "Failed to load image",
                )

            val imageSize = image.size
            val width = imageSize.useContents { this.width.toInt() }
            val height = imageSize.useContents { this.height.toInt() }

            val data = UIImagePNGRepresentation(image)
                ?: return@withContext Result.Error(
                    IllegalStateException("Failed to convert image to PNG"),
                    "Failed to convert image",
                )

            val bytes = data.toByteArray()

            Result.Success(
                ImageData(
                    width = width,
                    height = height,
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
                val uiImage = byteArrayToUIImage(data.bytes)
                    ?: return@withContext Result.Error(
                        IllegalStateException("Failed to create image from data"),
                        "Failed to create image",
                    )

                val imageData = if (path.endsWith(".png", ignoreCase = true)) {
                    UIImagePNGRepresentation(uiImage)
                } else {
                    val jpegQuality = quality.coerceIn(0, 100) / 100.0
                    UIImageJPEGRepresentation(uiImage, jpegQuality)
                }

                if (imageData == null) {
                    return@withContext Result.Error(
                        IllegalStateException("Failed to encode image"),
                        "Failed to encode image",
                    )
                }

                // Write using NSFileManager
                val fileManager = NSFileManager.defaultManager
                val success = fileManager.createFileAtPath(
                    path = path,
                    contents = imageData,
                    attributes = null,
                )
                if (!success) {
                    return@withContext Result.Error(
                        IllegalStateException("Failed to write image to file"),
                        "Failed to write image",
                    )
                }

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
            val uiImage = byteArrayToUIImage(image.bytes)
                ?: return@withContext Result.Error(
                    IllegalStateException("Failed to create image from data"),
                    "Failed to create image",
                )

            val cgImage = uiImage.CGImage
                ?: return@withContext Result.Error(
                    IllegalStateException("Failed to get CGImage"),
                    "Failed to get CGImage",
                )

            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val bytesPerPixel = 4
            val bytesPerRow = bytesPerPixel * outputWidth

            val context = CGBitmapContextCreate(
                data = null,
                width = outputWidth.toULong(),
                height = outputHeight.toULong(),
                bitsPerComponent = 8u,
                bytesPerRow = bytesPerRow.toULong(),
                space = colorSpace,
                bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            )

            if (context == null) {
                return@withContext Result.Error(
                    IllegalStateException("Failed to create bitmap context"),
                    "Failed to create context",
                )
            }

            CGContextSetInterpolationQuality(context, kCGInterpolationHigh)

            val transform = CGAffineTransformMake(
                a = matrix.scaleX.toDouble(),
                b = matrix.skewY.toDouble(),
                c = matrix.skewX.toDouble(),
                d = matrix.scaleY.toDouble(),
                tx = matrix.translateX.toDouble(),
                ty = matrix.translateY.toDouble(),
            )

            CGContextConcatCTM(context, transform)

            val rect = CGRectMake(
                x = 0.0,
                y = 0.0,
                width = CGImageGetWidth(cgImage).toDouble(),
                height = CGImageGetHeight(cgImage).toDouble(),
            )
            CGContextDrawImage(context, rect, cgImage)

            val resultImage = CGBitmapContextCreateImage(context)
            CGContextRelease(context)

            if (resultImage == null) {
                return@withContext Result.Error(
                    IllegalStateException("Failed to create result image"),
                    "Failed to create result",
                )
            }

            val resultUIImage = UIImage.imageWithCGImage(resultImage)
            CGImageRelease(resultImage)

            val resultData = UIImagePNGRepresentation(resultUIImage)
                ?: return@withContext Result.Error(
                    IllegalStateException("Failed to encode result image"),
                    "Failed to encode result",
                )

            Result.Success(
                ImageData(
                    width = outputWidth,
                    height = outputHeight,
                    bytes = resultData.toByteArray(),
                ),
            )
        } catch (e: Exception) {
            Result.Error(e, "Failed to apply transform: ${e.message}")
        }
    }

    override suspend fun cropImage(image: ImageData, bounds: BoundingBox): Result<ImageData> =
        withContext(Dispatchers.IO) {
            try {
                val uiImage = byteArrayToUIImage(image.bytes)
                    ?: return@withContext Result.Error(
                        IllegalStateException("Failed to create image from data"),
                        "Failed to create image",
                    )

                val cgImage = uiImage.CGImage
                    ?: return@withContext Result.Error(
                        IllegalStateException("Failed to get CGImage"),
                        "Failed to get CGImage",
                    )

                val imageWidth = CGImageGetWidth(cgImage).toInt()
                val imageHeight = CGImageGetHeight(cgImage).toInt()

                val left = (bounds.left * imageWidth).toInt().coerceIn(0, imageWidth - 1)
                val top = (bounds.top * imageHeight).toInt().coerceIn(0, imageHeight - 1)
                val right = (bounds.right * imageWidth).toInt().coerceIn(left + 1, imageWidth)
                val bottom = (bounds.bottom * imageHeight).toInt().coerceIn(top + 1, imageHeight)

                val cropWidth = right - left
                val cropHeight = bottom - top

                val cropRect = CGRectMake(
                    x = left.toDouble(),
                    y = top.toDouble(),
                    width = cropWidth.toDouble(),
                    height = cropHeight.toDouble(),
                )

                val croppedCGImage = platform.CoreGraphics.CGImageCreateWithImageInRect(cgImage, cropRect)
                    ?: return@withContext Result.Error(
                        IllegalStateException("Failed to crop image"),
                        "Failed to crop image",
                    )

                val croppedUIImage = UIImage.imageWithCGImage(croppedCGImage)
                CGImageRelease(croppedCGImage)

                val resultData = UIImagePNGRepresentation(croppedUIImage)
                    ?: return@withContext Result.Error(
                        IllegalStateException("Failed to encode cropped image"),
                        "Failed to encode result",
                    )

                Result.Success(
                    ImageData(
                        width = cropWidth,
                        height = cropHeight,
                        bytes = resultData.toByteArray(),
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
            val uiImage = byteArrayToUIImage(image.bytes)
                ?: return@withContext Result.Error(
                    IllegalStateException("Failed to create image from data"),
                    "Failed to create image",
                )

            val (targetWidth, targetHeight) = if (maintainAspectRatio) {
                calculateAspectRatioSize(image.width, image.height, width, height)
            } else {
                Pair(width, height)
            }

            val size = CGSizeMake(
                width = targetWidth.toDouble(),
                height = targetHeight.toDouble(),
            )

            UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
            uiImage.drawInRect(
                CGRectMake(
                    x = 0.0,
                    y = 0.0,
                    width = targetWidth.toDouble(),
                    height = targetHeight.toDouble(),
                ),
            )
            val resizedImage = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()

            if (resizedImage == null) {
                return@withContext Result.Error(
                    IllegalStateException("Failed to resize image"),
                    "Failed to resize image",
                )
            }

            val resultData = UIImagePNGRepresentation(resizedImage)
                ?: return@withContext Result.Error(
                    IllegalStateException("Failed to encode resized image"),
                    "Failed to encode result",
                )

            Result.Success(
                ImageData(
                    width = targetWidth,
                    height = targetHeight,
                    bytes = resultData.toByteArray(),
                ),
            )
        } catch (e: Exception) {
            Result.Error(e, "Failed to resize image: ${e.message}")
        }
    }

    override suspend fun rotateImage(image: ImageData, degrees: Float): Result<ImageData> =
        withContext(Dispatchers.IO) {
            try {
                val uiImage = byteArrayToUIImage(image.bytes)
                    ?: return@withContext Result.Error(
                        IllegalStateException("Failed to create image from data"),
                        "Failed to create image",
                    )

                val radians = degrees * kotlin.math.PI / 180.0
                val originalSize = uiImage.size
                val originalWidth = originalSize.useContents { this.width }
                val originalHeight = originalSize.useContents { this.height }

                val absRadians = kotlin.math.abs(radians)
                val sinVal = kotlin.math.sin(absRadians)
                val cosVal = kotlin.math.cos(absRadians)
                val newWidth = (originalWidth * cosVal + originalHeight * sinVal).toInt()
                val newHeight = (originalWidth * sinVal + originalHeight * cosVal).toInt()

                val newSize = CGSizeMake(
                    width = newWidth.toDouble(),
                    height = newHeight.toDouble(),
                )

                UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
                val context = platform.UIKit.UIGraphicsGetCurrentContext()

                if (context != null) {
                    platform.CoreGraphics.CGContextTranslateCTM(
                        context,
                        newWidth / 2.0,
                        newHeight / 2.0,
                    )
                    platform.CoreGraphics.CGContextRotateCTM(context, radians)
                    uiImage.drawInRect(
                        CGRectMake(
                            x = -originalWidth / 2.0,
                            y = -originalHeight / 2.0,
                            width = originalWidth,
                            height = originalHeight,
                        ),
                    )
                }

                val rotatedImage = UIGraphicsGetImageFromCurrentImageContext()
                UIGraphicsEndImageContext()

                if (rotatedImage == null) {
                    return@withContext Result.Error(
                        IllegalStateException("Failed to rotate image"),
                        "Failed to rotate image",
                    )
                }

                val resultData = UIImagePNGRepresentation(rotatedImage)
                    ?: return@withContext Result.Error(
                        IllegalStateException("Failed to encode rotated image"),
                        "Failed to encode result",
                    )

                Result.Success(
                    ImageData(
                        width = newWidth,
                        height = newHeight,
                        bytes = resultData.toByteArray(),
                    ),
                )
            } catch (e: Exception) {
                Result.Error(e, "Failed to rotate image: ${e.message}")
            }
        }

    override suspend fun getImageDimensions(path: String): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        try {
            val fileManager = NSFileManager.defaultManager
            if (!fileManager.fileExistsAtPath(path)) {
                return@withContext Result.Error(
                    IllegalArgumentException("File not found: $path"),
                    "File not found",
                )
            }

            val image = UIImage.imageWithContentsOfFile(path)
                ?: return@withContext Result.Error(
                    IllegalArgumentException("Failed to load image: $path"),
                    "Failed to load image",
                )

            val imageSize = image.size
            val width = imageSize.useContents { this.width.toInt() }
            val height = imageSize.useContents { this.height.toInt() }

            Result.Success(Pair(width, height))
        } catch (e: Exception) {
            Result.Error(e, "Failed to get image dimensions: ${e.message}")
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        val length = this.length.toInt()
        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, length.toULong())
        }
        return bytes
    }

    private fun byteArrayToUIImage(bytes: ByteArray): UIImage? {
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        return UIImage.imageWithData(data)
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
