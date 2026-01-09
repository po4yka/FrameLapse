package com.po4yka.framelapse.domain.usecase.landscape

import com.po4yka.framelapse.domain.entity.AlignmentDiagnostics
import com.po4yka.framelapse.domain.entity.EarlyStopReason
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
import com.po4yka.framelapse.domain.service.Clock
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.service.MediaStore
import com.po4yka.framelapse.domain.usecase.alignment.AlignmentPipeline
import com.po4yka.framelapse.domain.usecase.alignment.AlignmentPipelineStep
import com.po4yka.framelapse.domain.util.Result

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
    private val mediaStore: MediaStore,
    private val detectFeatures: DetectLandscapeFeaturesUseCase,
    private val matchFeatures: MatchLandscapeFeaturesUseCase,
    private val calculateHomography: CalculateHomographyMatrixUseCase,
    private val clock: Clock,
    private val multiPassStabilization: MultiPassLandscapeStabilizationUseCase? = null,
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

        // Branch based on stabilization mode
        return when (settings.stabilizationMode) {
            StabilizationMode.FAST -> executeFastMode(frame, referenceFrame, settings, onProgress)
            StabilizationMode.SLOW -> executeSlowMode(frame, referenceFrame, settings, onProgress)
        }
    }

    /**
     * Executes FAST mode (single-pass) alignment.
     */
    private suspend fun executeFastMode(
        frame: Frame,
        referenceFrame: Frame?,
        settings: LandscapeAlignmentSettings,
        onProgress: ((StabilizationProgress) -> Unit)?,
    ): Result<Frame> {
        val startTime = clock.nowMillis()
        val pipeline = AlignmentPipeline<LandscapePipelineContext>(
            mode = StabilizationMode.FAST,
            totalSteps = TOTAL_PROGRESS_STEPS,
            onProgress = onProgress,
        )

        val initialContext = LandscapePipelineContext(
            frame = frame,
            referenceFrame = referenceFrame,
            settings = settings,
        )

        val pipelineResult = pipeline.execute(
            initial = initialContext,
            steps = listOf(
                AlignmentPipelineStep(
                    stage = StabilizationStage.INITIAL,
                    message = "Loading images",
                    action = { context ->
                        val referenceLandmarksResult = getReferenceLandmarks(
                            referenceFrame = context.referenceFrame,
                            sourceFrame = context.frame,
                            settings = context.settings,
                        )
                        referenceLandmarksResult.map { landmarks ->
                            context.copy(referenceLandmarks = landmarks)
                        }
                    },
                ),
                AlignmentPipelineStep(
                    stage = StabilizationStage.INITIAL,
                    message = "Loading source image",
                    action = { context ->
                        val sourceImageResult = imageProcessor.loadImage(context.frame.originalPath)
                        sourceImageResult.map { sourceImage ->
                            context.copy(sourceImage = sourceImage)
                        }
                    },
                ),
                AlignmentPipelineStep(
                    stage = StabilizationStage.INITIAL,
                    message = "Detecting features",
                    action = { context ->
                        val sourceImage = context.sourceImage
                            ?: return@AlignmentPipelineStep Result.Error(
                                IllegalStateException("Source image not loaded"),
                                "Failed to load source image",
                            )
                        val sourceLandmarksResult = detectFeatures(
                            imageData = sourceImage,
                            detectorType = context.settings.detectorType,
                            maxKeypoints = context.settings.maxKeypoints,
                        )
                        sourceLandmarksResult.map { landmarks ->
                            context.copy(sourceLandmarks = landmarks)
                        }
                    },
                ),
                AlignmentPipelineStep(
                    stage = StabilizationStage.INITIAL,
                    message = "Matching features",
                    action = { context ->
                        val sourceLandmarks = context.sourceLandmarks
                            ?: return@AlignmentPipelineStep Result.Error(
                                IllegalStateException("Source landmarks not available"),
                                "Failed to detect features in source image",
                            )
                        val referenceLandmarks = context.referenceLandmarks
                            ?: return@AlignmentPipelineStep Result.Error(
                                IllegalStateException("Reference landmarks not available"),
                                "Failed to get reference landmarks",
                            )
                        val matchesResult = matchFeatures(
                            sourceLandmarks = sourceLandmarks,
                            referenceLandmarks = referenceLandmarks,
                            ratioTestThreshold = context.settings.ratioTestThreshold,
                            useCrossCheck = context.settings.useCrossCheck,
                            minMatchCount = context.settings.minMatchedKeypoints,
                        )
                        matchesResult.map { matches ->
                            context.copy(matches = matches)
                        }
                    },
                ),
                AlignmentPipelineStep(
                    stage = StabilizationStage.INITIAL,
                    message = "Computing homography",
                    action = { context ->
                        val sourceLandmarks = context.sourceLandmarks
                            ?: return@AlignmentPipelineStep Result.Error(
                                IllegalStateException("Source landmarks not available"),
                                "Failed to detect features in source image",
                            )
                        val referenceLandmarks = context.referenceLandmarks
                            ?: return@AlignmentPipelineStep Result.Error(
                                IllegalStateException("Reference landmarks not available"),
                                "Failed to get reference landmarks",
                            )
                        val matches = context.matches
                            ?: return@AlignmentPipelineStep Result.Error(
                                IllegalStateException("Feature matches not available"),
                                "Failed to match features between images",
                            )
                        val homographyResult = calculateHomography(
                            sourceKeypoints = sourceLandmarks.keypoints,
                            referenceKeypoints = referenceLandmarks.keypoints,
                            matches = matches,
                            ransacThreshold = context.settings.ransacReprojThreshold,
                        )
                        homographyResult.map { (homography, inlierCount) ->
                            context.copy(homography = homography, inlierCount = inlierCount)
                        }
                    },
                ),
                AlignmentPipelineStep(
                    stage = StabilizationStage.INITIAL,
                    message = "Applying transformation",
                    action = { context ->
                        val sourceImage = context.sourceImage
                            ?: return@AlignmentPipelineStep Result.Error(
                                IllegalStateException("Source image not loaded"),
                                "Failed to load source image",
                            )
                        val homography = context.homography
                            ?: return@AlignmentPipelineStep Result.Error(
                                IllegalStateException("Homography not available"),
                                "Failed to compute homography",
                            )
                        val transformedResult = imageProcessor.applyHomographyTransform(
                            image = sourceImage,
                            matrix = homography,
                            outputWidth = context.settings.outputSize,
                            outputHeight = context.settings.outputSize,
                        )
                        transformedResult.map { alignedImage ->
                            context.copy(alignedImage = alignedImage)
                        }
                    },
                ),
                AlignmentPipelineStep(
                    stage = StabilizationStage.INITIAL,
                    message = "Saving aligned image",
                    action = { context ->
                        val alignedImage = context.alignedImage
                            ?: return@AlignmentPipelineStep Result.Error(
                                IllegalStateException("Aligned image not available"),
                                "Failed to apply homography transform",
                            )
                        val alignedPath = mediaStore.getAlignedPath(
                            context.frame.projectId,
                            context.frame.originalPath,
                        )
                        val saveResult = imageProcessor.saveImage(alignedImage, alignedPath)
                        if (saveResult.isError) {
                            return@AlignmentPipelineStep Result.Error(
                                saveResult.exceptionOrNull()!!,
                                "Failed to save aligned image",
                            )
                        }
                        Result.Success(context.copy(alignedPath = alignedPath))
                    },
                ),
                AlignmentPipelineStep(
                    stage = StabilizationStage.INITIAL,
                    message = "Finalizing",
                    action = { context -> Result.Success(context) },
                ),
            ),
        )

        if (pipelineResult.isError) {
            return Result.Error(
                pipelineResult.exceptionOrNull()!!,
                pipelineResult.exceptionOrNull()?.message ?: "Landscape alignment failed",
            )
        }

        val pipelineContext = pipelineResult.getOrNull()!!
        val sourceLandmarks = pipelineContext.sourceLandmarks
            ?: return Result.Error(
                IllegalStateException("Source landmarks not available"),
                "Failed to detect features in source image",
            )
        val matches = pipelineContext.matches
            ?: return Result.Error(
                IllegalStateException("Feature matches not available"),
                "Failed to match features between images",
            )
        val inlierCount = pipelineContext.inlierCount ?: 0
        val alignedPath = pipelineContext.alignedPath
            ?: return Result.Error(
                IllegalStateException("Aligned path not available"),
                "Failed to save aligned image",
            )

        // Calculate confidence based on match quality
        val confidence = calculateConfidence(
            matchCount = matches.size,
            inlierCount = inlierCount,
            settings = settings,
        )

        // Create stabilization result for consistency with other alignment types
        val totalDuration = clock.nowMillis() - startTime
        val stabilizationResult = createStabilizationResult(
            matchCount = matches.size,
            inlierCount = inlierCount,
            confidence = confidence,
            durationMs = totalDuration,
            diagnostics = AlignmentDiagnostics(
                alignedLandmarksDetected = true,
                fallbackLandmarksGenerated = false,
                referenceFrameId = referenceFrame?.id,
            ),
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
     * Executes SLOW mode (multi-pass) alignment with iterative refinement.
     */
    private suspend fun executeSlowMode(
        frame: Frame,
        referenceFrame: Frame?,
        settings: LandscapeAlignmentSettings,
        onProgress: ((StabilizationProgress) -> Unit)?,
    ): Result<Frame> {
        // Check if multi-pass stabilization is available
        if (multiPassStabilization == null) {
            // Fall back to FAST mode if multi-pass not available
            return executeFastMode(frame, referenceFrame, settings, onProgress)
        }

        val startTime = clock.nowMillis()

        // Report initial progress
        onProgress?.invoke(
            StabilizationProgress.initial(StabilizationMode.SLOW),
        )

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
        val sourceImageResult = imageProcessor.loadImage(frame.originalPath)
        if (sourceImageResult.isError) {
            return Result.Error(
                sourceImageResult.exceptionOrNull()!!,
                "Failed to load source image",
            )
        }
        val sourceImage = sourceImageResult.getOrNull()!!

        // Step 3: Detect features in source image
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

        // Step 4: Load reference image for multi-pass
        val refFrame = referenceFrame ?: getFirstFrameInProject(frame.projectId)
        val referenceImageResult = imageProcessor.loadImage(refFrame?.originalPath ?: frame.originalPath)
        if (referenceImageResult.isError) {
            return Result.Error(
                referenceImageResult.exceptionOrNull()!!,
                "Failed to load reference image",
            )
        }
        val referenceImage = referenceImageResult.getOrNull()!!

        // Step 5: Run multi-pass stabilization
        val stabilizationResult = multiPassStabilization(
            sourceImage = sourceImage,
            referenceImage = referenceImage,
            sourceLandmarks = sourceLandmarks,
            referenceLandmarks = referenceLandmarks,
            alignmentSettings = settings,
            onProgress = onProgress,
        )

        if (stabilizationResult.isError) {
            return Result.Error(
                stabilizationResult.exceptionOrNull()!!,
                "Multi-pass stabilization failed",
            )
        }

        val (alignedImage, stabResult) = stabilizationResult.getOrNull()!!

        // Step 6: Save aligned image
        val alignedPath = mediaStore.getAlignedPath(frame.projectId, frame.originalPath)

        val saveResult = imageProcessor.saveImage(alignedImage, alignedPath)
        if (saveResult.isError) {
            return Result.Error(
                saveResult.exceptionOrNull()!!,
                "Failed to save aligned image",
            )
        }

        // Step 7: Calculate confidence from stabilization result
        val confidence = if (stabResult.success) {
            1f - (stabResult.finalScore.value / 100f).coerceIn(0f, 1f)
        } else {
            0.5f
        }

        // Step 8: Update frame in repository
        val stabilizedResult = stabResult.copy(
            diagnostics = AlignmentDiagnostics(
                alignedLandmarksDetected = true,
                fallbackLandmarksGenerated = false,
                referenceFrameId = referenceFrame?.id,
            ),
        )

        val updateResult = frameRepository.updateAlignedFrame(
            id = frame.id,
            alignedPath = alignedPath,
            confidence = confidence,
            landmarks = sourceLandmarks,
            stabilizationResult = stabilizedResult,
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
                landmarks = sourceLandmarks,
                stabilizationResult = stabilizedResult,
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
    private data class LandscapePipelineContext(
        val frame: Frame,
        val referenceFrame: Frame?,
        val settings: LandscapeAlignmentSettings,
        val referenceLandmarks: LandscapeLandmarks? = null,
        val sourceImage: com.po4yka.framelapse.domain.service.ImageData? = null,
        val sourceLandmarks: LandscapeLandmarks? = null,
        val matches: List<Pair<Int, Int>>? = null,
        val homography: com.po4yka.framelapse.domain.entity.HomographyMatrix? = null,
        val inlierCount: Int? = null,
        val alignedImage: com.po4yka.framelapse.domain.service.ImageData? = null,
        val alignedPath: String? = null,
    )

    /**
     * Calculates alignment confidence based on match quality.
     *
     * @param matchCount Total number of feature matches.
     * @param inlierCount Number of inliers after RANSAC.
     * @param settings Alignment settings for thresholds.
     * @return Confidence score from 0.0 to 1.0.
     */
    private fun calculateConfidence(matchCount: Int, inlierCount: Int, settings: LandscapeAlignmentSettings): Float {
        // Calculate inlier ratio
        val inlierRatio = if (matchCount > 0) {
            inlierCount.toFloat() / matchCount
        } else {
            0f
        }

        // Calculate match count factor (more matches = more confident)
        val matchFactor = (matchCount.toFloat() / OPTIMAL_MATCH_COUNT).coerceAtMost(1f)

        // Calculate inlier factor (higher inlier ratio = more confident)
        val inlierFactor = (
            (inlierRatio - settings.minInlierRatio) /
                (1f - settings.minInlierRatio)
            ).coerceIn(0f, 1f)

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
        diagnostics: AlignmentDiagnostics? = null,
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
            earlyStopReason = if (confidence < 0.5f) EarlyStopReason.SCORE_BELOW_THRESHOLD else null,
            totalDurationMs = durationMs,
            initialScore = 100f,
            finalEyeDeltaY = null,
            finalEyeDistance = null,
            goalEyeDistance = null,
            diagnostics = diagnostics,
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
