package com.po4yka.framelapse.domain.usecase.face

import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.service.FaceDetector
import com.po4yka.framelapse.domain.util.Result
import org.koin.core.annotation.Factory

/**
 * Detects face landmarks in an image.
 */
@Factory
class DetectFaceUseCase(private val faceDetector: FaceDetector) {
    /**
     * Detects face landmarks in an image file.
     *
     * @param imagePath Path to the image file.
     * @return Result containing FaceLandmarks or null if no face found.
     */
    suspend operator fun invoke(imagePath: String): Result<FaceLandmarks?> {
        if (imagePath.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Image path cannot be empty"),
                "Image path cannot be empty",
            )
        }

        if (!faceDetector.isAvailable) {
            return Result.Error(
                UnsupportedOperationException("Face detection is not available on this device"),
                "Face detection not available",
            )
        }

        return faceDetector.detectFaceFromPath(imagePath)
    }

    /**
     * Checks if face detection is available.
     */
    val isAvailable: Boolean
        get() = faceDetector.isAvailable
}
