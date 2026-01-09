package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.FeatureDetectorType
import com.po4yka.framelapse.domain.entity.FeatureKeypoint
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.service.FeatureMatchResult
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ReprojectionErrorResult
import com.po4yka.framelapse.domain.util.ReprojectionErrorCalculator
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.opencv.CVDetectorType
import com.po4yka.framelapse.opencv.OpenCVWrapper
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosFeatureMatcherCore(private val codec: IosImageCodec) {
    private var lastSourceDescriptors: DescriptorData? = null
    private var lastReferenceDescriptors: DescriptorData? = null

    val isAvailable: Boolean
        get() = checkOpenCVAvailable()

    suspend fun detectFeatures(
        imageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
        isSource: Boolean,
    ): Result<LandscapeLandmarks> = withContext(Dispatchers.Default) {
        if (!isAvailable) {
            return@withContext createOpenCVNotAvailableError()
        }

        try {
            detectFeaturesWithOpenCV(imageData, detectorType, maxKeypoints, isSource)
        } catch (e: Exception) {
            Result.Error(e, "Feature detection failed: ${e.message}")
        }
    }

    suspend fun detectFeaturesFromPath(
        imagePath: String,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks> = withContext(Dispatchers.IO) {
        if (!isAvailable) {
            return@withContext createOpenCVNotAvailableError()
        }

        try {
            val fileManager = platform.Foundation.NSFileManager.defaultManager
            if (!fileManager.fileExistsAtPath(imagePath)) {
                return@withContext Result.Error(
                    IllegalArgumentException("File not found: $imagePath"),
                    "File not found",
                )
            }

            val uiImage = platform.UIKit.UIImage.imageWithContentsOfFile(imagePath)
                ?: return@withContext Result.Error(
                    IllegalArgumentException("Failed to load image: $imagePath"),
                    "Failed to load image",
                )

            val imageData = codec.uiImageToImageData(uiImage)
                ?: return@withContext Result.Error(
                    IllegalStateException("Failed to convert image to ImageData"),
                    "Failed to convert image",
                )

            detectFeaturesWithOpenCV(imageData, detectorType, maxKeypoints, isSource = true)
        } catch (e: Exception) {
            Result.Error(e, "Feature detection failed: ${e.message}")
        }
    }

    suspend fun matchFeatures(
        sourceFeatures: LandscapeLandmarks,
        referenceFeatures: LandscapeLandmarks,
        ratioTestThreshold: Float,
        useCrossCheck: Boolean,
    ): Result<List<Pair<Int, Int>>> = withContext(Dispatchers.Default) {
        if (!isAvailable) {
            return@withContext Result.Error(
                UnsupportedOperationException(OPENCV_NOT_CONFIGURED_MESSAGE),
                OPENCV_NOT_CONFIGURED_MESSAGE,
            )
        }

        try {
            matchFeaturesWithOpenCV(
                sourceFeatures,
                referenceFeatures,
                ratioTestThreshold,
                useCrossCheck
            )
        } catch (e: Exception) {
            Result.Error(e, "Feature matching failed: ${e.message}")
        }
    }

    suspend fun computeHomography(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        ransacThreshold: Float,
    ): Result<Pair<HomographyMatrix, Int>> = withContext(Dispatchers.Default) {
        if (!isAvailable) {
            return@withContext Result.Error(
                UnsupportedOperationException(OPENCV_NOT_CONFIGURED_MESSAGE),
                OPENCV_NOT_CONFIGURED_MESSAGE,
            )
        }

        if (matches.size < MIN_MATCHES_FOR_HOMOGRAPHY) {
            return@withContext Result.Error(
                IllegalArgumentException("Not enough matches: ${matches.size} < $MIN_MATCHES_FOR_HOMOGRAPHY required"),
                "Insufficient matches for homography computation",
            )
        }

        try {
            computeHomographyWithOpenCV(
                sourceKeypoints,
                referenceKeypoints,
                matches,
                ransacThreshold
            )
        } catch (e: Exception) {
            Result.Error(e, "Homography computation failed: ${e.message}")
        }
    }

    suspend fun findHomography(
        sourceImageData: ImageData,
        referenceImageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
        ratioTestThreshold: Float,
        ransacThreshold: Float,
    ): Result<FeatureMatchResult> = withContext(Dispatchers.Default) {
        if (!isAvailable) {
            return@withContext Result.Error(
                UnsupportedOperationException(OPENCV_NOT_CONFIGURED_MESSAGE),
                OPENCV_NOT_CONFIGURED_MESSAGE,
            )
        }

        try {
            val sourceResult = detectFeaturesWithOpenCV(
                sourceImageData,
                detectorType,
                maxKeypoints,
                isSource = true
            )
            if (sourceResult is Result.Error) {
                return@withContext Result.Error(
                    sourceResult.exception,
                    "Failed to detect features in source image: ${sourceResult.message}",
                )
            }
            val sourceLandmarks = (sourceResult as Result.Success).data

            val refResult = detectFeaturesWithOpenCV(
                referenceImageData,
                detectorType,
                maxKeypoints,
                isSource = false
            )
            if (refResult is Result.Error) {
                return@withContext Result.Error(
                    refResult.exception,
                    "Failed to detect features in reference image: ${refResult.message}",
                )
            }
            val referenceLandmarks = (refResult as Result.Success).data

            if (!sourceLandmarks.hasEnoughKeypoints()) {
                return@withContext Result.Error(
                    IllegalStateException("Insufficient keypoints in source image: ${sourceLandmarks.keypointCount}"),
                    "Source image has too few features for reliable matching",
                )
            }
            if (!referenceLandmarks.hasEnoughKeypoints()) {
                return@withContext Result.Error(
                    IllegalStateException(
                        "Insufficient keypoints in reference image: ${referenceLandmarks.keypointCount}",
                    ),
                    "Reference image has too few features for reliable matching",
                )
            }

            val matchResult = matchFeaturesWithOpenCV(
                sourceLandmarks,
                referenceLandmarks,
                ratioTestThreshold,
                useCrossCheck = false,
            )
            if (matchResult is Result.Error) {
                return@withContext Result.Error(
                    matchResult.exception,
                    "Failed to match features: ${matchResult.message}",
                )
            }
            val matches = (matchResult as Result.Success).data

            if (matches.size < MIN_MATCHES_FOR_HOMOGRAPHY) {
                return@withContext Result.Error(
                    IllegalStateException("Insufficient matches: ${matches.size}"),
                    "Not enough feature matches for homography computation",
                )
            }

            val homographyResult = computeHomographyWithOpenCV(
                sourceLandmarks.keypoints,
                referenceLandmarks.keypoints,
                matches,
                ransacThreshold,
            )
            if (homographyResult is Result.Error) {
                return@withContext Result.Error(
                    homographyResult.exception,
                    "Failed to compute homography: ${homographyResult.message}",
                )
            }
            val (homography, inlierCount) = (homographyResult as Result.Success).data

            val confidence = calculateConfidence(
                matchCount = matches.size,
                inlierCount = inlierCount,
                sourceKeypointCount = sourceLandmarks.keypointCount,
                refKeypointCount = referenceLandmarks.keypointCount,
            )

            Result.Success(
                FeatureMatchResult(
                    homography = homography,
                    sourceLandmarks = sourceLandmarks,
                    referenceLandmarks = referenceLandmarks,
                    matchCount = matches.size,
                    inlierCount = inlierCount,
                    confidence = confidence,
                ),
            )
        } catch (e: Exception) {
            Result.Error(e, "findHomography failed: ${e.message}")
        }
    }

    fun calculateReprojectionError(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        homography: HomographyMatrix,
        imageWidth: Int,
        imageHeight: Int,
    ): Result<ReprojectionErrorResult> = ReprojectionErrorCalculator.calculate(
        sourceKeypoints = sourceKeypoints,
        referenceKeypoints = referenceKeypoints,
        matches = matches,
        homography = homography,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
    )

    fun release() {
        lastSourceDescriptors = null
        lastReferenceDescriptors = null
    }

    private fun checkOpenCVAvailable(): Boolean = try {
        OpenCVWrapper.isAvailable()
    } catch (e: Exception) {
        false
    }

    @Suppress("UNUSED_PARAMETER")
    private fun detectFeaturesWithOpenCV(
        imageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
        isSource: Boolean,
    ): Result<LandscapeLandmarks> {
        val rgbaDataResult = codec.imageDataToRgbaNSData(imageData)
            ?: return Result.Error(
                IllegalStateException("Failed to convert image to RGBA"),
                "Failed to convert image for OpenCV",
            )

        val (rgbaData, imageWidth, imageHeight) = rgbaDataResult
        val cvDetectorType = mapDetectorType(detectorType)

        val result = OpenCVWrapper.detectFeaturesWithImageData(
            imageData = rgbaData,
            width = imageWidth,
            height = imageHeight,
            detectorType = cvDetectorType,
            maxKeypoints = maxKeypoints,
        ) ?: return Result.Error(
            IllegalStateException("OpenCV feature detection returned null"),
            "Feature detection failed",
        )

        val keypoints = result.keypoints
        if (keypoints.isEmpty()) {
            return Result.Error(
                IllegalStateException("No keypoints detected"),
                "No features found in image",
            )
        }

        val featureKeypoints = keypoints.map { kp ->
            FeatureKeypoint.fromPixelCoordinates(
                x = kp.x,
                y = kp.y,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                response = kp.response,
                size = kp.size,
                angle = kp.angle,
                octave = kp.octave,
            )
        }

        val descriptorData = DescriptorData(
            data = result.descriptors,
            rows = result.descriptorRows,
            cols = result.descriptorCols,
            type = result.descriptorType,
        )

        if (isSource) {
            lastSourceDescriptors = descriptorData
        } else {
            lastReferenceDescriptors = descriptorData
        }

        val landmarks = createLandscapeLandmarks(featureKeypoints, detectorType)
        return Result.Success(landmarks)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun matchFeaturesWithOpenCV(
        sourceFeatures: LandscapeLandmarks,
        referenceFeatures: LandscapeLandmarks,
        ratioTestThreshold: Float,
        useCrossCheck: Boolean,
    ): Result<List<Pair<Int, Int>>> {
        if (sourceFeatures.keypoints.isEmpty() || referenceFeatures.keypoints.isEmpty()) {
            return Result.Error(
                IllegalArgumentException("Cannot match with empty keypoint sets"),
                "No keypoints to match",
            )
        }

        val sourceDescriptors = lastSourceDescriptors
            ?: return Result.Error(
                IllegalStateException("Source descriptors not available"),
                "Missing source descriptors from detectFeatures",
            )
        val referenceDescriptors = lastReferenceDescriptors
            ?: return Result.Error(
                IllegalStateException("Reference descriptors not available"),
                "Missing reference descriptors from detectFeatures",
            )

        if (sourceDescriptors.rows <= 0 || sourceDescriptors.cols <= 0 || sourceDescriptors.data == null) {
            return Result.Error(
                IllegalStateException("Source descriptors are empty"),
                "No source descriptors to match",
            )
        }
        if (referenceDescriptors.rows <= 0 || referenceDescriptors.cols <= 0 || referenceDescriptors.data == null) {
            return Result.Error(
                IllegalStateException("Reference descriptors are empty"),
                "No reference descriptors to match",
            )
        }

        val forwardMatches = OpenCVWrapper.matchFeaturesWithDescriptors1(
            descriptors1 = sourceDescriptors.data,
            rows1 = sourceDescriptors.rows,
            cols1 = sourceDescriptors.cols,
            type1 = sourceDescriptors.type,
            descriptors2 = referenceDescriptors.data,
            rows2 = referenceDescriptors.rows,
            cols2 = referenceDescriptors.cols,
            type2 = referenceDescriptors.type,
            ratioThreshold = ratioTestThreshold,
        ) ?: return Result.Error(
            IllegalStateException("OpenCV matchFeatures returned null"),
            "Feature matching failed",
        )

        val forwardPairs = forwardMatches.map { it.queryIdx to it.trainIdx }
        val filteredPairs = if (useCrossCheck) {
            val reverseMatches = OpenCVWrapper.matchFeaturesWithDescriptors1(
                descriptors1 = referenceDescriptors.data,
                rows1 = referenceDescriptors.rows,
                cols1 = referenceDescriptors.cols,
                type1 = referenceDescriptors.type,
                descriptors2 = sourceDescriptors.data,
                rows2 = sourceDescriptors.rows,
                cols2 = sourceDescriptors.cols,
                type2 = sourceDescriptors.type,
                ratioThreshold = ratioTestThreshold,
            ) ?: return Result.Error(
                IllegalStateException("OpenCV reverse matchFeatures returned null"),
                "Feature matching failed",
            )

            val reverseMap = reverseMatches.associate { it.queryIdx to it.trainIdx }
            forwardPairs.filter { (srcIdx, refIdx) -> reverseMap[refIdx] == srcIdx }
        } else {
            forwardPairs
        }

        return Result.Success(filteredPairs)
    }

    private fun computeHomographyWithOpenCV(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        ransacThreshold: Float,
    ): Result<Pair<HomographyMatrix, Int>> {
        val srcPoints = matches.map { (srcIdx, _) ->
            val kp = sourceKeypoints[srcIdx]
            floatArrayOf(kp.position.x, kp.position.y)
        }
        val dstPoints = matches.map { (_, refIdx) ->
            val kp = referenceKeypoints[refIdx]
            floatArrayOf(kp.position.x, kp.position.y)
        }

        val src = srcPoints.map { listOf(it[0], it[1]) }
        val dst = dstPoints.map { listOf(it[0], it[1]) }

        val result = OpenCVWrapper.findHomography(
            srcPoints = src,
            dstPoints = dst,
            ransacThreshold = ransacThreshold,
        ) ?: return Result.Error(
            IllegalStateException("OpenCV findHomography returned null"),
            "Homography computation failed",
        )

        val matrix = HomographyMatrix.fromDoubleArray(result.homography)
        return Result.Success(Pair(matrix, result.inlierCount))
    }

    private fun calculateConfidence(
        matchCount: Int,
        inlierCount: Int,
        sourceKeypointCount: Int,
        refKeypointCount: Int,
    ): Float {
        val inlierRatio = if (matchCount == 0) 0f else inlierCount.toFloat() / matchCount
        val minKeypoints = minOf(sourceKeypointCount, refKeypointCount).coerceAtLeast(1)
        val matchRatioScore = (matchCount.toFloat() / minKeypoints).coerceAtMost(1f)
        return (inlierRatio * INLIER_WEIGHT + matchRatioScore * MATCH_WEIGHT).coerceIn(0f, 1f)
    }

    private fun <T> createOpenCVNotAvailableError(): Result<T> = Result.Error(
        UnsupportedOperationException(OPENCV_NOT_CONFIGURED_MESSAGE),
        OPENCV_NOT_CONFIGURED_MESSAGE,
    )

    private fun createLandscapeLandmarks(
        keypoints: List<FeatureKeypoint>,
        detectorType: FeatureDetectorType,
    ): LandscapeLandmarks {
        val boundingBox = if (keypoints.isNotEmpty()) {
            val minX = keypoints.minOf { it.position.x }
            val maxX = keypoints.maxOf { it.position.x }
            val minY = keypoints.minOf { it.position.y }
            val maxY = keypoints.maxOf { it.position.y }
            BoundingBox(
                left = minX.coerceAtLeast(0f),
                top = minY.coerceAtLeast(0f),
                right = maxX.coerceAtMost(1f),
                bottom = maxY.coerceAtMost(1f),
            )
        } else {
            BoundingBox(0f, 0f, 1f, 1f)
        }

        val countScore = (keypoints.size.toFloat() / LandscapeLandmarks.RECOMMENDED_KEYPOINTS)
            .coerceAtMost(1f)
        val responseScore = if (keypoints.isNotEmpty()) {
            keypoints.map { it.response }.average().toFloat() / MAX_EXPECTED_RESPONSE
        } else {
            0f
        }.coerceAtMost(1f)
        val qualityScore =
            (countScore * COUNT_WEIGHT + responseScore * RESPONSE_WEIGHT).coerceIn(0f, 1f)

        return LandscapeLandmarks(
            keypoints = keypoints.take(LandscapeLandmarks.MAX_KEYPOINTS),
            detectorType = detectorType,
            keypointCount = keypoints.size,
            boundingBox = boundingBox,
            qualityScore = qualityScore,
        )
    }

    private fun mapDetectorType(detectorType: FeatureDetectorType): CVDetectorType =
        when (detectorType) {
            FeatureDetectorType.ORB -> CVDetectorType.CVDetectorTypeOrb
            FeatureDetectorType.AKAZE -> CVDetectorType.CVDetectorTypeAkaze
        }

    private data class DescriptorData(
        val data: platform.Foundation.NSData?,
        val rows: Int,
        val cols: Int,
        val type: Int,
    )

    companion object {
        private const val MIN_MATCHES_FOR_HOMOGRAPHY = 4
        private const val INLIER_WEIGHT = 0.6f
        private const val MATCH_WEIGHT = 0.4f
        private const val COUNT_WEIGHT = 0.7f
        private const val RESPONSE_WEIGHT = 0.3f
        private const val MAX_EXPECTED_RESPONSE = 100f
        private const val OPENCV_NOT_CONFIGURED_MESSAGE =
            "OpenCV not configured. Please build iOS app with OpenCV pod and enable cinterop."
    }
}
