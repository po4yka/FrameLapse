package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.FeatureDetectorType
import com.po4yka.framelapse.domain.entity.FeatureKeypoint
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.service.FeatureMatchResult
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ReprojectionErrorResult
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.opencv.CVDetectorType
import com.po4yka.framelapse.opencv.OpenCVWrapper
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy
import kotlin.math.sqrt

/**
 * iOS implementation of FeatureMatcher.
 *
 * This implementation uses the OpenCVWrapper Objective-C++ bridge to access
 * OpenCV's feature detection, matching, and homography computation functions.
 *
 * Note: The OpenCVWrapper code is compiled with the iOS app via Xcode and
 * bridged into Kotlin/Native using cinterop bindings.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Suppress("TooManyFunctions", "MagicNumber")
class FeatureMatcherImpl : FeatureMatcher {

    override val isAvailable: Boolean
        get() = checkOpenCVAvailable()

    // Storage for descriptors between detect and match calls
    private var lastSourceDescriptors: DescriptorData? = null
    private var lastReferenceDescriptors: DescriptorData? = null

    override suspend fun detectFeatures(
        imageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks> = withContext(Dispatchers.Default) {
        if (!isAvailable) {
            return@withContext createOpenCVNotAvailableError()
        }

        try {
            detectFeaturesWithOpenCV(imageData, detectorType, maxKeypoints, isSource = true)
        } catch (e: Exception) {
            Result.Error(e, "Feature detection failed: ${e.message}")
        }
    }

    override suspend fun detectFeaturesFromPath(
        imagePath: String,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks> = withContext(Dispatchers.IO) {
        if (!isAvailable) {
            return@withContext createOpenCVNotAvailableError()
        }

        try {
            val fileManager = NSFileManager.defaultManager
            if (!fileManager.fileExistsAtPath(imagePath)) {
                return@withContext Result.Error(
                    IllegalArgumentException("File not found: $imagePath"),
                    "File not found",
                )
            }

            val uiImage = UIImage.imageWithContentsOfFile(imagePath)
                ?: return@withContext Result.Error(
                    IllegalArgumentException("Failed to load image: $imagePath"),
                    "Failed to load image",
                )

            val imageData = uiImageToImageData(uiImage)
                ?: return@withContext Result.Error(
                    IllegalStateException("Failed to convert image to ImageData"),
                    "Failed to convert image",
                )

            detectFeaturesWithOpenCV(imageData, detectorType, maxKeypoints, isSource = true)
        } catch (e: Exception) {
            Result.Error(e, "Feature detection failed: ${e.message}")
        }
    }

    override suspend fun matchFeatures(
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
            matchFeaturesWithOpenCV(sourceFeatures, referenceFeatures, ratioTestThreshold, useCrossCheck)
        } catch (e: Exception) {
            Result.Error(e, "Feature matching failed: ${e.message}")
        }
    }

    override suspend fun computeHomography(
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
            computeHomographyWithOpenCV(sourceKeypoints, referenceKeypoints, matches, ransacThreshold)
        } catch (e: Exception) {
            Result.Error(e, "Homography computation failed: ${e.message}")
        }
    }

    override suspend fun findHomography(
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
            // Step 1: Detect features in source image
            val sourceResult = detectFeaturesWithOpenCV(sourceImageData, detectorType, maxKeypoints, isSource = true)
            if (sourceResult is Result.Error) {
                return@withContext Result.Error(
                    sourceResult.exception,
                    "Failed to detect features in source image: ${sourceResult.message}",
                )
            }
            val sourceLandmarks = (sourceResult as Result.Success).data

            // Step 2: Detect features in reference image
            val refResult = detectFeaturesWithOpenCV(referenceImageData, detectorType, maxKeypoints, isSource = false)
            if (refResult is Result.Error) {
                return@withContext Result.Error(
                    refResult.exception,
                    "Failed to detect features in reference image: ${refResult.message}",
                )
            }
            val referenceLandmarks = (refResult as Result.Success).data

            // Validate sufficient keypoints
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

            // Step 3: Match features between images
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

            // Step 4: Compute homography using RANSAC
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

            // Calculate confidence based on match quality
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

    override suspend fun calculateReprojectionError(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        homography: HomographyMatrix,
        imageWidth: Int,
        imageHeight: Int,
    ): Result<ReprojectionErrorResult> = withContext(Dispatchers.Default) {
        try {
            if (matches.isEmpty()) {
                return@withContext Result.Error(
                    IllegalArgumentException("No matches provided"),
                    "Cannot calculate reprojection error without matches",
                )
            }

            val inlierThreshold = DEFAULT_INLIER_THRESHOLD

            val errors = mutableListOf<Float>()
            var inlierCount = 0

            for ((srcIdx, refIdx) in matches) {
                if (srcIdx >= sourceKeypoints.size || refIdx >= referenceKeypoints.size) {
                    continue
                }

                val srcKp = sourceKeypoints[srcIdx]
                val refKp = referenceKeypoints[refIdx]

                // Convert normalized coordinates to pixel coordinates
                val srcX = srcKp.position.x * imageWidth
                val srcY = srcKp.position.y * imageHeight
                val refX = refKp.position.x * imageWidth
                val refY = refKp.position.y * imageHeight

                // Transform source point using homography
                val (projX, projY) = homography.transformPoint(srcX, srcY)

                // Calculate reprojection error (Euclidean distance)
                val dx = projX - refX
                val dy = projY - refY
                val error = sqrt(dx * dx + dy * dy)

                errors.add(error)

                if (error <= inlierThreshold) {
                    inlierCount++
                }
            }

            if (errors.isEmpty()) {
                return@withContext Result.Error(
                    IllegalStateException("No valid matches for error calculation"),
                    "No valid matches",
                )
            }

            val sortedErrors = errors.sorted()
            val meanError = errors.average().toFloat()
            val medianError = if (sortedErrors.size % 2 == 0) {
                (sortedErrors[sortedErrors.size / 2 - 1] + sortedErrors[sortedErrors.size / 2]) / 2f
            } else {
                sortedErrors[sortedErrors.size / 2]
            }
            val maxError = sortedErrors.last()

            Result.Success(
                ReprojectionErrorResult(
                    meanError = meanError,
                    medianError = medianError,
                    maxError = maxError,
                    inlierCount = inlierCount,
                    totalMatches = errors.size,
                    inlierThreshold = inlierThreshold,
                    errors = errors,
                ),
            )
        } catch (e: Exception) {
            Result.Error(e, "Failed to calculate reprojection error: ${e.message}")
        }
    }

    override fun release() {
        lastSourceDescriptors = null
        lastReferenceDescriptors = null
    }

    // ==========================================================================
    // Private helper methods - OpenCV wrapper access
    // ==========================================================================

    /**
     * Checks if OpenCV wrapper is available via cinterop bindings.
     *
     * Note: OpenCV integration requires the iOS app to be built with Xcode,
     * which compiles OpenCVWrapper.mm and links the OpenCV framework.
     * The cinterop bindings need additional configuration to work at runtime.
     * Until then, this returns false.
     */
    private fun checkOpenCVAvailable(): Boolean = try {
        OpenCVWrapper.isAvailable()
    } catch (e: Exception) {
        false
    }

    /**
     * Detects features using OpenCV wrapper.
     * Returns error until cinterop bindings are configured.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun detectFeaturesWithOpenCV(
        imageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
        isSource: Boolean,
    ): Result<LandscapeLandmarks> {
        val rgbaDataResult = imageDataToRgbaNSData(imageData)
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

    /**
     * Matches features using OpenCV wrapper.
     * Returns error until cinterop bindings are configured.
     */
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

        if (filteredPairs.isEmpty()) {
            return Result.Error(
                IllegalStateException("No matches found"),
                "No feature matches passed filtering",
            )
        }

        return Result.Success(filteredPairs)
    }

    /**
     * Computes homography using OpenCV wrapper.
     * Returns error until cinterop bindings are configured.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun computeHomographyWithOpenCV(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        ransacThreshold: Float,
    ): Result<Pair<HomographyMatrix, Int>> {
        if (matches.isEmpty()) {
            return Result.Error(
                IllegalArgumentException("No matches provided"),
                "No matches for homography computation",
            )
        }

        val srcPoints = mutableListOf<NSNumber>()
        val dstPoints = mutableListOf<NSNumber>()

        matches.forEach { (srcIdx, refIdx) ->
            val src = sourceKeypoints.getOrNull(srcIdx) ?: return@forEach
            val dst = referenceKeypoints.getOrNull(refIdx) ?: return@forEach

            srcPoints.add(NSNumber.numberWithDouble(src.position.x.toDouble()))
            srcPoints.add(NSNumber.numberWithDouble(src.position.y.toDouble()))
            dstPoints.add(NSNumber.numberWithDouble(dst.position.x.toDouble()))
            dstPoints.add(NSNumber.numberWithDouble(dst.position.y.toDouble()))
        }

        if (srcPoints.size < MIN_MATCHES_FOR_HOMOGRAPHY * 2 ||
            dstPoints.size < MIN_MATCHES_FOR_HOMOGRAPHY * 2
        ) {
            return Result.Error(
                IllegalArgumentException("Not enough valid matches for homography"),
                "Insufficient matches for homography computation",
            )
        }

        val result = OpenCVWrapper.computeHomographyWithSrcPoints(
            srcPoints = srcPoints,
            dstPoints = dstPoints,
            ransacThreshold = ransacThreshold.toDouble(),
        ) ?: return Result.Error(
            IllegalStateException("OpenCV homography returned null"),
            "Homography computation failed",
        )

        if (!result.success) {
            return Result.Error(
                IllegalStateException("OpenCV homography failed"),
                "Homography computation failed",
            )
        }

        val matrixValues = result.matrix
        if (matrixValues.size != HOMOGRAPHY_SIZE) {
            return Result.Error(
                IllegalStateException("Invalid homography matrix size: ${matrixValues.size}"),
                "Homography matrix invalid",
            )
        }

        val matrixArray = DoubleArray(HOMOGRAPHY_SIZE) { index ->
            matrixValues[index].doubleValue
        }

        val homography = HomographyMatrix.fromDoubleArray(matrixArray)
        return Result.Success(Pair(homography, result.inlierCount))
    }

    // ==========================================================================
    // Private helper methods - Image conversion
    // ==========================================================================

    private fun byteArrayToUIImage(bytes: ByteArray): UIImage? {
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        return UIImage.imageWithData(data)
    }

    private fun imageDataToRgbaNSData(imageData: ImageData): Triple<NSData, Int, Int>? {
        val expectedSize = imageData.width * imageData.height * RGBA_BYTES_PER_PIXEL
        if (imageData.bytes.size == expectedSize) {
            val data = imageData.bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = imageData.bytes.size.toULong())
            }
            return Triple(data, imageData.width, imageData.height)
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

        val data = rgbaBytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = rgbaBytes.size.toULong())
        }
        return Triple(data, width, height)
    }

    private fun uiImageToImageData(uiImage: UIImage): ImageData? {
        val size = uiImage.size
        val width = size.useContents { width.toInt() }
        val height = size.useContents { height.toInt() }

        val pngData = UIImagePNGRepresentation(uiImage) ?: return null
        val bytes = pngData.toByteArray()

        return ImageData(width = width, height = height, bytes = bytes)
    }

    private fun NSData.toByteArray(): ByteArray {
        val length = this.length.toInt()
        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, length.toULong())
        }
        return bytes
    }

    // ==========================================================================
    // Private helper methods - Calculations
    // ==========================================================================

    private fun calculateConfidence(
        matchCount: Int,
        inlierCount: Int,
        sourceKeypointCount: Int,
        refKeypointCount: Int,
    ): Float {
        val inlierRatio = if (matchCount > 0) inlierCount.toFloat() / matchCount else 0f
        val minKeypoints = minOf(sourceKeypointCount, refKeypointCount)
        val matchRatio = if (minKeypoints > 0) matchCount.toFloat() / minKeypoints else 0f
        val normalizedMatchCount = (matchCount.toFloat() / MAX_MATCHES_FOR_CONFIDENCE).coerceAtMost(1f)

        return (
            inlierRatio * INLIER_RATIO_WEIGHT +
                matchRatio * MATCH_RATIO_WEIGHT +
                normalizedMatchCount * MATCH_COUNT_WEIGHT
            ).coerceIn(0f, 1f)
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

        val qualityScore = (countScore * COUNT_WEIGHT + responseScore * RESPONSE_WEIGHT).coerceIn(0f, 1f)

        return LandscapeLandmarks(
            keypoints = keypoints.take(LandscapeLandmarks.MAX_KEYPOINTS),
            detectorType = detectorType,
            keypointCount = keypoints.size,
            boundingBox = boundingBox,
            qualityScore = qualityScore,
        )
    }

    private fun mapDetectorType(detectorType: FeatureDetectorType): CVDetectorType = when (detectorType) {
        FeatureDetectorType.ORB -> CVDetectorType.CVDetectorTypeORB
        FeatureDetectorType.AKAZE -> CVDetectorType.CVDetectorTypeAKAZE
    }

    /**
     * Internal class to hold descriptor data between detect and match calls.
     */
    private data class DescriptorData(val data: NSData?, val rows: Int, val cols: Int, val type: Int)

    companion object {
        private const val MIN_MATCHES_FOR_HOMOGRAPHY = 4
        private const val MAX_MATCHES_FOR_CONFIDENCE = 200

        private const val INLIER_RATIO_WEIGHT = 0.4f
        private const val MATCH_RATIO_WEIGHT = 0.3f
        private const val MATCH_COUNT_WEIGHT = 0.3f

        private const val COUNT_WEIGHT = 0.7f
        private const val RESPONSE_WEIGHT = 0.3f
        private const val MAX_EXPECTED_RESPONSE = 100f

        private const val HOMOGRAPHY_SIZE = 9
        private const val RGBA_BYTES_PER_PIXEL = 4

        private const val OPENCV_NOT_CONFIGURED_MESSAGE =
            "OpenCV wrapper is not available. Ensure the iOS app is built via Xcode " +
                "with the OpenCV pod linked and the OpenCVWrapper cinterop bindings configured."

        private const val DEFAULT_INLIER_THRESHOLD = 5.0f
    }
}
