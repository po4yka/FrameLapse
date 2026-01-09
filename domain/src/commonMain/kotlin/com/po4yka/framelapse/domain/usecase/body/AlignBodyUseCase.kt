package com.po4yka.framelapse.domain.usecase.body

import com.po4yka.framelapse.domain.entity.AlignmentDiagnostics
import com.po4yka.framelapse.domain.entity.BodyAlignmentSettings
import com.po4yka.framelapse.domain.entity.BodyLandmarks
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.entity.StabilizationProgress
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.service.BodyPoseDetector
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.service.MediaStore
import com.po4yka.framelapse.domain.util.ConfidenceCalculator
import com.po4yka.framelapse.domain.util.Result

/**
 * Performs body alignment pipeline using multi-pass stabilization.
 *
 * This use case aligns body poses based on shoulder positions, similar to
 * AlignFaceUseCase but using shoulders as reference points instead of eyes.
 *
 * The pipeline:
 * 1. Load the original image
 * 2. Determine goal shoulder positions (from reference frame or defaults)
 * 3. Execute multi-pass stabilization
 * 4. Save aligned image
 * 5. Update frame in database with stabilization metrics
 */
class AlignBodyUseCase(
    private val bodyPoseDetector: BodyPoseDetector,
    private val imageProcessor: ImageProcessor,
    private val frameRepository: FrameRepository,
    private val mediaStore: MediaStore,
    private val multiPassBodyStabilization: MultiPassBodyStabilizationUseCase,
    private val validateBodyAlignment: ValidateBodyAlignmentUseCase = ValidateBodyAlignmentUseCase(),
) {
    /**
     * Aligns a body in the given frame using multi-pass stabilization.
     *
     * @param frame The frame to process.
     * @param referenceFrame Optional reference frame for goal shoulder positions.
     *                       If provided and has body landmarks, its shoulder positions become the goal.
     *                       Otherwise, default centered positions are calculated.
     * @param settings Body alignment configuration (includes stabilization mode).
     * @param onProgress Optional callback for progress updates during stabilization.
     * @return Result containing the updated Frame with alignment data and stabilization metrics.
     */
    suspend operator fun invoke(
        frame: Frame,
        referenceFrame: Frame? = null,
        settings: BodyAlignmentSettings = BodyAlignmentSettings(),
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Frame> {
        // Skip if already aligned
        if (frame.alignedPath != null && frame.landmarks != null) {
            return Result.Success(frame)
        }

        // Check if body pose detection is available
        if (!bodyPoseDetector.isAvailable) {
            return Result.Error(
                UnsupportedOperationException("Body pose detection is not available"),
                "Body pose detection not available",
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

        // Calculate goal shoulder positions
        val (goalLeftShoulder, goalRightShoulder) = calculateGoalShoulderPositions(
            referenceFrame = referenceFrame,
            settings = settings,
        )

        // Execute multi-pass stabilization
        val stabilizationResult = multiPassBodyStabilization(
            imageData = imageData,
            goalLeftShoulder = goalLeftShoulder,
            goalRightShoulder = goalRightShoulder,
            bodyAlignmentSettings = settings,
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
        val alignedPath = mediaStore.getAlignedPath(frame.projectId, frame.originalPath)

        // Save aligned image
        val saveResult = imageProcessor.saveImage(alignedImage, alignedPath)
        if (saveResult.isError) {
            return Result.Error(
                saveResult.exceptionOrNull()!!,
                "Failed to save aligned image",
            )
        }

        // Detect landmarks on aligned image for database storage
        val alignedDetectResult = bodyPoseDetector.detectBodyPose(alignedImage)
        val landmarks = alignedDetectResult.getOrNull()
        val diagnostics = AlignmentDiagnostics(
            alignedLandmarksDetected = landmarks != null,
            alignedLandmarksError = when {
                alignedDetectResult.isError -> alignedDetectResult.exceptionOrNull()?.message ?: "Body detection failed"
                landmarks == null -> "No body detected in aligned image"
                else -> null
            },
            fallbackLandmarksGenerated = landmarks == null,
            referenceFrameId = referenceFrame?.id,
        )

        // Validate alignment quality if landmarks detected
        if (landmarks != null && !validateBodyAlignment(landmarks, settings)) {
            val validation = validateBodyAlignment.getDetailedValidation(landmarks, settings)
            return Result.Error(
                IllegalStateException("Alignment quality too low: ${validation.issues.joinToString()}"),
                "Alignment quality too low",
            )
        }

        // Calculate confidence from stabilization result
        val confidence = ConfidenceCalculator.fromStabilizationResult(stabResult)

        // Update frame in database with stabilization result
        val stabilizedResult = stabResult.copy(diagnostics = diagnostics)
        val updateResult = if (landmarks != null) {
            frameRepository.updateAlignedFrame(
                id = frame.id,
                alignedPath = alignedPath,
                confidence = confidence,
                landmarks = landmarks,
                stabilizationResult = stabilizedResult,
            )
        } else {
            // If landmarks detection failed on aligned image, store minimal data
            frameRepository.updateAlignedFrame(
                id = frame.id,
                alignedPath = alignedPath,
                confidence = confidence,
                landmarks = createMinimalBodyLandmarks(goalLeftShoulder, goalRightShoulder, settings.outputSize),
                stabilizationResult = stabilizedResult,
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
                stabilizationResult = stabilizedResult,
            ),
        )
    }

    /**
     * Calculates goal shoulder positions for stabilization.
     *
     * If a reference frame with body landmarks is provided, uses its shoulder positions.
     * Otherwise, calculates default centered positions based on settings.
     *
     * @param referenceFrame Optional reference frame with landmarks.
     * @param settings Body alignment settings with output size and target shoulder distance.
     * @return Pair of goal shoulder positions (left, right) in pixel coordinates.
     */
    private fun calculateGoalShoulderPositions(
        referenceFrame: Frame?,
        settings: BodyAlignmentSettings,
    ): Pair<LandmarkPoint, LandmarkPoint> {
        // Try to use reference frame landmarks
        val refLandmarks = referenceFrame?.landmarks as? BodyLandmarks
        refLandmarks?.let { bodyLandmarks ->
            // Convert from normalized to pixel coordinates
            val outputSize = settings.outputSize.toFloat()
            return Pair(
                LandmarkPoint(
                    x = bodyLandmarks.leftShoulder.x * outputSize,
                    y = bodyLandmarks.leftShoulder.y * outputSize,
                    z = bodyLandmarks.leftShoulder.z,
                ),
                LandmarkPoint(
                    x = bodyLandmarks.rightShoulder.x * outputSize,
                    y = bodyLandmarks.rightShoulder.y * outputSize,
                    z = bodyLandmarks.rightShoulder.z,
                ),
            )
        }

        // Calculate default centered positions
        val outputSize = settings.outputSize.toFloat()
        val shoulderDistance = settings.targetShoulderDistance * outputSize
        val centerX = outputSize / 2
        val centerY = outputSize / 2 - settings.verticalOffset * outputSize

        return Pair(
            LandmarkPoint(x = centerX - shoulderDistance / 2, y = centerY, z = 0f),
            LandmarkPoint(x = centerX + shoulderDistance / 2, y = centerY, z = 0f),
        )
    }

    /**
     * Creates minimal body landmarks for storage when detection fails on aligned image.
     */
    private fun createMinimalBodyLandmarks(
        goalLeftShoulder: LandmarkPoint,
        goalRightShoulder: LandmarkPoint,
        outputSize: Int,
    ): BodyLandmarks {
        val normalizedLeft = LandmarkPoint(
            x = goalLeftShoulder.x / outputSize,
            y = goalLeftShoulder.y / outputSize,
            z = 0f,
        )
        val normalizedRight = LandmarkPoint(
            x = goalRightShoulder.x / outputSize,
            y = goalRightShoulder.y / outputSize,
            z = 0f,
        )
        val hipY = (normalizedLeft.y + normalizedRight.y) / 2 + 0.25f
        val neckCenter = LandmarkPoint(
            x = (normalizedLeft.x + normalizedRight.x) / 2,
            y = (normalizedLeft.y + normalizedRight.y) / 2 - 0.05f,
            z = 0f,
        )

        return BodyLandmarks(
            keypoints = emptyList(),
            leftShoulder = normalizedLeft,
            rightShoulder = normalizedRight,
            leftHip = LandmarkPoint(normalizedLeft.x, hipY, 0f),
            rightHip = LandmarkPoint(normalizedRight.x, hipY, 0f),
            neckCenter = neckCenter,
            boundingBox = BoundingBox(0f, 0f, 1f, 1f),
            confidence = 0.5f,
        )
    }
}
