package com.po4yka.framelapse.domain.usecase.frame

import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.ImportError
import com.po4yka.framelapse.domain.entity.ImportErrorType
import com.po4yka.framelapse.domain.entity.ImportPhase
import com.po4yka.framelapse.domain.entity.ImportProgress
import com.po4yka.framelapse.domain.entity.ImportResult
import com.po4yka.framelapse.domain.usecase.face.AlignFaceUseCase
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.isActive
import org.koin.core.annotation.Factory
import kotlin.coroutines.coroutineContext

/**
 * Batch imports photos into a project with face alignment.
 */
@Factory
class ImportPhotosUseCase(
    private val addFrameUseCase: AddFrameUseCase,
    private val alignFaceUseCase: AlignFaceUseCase,
) {
    /**
     * Imports multiple photos into a project with detailed progress reporting.
     *
     * @param projectId The project ID.
     * @param photoPaths List of paths to photo files.
     * @param alignFaces Whether to perform face alignment on each photo.
     * @param onProgress Callback for detailed progress updates.
     * @return Result containing the import result with success and failure details.
     */
    suspend operator fun invoke(
        projectId: String,
        photoPaths: List<String>,
        alignFaces: Boolean = true,
        onProgress: (ImportProgress) -> Unit = { },
    ): Result<ImportResult> {
        if (projectId.isBlank()) {
            return Result.Error(
                IllegalArgumentException("Project ID cannot be empty"),
                "Project ID cannot be empty",
            )
        }

        if (photoPaths.isEmpty()) {
            return Result.Success(ImportResult(emptyList(), emptyList()))
        }

        val importedFrames = mutableListOf<Frame>()
        val errors = mutableListOf<ImportError>()
        val total = photoPaths.size

        for ((index, photoPath) in photoPaths.withIndex()) {
            // Check for cancellation
            if (!coroutineContext.isActive) {
                onProgress(
                    ImportProgress(
                        currentIndex = index,
                        totalCount = total,
                        importedFrames = importedFrames.toList(),
                        failedPhotos = errors.toList(),
                        phase = ImportPhase.CANCELLED,
                    ),
                )
                return Result.Success(
                    ImportResult(
                        importedFrames = importedFrames.toList(),
                        failedPhotos = errors.toList(),
                        wasCancelled = true,
                    ),
                )
            }

            val fileName = photoPath.substringAfterLast("/")

            // Report progress - saving phase
            onProgress(
                ImportProgress(
                    currentIndex = index + 1,
                    totalCount = total,
                    currentPhotoPath = photoPath,
                    importedFrames = importedFrames.toList(),
                    failedPhotos = errors.toList(),
                    phase = ImportPhase.SAVING,
                ),
            )

            // Add frame
            val addResult = addFrameUseCase(projectId, photoPath)
            if (addResult.isError) {
                errors.add(
                    ImportError(
                        photoPath = photoPath,
                        fileName = fileName,
                        errorType = categorizeError(addResult.exceptionOrNull()),
                        message = addResult.exceptionOrNull()?.message ?: "Failed to add frame",
                    ),
                )
                continue
            }

            var frame = addResult.getOrNull()!!

            // Align face if requested
            if (alignFaces) {
                // Report progress - aligning phase
                onProgress(
                    ImportProgress(
                        currentIndex = index + 1,
                        totalCount = total,
                        currentPhotoPath = photoPath,
                        importedFrames = importedFrames.toList(),
                        failedPhotos = errors.toList(),
                        phase = ImportPhase.ALIGNING,
                    ),
                )

                val alignResult = alignFaceUseCase(frame)
                if (alignResult.isSuccess) {
                    frame = alignResult.getOrNull()!!
                }
                // Continue even if alignment fails - the original image is still usable
            }

            importedFrames.add(frame)
        }

        // Report completion
        onProgress(
            ImportProgress(
                currentIndex = total,
                totalCount = total,
                importedFrames = importedFrames.toList(),
                failedPhotos = errors.toList(),
                phase = ImportPhase.COMPLETE,
            ),
        )

        return Result.Success(
            ImportResult(
                importedFrames = importedFrames.toList(),
                failedPhotos = errors.toList(),
            ),
        )
    }

    private fun categorizeError(exception: Throwable?): ImportErrorType {
        if (exception == null) return ImportErrorType.UNKNOWN

        val message = exception.message?.lowercase() ?: ""
        return when {
            message.contains("not found") || message.contains("no such file") ->
                ImportErrorType.FILE_NOT_FOUND

            message.contains("corrupt") ||
                message.contains("invalid") ||
                message.contains("decode") ||
                message.contains("format") ->
                ImportErrorType.CORRUPT_IMAGE

            message.contains("no face") || message.contains("face not") ->
                ImportErrorType.NO_FACE_DETECTED

            message.contains("align") ->
                ImportErrorType.ALIGNMENT_FAILED

            message.contains("storage") ||
                message.contains("space") ||
                message.contains("disk") ||
                message.contains("full") ->
                ImportErrorType.STORAGE_FULL

            message.contains("permission") || message.contains("denied") ->
                ImportErrorType.PERMISSION_DENIED

            else -> ImportErrorType.UNKNOWN
        }
    }
}
