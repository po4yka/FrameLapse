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

/**
 * iOS implementation of FeatureMatcher.
 *
 * This implementation requires OpenCV iOS framework integration via Kotlin/Native cinterop.
 * When OpenCV is not available, feature matching operations return appropriate error messages.
 *
 * The OpenCV wrapper (OpenCVWrapper.mm) must be compiled with the iOS app and linked
 * against the OpenCV framework from CocoaPods.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Suppress("TooManyFunctions", "MagicNumber")
class FeatureMatcherImpl : FeatureMatcher {

    /**
     * Check if OpenCV wrapper is available.
     * This will return true once the OpenCV framework is properly integrated
     * via the cinterop bindings.
     */
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

        // Validate minimum matches for homography computation
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
     * Currently returns false until runtime integration is complete.
     */
    private fun checkOpenCVAvailable(): Boolean {
        // OpenCV cinterop bindings are configured but require Xcode build
        // to compile the OpenCVWrapper Objective-C++ code and link OpenCV.
        // Once the iOS app is built via Xcode with the .xcworkspace (after pod install),
        // this can be updated to call the native wrapper.
        return false
    }

    /**
     * Detects features using OpenCV wrapper.
     * Stub implementation - requires Xcode build with OpenCV framework.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun detectFeaturesWithOpenCV(
        imageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
        isSource: Boolean,
    ): Result<LandscapeLandmarks> {
        // TODO: Implement when OpenCV cinterop bindings are available at runtime
        // The OpenCVWrapper provides:
        // - detectFeaturesWithImageData(data, width, height, type, maxKeypoints)
        // - Returns CVFeatureResult with keypoints and descriptors
        return createOpenCVNotAvailableError()
    }

    /**
     * Matches features using OpenCV wrapper.
     * Stub implementation - requires Xcode build with OpenCV framework.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun matchFeaturesWithOpenCV(
        sourceFeatures: LandscapeLandmarks,
        referenceFeatures: LandscapeLandmarks,
        ratioTestThreshold: Float,
        useCrossCheck: Boolean,
    ): Result<List<Pair<Int, Int>>> {
        // TODO: Implement when OpenCV cinterop bindings are available at runtime
        // The OpenCVWrapper provides:
        // - matchFeaturesWithDescriptors1(desc1, rows1, cols1, type1, desc2, rows2, cols2, type2, ratio)
        // - Returns NSArray of CVMatch objects
        return Result.Error(
            UnsupportedOperationException(OPENCV_NOT_CONFIGURED_MESSAGE),
            OPENCV_NOT_CONFIGURED_MESSAGE,
        )
    }

    /**
     * Computes homography using OpenCV wrapper.
     * Stub implementation - requires Xcode build with OpenCV framework.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun computeHomographyWithOpenCV(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        ransacThreshold: Float,
    ): Result<Pair<HomographyMatrix, Int>> {
        // TODO: Implement when OpenCV cinterop bindings are available at runtime
        // The OpenCVWrapper provides:
        // - computeHomographyWithSrcPoints(srcPoints, dstPoints, threshold)
        // - Returns CVHomographyResult with 3x3 matrix and inlier info
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

    // Note: imageDataToRGBA helper method is prepared for OpenCV integration.
    // It will be added back once the cinterop bindings are fully configured.

    private fun NSData.toByteArray(): ByteArray {
        val length = this.length.toInt()
        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            platform.posix.memcpy(pinned.addressOf(0), this.bytes, length.toULong())
        }
        return bytes
    }

    // ==========================================================================
    // Private helper methods - Calculations
    // ==========================================================================

    /**
     * Calculates a confidence score (0.0 to 1.0) based on match quality metrics.
     */
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
            "OpenCV is not yet integrated. Feature matching requires the iOS app to be built " +
                "with Xcode using iosApp.xcworkspace (after pod install). The OpenCVWrapper " +
                "Objective-C++ files and cinterop configuration are in place, but require " +
                "Xcode to compile and link the OpenCV framework from CocoaPods."
    }
}
