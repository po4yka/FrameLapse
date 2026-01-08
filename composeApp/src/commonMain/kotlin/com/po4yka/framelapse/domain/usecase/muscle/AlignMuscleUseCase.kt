package com.po4yka.framelapse.domain.usecase.muscle

import com.po4yka.framelapse.domain.entity.BodyLandmarks
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.MuscleAlignmentSettings
import com.po4yka.framelapse.domain.entity.StabilizationProgress
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.service.BodyPoseDetector
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.usecase.body.AlignBodyUseCase
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager

/**
 * Performs muscle alignment: body alignment followed by region-specific cropping.
 *
 * This use case combines body alignment (shoulder-based stabilization) with
 * targeted cropping to focus on specific muscle groups for fitness progress tracking.
 *
 * Pipeline:
 * 1. Execute body alignment (shoulder-based stabilization)
 * 2. Load the aligned image
 * 3. Detect body landmarks on aligned image
 * 4. Calculate crop bounds for the target muscle region
 * 5. Crop and resize to square output
 * 6. Save the muscle-cropped image
 * 7. Update frame in database
 */
class AlignMuscleUseCase(
    private val alignBody: AlignBodyUseCase,
    private val bodyPoseDetector: BodyPoseDetector,
    private val cropToRegion: CropToMuscleRegionUseCase,
    private val imageProcessor: ImageProcessor,
    private val frameRepository: FrameRepository,
    private val fileManager: FileManager,
) {

    /**
     * Aligns a body and crops to the specified muscle region.
     *
     * @param frame The frame to process.
     * @param referenceFrame Optional reference frame for consistent alignment.
     * @param settings Muscle alignment settings including target region.
     * @param onProgress Optional callback for progress updates during stabilization.
     * @return Result containing the updated Frame with muscle-cropped image.
     */
    suspend operator fun invoke(
        frame: Frame,
        referenceFrame: Frame? = null,
        settings: MuscleAlignmentSettings = MuscleAlignmentSettings(),
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Frame> {
        // Step 1: Body alignment (shoulder-based stabilization)
        val bodyAlignResult = alignBody(
            frame = frame,
            referenceFrame = referenceFrame,
            settings = settings.bodyAlignmentSettings,
            onProgress = onProgress,
        )

        if (bodyAlignResult is Result.Error) {
            return Result.Error(
                bodyAlignResult.exception,
                "Body alignment failed: ${bodyAlignResult.message}",
            )
        }

        val alignedFrame = (bodyAlignResult as Result.Success).data
        val alignedPath = alignedFrame.alignedPath ?: return Result.Error(
            IllegalStateException("Body alignment did not produce aligned image"),
            "Body alignment failed - no aligned image produced",
        )

        // Step 2: Load aligned image
        val loadResult = imageProcessor.loadImage(alignedPath)
        if (loadResult is Result.Error) {
            return Result.Error(
                loadResult.exception,
                "Failed to load aligned image: ${loadResult.message}",
            )
        }
        val alignedImage = (loadResult as Result.Success).data

        // Step 3: Get body landmarks (from aligned frame or re-detect)
        val landmarks = alignedFrame.landmarks as? BodyLandmarks
            ?: bodyPoseDetector.detectBodyPose(alignedImage).getOrNull()
            ?: return Result.Error(
                IllegalStateException("No body landmarks available for cropping"),
                "Could not detect body for muscle region cropping",
            )

        // Step 4 & 5: Crop to muscle region
        val cropResult = cropToRegion(
            alignedImage = alignedImage,
            landmarks = landmarks,
            settings = settings,
        )

        if (cropResult is Result.Error) {
            return Result.Error(
                cropResult.exception,
                "Failed to crop to ${settings.muscleRegion.displayName}: ${cropResult.message}",
            )
        }
        val croppedImage = (cropResult as Result.Success).data

        // Step 6: Save muscle-cropped image
        val projectDir = fileManager.getProjectDirectory(frame.projectId)
        val musclePath = "$projectDir/muscle_${frame.id}.jpg"

        val saveResult = imageProcessor.saveImage(croppedImage, musclePath)
        if (saveResult is Result.Error) {
            return Result.Error(
                saveResult.exception,
                "Failed to save muscle image: ${saveResult.message}",
            )
        }

        // Step 7: Update frame in database with muscle-cropped path
        // Note: We store the muscle path as the aligned path since that's what will be used for export
        val updateResult = frameRepository.updateAlignedFrame(
            id = frame.id,
            alignedPath = musclePath,
            confidence = alignedFrame.confidence ?: 0f,
            landmarks = landmarks,
            stabilizationResult = alignedFrame.stabilizationResult,
        )

        if (updateResult is Result.Error) {
            return Result.Error(
                updateResult.exception,
                "Failed to update frame: ${updateResult.message}",
            )
        }

        // Return updated frame
        return Result.Success(
            alignedFrame.copy(
                alignedPath = musclePath,
            ),
        )
    }
}
