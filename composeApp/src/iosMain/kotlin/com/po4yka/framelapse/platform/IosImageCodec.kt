package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.service.ImageData
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.posix.memcpy

internal class IosImageCodec {
    fun dataToByteArray(data: NSData): ByteArray {
        val length = data.length.toInt()
        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, length.toULong())
        }
        return bytes
    }

    fun byteArrayToUIImage(bytes: ByteArray): UIImage? {
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        return UIImage.imageWithData(data)
    }

    fun rgbaBytesToUIImage(bytes: ByteArray, width: Int, height: Int): UIImage? {
        if (bytes.size < width * height * RGBA_BYTES_PER_PIXEL) return null
        val bytesPerRow = width * RGBA_BYTES_PER_PIXEL
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val cgImage = bytes.usePinned { pinned ->
            val context = CGBitmapContextCreate(
                data = pinned.addressOf(0),
                width = width.toULong(),
                height = height.toULong(),
                bitsPerComponent = 8u,
                bytesPerRow = bytesPerRow.toULong(),
                space = colorSpace,
                bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            ) ?: return null

            val image = platform.CoreGraphics.CGBitmapContextCreateImage(context)
            CGContextRelease(context)
            image
        } ?: return null

        val uiImage = UIImage.imageWithCGImage(cgImage)
        CGImageRelease(cgImage)
        return uiImage
    }

    fun imageDataToRgbaBytes(imageData: ImageData): Triple<ByteArray, Int, Int>? {
        val expectedSize = imageData.width * imageData.height * RGBA_BYTES_PER_PIXEL
        if (imageData.bytes.size == expectedSize) {
            return Triple(imageData.bytes, imageData.width, imageData.height)
        }

        val uiImage = byteArrayToUIImage(imageData.bytes) ?: return null
        val cgImage = uiImage.CGImage ?: return null
        val width = CGImageGetWidth(cgImage).toInt()
        val height = CGImageGetHeight(cgImage).toInt()
        val bytesPerRow = width * RGBA_BYTES_PER_PIXEL
        val rgbaBytes = ByteArray(bytesPerRow * height)

        rgbaBytes.usePinned { pinned ->
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val context = CGBitmapContextCreate(
                data = pinned.addressOf(0),
                width = width.toULong(),
                height = height.toULong(),
                bitsPerComponent = 8u,
                bytesPerRow = bytesPerRow.toULong(),
                space = colorSpace,
                bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            ) ?: return null

            val rect = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
            CGContextDrawImage(context, rect, cgImage)
            CGContextRelease(context)
        }

        return Triple(rgbaBytes, width, height)
    }

    fun imageDataToRgbaNSData(imageData: ImageData): Triple<NSData, Int, Int>? {
        val rgbaResult = imageDataToRgbaBytes(imageData) ?: return null
        val (rgbaBytes, width, height) = rgbaResult
        val data = rgbaBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = rgbaBytes.size.toULong())
        }
        return Triple(data, width, height)
    }

    fun uiImageToImageData(uiImage: UIImage): ImageData? {
        val cgImage = uiImage.CGImage ?: return null
        val width = CGImageGetWidth(cgImage).toInt()
        val height = CGImageGetHeight(cgImage).toInt()
        val bytesPerRow = width * RGBA_BYTES_PER_PIXEL
        val rgbaBytes = ByteArray(bytesPerRow * height)

        rgbaBytes.usePinned { pinned ->
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val context = CGBitmapContextCreate(
                data = pinned.addressOf(0),
                width = width.toULong(),
                height = height.toULong(),
                bitsPerComponent = 8u,
                bytesPerRow = bytesPerRow.toULong(),
                space = colorSpace,
                bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            ) ?: return null

            val rect = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
            CGContextDrawImage(context, rect, cgImage)
            CGContextRelease(context)
        }

        return ImageData(
            width = width,
            height = height,
            bytes = rgbaBytes,
        )
    }

    fun extractImageSize(uiImage: UIImage): Pair<Int, Int> {
        val size = uiImage.size
        return size.useContents { this.width.toInt() to this.height.toInt() }
    }

    companion object {
        private const val RGBA_BYTES_PER_PIXEL = 4
    }
}
