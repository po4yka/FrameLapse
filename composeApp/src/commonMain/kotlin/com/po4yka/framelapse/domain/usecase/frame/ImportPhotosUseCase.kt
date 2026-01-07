package com.po4yka.framelapse.domain.usecase.frame

import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.usecase.face.AlignFaceUseCase
import com.po4yka.framelapse.domain.util.Result

/**
 * Batch imports photos into a project with face alignment.
 */
class ImportPhotosUseCase(
    private val addFrameUseCase: AddFrameUseCase,
    private val alignFaceUseCase: AlignFaceUseCase,
) {
    /**
     * Imports multiple photos into a project.
     *
     * @param projectId The project ID.
     * @param photoPaths List of paths to photo files.
     * @param alignFaces Whether to perform face alignment on each photo.
     * @param onProgress Callback for progress updates (current, total).
     * @return Result containing the list of imported frames.
     */
    suspend operator fun invoke(
        projectId: String,
        photoPaths: List<String>,
        alignFaces: Boolean = true,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<List<Frame>> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        if (photoPaths.isEmpty()) {
            return Result.Success(emptyList())
        }

        val importedFrames = mutableListOf<Frame>()
        val errors = mutableListOf<Pair<String, String>>()
        val total = photoPaths.size

        for ((index, photoPath) in photoPaths.withIndex()) {
            onProgress(index + 1, total)

            // Add frame
            val addResult = addFrameUseCase(projectId, photoPath)
            if (addResult.isError) {
                errors.add(photoPath to (addResult.exceptionOrNull()?.message ?: "Unknown error"))
                continue
            }

            var frame = addResult.getOrNull()!!

            // Align face if requested
            if (alignFaces) {
                val alignResult = alignFaceUseCase(frame)
                if (alignResult.isSuccess) {
                    frame = alignResult.getOrNull()!!
                }
                // Continue even if alignment fails - the original image is still usable
            }

            importedFrames.add(frame)
        }

        return if (importedFrames.isNotEmpty()) {
            Result.Success(importedFrames)
        } else if (errors.isNotEmpty()) {
            Result.Error(
                Exception("Failed to import any photos"),
                "Import failed: ${errors.first().second}",
            )
        } else {
            Result.Success(emptyList())
        }
    }
}
