package com.po4yka.framelapse.domain.entity

/**
 * Tracks the progress of a photo import operation.
 *
 * @property currentIndex The current photo being processed (1-based)
 * @property totalCount Total number of photos to import
 * @property currentPhotoPath Path of the photo currently being processed
 * @property importedFrames List of successfully imported frames
 * @property failedPhotos List of photos that failed to import
 * @property phase Current phase of the import operation
 */
data class ImportProgress(
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val currentPhotoPath: String? = null,
    val importedFrames: List<Frame> = emptyList(),
    val failedPhotos: List<ImportError> = emptyList(),
    val phase: ImportPhase = ImportPhase.IDLE,
) {
    /**
     * Progress as a percentage (0-100).
     */
    val progressPercent: Int
        get() = if (totalCount > 0) (currentIndex * 100) / totalCount else 0

    /**
     * Progress as a fraction (0.0-1.0).
     */
    val progressFraction: Float
        get() = if (totalCount > 0) currentIndex.toFloat() / totalCount else 0f
}

/**
 * Phases of the photo import process.
 */
enum class ImportPhase {
    /** No import in progress */
    IDLE,

    /** Copying file to internal storage */
    COPYING,

    /** Detecting face in the image */
    DETECTING,

    /** Aligning the photo based on detected landmarks */
    ALIGNING,

    /** Saving the frame to database */
    SAVING,

    /** Import completed successfully */
    COMPLETE,

    /** Import was cancelled by user */
    CANCELLED,
}

/**
 * Represents an error that occurred while importing a specific photo.
 *
 * @property photoPath Full path to the photo that failed
 * @property fileName Just the filename for display purposes
 * @property errorType Category of error
 * @property message Human-readable error message
 */
data class ImportError(
    val photoPath: String,
    val fileName: String,
    val errorType: ImportErrorType,
    val message: String,
)

/**
 * Categories of import errors for targeted error handling.
 */
enum class ImportErrorType {
    /** The source file could not be found */
    FILE_NOT_FOUND,

    /** The image file is corrupted or unreadable */
    CORRUPT_IMAGE,

    /** No face was detected in the image (for face alignment) */
    NO_FACE_DETECTED,

    /** Face alignment failed */
    ALIGNMENT_FAILED,

    /** Device storage is full */
    STORAGE_FULL,

    /** Permission denied to access the file */
    PERMISSION_DENIED,

    /** Unknown or unclassified error */
    UNKNOWN,
}

/**
 * Result of a photo import operation.
 *
 * @property importedFrames Successfully imported frames
 * @property failedPhotos Photos that failed to import
 * @property wasCancelled Whether the import was cancelled by the user
 */
data class ImportResult(
    val importedFrames: List<Frame>,
    val failedPhotos: List<ImportError>,
    val wasCancelled: Boolean = false,
) {
    /** Total number of photos that were processed (success + failure) */
    val totalProcessed: Int get() = importedFrames.size + failedPhotos.size

    /** Whether all photos were imported successfully */
    val isFullSuccess: Boolean get() = failedPhotos.isEmpty() && !wasCancelled

    /** Whether some photos failed to import */
    val hasFailures: Boolean get() = failedPhotos.isNotEmpty()
}
