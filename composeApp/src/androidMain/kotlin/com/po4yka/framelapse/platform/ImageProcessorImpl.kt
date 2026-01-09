package com.po4yka.framelapse.platform

import android.content.Context
import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageProcessorImpl(private val context: Context) : ImageProcessor {
    private val openCvInitializer = OpenCvInitializer()
    private val codec = AndroidBitmapCodec()
    private val bitmapTransformer = AndroidBitmapTransformer(codec)
    private val openCvTransformer = AndroidOpenCvTransformer(openCvInitializer, codec)

    override suspend fun loadImage(path: String): Result<ImageData> = withContext(Dispatchers.IO) {
        codec.loadImage(path)
    }

    override suspend fun saveImage(data: ImageData, path: String, quality: Int): Result<String> =
        withContext(Dispatchers.IO) {
            codec.saveImage(data, path, quality)
        }

    override suspend fun applyAffineTransform(
        image: ImageData,
        matrix: AlignmentMatrix,
        outputWidth: Int,
        outputHeight: Int,
    ): Result<ImageData> = withContext(Dispatchers.IO) {
        bitmapTransformer.applyAffineTransform(image, matrix, outputWidth, outputHeight)
    }

    override suspend fun applyHomographyTransform(
        image: ImageData,
        matrix: HomographyMatrix,
        outputWidth: Int,
        outputHeight: Int,
    ): Result<ImageData> = withContext(Dispatchers.IO) {
        openCvTransformer.applyHomographyTransform(image, matrix, outputWidth, outputHeight)
    }

    override suspend fun cropImage(image: ImageData, bounds: BoundingBox): Result<ImageData> =
        withContext(Dispatchers.IO) {
            bitmapTransformer.cropImage(image, bounds)
        }

    override suspend fun resizeImage(
        image: ImageData,
        width: Int,
        height: Int,
        maintainAspectRatio: Boolean,
    ): Result<ImageData> = withContext(Dispatchers.IO) {
        bitmapTransformer.resizeImage(image, width, height, maintainAspectRatio)
    }

    override suspend fun rotateImage(image: ImageData, degrees: Float): Result<ImageData> =
        withContext(Dispatchers.IO) {
            bitmapTransformer.rotateImage(image, degrees)
        }

    override suspend fun getImageDimensions(path: String): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        codec.getImageDimensions(path)
    }
}
