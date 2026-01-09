package com.po4yka.framelapse.domain.usecase.frame

import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.util.Result

/**
 * Retrieves the latest frame for a project (for ghost image overlay).
 */
class GetLatestFrameUseCase(private val frameRepository: FrameRepository) {
    /**
     * Gets the most recently captured frame for a project.
     *
     * @param projectId The project ID.
     * @return Result containing the latest Frame or null if no frames exist.
     */
    suspend operator fun invoke(projectId: String): Result<Frame?> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        return frameRepository.getLatestFrame(projectId)
    }
}
