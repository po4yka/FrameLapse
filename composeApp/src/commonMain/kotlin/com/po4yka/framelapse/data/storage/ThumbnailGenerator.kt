package com.po4yka.framelapse.data.storage

import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.util.Result

/**
 * Generates thumbnail images for projects.
 */
class ThumbnailGenerator(
    private val imageProcessor: ImageProcessor,
    private val imageStorageManager: ImageStorageManager,
) {

    /**
     * Generates a thumbnail from a source image.
     *
     * @param sourcePath Path to the source image.
     * @param projectId Project ID for storage location.
     * @param size Target thumbnail size (square).
     * @return Result containing the thumbnail path or an error.
     */
    suspend fun generateThumbnail(
        sourcePath: String,
        projectId: String,
        size: Int = DEFAULT_THUMBNAIL_SIZE,
    ): Result<String> {
        val loadResult = imageProcessor.loadImage(sourcePath)
        if (loadResult is Result.Error) {
            return Result.Error(loadResult.exception, "Failed to load source image: ${loadResult.message}")
        }

        val imageData = (loadResult as Result.Success).data

        val resizeResult = imageProcessor.resizeImage(
            image = imageData,
            width = size,
            height = size,
            maintainAspectRatio = true,
        )
        if (resizeResult is Result.Error) {
            return Result.Error(resizeResult.exception, "Failed to resize image: ${resizeResult.message}")
        }

        val resizedData = (resizeResult as Result.Success).data
        val thumbnailPath = imageStorageManager.getThumbnailPath(projectId)

        val saveResult = imageProcessor.saveImage(
            data = resizedData,
            path = thumbnailPath,
            quality = THUMBNAIL_QUALITY,
        )

        return when (saveResult) {
            is Result.Success -> Result.Success(thumbnailPath)
            is Result.Error -> Result.Error(saveResult.exception, "Failed to save thumbnail: ${saveResult.message}")
            is Result.Loading -> Result.Error(
                IllegalStateException("Unexpected loading state"),
                "Unexpected loading state",
            )
        }
    }

    /**
     * Deletes the thumbnail for a project.
     *
     * @param projectId Project ID.
     * @return Result indicating success or failure.
     */
    suspend fun deleteThumbnail(projectId: String): Result<Unit> {
        val thumbnailPath = imageStorageManager.getThumbnailPath(projectId)
        return imageStorageManager.deleteImage(thumbnailPath)
    }

    companion object {
        const val DEFAULT_THUMBNAIL_SIZE = 256
        const val THUMBNAIL_QUALITY = 80
    }
}
