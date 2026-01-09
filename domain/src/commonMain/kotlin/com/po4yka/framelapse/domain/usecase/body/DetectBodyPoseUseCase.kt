package com.po4yka.framelapse.domain.usecase.body

import com.po4yka.framelapse.domain.entity.BodyLandmarks
import com.po4yka.framelapse.domain.service.BodyPoseDetector
import com.po4yka.framelapse.domain.util.Result

/**
 * Detects body pose landmarks in an image.
 */
class DetectBodyPoseUseCase(private val bodyPoseDetector: BodyPoseDetector) {
    /**
     * Detects body pose landmarks in an image file.
     *
     * @param imagePath Path to the image file.
     * @return Result containing BodyLandmarks or null if no body found.
     */
    suspend operator fun invoke(imagePath: String): Result<BodyLandmarks?> {
        if (imagePath.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Image path cannot be empty"),
                "Image path cannot be empty",
            )
        }

        if (!bodyPoseDetector.isAvailable) {
            return Result.Error(
                UnsupportedOperationException("Body pose detection is not available on this device"),
                "Body pose detection not available",
            )
        }

        return bodyPoseDetector.detectBodyPoseFromPath(imagePath)
    }

    /**
     * Checks if body pose detection is available.
     */
    val isAvailable: Boolean
        get() = bodyPoseDetector.isAvailable
}
