package com.po4yka.framelapse.domain.usecase.capture

import com.po4yka.framelapse.domain.entity.AlignmentSettings
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.service.CameraController
import com.po4yka.framelapse.domain.usecase.face.AlignFaceUseCase
import com.po4yka.framelapse.domain.usecase.frame.AddFrameUseCase
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager

/**
 * Captures an image, adds it to a project, and performs face alignment.
 *
 * This is the main capture flow that combines:
 * 1. Camera image capture
 * 2. Frame creation
 * 3. Face detection and alignment
 */
class CaptureImageUseCase(
    private val cameraController: CameraController,
    private val addFrameUseCase: AddFrameUseCase,
    private val alignFaceUseCase: AlignFaceUseCase,
    private val fileManager: FileManager,
) {
    /**
     * Captures an image and adds it to the project.
     *
     * @param projectId The project ID.
     * @param alignFace Whether to perform face alignment.
     * @param alignmentSettings Optional alignment configuration.
     * @return Result containing the captured Frame.
     */
    suspend operator fun invoke(
        projectId: String,
        alignFace: Boolean = true,
        alignmentSettings: AlignmentSettings = AlignmentSettings(),
    ): Result<Frame> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        // Generate output path for captured image
        val projectDir = fileManager.getProjectDirectory(projectId)
        val timestamp = System.currentTimeMillis()
        val capturePath = "$projectDir/capture_$timestamp.jpg"

        // Capture image
        val captureResult = cameraController.captureImage(capturePath)
        if (captureResult.isError) {
            return Result.Error(
                captureResult.exceptionOrNull()!!,
                "Failed to capture image",
            )
        }

        val savedPath = captureResult.getOrNull()!!

        // Add frame to project
        val addResult = addFrameUseCase(projectId, savedPath)
        if (addResult.isError) {
            // Clean up the captured file on failure
            fileManager.deleteFile(savedPath)
            return Result.Error(
                addResult.exceptionOrNull()!!,
                "Failed to save frame",
            )
        }

        var frame = addResult.getOrNull()!!

        // Perform face alignment if requested
        if (alignFace) {
            val alignResult = alignFaceUseCase(frame, alignmentSettings)
            if (alignResult.isSuccess) {
                frame = alignResult.getOrNull()!!
            }
            // Continue even if alignment fails - the original image is still saved
        }

        return Result.Success(frame)
    }
}
