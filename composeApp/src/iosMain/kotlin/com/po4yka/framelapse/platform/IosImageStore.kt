package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import kotlinx.cinterop.useContents
import platform.Foundation.NSFileManager
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation

internal class IosImageStore(private val codec: IosImageCodec) {
    fun loadImage(path: String): Result<ImageData> {
        return try {
            val fileManager = NSFileManager.defaultManager
            if (!fileManager.fileExistsAtPath(path)) {
                return Result.Error(
                    IllegalArgumentException("File not found: $path"),
                    "File not found",
                )
            }

            val image = UIImage.imageWithContentsOfFile(path)
                ?: return Result.Error(
                    IllegalArgumentException("Failed to load image: $path"),
                    "Failed to load image",
                )

            val size = image.size
            val width = size.useContents { this.width.toInt() }
            val height = size.useContents { this.height.toInt() }

            val data = UIImagePNGRepresentation(image)
                ?: return Result.Error(
                    IllegalStateException("Failed to convert image to PNG"),
                    "Failed to convert image",
                )

            Result.Success(
                ImageData(
                    width = width,
                    height = height,
                    bytes = codec.dataToByteArray(data),
                ),
            )
        } catch (e: Exception) {
            Result.Error(e, "Failed to load image: ${e.message}")
        }
    }

    fun saveImage(data: ImageData, path: String, quality: Int): Result<String> {
        return try {
            val uiImage = codec.byteArrayToUIImage(data.bytes)
                ?: return Result.Error(
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
                return Result.Error(
                    IllegalStateException("Failed to encode image"),
                    "Failed to encode image",
                )
            }

            val fileManager = NSFileManager.defaultManager
            val success = fileManager.createFileAtPath(
                path = path,
                contents = imageData,
                attributes = null,
            )
            if (!success) {
                return Result.Error(
                    IllegalStateException("Failed to write image to file"),
                    "Failed to write image",
                )
            }

            Result.Success(path)
        } catch (e: Exception) {
            Result.Error(e, "Failed to save image: ${e.message}")
        }
    }

    fun getImageDimensions(path: String): Result<Pair<Int, Int>> {
        return try {
            val fileManager = NSFileManager.defaultManager
            if (!fileManager.fileExistsAtPath(path)) {
                return Result.Error(
                    IllegalArgumentException("File not found: $path"),
                    "File not found",
                )
            }

            val image = UIImage.imageWithContentsOfFile(path)
                ?: return Result.Error(
                    IllegalArgumentException("Failed to load image: $path"),
                    "Failed to load image",
                )

            val (width, height) = codec.extractImageSize(image)
            Result.Success(Pair(width, height))
        } catch (e: Exception) {
            Result.Error(e, "Failed to get image dimensions: ${e.message}")
        }
    }
}
