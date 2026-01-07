package com.po4yka.framelapse.domain.usecase.face

import com.po4yka.framelapse.domain.entity.AlignmentSettings
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.service.FaceDetector
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager

/**
 * Performs full face alignment pipeline on a frame.
 *
 * The pipeline:
 * 1. Load the original image
 * 2. Detect face landmarks
 * 3. Validate detection quality
 * 4. Calculate alignment transformation
 * 5. Apply transformation
 * 6. Save aligned image
 * 7. Update frame in database
 */
class AlignFaceUseCase(
    private val faceDetector: FaceDetector,
    private val imageProcessor: ImageProcessor,
    private val frameRepository: FrameRepository,
    private val fileManager: FileManager,
    private val calculateAlignmentMatrix: CalculateAlignmentMatrixUseCase = CalculateAlignmentMatrixUseCase(),
    private val validateAlignment: ValidateAlignmentUseCase = ValidateAlignmentUseCase(),
) {
    /**
     * Aligns a face in the given frame.
     *
     * @param frame The frame to process.
     * @param settings Alignment configuration.
     * @return Result containing the updated Frame with alignment data.
     */
    suspend operator fun invoke(frame: Frame, settings: AlignmentSettings = AlignmentSettings()): Result<Frame> {
        // Skip if already aligned
        if (frame.alignedPath != null && frame.landmarks != null) {
            return Result.Success(frame)
        }

        // Check if face detection is available
        if (!faceDetector.isAvailable) {
            return Result.Error(
                UnsupportedOperationException("Face detection is not available"),
                "Face detection not available",
            )
        }

        // Load original image
        val imageResult = imageProcessor.loadImage(frame.originalPath)
        if (imageResult.isError) {
            return Result.Error(
                imageResult.exceptionOrNull()!!,
                "Failed to load image",
            )
        }
        val imageData = imageResult.getOrNull()!!

        // Detect face
        val detectResult = faceDetector.detectFace(imageData)
        if (detectResult.isError) {
            return Result.Error(
                detectResult.exceptionOrNull()!!,
                "Face detection failed",
            )
        }

        val landmarks = detectResult.getOrNull()
        if (landmarks == null) {
            return Result.Error(
                NoSuchElementException("No face detected in image"),
                "No face detected",
            )
        }

        // Validate detection quality
        if (!validateAlignment(landmarks, settings)) {
            val validation = validateAlignment.getDetailedValidation(landmarks, settings)
            return Result.Error(
                IllegalStateException("Face detection quality too low: ${validation.issues.joinToString()}"),
                "Face detection quality too low",
            )
        }

        // Calculate alignment matrix
        val matrix = calculateAlignmentMatrix(landmarks, settings)

        // Apply transformation
        val transformResult = imageProcessor.applyAffineTransform(
            image = imageData,
            matrix = matrix,
            outputWidth = settings.outputSize,
            outputHeight = settings.outputSize,
        )
        if (transformResult.isError) {
            return Result.Error(
                transformResult.exceptionOrNull()!!,
                "Failed to apply transformation",
            )
        }
        val alignedImage = transformResult.getOrNull()!!

        // Generate aligned image path
        val projectDir = fileManager.getProjectDirectory(frame.projectId)
        val alignedPath = "$projectDir/aligned_${frame.id}.jpg"

        // Save aligned image
        val saveResult = imageProcessor.saveImage(alignedImage, alignedPath)
        if (saveResult.isError) {
            return Result.Error(
                saveResult.exceptionOrNull()!!,
                "Failed to save aligned image",
            )
        }

        // Calculate confidence (use bounding box area ratio as proxy)
        val confidence = calculateConfidence(landmarks, imageData.width, imageData.height)

        // Update frame in database
        val updateResult = frameRepository.updateAlignedFrame(
            id = frame.id,
            alignedPath = alignedPath,
            confidence = confidence,
            landmarks = landmarks,
        )
        if (updateResult.isError) {
            return Result.Error(
                updateResult.exceptionOrNull()!!,
                "Failed to update frame",
            )
        }

        // Return updated frame
        return Result.Success(
            frame.copy(
                alignedPath = alignedPath,
                confidence = confidence,
                landmarks = landmarks,
            ),
        )
    }

    private fun calculateConfidence(
        landmarks: com.po4yka.framelapse.domain.entity.FaceLandmarks,
        imageWidth: Int,
        imageHeight: Int,
    ): Float {
        // Calculate confidence based on:
        // 1. Face size relative to image (larger is better)
        // 2. Face position (centered is better)

        val box = landmarks.boundingBox
        val faceArea = box.width * box.height
        val imageArea = imageWidth.toFloat() * imageHeight
        val areaRatio = (faceArea / imageArea).coerceIn(0f, 1f)

        // Score based on face taking up a reasonable portion of the image
        val sizeScore = when {
            areaRatio < 0.05f -> 0.5f // Face too small
            areaRatio > 0.8f -> 0.8f // Face too close
            else -> 1.0f
        }

        // Score based on face being centered
        val faceCenterX = box.centerX / imageWidth
        val faceCenterY = box.centerY / imageHeight
        val centerScore = 1f - (kotlin.math.abs(faceCenterX - 0.5f) + kotlin.math.abs(faceCenterY - 0.5f))

        return (sizeScore * 0.6f + centerScore * 0.4f).coerceIn(0f, 1f)
    }
}
