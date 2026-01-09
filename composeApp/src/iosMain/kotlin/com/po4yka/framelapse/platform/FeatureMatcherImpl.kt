package com.po4yka.framelapse.platform

import com.po4yka.framelapse.domain.entity.FeatureDetectorType
import com.po4yka.framelapse.domain.entity.FeatureKeypoint
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.service.FeatureMatchResult
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/**
 * iOS implementation of FeatureMatcher.
 *
 * This implementation uses the OpenCVWrapper Objective-C++ bridge to access
 * OpenCV's feature detection, matching, and homography computation functions.
 *
 * Note: The OpenCV cinterop bindings require the header to be properly processed.
 * Until the cinterop is fully configured, this returns appropriate fallback responses.
 * The OpenCVWrapper code is compiled with the iOS app via Xcode and can be called
 * from Swift directly.
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
    private fun checkOpenCVAvailable(): Boolean {
        // OpenCV cinterop bindings require Objective-C runtime integration.
        // The OpenCVWrapper is compiled into the iOS app but calling it from
        // Kotlin requires properly configured cinterop bindings.
        // For now, return false until cinterop is fully working.
        return false
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
        // TODO: Implement when OpenCV cinterop bindings are properly configured
        // The OpenCVWrapper provides:
        // - detectFeaturesWithImageData(data, width, height, type, maxKeypoints)
        // - Returns CVFeatureResult with keypoints and descriptors
        return createOpenCVNotAvailableError()
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
        // TODO: Implement when OpenCV cinterop bindings are properly configured
        return Result.Error(
            UnsupportedOperationException(OPENCV_NOT_CONFIGURED_MESSAGE),
            OPENCV_NOT_CONFIGURED_MESSAGE,
        )
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
        // TODO: Implement when OpenCV cinterop bindings are properly configured
        return Result.Error(
            UnsupportedOperationException(OPENCV_NOT_CONFIGURED_MESSAGE),
            OPENCV_NOT_CONFIGURED_MESSAGE,
        )
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

        private const val OPENCV_NOT_CONFIGURED_MESSAGE =
            "OpenCV cinterop bindings are not yet fully configured. " +
                "The OpenCVWrapper Objective-C++ code is compiled into the iOS app, " +
                "but calling it from Kotlin requires additional cinterop setup. " +
                "Feature matching can be used from Swift/Objective-C directly."
    }
}
