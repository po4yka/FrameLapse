package com.po4yka.framelapse.domain.usecase.muscle

import com.po4yka.framelapse.domain.entity.BodyLandmarks
import com.po4yka.framelapse.domain.entity.MuscleAlignmentSettings
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.util.Result

/**
 * Crops an aligned image to the specified muscle region.
 *
 * Takes an already body-aligned image and crops it to focus on the
 * target muscle region. The output is always square for consistent
 * timelapse framing.
 *
 * Pipeline:
 * 1. Calculate region bounds from body landmarks
 * 2. Convert to pixel coordinates
 * 3. Crop the image
 * 4. Resize to square output size
 */
class CropToMuscleRegionUseCase(
    private val imageProcessor: ImageProcessor,
    private val calculateBounds: CalculateMuscleRegionBoundsUseCase,
) {

    /**
     * Crops an aligned image to the muscle region.
     *
     * @param alignedImage The body-aligned image to crop.
     * @param landmarks Body landmarks detected in the aligned image.
     * @param settings Muscle alignment settings including target region.
     * @return Result containing the cropped and resized ImageData.
     */
    suspend operator fun invoke(
        alignedImage: ImageData,
        landmarks: BodyLandmarks,
        settings: MuscleAlignmentSettings,
    ): Result<ImageData> {
        // Calculate region bounds using body landmarks
        val bounds = calculateBounds(
            landmarks = landmarks,
            region = settings.muscleRegion,
            padding = settings.regionPadding,
        )

        // Convert normalized bounds to pixel coordinates
        val pixelBounds = bounds.toPixelBounds(alignedImage.width, alignedImage.height)

        // Crop the image to the region
        val cropResult = imageProcessor.cropImage(alignedImage, pixelBounds)
        if (cropResult is Result.Error) {
            return Result.Error(
                cropResult.exception,
                "Failed to crop to ${settings.muscleRegion.displayName} region: ${cropResult.message}",
            )
        }

        val croppedImage = (cropResult as Result.Success).data

        // Resize to square output size
        return imageProcessor.resizeImage(
            image = croppedImage,
            width = settings.outputSize,
            height = settings.outputSize,
            maintainAspectRatio = false, // Force exact square dimensions
        )
    }
}
