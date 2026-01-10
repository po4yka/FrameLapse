package com.po4yka.framelapse.domain.usecase.calibration

import com.po4yka.framelapse.domain.entity.FaceProjectContent
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.util.Result
import org.koin.core.annotation.Factory

/**
 * Retrieves calibration data for a project.
 */
@Factory
class GetCalibrationUseCase(private val projectRepository: ProjectRepository) {

    /**
     * Gets calibration data for the specified project.
     *
     * @param projectId The project ID.
     * @return Result containing CalibrationData if calibration exists, null if not calibrated.
     */
    suspend operator fun invoke(projectId: String): Result<CalibrationData?> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        val projectResult = projectRepository.getProject(projectId)
        if (projectResult is Result.Error) {
            return Result.Error(projectResult.exception, projectResult.message)
        }

        val project = (projectResult as Result.Success).data
        return Result.Success(project.toCalibrationData())
    }

    /**
     * Checks if a project has calibration data.
     *
     * @param projectId The project ID.
     * @return Result containing true if calibrated, false otherwise.
     */
    suspend fun hasCalibration(projectId: String): Result<Boolean> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        val projectResult = projectRepository.getProject(projectId)
        if (projectResult is Result.Error) {
            return Result.Error(projectResult.exception, projectResult.message)
        }

        val project = (projectResult as Result.Success).data
        val faceContent = project.content as? FaceProjectContent
        return Result.Success(faceContent?.calibrationImagePath != null)
    }
}

/**
 * Calibration data extracted from a Project.
 */
data class CalibrationData(
    /** Path to the calibration reference image. */
    val imagePath: String,
    /** Calibrated left eye position (normalized 0-1). */
    val leftEye: LandmarkPoint,
    /** Calibrated right eye position (normalized 0-1). */
    val rightEye: LandmarkPoint,
    /** Horizontal offset adjustment. */
    val offsetX: Float,
    /** Vertical offset adjustment. */
    val offsetY: Float,
)

/**
 * Extension function to extract calibration data from a Project.
 * Returns null if the project has no calibration.
 */
fun Project.toCalibrationData(): CalibrationData? {
    val faceContent = content as? FaceProjectContent ?: return null
    val imagePath = faceContent.calibrationImagePath ?: return null
    val leftEyeX = faceContent.calibrationLeftEyeX ?: return null
    val leftEyeY = faceContent.calibrationLeftEyeY ?: return null
    val rightEyeX = faceContent.calibrationRightEyeX ?: return null
    val rightEyeY = faceContent.calibrationRightEyeY ?: return null

    return CalibrationData(
        imagePath = imagePath,
        leftEye = LandmarkPoint(x = leftEyeX, y = leftEyeY, z = 0f),
        rightEye = LandmarkPoint(x = rightEyeX, y = rightEyeY, z = 0f),
        offsetX = faceContent.calibrationOffsetX,
        offsetY = faceContent.calibrationOffsetY,
    )
}

/**
 * Extension property to check if a project has calibration.
 */
val Project.hasCalibration: Boolean
    get() {
        val faceContent = content as? FaceProjectContent ?: return false
        return faceContent.calibrationImagePath != null &&
            faceContent.calibrationLeftEyeX != null &&
            faceContent.calibrationRightEyeX != null
    }
