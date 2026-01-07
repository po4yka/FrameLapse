package com.po4yka.framelapse.domain.usecase.frame

import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager

/**
 * Adds a new frame to a project.
 */
class AddFrameUseCase(
    private val frameRepository: FrameRepository,
    private val projectRepository: ProjectRepository,
    private val fileManager: FileManager,
) {
    /**
     * Adds a new frame to a project.
     *
     * @param projectId The project ID.
     * @param imagePath Path to the captured/imported image.
     * @return Result containing the added Frame or an error.
     */
    suspend operator fun invoke(projectId: String, imagePath: String): Result<Frame> {
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

        // Check if project exists
        val projectResult = projectRepository.exists(projectId)
        if (projectResult.isError || projectResult.getOrNull() != true) {
            return Result.Error(
                NoSuchElementException("Project not found: $projectId"),
                "Project not found",
            )
        }

        // Check if image file exists
        if (!fileManager.fileExists(imagePath)) {
            return Result.Error(
                IllegalArgumentException("Image file not found: $imagePath"),
                "Image file not found",
            )
        }

        // Get next sort order
        val frameCountResult = frameRepository.getFrameCount(projectId)
        val sortOrder = frameCountResult.getOrNull()?.toInt() ?: 0

        // Create frame
        val now = System.currentTimeMillis()
        val frame = Frame(
            id = generateFrameId(),
            projectId = projectId,
            originalPath = imagePath,
            alignedPath = null,
            timestamp = now,
            capturedAt = now,
            confidence = null,
            landmarks = null,
            sortOrder = sortOrder,
        )

        // Add frame to repository
        val addResult = frameRepository.addFrame(frame)

        // Update project thumbnail if this is the first frame
        if (addResult.isSuccess && sortOrder == 0) {
            projectRepository.updateThumbnail(projectId, imagePath)
        }

        return addResult
    }

    private fun generateFrameId(): String = "frame_${System.currentTimeMillis()}_${(0..9999).random()}"
}
