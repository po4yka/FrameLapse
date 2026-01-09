package com.po4yka.framelapse.platform

import android.content.Context
import android.graphics.BitmapFactory
import com.po4yka.framelapse.domain.entity.FeatureDetectorType
import com.po4yka.framelapse.domain.entity.FeatureKeypoint
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.service.FeatureMatchResult
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ReprojectionErrorResult
import com.po4yka.framelapse.domain.util.ReprojectionErrorCalculator
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of FeatureMatcher using OpenCV Android SDK.
 *
 * Uses ORB or AKAZE feature detectors for keypoint extraction and
 * brute-force matching with Lowe's ratio test for robust matching.
 */
class FeatureMatcherImpl(private val context: Context) : FeatureMatcher {

    private val mutex = Mutex()
    private val openCvInitializer = OpenCvInitializer()
    private val core = OpenCvFeatureMatcherCore(openCvInitializer)

    override val isAvailable: Boolean
        get() = try {
            openCvInitializer.ensureInitialized()
            openCvInitializer.isInitialized()
        } catch (e: Exception) {
            false
        }

    override suspend fun detectFeatures(
        imageData: ImageData,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks> = withContext(Dispatchers.Default) {
        mutex.withLock {
            try {
                ensureOpenCvInitialized()

                val bitmap = BitmapFactory.decodeByteArray(
                    imageData.bytes,
                    0,
                    imageData.bytes.size,
                ) ?: return@withContext Result.Error(
                    IllegalArgumentException("Failed to decode image"),
                    "Failed to decode image",
                )

                val result = core.detectFeaturesFromBitmap(
                    bitmap,
                    imageData.width,
                    imageData.height,
                    detectorType,
                    maxKeypoints,
                )

                bitmap.recycle()
                result
            } catch (e: Exception) {
                Result.Error(e, "Feature detection failed: ${e.message}")
            }
        }
    }

    override suspend fun detectFeaturesFromPath(
        imagePath: String,
        detectorType: FeatureDetectorType,
        maxKeypoints: Int,
    ): Result<LandscapeLandmarks> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (!File(imagePath).exists()) {
                    return@withContext Result.Error(
                        IllegalArgumentException("File not found: $imagePath"),
                        "File not found",
                    )
                }

                ensureOpenCvInitialized()

                val bitmap = BitmapFactory.decodeFile(imagePath)
                    ?: return@withContext Result.Error(
                        IllegalArgumentException("Failed to decode image: $imagePath"),
                        "Failed to decode image",
                    )

                val result = core.detectFeaturesFromBitmap(
                    bitmap,
                    bitmap.width,
                    bitmap.height,
                    detectorType,
                    maxKeypoints,
                )

                bitmap.recycle()
                result
            } catch (e: Exception) {
                Result.Error(e, "Feature detection failed: ${e.message}")
            }
        }
    }

    override suspend fun matchFeatures(
        sourceFeatures: LandscapeLandmarks,
        referenceFeatures: LandscapeLandmarks,
        ratioTestThreshold: Float,
        useCrossCheck: Boolean,
    ): Result<List<Pair<Int, Int>>> = withContext(Dispatchers.Default) {
        mutex.withLock {
            try {
                ensureOpenCvInitialized()

                if (sourceFeatures.keypoints.isEmpty() || referenceFeatures.keypoints.isEmpty()) {
                    return@withContext Result.Error(
                        IllegalArgumentException("Cannot match with empty keypoint sets"),
                        "No keypoints to match",
                    )
                }

                // For brute-force matching, we need descriptors which are not stored in LandscapeLandmarks
                // This method is meant to be called after detectFeatures, which stores descriptors internally
                // For now, return an error indicating this limitation
                return@withContext Result.Error(
                    UnsupportedOperationException(
                        "matchFeatures requires descriptor data. Use findHomography for full pipeline.",
                    ),
                    "Use findHomography for matching with descriptors",
                )
            } catch (e: Exception) {
                Result.Error(e, "Feature matching failed: ${e.message}")
            }
        }
    }

    override suspend fun computeHomography(
        sourceKeypoints: List<FeatureKeypoint>,
        referenceKeypoints: List<FeatureKeypoint>,
        matches: List<Pair<Int, Int>>,
        ransacThreshold: Float,
    ): Result<Pair<HomographyMatrix, Int>> = withContext(Dispatchers.Default) {
        mutex.withLock {
            try {
                ensureOpenCvInitialized()

                if (matches.size < MIN_MATCHES_FOR_HOMOGRAPHY) {
                    return@withContext Result.Error(
                        IllegalArgumentException("Need at least $MIN_MATCHES_FOR_HOMOGRAPHY matches"),
                        "Insufficient matches for homography",
                    )
                }

                core.computeHomography(sourceKeypoints, referenceKeypoints, matches, ransacThreshold)
            } catch (e: Exception) {
                Result.Error(e, "Homography computation failed: ${e.message}")
            }
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
        mutex.withLock {
            try {
                ensureOpenCvInitialized()

                core.findHomography(
                    sourceImageData = sourceImageData,
                    referenceImageData = referenceImageData,
                    detectorType = detectorType,
                    maxKeypoints = maxKeypoints,
                    ratioTestThreshold = ratioTestThreshold,
                    ransacThreshold = ransacThreshold,
                )
            } catch (e: Exception) {
                Result.Error(e, "findHomography failed: ${e.message}")
            }
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
        ReprojectionErrorCalculator.calculate(
            sourceKeypoints = sourceKeypoints,
            referenceKeypoints = referenceKeypoints,
            matches = matches,
            homography = homography,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
        )
    }

    override fun release() {
        core.release()
    }

    companion object {
        /** Minimum number of matches required for homography computation. */
        private const val MIN_MATCHES_FOR_HOMOGRAPHY = 4
    }
}
