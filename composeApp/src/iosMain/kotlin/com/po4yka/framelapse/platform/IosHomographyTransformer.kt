package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.opencv.OpenCVWrapper
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSNumber

internal class IosHomographyTransformer(private val codec: IosImageCodec) {
    fun applyHomographyTransform(
        image: ImageData,
        matrix: HomographyMatrix,
        outputWidth: Int,
        outputHeight: Int,
        applyAffineFallback: (AlignmentMatrix) -> Result<ImageData>,
    ): Result<ImageData> {
        return try {
            val isNearAffine = kotlin.math.abs(matrix.h31) < PERSPECTIVE_THRESHOLD &&
                kotlin.math.abs(matrix.h32) < PERSPECTIVE_THRESHOLD

            if (!isNearAffine) {
                if (!OpenCVWrapper.isAvailable()) {
                    return Result.Error(
                        UnsupportedOperationException(HOMOGRAPHY_NOT_SUPPORTED_MESSAGE),
                        HOMOGRAPHY_NOT_SUPPORTED_MESSAGE,
                    )
                }

                val rgbaResult = codec.imageDataToRgbaBytes(image)
                    ?: return Result.Error(
                        IllegalStateException("Failed to convert image to RGBA"),
                        "Failed to convert image for OpenCV",
                    )
                val (rgbaBytes, inputWidth, inputHeight) = rgbaResult

                val inputData = rgbaBytes.usePinned { pinned ->
                    platform.Foundation.NSData.create(bytes = pinned.addressOf(0), length = rgbaBytes.size.toULong())
                }

                val homographyValues = listOf(
                    NSNumber.numberWithDouble(matrix.h11.toDouble()),
                    NSNumber.numberWithDouble(matrix.h12.toDouble()),
                    NSNumber.numberWithDouble(matrix.h13.toDouble()),
                    NSNumber.numberWithDouble(matrix.h21.toDouble()),
                    NSNumber.numberWithDouble(matrix.h22.toDouble()),
                    NSNumber.numberWithDouble(matrix.h23.toDouble()),
                    NSNumber.numberWithDouble(matrix.h31.toDouble()),
                    NSNumber.numberWithDouble(matrix.h32.toDouble()),
                    NSNumber.numberWithDouble(matrix.h33.toDouble()),
                )

                val outputData = OpenCVWrapper.warpPerspectiveWithImageData(
                    imageData = inputData,
                    width = inputWidth,
                    height = inputHeight,
                    homography = homographyValues,
                    outputWidth = outputWidth,
                    outputHeight = outputHeight,
                ) ?: return Result.Error(
                    IllegalStateException("OpenCV warpPerspective returned null"),
                    "Failed to apply homography",
                )

                val outputBytes = codec.dataToByteArray(outputData)
                val outputImage = codec.rgbaBytesToUIImage(outputBytes, outputWidth, outputHeight)
                    ?: return Result.Error(
                        IllegalStateException("Failed to build output image"),
                        "Failed to convert OpenCV output",
                    )

                val resultData = platform.UIKit.UIImagePNGRepresentation(outputImage)
                    ?: return Result.Error(
                        IllegalStateException("Failed to encode result image"),
                        "Failed to encode result",
                    )

                return Result.Success(
                    ImageData(
                        width = outputWidth,
                        height = outputHeight,
                        bytes = codec.dataToByteArray(resultData),
                    ),
                )
            }

            val h33 = if (kotlin.math.abs(matrix.h33) > EPSILON) matrix.h33 else 1f

            val affineMatrix = AlignmentMatrix(
                scaleX = matrix.h11 / h33,
                skewX = matrix.h12 / h33,
                translateX = matrix.h13 / h33,
                skewY = matrix.h21 / h33,
                scaleY = matrix.h22 / h33,
                translateY = matrix.h23 / h33,
            )

            applyAffineFallback(affineMatrix)
        } catch (e: Exception) {
            Result.Error(e, "Failed to apply homography transform: ${e.message}")
        }
    }

    companion object {
        private const val PERSPECTIVE_THRESHOLD = 0.001f
        private const val EPSILON = 1e-6f
        private const val HOMOGRAPHY_NOT_SUPPORTED_MESSAGE =
            "Full perspective (homography) transformation requires OpenCV integration. " +
                "Ensure the iOS app is built via Xcode with the OpenCV pod linked."
    }
}
