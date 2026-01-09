package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class ImageProcessorImpl : ImageProcessor {
    private val codec = IosImageCodec()
    private val imageStore = IosImageStore(codec)
    private val transformer = IosImageTransformer(codec)
    private val homographyTransformer = IosHomographyTransformer(codec)

    override suspend fun loadImage(path: String): Result<ImageData> = withContext(Dispatchers.IO) {
        imageStore.loadImage(path)
    }

    override suspend fun saveImage(data: ImageData, path: String, quality: Int): Result<String> =
        withContext(Dispatchers.IO) {
            imageStore.saveImage(data, path, quality)
        }

    override suspend fun applyAffineTransform(
        image: ImageData,
        matrix: AlignmentMatrix,
        outputWidth: Int,
        outputHeight: Int,
    ): Result<ImageData> = withContext(Dispatchers.IO) {
        transformer.applyAffineTransform(image, matrix, outputWidth, outputHeight)
    }

    override suspend fun applyHomographyTransform(
        image: ImageData,
        matrix: HomographyMatrix,
        outputWidth: Int,
        outputHeight: Int,
    ): Result<ImageData> = withContext(Dispatchers.IO) {
        homographyTransformer.applyHomographyTransform(
            image = image,
            matrix = matrix,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            applyAffineFallback = { affineMatrix ->
                transformer.applyAffineTransform(image, affineMatrix, outputWidth, outputHeight)
            },
        )
    }

    override suspend fun cropImage(image: ImageData, bounds: BoundingBox): Result<ImageData> =
        withContext(Dispatchers.IO) {
            transformer.cropImage(image, bounds)
        }

    override suspend fun resizeImage(
        image: ImageData,
        width: Int,
        height: Int,
        maintainAspectRatio: Boolean,
    ): Result<ImageData> = withContext(Dispatchers.IO) {
        transformer.resizeImage(image, width, height, maintainAspectRatio)
    }

    override suspend fun rotateImage(image: ImageData, degrees: Float): Result<ImageData> =
        withContext(Dispatchers.IO) {
            transformer.rotateImage(image, degrees)
        }

    override suspend fun getImageDimensions(path: String): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        imageStore.getImageDimensions(path)
    }
}
