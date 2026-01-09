package com.po4yka.framelapse.domain.usecase.calibration

import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.util.Result

/**
 * Saves calibration data to a project.
 *
 * Calibration consists of:
 * - Reference image path for ghost overlay
 * - Eye positions (left and right) in normalized coordinates (0-1)
 * - Offset adjustments for fine-tuning alignment target
 */
class SaveCalibrationUseCase(
    private val projectRepository: ProjectRepository,
) {

    /**
     * Saves calibration data to the specified project.
     *
     * @param projectId The project ID to save calibration to.
     * @param imagePath Path to the calibration reference image.
     * @param leftEye Left eye position (normalized 0-1).
     * @param rightEye Right eye position (normalized 0-1).
     * @param offsetX Horizontal offset adjustment (-0.2 to +0.2).
     * @param offsetY Vertical offset adjustment (-0.2 to +0.2).
     * @return Result indicating success or failure.
     */
    suspend operator fun invoke(
        projectId: String,
        imagePath: String,
        leftEye: LandmarkPoint,
        rightEye: LandmarkPoint,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
    ): Result<Unit> {
        // Validate inputs
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        if (imagePath.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Image path cannot be empty"),
                "Image path cannot be empty",
            )
        }

        // Validate eye positions are within bounds
        if (!isValidNormalizedPosition(leftEye) || !isValidNormalizedPosition(rightEye)) {
            return Result.Error(
                IllegalArgumentException("Eye positions must be normalized (0-1)"),
                "Invalid eye positions",
            )
        }

        // Validate offsets are within bounds
        val clampedOffsetX = offsetX.coerceIn(OFFSET_MIN, OFFSET_MAX)
        val clampedOffsetY = offsetY.coerceIn(OFFSET_MIN, OFFSET_MAX)

        return projectRepository.updateCalibration(
            projectId = projectId,
            imagePath = imagePath,
            leftEyeX = leftEye.x,
            leftEyeY = leftEye.y,
            rightEyeX = rightEye.x,
            rightEyeY = rightEye.y,
            offsetX = clampedOffsetX,
            offsetY = clampedOffsetY,
        )
    }

    private fun isValidNormalizedPosition(point: LandmarkPoint): Boolean =
        point.x in 0f..1f && point.y in 0f..1f

    companion object {
        private const val OFFSET_MIN = -0.2f
        private const val OFFSET_MAX = 0.2f
    }
}
