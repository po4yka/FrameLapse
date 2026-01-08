package com.po4yka.framelapse.domain.usecase.project

import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.repository.ProjectRepository
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager

/**
 * Deletes a project and all its associated data.
 */
class DeleteProjectUseCase(
    private val projectRepository: ProjectRepository,
    private val frameRepository: FrameRepository,
    private val fileManager: FileManager,
) {
    /**
     * Deletes a project, its frames, and all associated files.
     *
     * @param projectId The project ID to delete.
     * @return Result indicating success or failure.
     */
    suspend operator fun invoke(projectId: String): Result<Unit> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        // Check if project exists
        val projectResult = projectRepository.getProject(projectId)
        if (projectResult.isError) {
            return Result.Error(
                NoSuchElementException("Project not found: $projectId"),
                "Project not found",
            )
        }

        // Delete frame files (including imported photos outside project directory)
        val framesResult = frameRepository.getFramesByProject(projectId)
        if (framesResult.isError) {
            return Result.Error(
                framesResult.exceptionOrNull()!!,
                "Failed to retrieve project frames",
            )
        }

        val frames = framesResult.getOrNull()!!
        val deleteErrors = mutableListOf<String>()

        fun deleteIfExists(path: String) {
            if (fileManager.fileExists(path) && !fileManager.deleteFile(path)) {
                deleteErrors.add(path)
            }
        }

        for (frame in frames) {
            deleteIfExists(frame.originalPath)
            frame.alignedPath?.let { deleteIfExists(it) }
        }

        if (deleteErrors.isNotEmpty()) {
            return Result.Error(
                IllegalStateException("Failed to delete frame files: ${deleteErrors.joinToString()}"),
                "Failed to delete some project files",
            )
        }

        // Delete all frames from database (cascade should handle this, but be explicit)
        val deleteFramesResult = frameRepository.deleteFramesByProject(projectId)
        if (deleteFramesResult.isError) {
            return deleteFramesResult
        }

        // Delete project and cleanup remaining files
        return projectRepository.deleteProject(projectId)
    }
}
