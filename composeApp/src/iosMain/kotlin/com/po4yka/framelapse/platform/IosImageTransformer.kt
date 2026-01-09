package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGAffineTransformMake
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextConcatCTM
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGInterpolationHigh
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosImageTransformer(private val codec: IosImageCodec) {
    fun applyAffineTransform(
        image: ImageData,
        matrix: AlignmentMatrix,
        outputWidth: Int,
        outputHeight: Int,
    ): Result<ImageData> {
        return try {
            val uiImage = codec.byteArrayToUIImage(image.bytes)
                ?: return Result.Error(
                    IllegalStateException("Failed to create image from data"),
                    "Failed to create image",
                )

            val cgImage = uiImage.CGImage
                ?: return Result.Error(
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
                return Result.Error(
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
                return Result.Error(
                    IllegalStateException("Failed to create result image"),
                    "Failed to create result",
                )
            }

            val resultUIImage = UIImage.imageWithCGImage(resultImage)
            CGImageRelease(resultImage)

            val resultData = UIImagePNGRepresentation(resultUIImage)
                ?: return Result.Error(
                    IllegalStateException("Failed to encode result image"),
                    "Failed to encode result",
                )

            Result.Success(
                ImageData(
                    width = outputWidth,
                    height = outputHeight,
                    bytes = codec.dataToByteArray(resultData),
                ),
            )
        } catch (e: Exception) {
            Result.Error(e, "Failed to apply transform: ${e.message}")
        }
    }

    fun cropImage(image: ImageData, bounds: BoundingBox): Result<ImageData> {
        return try {
            val uiImage = codec.byteArrayToUIImage(image.bytes)
                ?: return Result.Error(
                    IllegalStateException("Failed to create image from data"),
                    "Failed to create image",
                )

            val cgImage = uiImage.CGImage
                ?: return Result.Error(
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

            val croppedCGImage = CGImageCreateWithImageInRect(cgImage, cropRect)
                ?: return Result.Error(
                    IllegalStateException("Failed to crop image"),
                    "Failed to crop image",
                )

            val croppedUIImage = UIImage.imageWithCGImage(croppedCGImage)
            CGImageRelease(croppedCGImage)

            val resultData = UIImagePNGRepresentation(croppedUIImage)
                ?: return Result.Error(
                    IllegalStateException("Failed to encode cropped image"),
                    "Failed to encode cropped image",
                )

            Result.Success(
                ImageData(
                    width = cropWidth,
                    height = cropHeight,
                    bytes = codec.dataToByteArray(resultData),
                ),
            )
        } catch (e: Exception) {
            Result.Error(e, "Failed to crop image: ${e.message}")
        }
    }

    fun resizeImage(
        image: ImageData,
        width: Int,
        height: Int,
        maintainAspectRatio: Boolean,
    ): Result<ImageData> {
        return try {
            val uiImage = codec.byteArrayToUIImage(image.bytes)
                ?: return Result.Error(
                    IllegalStateException("Failed to create image from data"),
                    "Failed to create image",
                )

            val (targetWidth, targetHeight) = if (maintainAspectRatio) {
                calculateAspectRatioSize(image.width, image.height, width, height)
            } else {
                Pair(width, height)
            }

            UIGraphicsBeginImageContextWithOptions(
                CGSizeMake(targetWidth.toDouble(), targetHeight.toDouble()),
                false,
                1.0,
            )

            uiImage.drawInRect(
                CGRectMake(0.0, 0.0, targetWidth.toDouble(), targetHeight.toDouble()),
            )

            val resizedImage = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()

            if (resizedImage == null) {
                return Result.Error(
                    IllegalStateException("Failed to resize image"),
                    "Failed to resize image",
                )
            }

            val resultData = UIImagePNGRepresentation(resizedImage)
                ?: return Result.Error(
                    IllegalStateException("Failed to encode resized image"),
                    "Failed to encode resized image",
                )

            Result.Success(
                ImageData(
                    width = targetWidth,
                    height = targetHeight,
                    bytes = codec.dataToByteArray(resultData),
                ),
            )
        } catch (e: Exception) {
            Result.Error(e, "Failed to resize image: ${e.message}")
        }
    }

    fun rotateImage(image: ImageData, degrees: Float): Result<ImageData> {
        return try {
            val uiImage = codec.byteArrayToUIImage(image.bytes)
                ?: return Result.Error(
                    IllegalStateException("Failed to create image from data"),
                    "Failed to create image",
                )

            val radians = degrees * kotlin.math.PI.toFloat() / 180f
            val rotatedSize = CGSizeMake(uiImage.size.width, uiImage.size.height)

            UIGraphicsBeginImageContextWithOptions(rotatedSize, false, 1.0)

            val context = platform.UIKit.UIGraphicsGetCurrentContext()
                ?: return Result.Error(
                    IllegalStateException("Failed to get graphics context"),
                    "Failed to get graphics context",
                )

            platform.CoreGraphics.CGContextTranslateCTM(
                context,
                uiImage.size.width / 2,
                uiImage.size.height / 2,
            )
            platform.CoreGraphics.CGContextRotateCTM(context, radians.toDouble())
            platform.CoreGraphics.CGContextTranslateCTM(
                context,
                -uiImage.size.width / 2,
                -uiImage.size.height / 2,
            )

            uiImage.drawInRect(
                CGRectMake(0.0, 0.0, uiImage.size.width, uiImage.size.height),
            )

            val rotatedImage = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()

            if (rotatedImage == null) {
                return Result.Error(
                    IllegalStateException("Failed to rotate image"),
                    "Failed to rotate image",
                )
            }

            val resultData = UIImagePNGRepresentation(rotatedImage)
                ?: return Result.Error(
                    IllegalStateException("Failed to encode rotated image"),
                    "Failed to encode rotated image",
                )

            Result.Success(
                ImageData(
                    width = rotatedImage.size.useContents { this.width.toInt() },
                    height = rotatedImage.size.useContents { this.height.toInt() },
                    bytes = codec.dataToByteArray(resultData),
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
