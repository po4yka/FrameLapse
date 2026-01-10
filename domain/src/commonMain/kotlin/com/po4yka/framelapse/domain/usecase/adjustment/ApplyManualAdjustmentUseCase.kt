package com.po4yka.framelapse.domain.usecase.adjustment

import com.po4yka.framelapse.domain.entity.AlignmentSettings
import com.po4yka.framelapse.domain.entity.BodyAlignmentSettings
import com.po4yka.framelapse.domain.entity.BodyManualAdjustment
import com.po4yka.framelapse.domain.entity.ContentType
import com.po4yka.framelapse.domain.entity.FaceManualAdjustment
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.LandscapeManualAdjustment
import com.po4yka.framelapse.domain.entity.ManualAdjustment
import com.po4yka.framelapse.domain.entity.MuscleManualAdjustment
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.repository.ManualAdjustmentRepository
import com.po4yka.framelapse.domain.service.FileSystem
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.usecase.body.CalculateBodyAlignmentMatrixUseCase
import com.po4yka.framelapse.domain.usecase.face.CalculateAlignmentMatrixUseCase
import com.po4yka.framelapse.domain.usecase.landscape.CalculateHomographyMatrixUseCase
import com.po4yka.framelapse.domain.util.Result
import org.koin.core.annotation.Factory

/**
 * Applies a manual adjustment to a frame and generates the aligned image.
 *
 * This use case handles the complete pipeline:
 * 1. Load the original image
 * 2. Convert manual adjustment to landmarks
 * 3. Calculate alignment transformation matrix
 * 4. Apply transformation to generate aligned image
 * 5. Save the aligned image
 * 6. Persist the manual adjustment
 * 7. Update the frame with new aligned path
 *
 * Supports all content types: Face, Body, Muscle, and Landscape.
 */
@Factory
class ApplyManualAdjustmentUseCase(
    private val frameRepository: FrameRepository,
    private val adjustmentRepository: ManualAdjustmentRepository,
    private val imageProcessor: ImageProcessor,
    private val calculateFaceMatrix: CalculateAlignmentMatrixUseCase,
    private val calculateBodyMatrix: CalculateBodyAlignmentMatrixUseCase,
    private val calculateHomography: CalculateHomographyMatrixUseCase,
    private val fileSystem: FileSystem,
) {
    /**
     * Applies a manual adjustment to a frame.
     *
     * @param frameId The ID of the frame to adjust.
     * @param adjustment The manual adjustment to apply.
     * @param contentType The content type (FACE, BODY, MUSCLE, LANDSCAPE).
     * @param settings Optional alignment settings for output size and parameters.
     * @return Result containing the updated Frame or an error.
     */
    suspend operator fun invoke(
        frameId: String,
        adjustment: ManualAdjustment,
        contentType: ContentType,
        settings: AlignmentSettings = AlignmentSettings(),
    ): Result<Frame> {
        // Get the frame
        val frameResult = frameRepository.getFrame(frameId)
        if (frameResult is Result.Error) {
            return frameResult
        }
        val frame = (frameResult as Result.Success).data

        // Load the original image
        val imageResult = imageProcessor.loadImage(frame.originalPath)
        if (imageResult is Result.Error) {
            return Result.Error(imageResult.exception, "Failed to load image")
        }
        val imageData = (imageResult as Result.Success).data

        // Apply the transformation based on content type
        val alignedImageResult = when (adjustment) {
            is FaceManualAdjustment -> applyFaceAdjustment(imageData, adjustment, settings)
            is BodyManualAdjustment -> applyBodyAdjustment(imageData, adjustment, settings)
            is MuscleManualAdjustment -> applyMuscleAdjustment(imageData, adjustment, settings)
            is LandscapeManualAdjustment -> applyLandscapeAdjustment(imageData, adjustment, settings)
        }

        if (alignedImageResult is Result.Error) {
            return alignedImageResult
        }
        val alignedImage = (alignedImageResult as Result.Success).data

        // Generate output path
        val projectDir = fileSystem.getProjectDirectory(frame.projectId)
        val alignedPath = "$projectDir/aligned_${frame.id}.jpg"

        // Save the aligned image
        val saveResult = imageProcessor.saveImage(alignedImage, alignedPath)
        if (saveResult is Result.Error) {
            return Result.Error(saveResult.exception, "Failed to save aligned image")
        }

        // Get landmarks from adjustment for persistence
        val landmarks = adjustment.toLandmarks()

        // Persist the manual adjustment
        val adjustmentSaveResult = adjustmentRepository.saveAdjustment(
            frameId = frameId,
            contentType = contentType,
            adjustment = adjustment,
        )
        if (adjustmentSaveResult is Result.Error) {
            return Result.Error(adjustmentSaveResult.exception, "Failed to save adjustment")
        }

        // Update frame with new alignment data
        if (landmarks != null) {
            val updateResult = frameRepository.updateAlignedFrame(
                id = frameId,
                alignedPath = alignedPath,
                confidence = MANUAL_ADJUSTMENT_CONFIDENCE,
                landmarks = landmarks,
                stabilizationResult = null, // Manual adjustments don't have stabilization metrics
            )
            if (updateResult is Result.Error) {
                return updateResult
            }
        }

        // Return updated frame
        return frameRepository.getFrame(frameId)
    }

    /**
     * Generates a preview of the adjustment without saving.
     *
     * @param frameId The frame ID.
     * @param adjustment The adjustment to preview.
     * @param settings Alignment settings.
     * @return Result containing the preview ImageData or an error.
     */
    suspend fun generatePreview(
        frameId: String,
        adjustment: ManualAdjustment,
        settings: AlignmentSettings = AlignmentSettings(),
    ): Result<ImageData> {
        // Get the frame
        val frameResult = frameRepository.getFrame(frameId)
        if (frameResult is Result.Error) {
            return Result.Error(frameResult.exception, frameResult.message)
        }
        val frame = (frameResult as Result.Success).data

        // Load the original image
        val imageResult = imageProcessor.loadImage(frame.originalPath)
        if (imageResult is Result.Error) {
            return Result.Error(imageResult.exception, "Failed to load image")
        }
        val imageData = (imageResult as Result.Success).data

        // Apply the transformation based on adjustment type
        return when (adjustment) {
            is FaceManualAdjustment -> applyFaceAdjustment(imageData, adjustment, settings)
            is BodyManualAdjustment -> applyBodyAdjustment(imageData, adjustment, settings)
            is MuscleManualAdjustment -> applyMuscleAdjustment(imageData, adjustment, settings)
            is LandscapeManualAdjustment -> applyLandscapeAdjustment(imageData, adjustment, settings)
        }
    }

    private suspend fun applyFaceAdjustment(
        imageData: ImageData,
        adjustment: FaceManualAdjustment,
        settings: AlignmentSettings,
    ): Result<ImageData> {
        val landmarks = adjustment.toLandmarks()
            ?: return Result.Error(
                IllegalStateException("Failed to convert face adjustment to landmarks"),
                "Invalid face adjustment",
            )

        val matrix = calculateFaceMatrix(landmarks, settings)

        return imageProcessor.applyAffineTransform(
            image = imageData,
            matrix = matrix,
            outputWidth = settings.outputSize,
            outputHeight = settings.outputSize,
        )
    }

    private suspend fun applyBodyAdjustment(
        imageData: ImageData,
        adjustment: BodyManualAdjustment,
        settings: AlignmentSettings,
    ): Result<ImageData> {
        val landmarks = adjustment.toLandmarks()
            ?: return Result.Error(
                IllegalStateException("Failed to convert body adjustment to landmarks"),
                "Invalid body adjustment",
            )

        // Convert AlignmentSettings to BodyAlignmentSettings
        val bodySettings = BodyAlignmentSettings(
            outputSize = settings.outputSize,
            stabilizationSettings = settings.stabilizationSettings,
        )
        val matrix = calculateBodyMatrix(landmarks, bodySettings)

        return imageProcessor.applyAffineTransform(
            image = imageData,
            matrix = matrix,
            outputWidth = settings.outputSize,
            outputHeight = settings.outputSize,
        )
    }

    private suspend fun applyMuscleAdjustment(
        imageData: ImageData,
        adjustment: MuscleManualAdjustment,
        settings: AlignmentSettings,
    ): Result<ImageData> {
        // First apply body alignment
        val bodyResult = applyBodyAdjustment(imageData, adjustment.bodyAdjustment, settings)
        if (bodyResult is Result.Error) {
            return bodyResult
        }
        val bodyAligned = (bodyResult as Result.Success).data

        // Then crop to the region bounds
        val pixelBounds = adjustment.regionBounds.toPixelBounds(
            settings.outputSize,
            settings.outputSize,
        )

        return imageProcessor.cropImage(bodyAligned, pixelBounds)
    }

    private suspend fun applyLandscapeAdjustment(
        imageData: ImageData,
        adjustment: LandscapeManualAdjustment,
        settings: AlignmentSettings,
    ): Result<ImageData> {
        // For landscape, we need to calculate homography from the corner keypoints
        // The source corners are the manual adjustment corners
        // The destination corners are the standard output rectangle

        val sourceCorners = adjustment.cornerKeypoints.map { point ->
            // Convert from normalized to pixel coordinates
            com.po4yka.framelapse.domain.entity.LandmarkPoint(
                x = point.x * imageData.width,
                y = point.y * imageData.height,
                z = 0f,
            )
        }

        // Destination corners (full output rectangle)
        val destCorners = listOf(
            com.po4yka.framelapse.domain.entity.LandmarkPoint(0f, 0f, 0f), // Top-left
            com.po4yka.framelapse.domain.entity.LandmarkPoint(settings.outputSize.toFloat(), 0f, 0f), // Top-right
            com.po4yka.framelapse.domain.entity.LandmarkPoint(0f, settings.outputSize.toFloat(), 0f), // Bottom-left
            com.po4yka.framelapse.domain.entity.LandmarkPoint(
                settings.outputSize.toFloat(),
                settings.outputSize.toFloat(),
                0f,
            ), // Bottom-right
        )

        // Calculate homography matrix from corners
        val homographyResult = calculateHomographyFromCorners(sourceCorners, destCorners)
        if (homographyResult is Result.Error) {
            return Result.Error(homographyResult.exception, homographyResult.message)
        }
        val homography = (homographyResult as Result.Success).data

        return imageProcessor.applyHomographyTransform(
            image = imageData,
            matrix = homography,
            outputWidth = settings.outputSize,
            outputHeight = settings.outputSize,
        )
    }

    /**
     * Calculates a simple homography matrix from 4 corner correspondences.
     * This is a simplified implementation for manual corner adjustment.
     */
    private fun calculateHomographyFromCorners(
        sourceCorners: List<com.po4yka.framelapse.domain.entity.LandmarkPoint>,
        destCorners: List<com.po4yka.framelapse.domain.entity.LandmarkPoint>,
    ): Result<com.po4yka.framelapse.domain.entity.HomographyMatrix> {
        if (sourceCorners.size != 4 || destCorners.size != 4) {
            return Result.Error(
                IllegalArgumentException("Homography requires exactly 4 corner points"),
                "Invalid corner count",
            )
        }

        // For a proper implementation, we would use DLT (Direct Linear Transform)
        // This simplified version creates an approximation based on corner offsets
        // In production, this should delegate to the platform's OpenCV binding

        // Calculate center offset
        val srcCenterX = sourceCorners.map { it.x }.average().toFloat()
        val srcCenterY = sourceCorners.map { it.y }.average().toFloat()
        val dstCenterX = destCorners.map { it.x }.average().toFloat()
        val dstCenterY = destCorners.map { it.y }.average().toFloat()

        // Calculate approximate scale
        val srcDiag = kotlin.math.sqrt(
            (sourceCorners[3].x - sourceCorners[0].x).let { it * it } +
                (sourceCorners[3].y - sourceCorners[0].y).let { it * it },
        )
        val dstDiag = kotlin.math.sqrt(
            (destCorners[3].x - destCorners[0].x).let { it * it } +
                (destCorners[3].y - destCorners[0].y).let { it * it },
        )
        val scale = if (srcDiag > 0) dstDiag / srcDiag else 1f

        // Create simplified homography (scale + translate, no perspective)
        // A full implementation would compute perspective coefficients
        return Result.Success(
            com.po4yka.framelapse.domain.entity.HomographyMatrix(
                h11 = scale,
                h12 = 0f,
                h13 = dstCenterX - srcCenterX * scale,
                h21 = 0f,
                h22 = scale,
                h23 = dstCenterY - srcCenterY * scale,
                h31 = 0f,
                h32 = 0f,
                h33 = 1f,
            ),
        )
    }

    companion object {
        /** Confidence score for manually adjusted frames (always high). */
        const val MANUAL_ADJUSTMENT_CONFIDENCE = 1.0f
    }
}
