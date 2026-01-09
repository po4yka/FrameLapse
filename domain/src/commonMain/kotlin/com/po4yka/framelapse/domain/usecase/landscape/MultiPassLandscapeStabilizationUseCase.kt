package com.po4yka.framelapse.domain.usecase.landscape

import com.po4yka.framelapse.domain.entity.EarlyStopReason
import com.po4yka.framelapse.domain.entity.LandscapeAlignmentSettings
import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.entity.LandscapeStabilizationSettings
import com.po4yka.framelapse.domain.entity.StabilizationMode
import com.po4yka.framelapse.domain.entity.StabilizationPass
import com.po4yka.framelapse.domain.entity.StabilizationProgress
import com.po4yka.framelapse.domain.entity.StabilizationResult
import com.po4yka.framelapse.domain.entity.StabilizationScore
import com.po4yka.framelapse.domain.entity.StabilizationStage
import com.po4yka.framelapse.domain.service.FeatureMatcher
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.currentTimeMillis

/**
 * Orchestrates multi-pass landscape stabilization algorithm.
 *
 * This use case implements the full multi-pass stabilization algorithm for landscape
 * alignment, using feature-based homography refinement.
 *
 * ## SLOW Mode Algorithm (max 10 passes):
 * 1. Pass 1: Initial alignment with standard homography
 * 2. Passes 2-4: Match quality refinement (filter low-quality matches)
 * 3. Passes 5-7: RANSAC threshold refinement (tighten reprojection threshold)
 * 4. Passes 8-10: Perspective stability refinement (ensure valid homography)
 *
 * ## Early Stop Conditions:
 * - Inlier ratio converged (improvement < 1%)
 * - Reprojection error converged (< 1.0px)
 * - Perspective converged (determinant change < 0.01)
 * - Feature detection failed
 * - Maximum passes reached
 */
class MultiPassLandscapeStabilizationUseCase(
    private val featureMatcher: FeatureMatcher,
    private val imageProcessor: ImageProcessor,
    private val detectFeatures: DetectLandscapeFeaturesUseCase,
    private val matchFeatures: MatchLandscapeFeaturesUseCase,
    private val calculateHomography: CalculateHomographyMatrixUseCase,
    private val refineMatchQuality: RefineMatchQualityUseCase,
    private val refineRansacThreshold: RefineRansacThresholdUseCase,
    private val refinePerspectiveStability: RefinePerspectiveStabilityUseCase,
) {
    /**
     * Performs multi-pass landscape stabilization.
     *
     * @param sourceImage The original source image data.
     * @param referenceImage The reference image to align to.
     * @param sourceLandmarks Features detected in the source image.
     * @param referenceLandmarks Features detected in the reference image.
     * @param alignmentSettings Landscape alignment configuration.
     * @param onProgress Optional callback for progress updates.
     * @return Result containing the stabilized image and stabilization result.
     */
    suspend operator fun invoke(
        sourceImage: ImageData,
        referenceImage: ImageData,
        sourceLandmarks: LandscapeLandmarks,
        referenceLandmarks: LandscapeLandmarks,
        alignmentSettings: LandscapeAlignmentSettings,
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Pair<ImageData, StabilizationResult>> {
        val startTime = currentTimeMillis()
        val stabilizationSettings = alignmentSettings.stabilizationSettings

        // Report initial progress
        onProgress?.invoke(createProgress(0, StabilizationStage.INITIAL, 0f, stabilizationSettings))

        // Step 1: Initial feature matching
        val matchResult = matchFeatures(
            sourceLandmarks = sourceLandmarks,
            referenceLandmarks = referenceLandmarks,
            ratioTestThreshold = alignmentSettings.ratioTestThreshold,
            useCrossCheck = alignmentSettings.useCrossCheck,
            minMatchCount = alignmentSettings.minMatchedKeypoints,
        )

        if (matchResult.isError) {
            return Result.Error(
                matchResult.exceptionOrNull()!!,
                "Initial feature matching failed",
            )
        }

        var currentMatches = matchResult.getOrNull()!!

        // Step 2: Initial homography computation
        val initialHomographyResult = calculateHomography(
            sourceKeypoints = sourceLandmarks.keypoints,
            referenceKeypoints = referenceLandmarks.keypoints,
            matches = currentMatches,
            ransacThreshold = stabilizationSettings.initialRansacThreshold,
        )

        if (initialHomographyResult.isError) {
            return Result.Error(
                initialHomographyResult.exceptionOrNull()!!,
                "Initial homography computation failed",
            )
        }

        var (currentHomography, currentInlierCount) = initialHomographyResult.getOrNull()!!
        var currentInlierRatio = if (currentMatches.isNotEmpty()) {
            currentInlierCount.toFloat() / currentMatches.size
        } else {
            0f
        }

        val passes = mutableListOf<StabilizationPass>()
        var passNumber = 0
        var earlyStopReason: EarlyStopReason? = null
        var bestHomography = currentHomography
        var bestInlierRatio = currentInlierRatio
        var currentRansacThreshold = stabilizationSettings.initialRansacThreshold
        var previousDeterminant: Float? = null
        var meanReprojError = stabilizationSettings.initialRansacThreshold

        // Pass 1: Record initial state
        passNumber++
        onProgress?.invoke(
            createProgress(passNumber, StabilizationStage.INITIAL, currentInlierRatio * 100, stabilizationSettings),
        )

        passes.add(
            StabilizationPass(
                passNumber = passNumber,
                stage = StabilizationStage.INITIAL,
                scoreBefore = 100f,
                scoreAfter = (1f - currentInlierRatio) * 100f,
                converged = false,
                durationMs = currentTimeMillis() - startTime,
            ),
        )

        // Update best if this is better
        if (currentInlierRatio > bestInlierRatio) {
            bestHomography = currentHomography
            bestInlierRatio = currentInlierRatio
        }

        // Stage 2: Match Quality Refinement (Passes 2-4)
        for (i in 1..3) {
            if (earlyStopReason != null) break
            passNumber++

            val passStartTime = currentTimeMillis()

            onProgress?.invoke(
                createProgress(
                    passNumber,
                    StabilizationStage.MATCH_QUALITY_REFINE,
                    currentInlierRatio * 100,
                    stabilizationSettings,
                ),
            )

            val refineResult = refineMatchQuality(
                sourceKeypoints = sourceLandmarks.keypoints,
                referenceKeypoints = referenceLandmarks.keypoints,
                currentMatches = currentMatches,
                previousInlierRatio = currentInlierRatio,
                passNumber = passNumber,
                settings = stabilizationSettings,
            )

            if (refineResult.isError) {
                earlyStopReason = EarlyStopReason.FEATURE_DETECTION_FAILED
                break
            }

            val result = refineResult.getOrNull()!!
            val scoreBefore = (1f - currentInlierRatio) * 100f

            currentHomography = result.homography
            currentMatches = result.filteredMatches
            currentInlierCount = result.inlierCount
            currentInlierRatio = result.inlierRatio

            val scoreAfter = (1f - currentInlierRatio) * 100f

            passes.add(
                StabilizationPass(
                    passNumber = passNumber,
                    stage = StabilizationStage.MATCH_QUALITY_REFINE,
                    scoreBefore = scoreBefore,
                    scoreAfter = scoreAfter,
                    converged = result.converged,
                    durationMs = currentTimeMillis() - passStartTime,
                ),
            )

            // Update best
            if (currentInlierRatio > bestInlierRatio) {
                bestHomography = currentHomography
                bestInlierRatio = currentInlierRatio
            }

            if (result.converged) {
                earlyStopReason = EarlyStopReason.INLIER_RATIO_CONVERGED
                break
            }
        }

        // Stage 3: RANSAC Threshold Refinement (Passes 5-7)
        if (earlyStopReason == null || earlyStopReason == EarlyStopReason.INLIER_RATIO_CONVERGED) {
            earlyStopReason = null
            for (i in 1..3) {
                if (earlyStopReason != null) break
                passNumber++

                val passStartTime = currentTimeMillis()

                onProgress?.invoke(
                    createProgress(
                        passNumber,
                        StabilizationStage.RANSAC_THRESHOLD_REFINE,
                        currentInlierRatio * 100,
                        stabilizationSettings,
                    ),
                )

                val refineResult = refineRansacThreshold(
                    sourceKeypoints = sourceLandmarks.keypoints,
                    referenceKeypoints = referenceLandmarks.keypoints,
                    matches = currentMatches,
                    previousThreshold = currentRansacThreshold,
                    settings = stabilizationSettings,
                    imageWidth = alignmentSettings.outputSize,
                    imageHeight = alignmentSettings.outputSize,
                )

                if (refineResult.isError) {
                    earlyStopReason = EarlyStopReason.HOMOGRAPHY_INVALID
                    break
                }

                val result = refineResult.getOrNull()!!
                val scoreBefore = (1f - currentInlierRatio) * 100f

                currentHomography = result.homography
                currentInlierCount = result.inlierCount
                currentInlierRatio = result.inlierRatio
                currentRansacThreshold = result.ransacThreshold
                meanReprojError = result.meanReprojectionError

                val scoreAfter = (1f - currentInlierRatio) * 100f

                passes.add(
                    StabilizationPass(
                        passNumber = passNumber,
                        stage = StabilizationStage.RANSAC_THRESHOLD_REFINE,
                        scoreBefore = scoreBefore,
                        scoreAfter = scoreAfter,
                        converged = result.converged,
                        durationMs = currentTimeMillis() - passStartTime,
                    ),
                )

                // Update best
                if (currentInlierRatio > bestInlierRatio) {
                    bestHomography = currentHomography
                    bestInlierRatio = currentInlierRatio
                }

                if (result.converged) {
                    earlyStopReason = EarlyStopReason.REPROJECTION_ERROR_CONVERGED
                    break
                }
            }
        }

        // Stage 4: Perspective Stability Refinement (Passes 8-10)
        if (earlyStopReason == null || earlyStopReason == EarlyStopReason.REPROJECTION_ERROR_CONVERGED) {
            earlyStopReason = null
            for (i in 1..3) {
                if (earlyStopReason != null) break
                passNumber++

                val passStartTime = currentTimeMillis()

                onProgress?.invoke(
                    createProgress(
                        passNumber,
                        StabilizationStage.PERSPECTIVE_STABILITY_REFINE,
                        currentInlierRatio * 100,
                        stabilizationSettings,
                    ),
                )

                val refineResult = refinePerspectiveStability(
                    currentHomography = currentHomography,
                    previousDeterminant = previousDeterminant,
                    settings = stabilizationSettings,
                )

                if (refineResult.isError) {
                    earlyStopReason = EarlyStopReason.HOMOGRAPHY_INVALID
                    break
                }

                val result = refineResult.getOrNull()!!
                val scoreBefore = (1f - currentInlierRatio) * 100f

                previousDeterminant = currentHomography.determinant()
                currentHomography = result.homography

                // Score based on perspective validity
                val perspectiveScore = if (result.perspectiveValid) 0f else 20f
                val scoreAfter = (1f - currentInlierRatio) * 100f + perspectiveScore

                passes.add(
                    StabilizationPass(
                        passNumber = passNumber,
                        stage = StabilizationStage.PERSPECTIVE_STABILITY_REFINE,
                        scoreBefore = scoreBefore,
                        scoreAfter = scoreAfter,
                        converged = result.converged,
                        durationMs = currentTimeMillis() - passStartTime,
                    ),
                )

                // Update best if perspective is valid
                if (result.perspectiveValid && currentInlierRatio >= bestInlierRatio) {
                    bestHomography = currentHomography
                    bestInlierRatio = currentInlierRatio
                }

                if (result.converged) {
                    earlyStopReason = EarlyStopReason.PERSPECTIVE_CONVERGED
                    break
                }
            }
        }

        // Set max passes reached if no other reason
        if (earlyStopReason == null) {
            earlyStopReason = EarlyStopReason.MAX_PASSES_REACHED
        }

        // Apply final homography to source image
        val transformResult = imageProcessor.applyHomographyTransform(
            image = sourceImage,
            matrix = bestHomography,
            outputWidth = alignmentSettings.outputSize,
            outputHeight = alignmentSettings.outputSize,
        )

        if (transformResult.isError) {
            return Result.Error(
                transformResult.exceptionOrNull()!!,
                "Failed to apply final homography transform",
            )
        }

        val alignedImage = transformResult.getOrNull()!!
        val totalDuration = currentTimeMillis() - startTime

        // Calculate final score (lower is better)
        val finalScore = (1f - bestInlierRatio) * 100f
        val confidence = bestInlierRatio

        val stabilizationResult = StabilizationResult(
            success = confidence >= stabilizationSettings.successConfidenceThreshold,
            finalScore = StabilizationScore(
                value = finalScore,
                leftEyeDistance = meanReprojError, // Repurpose for mean reproj error
                rightEyeDistance = currentRansacThreshold, // Repurpose for final threshold
            ),
            passesExecuted = passNumber,
            passes = passes,
            mode = StabilizationMode.SLOW,
            earlyStopReason = earlyStopReason,
            totalDurationMs = totalDuration,
            initialScore = passes.firstOrNull()?.scoreBefore ?: 100f,
            goalEyeDistance = null,
        )

        // Report completion
        onProgress?.invoke(
            StabilizationProgress.completed(
                finalScore = finalScore,
                passesExecuted = passNumber,
                mode = StabilizationMode.SLOW,
                success = stabilizationResult.success,
            ),
        )

        return Result.Success(Pair(alignedImage, stabilizationResult))
    }

    /**
     * Creates a progress update for the landscape stabilization pipeline.
     */
    private fun createProgress(
        passNumber: Int,
        stage: StabilizationStage,
        score: Float,
        settings: LandscapeStabilizationSettings,
    ): StabilizationProgress {
        val message = when (stage) {
            StabilizationStage.INITIAL -> "Initial alignment..."
            StabilizationStage.MATCH_QUALITY_REFINE -> "Refining match quality (pass $passNumber)..."
            StabilizationStage.RANSAC_THRESHOLD_REFINE -> "Tightening alignment (pass $passNumber)..."
            StabilizationStage.PERSPECTIVE_STABILITY_REFINE -> "Stabilizing perspective (pass $passNumber)..."
            else -> "Processing (pass $passNumber)..."
        }

        return StabilizationProgress(
            currentPass = passNumber,
            maxPasses = settings.maxPasses,
            currentStage = stage,
            currentScore = score,
            progressPercent = passNumber.toFloat() / settings.maxPasses,
            message = message,
            mode = StabilizationMode.SLOW,
        )
    }

    /**
     * Checks if multi-pass stabilization is available.
     */
    val isAvailable: Boolean
        get() = featureMatcher.isAvailable
}
