package com.po4yka.framelapse.domain.usecase.frame

import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

/**
 * Retrieves frames for a project.
 */
@Factory
class GetFramesUseCase(private val frameRepository: FrameRepository) {
    /**
     * Gets all frames for a project.
     *
     * @param projectId The project ID.
     * @return Result containing a list of frames sorted by sortOrder/timestamp.
     */
    suspend operator fun invoke(projectId: String): Result<List<Frame>> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        return frameRepository.getFramesByProject(projectId)
    }

    /**
     * Observes frames for a project reactively.
     *
     * @param projectId The project ID.
     * @return Flow emitting the list of frames when changes occur.
     */
    fun observe(projectId: String): Flow<List<Frame>> = frameRepository.observeFrames(projectId)

    /**
     * Gets frames within a date range.
     *
     * @param projectId The project ID.
     * @param startTimestamp Start of the range (inclusive).
     * @param endTimestamp End of the range (inclusive).
     * @return Result containing matching frames.
     */
    suspend fun getByDateRange(projectId: String, startTimestamp: Long, endTimestamp: Long): Result<List<Frame>> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        if (startTimestamp > endTimestamp) {
            return Result.Error(
                IllegalArgumentException("Start timestamp must be before or equal to end timestamp"),
                "Invalid date range",
            )
        }

        return frameRepository.getFramesByDateRange(projectId, startTimestamp, endTimestamp)
    }
}
