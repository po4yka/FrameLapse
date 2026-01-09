package com.po4yka.framelapse.domain.usecase.face

import com.po4yka.framelapse.domain.entity.AlignmentDiagnostics
import com.po4yka.framelapse.domain.entity.AlignmentSettings
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.entity.StabilizationProgress
import com.po4yka.framelapse.domain.entity.StabilizationResult
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.service.FaceDetector
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.service.MediaStore
import com.po4yka.framelapse.domain.util.Result

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
    private val mediaStore: MediaStore,
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
     * @param project Optional project for calibration data. If the project has calibration,
     *                the calibrated eye positions will be used as the goal (highest priority).
     * @param settings Alignment configuration (includes stabilization mode).
     * @param onProgress Optional callback for progress updates during stabilization.
     * @return Result containing the updated Frame with alignment data and stabilization metrics.
     */
    suspend operator fun invoke(
        frame: Frame,
        referenceFrame: Frame? = null,
        project: Project? = null,
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
            project = project,
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
        val alignedDetectResult = faceDetector.detectFace(alignedImage)
        val landmarks = alignedDetectResult.getOrNull()
        val diagnostics = AlignmentDiagnostics(
            alignedLandmarksDetected = landmarks != null,
            alignedLandmarksError = when {
                alignedDetectResult.isError -> alignedDetectResult.exceptionOrNull()?.message ?: "Face detection failed"
                landmarks == null -> "No face detected in aligned image"
                else -> null
            },
            fallbackLandmarksGenerated = landmarks == null,
            referenceFrameId = referenceFrame?.id,
        )

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
                landmarks = FaceLandmarks(
                    points = emptyList(),
                    leftEyeCenter = normalizePoint(goalLeftEye, settings.outputSize),
                    rightEyeCenter = normalizePoint(goalRightEye, settings.outputSize),
                    noseTip = LandmarkPoint(0.5f, 0.6f, 0f),
                    boundingBox = BoundingBox(0f, 0f, 1f, 1f),
                ),
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
     * Calculates goal eye positions for stabilization.
     *
     * Priority order:
     * 1. Project calibration (if available) - user-adjusted eye positions with offsets
     * 2. Reference frame landmarks - uses first frame's eye positions
     * 3. Defaults - centered positions based on settings
     *
     * @param project Optional project with calibration data.
     * @param referenceFrame Optional reference frame with landmarks.
     * @param settings Alignment settings with output size and target eye distance.
     * @return Pair of goal eye positions (left, right) in pixel coordinates.
     */
    private fun calculateGoalEyePositions(
        project: Project?,
        referenceFrame: Frame?,
        settings: AlignmentSettings,
    ): Pair<LandmarkPoint, LandmarkPoint> {
        val outputSize = settings.outputSize.toFloat()

        // Priority 1: Use project calibration if available
        project?.let { proj ->
            val leftEyeX = proj.calibrationLeftEyeX
            val leftEyeY = proj.calibrationLeftEyeY
            val rightEyeX = proj.calibrationRightEyeX
            val rightEyeY = proj.calibrationRightEyeY

            if (leftEyeX != null && leftEyeY != null && rightEyeX != null && rightEyeY != null) {
                // Apply calibration offsets and convert to pixel coordinates
                val leftX = (leftEyeX + proj.calibrationOffsetX) * outputSize
                val leftY = (leftEyeY + proj.calibrationOffsetY) * outputSize
                val rightX = (rightEyeX + proj.calibrationOffsetX) * outputSize
                val rightY = (rightEyeY + proj.calibrationOffsetY) * outputSize
                return Pair(
                    LandmarkPoint(x = leftX, y = leftY, z = 0f),
                    LandmarkPoint(x = rightX, y = rightY, z = 0f),
                )
            }
        }

        // Priority 2: Try to use reference frame landmarks (only if they are face landmarks)
        (referenceFrame?.landmarks as? FaceLandmarks)?.let { refLandmarks ->
            // Convert from normalized to pixel coordinates
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

        // Priority 3: Calculate default centered positions
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
