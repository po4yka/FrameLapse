package com.po4yka.framelapse.domain.usecase.landscape

import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.EarlyStopReason
import com.po4yka.framelapse.domain.entity.FeatureDetectorType
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.LandscapeAlignmentSettings
import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.entity.StabilizationMode
import com.po4yka.framelapse.domain.entity.StabilizationPass
import com.po4yka.framelapse.domain.entity.StabilizationProgress
import com.po4yka.framelapse.domain.entity.StabilizationResult
import com.po4yka.framelapse.domain.entity.StabilizationScore
import com.po4yka.framelapse.domain.entity.StabilizationStage
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.FileManager

/**
 * Performs landscape/scenery alignment using feature-based homography.
 *
 * This is the main orchestrator use case for landscape alignment. It coordinates
 * the complete alignment pipeline:
 * 1. Load reference and source images
 * 2. Detect features in both images
 * 3. Match features between images
 * 4. Compute homography matrix using RANSAC
 * 5. Apply perspective transformation to align source to reference
 * 6. Save aligned image and update frame metadata
 *
 * Unlike face/body alignment which uses affine transforms based on anatomical
 * landmarks, landscape alignment uses perspective-corrected homography based
 * on matched keypoints (corners, edges, etc.).
 *
 * Suitable for:
 * - Scenery timelapses (sunsets, cityscapes, nature)
 * - Architecture photography
 * - Any scene without detectable faces or bodies
 */
class AlignLandscapeUseCase(
    private val featureMatcher: FeatureMatcher,
    private val imageProcessor: ImageProcessor,
    private val frameRepository: FrameRepository,
    private val fileManager: FileManager,
    private val detectFeatures: DetectLandscapeFeaturesUseCase,
    private val matchFeatures: MatchLandscapeFeaturesUseCase,
    private val calculateHomography: CalculateHomographyMatrixUseCase,
) {
    // Cache for reference frame features to avoid re-detection
    private var cachedReferenceFrameId: String? = null
    private var cachedReferenceLandmarks: LandscapeLandmarks? = null

    /**
     * Aligns a landscape frame using feature-based homography.
     *
     * @param frame The frame to align.
     * @param referenceFrame Optional reference frame to align to.
     *                       If not provided, uses the first frame in the project.
     * @param settings Landscape alignment configuration.
     * @param onProgress Optional callback for progress updates.
     * @return Result containing the updated Frame with alignment data.
     */
    suspend operator fun invoke(
        frame: Frame,
        referenceFrame: Frame? = null,
        settings: LandscapeAlignmentSettings = LandscapeAlignmentSettings(),
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Frame> {
        val startTime = System.currentTimeMillis()

        // Skip if already aligned
        if (frame.alignedPath != null && frame.landmarks != null) {
            return Result.Success(frame)
        }

        // Check if feature matching is available
        if (!featureMatcher.isAvailable) {
            return Result.Error(
                UnsupportedOperationException("Feature matching is not available"),
                "Feature matching not available",
            )
        }

        // Report initial progress
        onProgress?.invoke(createProgress(1, "Loading images"))

        // Step 1: Load and prepare reference frame
        val referenceLandmarksResult = getReferenceLandmarks(
            referenceFrame = referenceFrame,
            sourceFrame = frame,
            settings = settings,
        )
        if (referenceLandmarksResult.isError) {
            return Result.Error(
                referenceLandmarksResult.exceptionOrNull()!!,
                "Failed to get reference landmarks",
            )
        }
        val referenceLandmarks = referenceLandmarksResult.getOrNull()!!

        // Step 2: Load source image
        onProgress?.invoke(createProgress(2, "Loading source image"))

        val sourceImageResult = imageProcessor.loadImage(frame.originalPath)
        if (sourceImageResult.isError) {
            return Result.Error(
                sourceImageResult.exceptionOrNull()!!,
                "Failed to load source image",
            )
        }
        val sourceImage = sourceImageResult.getOrNull()!!

        // Step 3: Detect features in source image
        onProgress?.invoke(createProgress(3, "Detecting features"))

        val sourceLandmarksResult = detectFeatures(
            imageData = sourceImage,
            detectorType = settings.detectorType,
            maxKeypoints = settings.maxKeypoints,
        )
        if (sourceLandmarksResult.isError) {
            return Result.Error(
                sourceLandmarksResult.exceptionOrNull()!!,
                "Failed to detect features in source image",
            )
        }
        val sourceLandmarks = sourceLandmarksResult.getOrNull()!!

        // Step 4: Match features between source and reference
        onProgress?.invoke(createProgress(4, "Matching features"))

        val matchesResult = matchFeatures(
            sourceLandmarks = sourceLandmarks,
            referenceLandmarks = referenceLandmarks,
            ratioTestThreshold = settings.ratioTestThreshold,
            useCrossCheck = settings.useCrossCheck,
            minMatchCount = settings.minMatchedKeypoints,
        )
        if (matchesResult.isError) {
            return Result.Error(
                matchesResult.exceptionOrNull()!!,
                "Failed to match features between images",
            )
        }
        val matches = matchesResult.getOrNull()!!

        // Step 5: Compute homography matrix
        onProgress?.invoke(createProgress(5, "Computing homography"))

        val homographyResult = calculateHomography(
            sourceKeypoints = sourceLandmarks.keypoints,
            referenceKeypoints = referenceLandmarks.keypoints,
            matches = matches,
            ransacThreshold = settings.ransacReprojThreshold,
        )
        if (homographyResult.isError) {
            return Result.Error(
                homographyResult.exceptionOrNull()!!,
                "Failed to compute homography",
            )
        }
        val (homography, inlierCount) = homographyResult.getOrNull()!!

        // Step 6: Apply homography transform
        onProgress?.invoke(createProgress(6, "Applying transformation"))

        val transformedResult = imageProcessor.applyHomographyTransform(
            image = sourceImage,
            matrix = homography,
            outputWidth = settings.outputSize,
            outputHeight = settings.outputSize,
        )
        if (transformedResult.isError) {
            return Result.Error(
                transformedResult.exceptionOrNull()!!,
                "Failed to apply homography transform",
            )
        }
        val alignedImage = transformedResult.getOrNull()!!

        // Step 7: Save aligned image
        onProgress?.invoke(createProgress(7, "Saving aligned image"))

        val projectDir = fileManager.getProjectDirectory(frame.projectId)
        val alignedPath = "$projectDir/aligned_${frame.id}.jpg"

        val saveResult = imageProcessor.saveImage(alignedImage, alignedPath)
        if (saveResult.isError) {
            return Result.Error(
                saveResult.exceptionOrNull()!!,
                "Failed to save aligned image",
            )
        }

        // Step 8: Update frame in repository
        onProgress?.invoke(createProgress(8, "Finalizing"))

        // Calculate confidence based on match quality
        val confidence = calculateConfidence(
            matchCount = matches.size,
            inlierCount = inlierCount,
            settings = settings,
        )

        // Create stabilization result for consistency with other alignment types
        val totalDuration = System.currentTimeMillis() - startTime
        val stabilizationResult = createStabilizationResult(
            matchCount = matches.size,
            inlierCount = inlierCount,
            confidence = confidence,
            durationMs = totalDuration,
        )

        // Update frame in database
        val updateResult = frameRepository.updateAlignedFrame(
            id = frame.id,
            alignedPath = alignedPath,
            confidence = confidence,
            landmarks = sourceLandmarks,
            stabilizationResult = stabilizationResult,
        )

        if (updateResult.isError) {
            return Result.Error(
                updateResult.exceptionOrNull()!!,
                "Failed to update frame",
            )
        }

        // Report completion
        onProgress?.invoke(
            StabilizationProgress.completed(
                finalScore = (1f - confidence) * 100f,
                passesExecuted = 1,
                mode = StabilizationMode.FAST,
                success = true,
            ),
        )

        // Return updated frame
        return Result.Success(
            frame.copy(
                alignedPath = alignedPath,
                confidence = confidence,
                landmarks = sourceLandmarks,
                stabilizationResult = stabilizationResult,
            ),
        )
    }

    /**
     * Gets or computes reference landmarks, using cache when possible.
     */
    private suspend fun getReferenceLandmarks(
        referenceFrame: Frame?,
        sourceFrame: Frame,
        settings: LandscapeAlignmentSettings,
    ): Result<LandscapeLandmarks> {
        // Try to get existing landmarks from reference frame
        val refFrame = referenceFrame ?: getFirstFrameInProject(sourceFrame.projectId)

        if (refFrame == null) {
            return Result.Error(
                IllegalStateException("No reference frame available for alignment"),
                "No reference frame found",
            )
        }

        // Check if source is the reference frame (nothing to align to)
        if (refFrame.id == sourceFrame.id) {
            return Result.Error(
                IllegalArgumentException("Source frame cannot be its own reference"),
                "Cannot align frame to itself",
            )
        }

        // Try to use cached landmarks
        if (cachedReferenceFrameId == refFrame.id && cachedReferenceLandmarks != null) {
            return Result.Success(cachedReferenceLandmarks!!)
        }

        // Try to use existing landmarks from reference frame
        val existingLandmarks = refFrame.landmarks as? LandscapeLandmarks
        if (existingLandmarks != null && existingLandmarks.hasEnoughKeypoints()) {
            // Cache the landmarks
            cachedReferenceFrameId = refFrame.id
            cachedReferenceLandmarks = existingLandmarks
            return Result.Success(existingLandmarks)
        }

        // Need to detect features in reference image
        val refImageResult = imageProcessor.loadImage(refFrame.originalPath)
        if (refImageResult.isError) {
            return Result.Error(
                refImageResult.exceptionOrNull()!!,
                "Failed to load reference image",
            )
        }
        val refImage = refImageResult.getOrNull()!!

        val detectResult = detectFeatures(
            imageData = refImage,
            detectorType = settings.detectorType,
            maxKeypoints = settings.maxKeypoints,
        )

        if (detectResult.isSuccess) {
            val landmarks = detectResult.getOrNull()!!
            // Cache for future use
            cachedReferenceFrameId = refFrame.id
            cachedReferenceLandmarks = landmarks
        }

        return detectResult
    }

    /**
     * Gets the first frame in a project to use as reference.
     */
    private suspend fun getFirstFrameInProject(projectId: String): Frame? {
        val framesResult = frameRepository.getFramesByProject(projectId)
        return framesResult.getOrNull()?.firstOrNull()
    }

    /**
     * Creates a StabilizationProgress for the landscape alignment pipeline.
     */
    private fun createProgress(step: Int, message: String): StabilizationProgress =
        StabilizationProgress(
            currentPass = step,
            maxPasses = TOTAL_PROGRESS_STEPS,
            currentStage = StabilizationStage.INITIAL,
            currentScore = 0f,
            progressPercent = step.toFloat() / TOTAL_PROGRESS_STEPS,
            message = message,
            mode = StabilizationMode.FAST,
        )

    /**
     * Calculates alignment confidence based on match quality.
     *
     * @param matchCount Total number of feature matches.
     * @param inlierCount Number of inliers after RANSAC.
     * @param settings Alignment settings for thresholds.
     * @return Confidence score from 0.0 to 1.0.
     */
    private fun calculateConfidence(
        matchCount: Int,
        inlierCount: Int,
        settings: LandscapeAlignmentSettings,
    ): Float {
        // Calculate inlier ratio
        val inlierRatio = if (matchCount > 0) {
            inlierCount.toFloat() / matchCount
        } else {
            0f
        }

        // Calculate match count factor (more matches = more confident)
        val matchFactor = (matchCount.toFloat() / OPTIMAL_MATCH_COUNT).coerceAtMost(1f)

        // Calculate inlier factor (higher inlier ratio = more confident)
        val inlierFactor = ((inlierRatio - settings.minInlierRatio) /
            (1f - settings.minInlierRatio)).coerceIn(0f, 1f)

        // Weighted combination
        val confidence = matchFactor * MATCH_FACTOR_WEIGHT + inlierFactor * INLIER_FACTOR_WEIGHT

        return confidence.coerceIn(0f, 1f)
    }

    /**
     * Creates a StabilizationResult for compatibility with the Frame entity.
     */
    private fun createStabilizationResult(
        matchCount: Int,
        inlierCount: Int,
        confidence: Float,
        durationMs: Long,
    ): StabilizationResult {
        // Convert confidence to a "stabilization score" (lower is better)
        val score = ((1f - confidence) * 100f).coerceAtLeast(0f)

        return StabilizationResult(
            success = confidence >= 0.5f,
            finalScore = StabilizationScore(
                value = score,
                leftEyeDistance = 0f,
                rightEyeDistance = 0f,
            ),
            passesExecuted = 1,
            passes = listOf(
                StabilizationPass(
                    passNumber = 1,
                    stage = StabilizationStage.INITIAL,
                    scoreBefore = 100f,
                    scoreAfter = score,
                    converged = confidence >= 0.5f,
                    durationMs = durationMs,
                ),
            ),
            mode = StabilizationMode.FAST,
            earlyStopReason = if (confidence < 0.5f) EarlyStopReason.MIN_SCORE_REACHED else null,
            totalDurationMs = durationMs,
            initialScore = 100f,
            finalEyeDeltaY = null,
            finalEyeDistance = null,
            goalEyeDistance = null,
        )
    }

    /**
     * Clears the reference landmarks cache.
     * Call this when the reference frame changes or to force re-detection.
     */
    fun clearCache() {
        cachedReferenceFrameId = null
        cachedReferenceLandmarks = null
    }

    /**
     * Creates minimal landscape landmarks for storage when detection fails.
     */
    @Suppress("unused")
    private fun createMinimalLandmarks(
        outputSize: Int,
        detectorType: FeatureDetectorType,
    ): LandscapeLandmarks = LandscapeLandmarks(
        keypoints = emptyList(),
        detectorType = detectorType,
        keypointCount = 0,
        boundingBox = BoundingBox(0f, 0f, 1f, 1f),
        qualityScore = 0f,
    )

    /**
     * Checks if landscape alignment is available.
     */
    val isAvailable: Boolean
        get() = featureMatcher.isAvailable

    companion object {
        /** Total number of progress steps in the alignment pipeline. */
        private const val TOTAL_PROGRESS_STEPS = 8

        /** Optimal match count for full confidence. */
        private const val OPTIMAL_MATCH_COUNT = 100

        /** Weight for match count factor in confidence calculation. */
        private const val MATCH_FACTOR_WEIGHT = 0.4f

        /** Weight for inlier ratio factor in confidence calculation. */
        private const val INLIER_FACTOR_WEIGHT = 0.6f
    }
}
