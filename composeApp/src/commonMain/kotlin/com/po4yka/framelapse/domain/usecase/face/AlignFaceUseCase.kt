package com.po4yka.framelapse.domain.usecase.face

import com.po4yka.framelapse.domain.entity.AlignmentSettings
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.entity.StabilizationProgress
import com.po4yka.framelapse.domain.entity.StabilizationResult
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.service.FaceDetector
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager

/**
 * Performs face alignment pipeline using multi-pass stabilization.
 *
 * This use case replaces the original single-pass alignment with AgeLapse's
 * multi-pass stabilization algorithm that supports both FAST (4 passes) and
 * SLOW (10+ passes) modes for improved alignment accuracy.
 *
 * The pipeline:
 * 1. Load the original image
 * 2. Determine goal eye positions (from reference frame or defaults)
 * 3. Execute multi-pass stabilization
 * 4. Save aligned image
 * 5. Update frame in database with stabilization metrics
 */
class AlignFaceUseCase(
    private val faceDetector: FaceDetector,
    private val imageProcessor: ImageProcessor,
    private val frameRepository: FrameRepository,
    private val fileManager: FileManager,
    private val multiPassStabilization: MultiPassStabilizationUseCase,
    private val validateAlignment: ValidateAlignmentUseCase = ValidateAlignmentUseCase(),
) {
    /**
     * Aligns a face in the given frame using multi-pass stabilization.
     *
     * @param frame The frame to process.
     * @param referenceFrame Optional reference frame for goal eye positions.
     *                       If provided and has landmarks, its eye positions become the goal.
     *                       Otherwise, default centered positions are calculated.
     * @param settings Alignment configuration (includes stabilization mode).
     * @param onProgress Optional callback for progress updates during stabilization.
     * @return Result containing the updated Frame with alignment data and stabilization metrics.
     */
    suspend operator fun invoke(
        frame: Frame,
        referenceFrame: Frame? = null,
        settings: AlignmentSettings = AlignmentSettings(),
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Frame> {
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

        // Calculate goal eye positions
        val (goalLeftEye, goalRightEye) = calculateGoalEyePositions(
            referenceFrame = referenceFrame,
            settings = settings,
        )

        // Execute multi-pass stabilization
        val stabilizationResult = multiPassStabilization(
            imageData = imageData,
            goalLeftEye = goalLeftEye,
            goalRightEye = goalRightEye,
            alignmentSettings = settings,
            onProgress = onProgress,
        )

        if (stabilizationResult.isError) {
            return Result.Error(
                stabilizationResult.exceptionOrNull()!!,
                "Stabilization failed",
            )
        }

        val (alignedImage, stabResult) = stabilizationResult.getOrNull()!!

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

        // Detect landmarks on aligned image for database storage
        val alignedDetectResult = faceDetector.detectFace(alignedImage)
        val landmarks = alignedDetectResult.getOrNull()

        // Validate alignment quality if landmarks detected
        if (landmarks != null && !validateAlignment(landmarks, settings)) {
            val validation = validateAlignment.getDetailedValidation(landmarks, settings)
            return Result.Error(
                IllegalStateException("Alignment quality too low: ${validation.issues.joinToString()}"),
                "Alignment quality too low",
            )
        }

        // Calculate confidence from stabilization result
        val confidence = calculateConfidenceFromStabilization(stabResult)

        // Update frame in database with stabilization result
        val updateResult = if (landmarks != null) {
            frameRepository.updateAlignedFrame(
                id = frame.id,
                alignedPath = alignedPath,
                confidence = confidence,
                landmarks = landmarks,
                stabilizationResult = stabResult,
            )
        } else {
            // If landmarks detection failed on aligned image, store minimal data
            frameRepository.updateAlignedFrame(
                id = frame.id,
                alignedPath = alignedPath,
                confidence = confidence,
                landmarks = FaceLandmarks(
                    points = emptyList(),
                    leftEyeCenter = normalizePoint(goalLeftEye, settings.outputSize),
                    rightEyeCenter = normalizePoint(goalRightEye, settings.outputSize),
                    noseTip = LandmarkPoint(0.5f, 0.6f, 0f),
                    boundingBox = BoundingBox(0f, 0f, 1f, 1f),
                ),
                stabilizationResult = stabResult,
            )
        }

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
                stabilizationResult = stabResult,
            ),
        )
    }

    /**
     * Calculates goal eye positions for stabilization.
     *
     * If a reference frame with landmarks is provided, uses its eye positions.
     * Otherwise, calculates default centered positions based on settings.
     *
     * @param referenceFrame Optional reference frame with landmarks.
     * @param settings Alignment settings with output size and target eye distance.
     * @return Pair of goal eye positions (left, right) in pixel coordinates.
     */
    private fun calculateGoalEyePositions(
        referenceFrame: Frame?,
        settings: AlignmentSettings,
    ): Pair<LandmarkPoint, LandmarkPoint> {
        // Try to use reference frame landmarks (only if they are face landmarks)
        (referenceFrame?.landmarks as? FaceLandmarks)?.let { refLandmarks ->
            // Convert from normalized to pixel coordinates
            val outputSize = settings.outputSize.toFloat()
            return Pair(
                LandmarkPoint(
                    x = refLandmarks.leftEyeCenter.x * outputSize,
                    y = refLandmarks.leftEyeCenter.y * outputSize,
                    z = refLandmarks.leftEyeCenter.z,
                ),
                LandmarkPoint(
                    x = refLandmarks.rightEyeCenter.x * outputSize,
                    y = refLandmarks.rightEyeCenter.y * outputSize,
                    z = refLandmarks.rightEyeCenter.z,
                ),
            )
        }

        // Calculate default centered positions
        val outputSize = settings.outputSize.toFloat()
        val eyeDistance = settings.targetEyeDistance * outputSize
        val centerX = outputSize / 2
        val centerY = outputSize / 2 - settings.verticalOffset * outputSize

        return Pair(
            LandmarkPoint(x = centerX - eyeDistance / 2, y = centerY, z = 0f),
            LandmarkPoint(x = centerX + eyeDistance / 2, y = centerY, z = 0f),
        )
    }

    /**
     * Normalizes a pixel coordinate point to 0-1 range.
     */
    private fun normalizePoint(point: LandmarkPoint, outputSize: Int): LandmarkPoint = LandmarkPoint(
        x = point.x / outputSize,
        y = point.y / outputSize,
        z = point.z,
    )

    /**
     * Calculates confidence score from stabilization result.
     *
     * Uses the final stabilization score:
     * - Score < 0.5: 1.0 confidence (perfect)
     * - Score < 20.0: 0.7-0.99 confidence (good)
     * - Score >= 20.0: 0.3-0.7 confidence (poor)
     */
    private fun calculateConfidenceFromStabilization(result: StabilizationResult): Float {
        val score = result.finalScore.value
        return when {
            score < 0.5f -> 1.0f
            score < 20.0f -> 0.7f + (20.0f - score) / 20.0f * 0.29f
            else -> (0.7f - (score - 20.0f) / 100.0f * 0.4f).coerceAtLeast(0.3f)
        }
    }
}
