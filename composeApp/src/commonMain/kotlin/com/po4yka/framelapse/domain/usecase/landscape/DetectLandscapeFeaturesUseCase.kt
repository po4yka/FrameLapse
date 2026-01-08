package com.po4yka.framelapse.domain.usecase.landscape

import com.po4yka.framelapse.domain.entity.FeatureDetectorType
import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result

/**
 * Detects feature keypoints in a landscape/scenery image.
 *
 * This use case wraps the FeatureMatcher service to detect distinctive
 * keypoints (corners, edges, etc.) that can be used for image alignment.
 * It validates that enough keypoints were detected for reliable matching.
 *
 * Use cases for landscape alignment:
 * - Scenery timelapse (sunsets, cityscapes, nature)
 * - Architecture photography alignment
 * - Any scene without detectable faces or bodies
 */
class DetectLandscapeFeaturesUseCase(
    private val featureMatcher: FeatureMatcher,
) {
    /**
     * Detects feature keypoints from image data.
     *
     * @param imageData The image to analyze.
     * @param detectorType The feature detector algorithm (ORB or AKAZE).
     * @param maxKeypoints Maximum number of keypoints to detect.
     * @return Result containing LandscapeLandmarks or an error.
     */
    suspend operator fun invoke(
        imageData: ImageData,
        detectorType: FeatureDetectorType = DEFAULT_DETECTOR_TYPE,
        maxKeypoints: Int = DEFAULT_MAX_KEYPOINTS,
    ): Result<LandscapeLandmarks> {
        // Validate input parameters
        if (imageData.width <= 0 || imageData.height <= 0) {
            return Result.Error(
                IllegalArgumentException("Invalid image dimensions"),
                "Image has invalid dimensions",
            )
        }

        if (maxKeypoints < MIN_KEYPOINTS_REQUIRED) {
            return Result.Error(
                IllegalArgumentException("Max keypoints must be at least $MIN_KEYPOINTS_REQUIRED"),
                "Max keypoints too low",
            )
        }

        // Check if feature detection is available
        if (!featureMatcher.isAvailable) {
            return Result.Error(
                UnsupportedOperationException("Feature detection is not available on this device"),
                "Feature detection not available",
            )
        }

        // Detect features
        val detectResult = featureMatcher.detectFeatures(
            imageData = imageData,
            detectorType = detectorType,
            maxKeypoints = maxKeypoints,
        )

        if (detectResult.isError) {
            return detectResult
        }

        val landmarks = detectResult.getOrNull()!!

        // Validate minimum keypoint count
        if (!landmarks.hasEnoughKeypoints()) {
            return Result.Error(
                IllegalStateException(
                    "Insufficient keypoints detected: ${landmarks.keypointCount} " +
                        "(minimum required: $MIN_KEYPOINTS_REQUIRED)",
                ),
                "Not enough features detected in image",
            )
        }

        return Result.Success(landmarks)
    }

    /**
     * Detects feature keypoints from an image file path.
     *
     * @param imagePath Path to the image file.
     * @param detectorType The feature detector algorithm (ORB or AKAZE).
     * @param maxKeypoints Maximum number of keypoints to detect.
     * @return Result containing LandscapeLandmarks or an error.
     */
    suspend fun fromPath(
        imagePath: String,
        detectorType: FeatureDetectorType = DEFAULT_DETECTOR_TYPE,
        maxKeypoints: Int = DEFAULT_MAX_KEYPOINTS,
    ): Result<LandscapeLandmarks> {
        // Validate input parameters
        if (imagePath.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Image path cannot be empty"),
                "Image path cannot be empty",
            )
        }

        if (maxKeypoints < MIN_KEYPOINTS_REQUIRED) {
            return Result.Error(
                IllegalArgumentException("Max keypoints must be at least $MIN_KEYPOINTS_REQUIRED"),
                "Max keypoints too low",
            )
        }

        // Check if feature detection is available
        if (!featureMatcher.isAvailable) {
            return Result.Error(
                UnsupportedOperationException("Feature detection is not available on this device"),
                "Feature detection not available",
            )
        }

        // Detect features from path
        val detectResult = featureMatcher.detectFeaturesFromPath(
            imagePath = imagePath,
            detectorType = detectorType,
            maxKeypoints = maxKeypoints,
        )

        if (detectResult.isError) {
            return detectResult
        }

        val landmarks = detectResult.getOrNull()!!

        // Validate minimum keypoint count
        if (!landmarks.hasEnoughKeypoints()) {
            return Result.Error(
                IllegalStateException(
                    "Insufficient keypoints detected: ${landmarks.keypointCount} " +
                        "(minimum required: $MIN_KEYPOINTS_REQUIRED)",
                ),
                "Not enough features detected in image",
            )
        }

        return Result.Success(landmarks)
    }

    /**
     * Checks if feature detection is available.
     */
    val isAvailable: Boolean
        get() = featureMatcher.isAvailable

    companion object {
        /** Minimum keypoints required for reliable homography computation. */
        const val MIN_KEYPOINTS_REQUIRED = 10

        /** Default feature detector type. */
        val DEFAULT_DETECTOR_TYPE = FeatureDetectorType.ORB

        /** Default maximum keypoints to detect. */
        const val DEFAULT_MAX_KEYPOINTS = 500
    }
}
