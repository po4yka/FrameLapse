package com.po4yka.framelapse.platform

import android.graphics.Bitmap
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

internal class AndroidOpenCvTransformer(
    private val initializer: OpenCvInitializer,
    private val codec: AndroidBitmapCodec,
) {
    fun applyHomographyTransform(
        image: ImageData,
        matrix: HomographyMatrix,
        outputWidth: Int,
        outputHeight: Int,
    ): Result<ImageData> {
        return try {
            initializer.ensureInitialized()

            val sourceBitmap = codec.byteArrayToBitmap(image.bytes)
                ?: return Result.Error(
                    IllegalStateException("Failed to create bitmap from data"),
                    "Failed to create bitmap",
                )

            val sourceMat = Mat()
            Utils.bitmapToMat(sourceBitmap, sourceMat)

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

            val outputMat = Mat()
            val outputSize = Size(outputWidth.toDouble(), outputHeight.toDouble())

            Imgproc.warpPerspective(
                sourceMat,
                outputMat,
                homographyMat,
                outputSize,
                Imgproc.INTER_LINEAR,
                org.opencv.core.Core.BORDER_CONSTANT,
            )

            val outputBitmap = Bitmap.createBitmap(
                outputWidth,
                outputHeight,
                Bitmap.Config.ARGB_8888,
            )
            Utils.matToBitmap(outputMat, outputBitmap)

            val bytes = codec.bitmapToByteArray(outputBitmap)

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
}
