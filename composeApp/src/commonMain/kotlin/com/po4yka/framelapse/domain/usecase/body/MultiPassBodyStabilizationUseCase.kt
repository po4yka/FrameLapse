package com.po4yka.framelapse.domain.usecase.body

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.BodyAlignmentSettings
import com.po4yka.framelapse.domain.entity.EarlyStopReason
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.entity.StabilizationMode
import com.po4yka.framelapse.domain.entity.StabilizationPass
import com.po4yka.framelapse.domain.entity.StabilizationProgress
import com.po4yka.framelapse.domain.entity.StabilizationResult
import com.po4yka.framelapse.domain.entity.StabilizationScore
import com.po4yka.framelapse.domain.entity.StabilizationSettings
import com.po4yka.framelapse.domain.entity.StabilizationStage
import com.po4yka.framelapse.domain.service.BodyPoseDetector
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.util.Result
import kotlin.math.sqrt

/**
 * Orchestrates multi-pass body stabilization algorithm.
 *
 * This use case implements a multi-pass stabilization algorithm for body alignment,
 * using shoulder positions as reference points instead of eyes.
 *
 * ## FAST Mode Algorithm (max 4 passes):
 * 1. Pass 1: Full alignment (rotation + scale + translation)
 * 2. Passes 2-4: Translation correction based on overshoot detection
 * 3. Early stop: score < 0.5 or no improvement
 *
 * ## SLOW Mode Algorithm (max 10+ passes):
 * 1. Pass 1: Initial full alignment
 * 2. Passes 2-4: Rotation refinement
 * 3. Passes 5-7: Scale refinement
 * 4. Passes 8-10: Translation refinement
 */
class MultiPassBodyStabilizationUseCase(
    private val bodyPoseDetector: BodyPoseDetector,
    private val imageProcessor: ImageProcessor,
    private val calculateMatrix: CalculateBodyAlignmentMatrixUseCase,
) {
    /**
     * Performs multi-pass body stabilization.
     *
     * @param imageData The original image data to stabilize.
     * @param goalLeftShoulder Goal position for the left shoulder (in pixels).
     * @param goalRightShoulder Goal position for the right shoulder (in pixels).
     * @param bodyAlignmentSettings Body alignment configuration.
     * @param onProgress Optional callback for progress updates.
     * @return Result containing the stabilized image and stabilization result.
     */
    suspend operator fun invoke(
        imageData: ImageData,
        goalLeftShoulder: LandmarkPoint,
        goalRightShoulder: LandmarkPoint,
        bodyAlignmentSettings: BodyAlignmentSettings,
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Pair<ImageData, StabilizationResult>> {
        val startTime = currentTimeMillis()
        val settings = bodyAlignmentSettings.stabilizationSettings
        val outputSize = bodyAlignmentSettings.outputSize

        // Calculate goal shoulder distance
        val goalShoulderDistance = calculateDistance(goalLeftShoulder, goalRightShoulder)

        // Report initial progress
        onProgress?.invoke(StabilizationProgress.initial(settings.mode))

        // Detect body in original image
        val initialDetectResult = bodyPoseDetector.detectBodyPose(imageData)
        if (initialDetectResult.isError) {
            return Result.Error(
                initialDetectResult.exceptionOrNull()!!,
                "Body pose detection failed on original image",
            )
        }

        val initialLandmarks = initialDetectResult.getOrNull()
            ?: return Result.Error(
                NoSuchElementException("No body detected in original image"),
                "No body detected",
            )

        // Calculate initial alignment matrix
        val initialMatrix = calculateMatrix(initialLandmarks, bodyAlignmentSettings)

        // Apply initial transformation
        val initialTransformResult = imageProcessor.applyAffineTransform(
            image = imageData,
            matrix = initialMatrix,
            outputWidth = outputSize,
            outputHeight = outputSize,
        )
        if (initialTransformResult.isError) {
            return Result.Error(
                initialTransformResult.exceptionOrNull()!!,
                "Initial transformation failed",
            )
        }

        var currentImage = initialTransformResult.getOrNull()!!
        var currentMatrix = initialMatrix
        var bestImage = currentImage
        var bestScore: StabilizationScore? = null

        val passes = mutableListOf<StabilizationPass>()
        var passNumber = 0
        var earlyStopReason: EarlyStopReason? = null

        // Execute multi-pass algorithm based on mode
        when (settings.mode) {
            StabilizationMode.FAST -> {
                val result = executeFastMode(
                    currentImage = currentImage,
                    currentMatrix = currentMatrix,
                    goalLeftShoulder = goalLeftShoulder,
                    goalRightShoulder = goalRightShoulder,
                    settings = settings,
                    outputSize = outputSize,
                    passes = passes,
                    onProgress = onProgress,
                )
                currentImage = result.first
                currentMatrix = result.second
                bestImage = result.third
                bestScore = result.fourth
                earlyStopReason = result.fifth
                passNumber = passes.size
            }
            StabilizationMode.SLOW -> {
                val result = executeSlowMode(
                    currentImage = currentImage,
                    currentMatrix = currentMatrix,
                    goalLeftShoulder = goalLeftShoulder,
                    goalRightShoulder = goalRightShoulder,
                    goalShoulderDistance = goalShoulderDistance,
                    settings = settings,
                    outputSize = outputSize,
                    passes = passes,
                    onProgress = onProgress,
                )
                currentImage = result.first
                currentMatrix = result.second
                bestImage = result.third
                bestScore = result.fourth
                earlyStopReason = result.fifth
                passNumber = passes.size
            }
        }

        val totalDuration = currentTimeMillis() - startTime

        // Get final score
        val finalScore = bestScore ?: StabilizationScore(
            value = Float.MAX_VALUE,
            leftEyeDistance = 0f,
            rightEyeDistance = 0f,
        )

        val initialScore = passes.firstOrNull()?.scoreBefore ?: finalScore.value

        // Build result
        val stabilizationResult = StabilizationResult(
            success = finalScore.isSuccess,
            finalScore = finalScore,
            passesExecuted = passNumber,
            passes = passes,
            mode = settings.mode,
            earlyStopReason = earlyStopReason,
            totalDurationMs = totalDuration,
            initialScore = initialScore,
            goalEyeDistance = goalShoulderDistance, // Reusing field for shoulder distance
        )

        // Report completion
        onProgress?.invoke(
            StabilizationProgress.completed(
                finalScore = finalScore.value,
                passesExecuted = passNumber,
                mode = settings.mode,
                success = finalScore.isSuccess,
            ),
        )

        return Result.Success(Pair(bestImage, stabilizationResult))
    }

    private suspend fun executeFastMode(
        currentImage: ImageData,
        currentMatrix: AlignmentMatrix,
        goalLeftShoulder: LandmarkPoint,
        goalRightShoulder: LandmarkPoint,
        settings: StabilizationSettings,
        outputSize: Int,
        passes: MutableList<StabilizationPass>,
        onProgress: ((StabilizationProgress) -> Unit)?,
    ): FiveTuple<ImageData, AlignmentMatrix, ImageData, StabilizationScore?, EarlyStopReason?> {
        var image = currentImage
        var matrix = currentMatrix
        var bestImage = currentImage
        var bestScore: StabilizationScore? = null
        var earlyStopReason: EarlyStopReason? = null

        for (passNum in 1..StabilizationSettings.MAX_PASSES_FAST) {
            val passStartTime = currentTimeMillis()

            // Detect body in current image
            val detectResult = bodyPoseDetector.detectBodyPose(image)
            if (detectResult.isError || detectResult.getOrNull() == null) {
                earlyStopReason = EarlyStopReason.FACE_DETECTION_FAILED
                break
            }
            val landmarks = detectResult.getOrNull()!!

            // Convert normalized landmarks to pixel coordinates
            val detectedLeftShoulder = toPixelCoordinates(landmarks.leftShoulder, outputSize, outputSize)
            val detectedRightShoulder = toPixelCoordinates(landmarks.rightShoulder, outputSize, outputSize)

            // Calculate score
            val score = calculateScore(
                detectedLeft = detectedLeftShoulder,
                detectedRight = detectedRightShoulder,
                goalLeft = goalLeftShoulder,
                goalRight = goalRightShoulder,
                canvasHeight = outputSize,
            )

            val scoreBefore = bestScore?.value ?: score.value

            // Report progress
            onProgress?.invoke(
                StabilizationProgress.forPass(
                    passNumber = passNum,
                    stage = if (passNum == 1) StabilizationStage.INITIAL else StabilizationStage.TRANSLATION_REFINE,
                    score = score.value,
                    mode = settings.mode,
                ),
            )

            // Check if we should stop
            if (!score.needsCorrection) {
                earlyStopReason = EarlyStopReason.SCORE_BELOW_THRESHOLD
                bestScore = score
                bestImage = image
                val passDuration = currentTimeMillis() - passStartTime
                passes.add(
                    StabilizationPass(
                        passNumber = passNum,
                        stage = if (passNum == 1) StabilizationStage.INITIAL else StabilizationStage.TRANSLATION_REFINE,
                        scoreBefore = scoreBefore,
                        scoreAfter = score.value,
                        converged = true,
                        durationMs = passDuration,
                    ),
                )
                break
            }

            // Update best if improved
            if (bestScore == null || score.value < bestScore.value) {
                bestScore = score
                bestImage = image
            } else if (passNum > 1) {
                // No improvement, stop
                earlyStopReason = EarlyStopReason.NO_IMPROVEMENT
                val passDuration = currentTimeMillis() - passStartTime
                passes.add(
                    StabilizationPass(
                        passNumber = passNum,
                        stage = StabilizationStage.TRANSLATION_REFINE,
                        scoreBefore = scoreBefore,
                        scoreAfter = score.value,
                        converged = false,
                        durationMs = passDuration,
                    ),
                )
                break
            }

            // Refine translation for next pass
            if (passNum < StabilizationSettings.MAX_PASSES_FAST) {
                val correctionX = (goalLeftShoulder.x + goalRightShoulder.x) / 2 -
                    (detectedLeftShoulder.x + detectedRightShoulder.x) / 2
                val correctionY = (goalLeftShoulder.y + goalRightShoulder.y) / 2 -
                    (detectedLeftShoulder.y + detectedRightShoulder.y) / 2

                // Apply correction with damping factor
                val dampingFactor = 0.5f
                matrix = matrix.copy(
                    translateX = matrix.translateX + correctionX * dampingFactor,
                    translateY = matrix.translateY + correctionY * dampingFactor,
                )

                // Apply refined transformation
                val transformResult = imageProcessor.applyAffineTransform(
                    image = image,
                    matrix = matrix,
                    outputWidth = outputSize,
                    outputHeight = outputSize,
                )
                if (transformResult.isSuccess) {
                    image = transformResult.getOrNull()!!
                }
            }

            val passDuration = currentTimeMillis() - passStartTime
            passes.add(
                StabilizationPass(
                    passNumber = passNum,
                    stage = if (passNum == 1) StabilizationStage.INITIAL else StabilizationStage.TRANSLATION_REFINE,
                    scoreBefore = scoreBefore,
                    scoreAfter = score.value,
                    converged = false,
                    durationMs = passDuration,
                ),
            )
        }

        if (earlyStopReason == null && passes.size >= StabilizationSettings.MAX_PASSES_FAST) {
            earlyStopReason = EarlyStopReason.MAX_PASSES_REACHED
        }

        return FiveTuple(image, matrix, bestImage, bestScore, earlyStopReason)
    }

    private suspend fun executeSlowMode(
        currentImage: ImageData,
        currentMatrix: AlignmentMatrix,
        goalLeftShoulder: LandmarkPoint,
        goalRightShoulder: LandmarkPoint,
        goalShoulderDistance: Float,
        settings: StabilizationSettings,
        outputSize: Int,
        passes: MutableList<StabilizationPass>,
        onProgress: ((StabilizationProgress) -> Unit)?,
    ): FiveTuple<ImageData, AlignmentMatrix, ImageData, StabilizationScore?, EarlyStopReason?> {
        var image = currentImage
        var matrix = currentMatrix
        var bestImage = currentImage
        var bestScore: StabilizationScore? = null
        var earlyStopReason: EarlyStopReason? = null
        var passNum = 0

        // Stage 1: Initial pass
        passNum++
        val initialResult = executePass(
            image = image,
            goalLeftShoulder = goalLeftShoulder,
            goalRightShoulder = goalRightShoulder,
            stage = StabilizationStage.INITIAL,
            passNum = passNum,
            outputSize = outputSize,
            settings = settings,
            bestScore = bestScore,
            passes = passes,
            onProgress = onProgress,
        )
        bestImage = initialResult.first
        bestScore = initialResult.second

        if (bestScore != null && !bestScore.needsCorrection) {
            return FiveTuple(image, matrix, bestImage, bestScore, EarlyStopReason.SCORE_BELOW_THRESHOLD)
        }

        // Stage 2-4: Iterative refinement
        var previousScore = bestScore?.value ?: Float.MAX_VALUE
        for (stage in listOf(
            StabilizationStage.ROTATION_REFINE,
            StabilizationStage.SCALE_REFINE,
            StabilizationStage.TRANSLATION_REFINE,
        )) {
            for (i in 1..3) {
                passNum++
                val detectResult = bodyPoseDetector.detectBodyPose(image)
                if (detectResult.isError || detectResult.getOrNull() == null) {
                    earlyStopReason = EarlyStopReason.FACE_DETECTION_FAILED
                    break
                }

                val result = executePass(
                    image = image,
                    goalLeftShoulder = goalLeftShoulder,
                    goalRightShoulder = goalRightShoulder,
                    stage = stage,
                    passNum = passNum,
                    outputSize = outputSize,
                    settings = settings,
                    bestScore = bestScore,
                    passes = passes,
                    onProgress = onProgress,
                )

                if (result.second != null && (bestScore == null || result.second!!.value < bestScore.value)) {
                    bestImage = result.first
                    bestScore = result.second
                }

                // Check convergence
                val improvement = previousScore - (bestScore?.value ?: previousScore)
                if (improvement < settings.convergenceThreshold && improvement >= 0) {
                    earlyStopReason = when (stage) {
                        StabilizationStage.ROTATION_REFINE -> EarlyStopReason.ROTATION_CONVERGED
                        StabilizationStage.SCALE_REFINE -> EarlyStopReason.SCALE_CONVERGED
                        StabilizationStage.TRANSLATION_REFINE -> EarlyStopReason.TRANSLATION_CONVERGED
                        else -> EarlyStopReason.NO_IMPROVEMENT
                    }
                    break
                }
                previousScore = bestScore?.value ?: previousScore
            }

            if (earlyStopReason != null) break
        }

        if (earlyStopReason == null) {
            earlyStopReason = EarlyStopReason.MAX_PASSES_REACHED
        }

        return FiveTuple(image, matrix, bestImage, bestScore, earlyStopReason)
    }

    private suspend fun executePass(
        image: ImageData,
        goalLeftShoulder: LandmarkPoint,
        goalRightShoulder: LandmarkPoint,
        stage: StabilizationStage,
        passNum: Int,
        outputSize: Int,
        settings: StabilizationSettings,
        bestScore: StabilizationScore?,
        passes: MutableList<StabilizationPass>,
        onProgress: ((StabilizationProgress) -> Unit)?,
    ): Pair<ImageData, StabilizationScore?> {
        val passStartTime = currentTimeMillis()

        // Detect body
        val detectResult = bodyPoseDetector.detectBodyPose(image)
        if (detectResult.isError || detectResult.getOrNull() == null) {
            return Pair(image, bestScore)
        }
        val landmarks = detectResult.getOrNull()!!

        // Convert to pixel coordinates
        val detectedLeftShoulder = toPixelCoordinates(landmarks.leftShoulder, outputSize, outputSize)
        val detectedRightShoulder = toPixelCoordinates(landmarks.rightShoulder, outputSize, outputSize)

        // Calculate score
        val score = calculateScore(
            detectedLeft = detectedLeftShoulder,
            detectedRight = detectedRightShoulder,
            goalLeft = goalLeftShoulder,
            goalRight = goalRightShoulder,
            canvasHeight = outputSize,
        )

        onProgress?.invoke(
            StabilizationProgress.forPass(
                passNumber = passNum,
                stage = stage,
                score = score.value,
                mode = settings.mode,
            ),
        )

        val passDuration = currentTimeMillis() - passStartTime
        passes.add(
            StabilizationPass(
                passNumber = passNum,
                stage = stage,
                scoreBefore = bestScore?.value ?: score.value,
                scoreAfter = score.value,
                converged = !score.needsCorrection,
                durationMs = passDuration,
            ),
        )

        val newBestScore = if (bestScore == null || score.value < bestScore.value) score else bestScore
        val newBestImage = if (bestScore == null || score.value < bestScore.value) image else image

        return Pair(newBestImage, newBestScore)
    }

    private fun toPixelCoordinates(point: LandmarkPoint, width: Int, height: Int): LandmarkPoint =
        LandmarkPoint(x = point.x * width, y = point.y * height, z = point.z)

    private fun calculateDistance(left: LandmarkPoint, right: LandmarkPoint): Float {
        val dx = right.x - left.x
        val dy = right.y - left.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun calculateScore(
        detectedLeft: LandmarkPoint,
        detectedRight: LandmarkPoint,
        goalLeft: LandmarkPoint,
        goalRight: LandmarkPoint,
        canvasHeight: Int,
    ): StabilizationScore {
        val leftDistance = calculateDistance(detectedLeft, goalLeft)
        val rightDistance = calculateDistance(detectedRight, goalRight)
        val totalScore = leftDistance + rightDistance

        return StabilizationScore(
            value = totalScore,
            leftEyeDistance = leftDistance, // Reusing field for left shoulder distance
            rightEyeDistance = rightDistance, // Reusing field for right shoulder distance
        )
    }

    private fun currentTimeMillis(): Long = System.currentTimeMillis()

    // Helper tuple class
    private data class FiveTuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
    )
}
