package com.po4yka.framelapse.domain.usecase.face

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.AlignmentSettings
import com.po4yka.framelapse.domain.entity.EarlyStopReason
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.entity.StabilizationMode
import com.po4yka.framelapse.domain.entity.StabilizationPass
import com.po4yka.framelapse.domain.entity.StabilizationProgress
import com.po4yka.framelapse.domain.entity.StabilizationResult
import com.po4yka.framelapse.domain.entity.StabilizationScore
import com.po4yka.framelapse.domain.entity.StabilizationSettings
import com.po4yka.framelapse.domain.entity.StabilizationStage
import com.po4yka.framelapse.domain.service.Clock
import com.po4yka.framelapse.domain.service.FaceDetector
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.service.ImageProcessor
import com.po4yka.framelapse.domain.util.Result
import org.koin.core.annotation.Factory
import kotlin.math.sqrt

/**
 * Orchestrates multi-pass face stabilization algorithm.
 *
 * This use case implements the full multi-pass stabilization algorithm from AgeLapse,
 * supporting both FAST (4 passes, translation-only) and SLOW (10+ passes, full affine) modes.
 *
 * ## FAST Mode Algorithm (max 4 passes):
 * 1. Pass 1: Full alignment (rotation + scale + translation)
 * 2. Passes 2-4: Translation correction based on overshoot detection
 * 3. Early stop: score < 0.5 or no improvement
 *
 * ## SLOW Mode Algorithm (max 10+ passes):
 * 1. Pass 1: Initial full alignment
 * 2. Passes 2-4: Rotation refinement (stop when eyeDeltaY <= 0.1px)
 * 3. Passes 5-7: Scale refinement (stop when scaleError <= 1.0px)
 * 4. Passes 8-10: Translation refinement (stop on convergence < 0.05)
 * 5. Pass 11: Optional cleanup if score >= 20.0
 */
@Factory
class MultiPassStabilizationUseCase(
    private val faceDetector: FaceDetector,
    private val imageProcessor: ImageProcessor,
    private val calculateMatrix: CalculateAlignmentMatrixUseCase,
    private val calculateScore: CalculateStabilizationScoreUseCase,
    private val detectOvershoot: DetectOvershootUseCase,
    private val refineRotation: RefineRotationUseCase,
    private val refineScale: RefineScaleUseCase,
    private val refineTranslation: RefineTranslationUseCase,
    private val clock: Clock,
) {
    /**
     * Performs multi-pass face stabilization.
     *
     * @param imageData The original image data to stabilize.
     * @param goalLeftEye Goal position for the left eye (in pixels).
     * @param goalRightEye Goal position for the right eye (in pixels).
     * @param alignmentSettings Alignment configuration (includes stabilization settings).
     * @param onProgress Optional callback for progress updates.
     * @return Result containing the stabilized image and stabilization result.
     */
    suspend operator fun invoke(
        imageData: ImageData,
        goalLeftEye: LandmarkPoint,
        goalRightEye: LandmarkPoint,
        alignmentSettings: AlignmentSettings,
        onProgress: ((StabilizationProgress) -> Unit)? = null,
    ): Result<Pair<ImageData, StabilizationResult>> {
        val startTime = clock.nowMillis()
        val settings = alignmentSettings.stabilizationSettings
        val outputSize = alignmentSettings.outputSize

        // Calculate goal eye distance
        val goalEyeDistance = calculateEyeDistance(goalLeftEye, goalRightEye)

        // Report initial progress
        onProgress?.invoke(StabilizationProgress.initial(settings.mode))

        // Detect face in original image
        val initialDetectResult = faceDetector.detectFace(imageData)
        if (initialDetectResult.isError) {
            return Result.Error(
                initialDetectResult.exceptionOrNull()!!,
                "Face detection failed on original image",
            )
        }

        val initialLandmarks = initialDetectResult.getOrNull()
            ?: return Result.Error(
                NoSuchElementException("No face detected in original image"),
                "No face detected",
            )

        // Calculate initial alignment matrix
        val initialMatrix = calculateMatrix(initialLandmarks, alignmentSettings)

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
        var bestMatrix = currentMatrix
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
                    goalLeftEye = goalLeftEye,
                    goalRightEye = goalRightEye,
                    settings = settings,
                    outputSize = outputSize,
                    passes = passes,
                    onProgress = onProgress,
                )
                currentImage = result.currentImage
                currentMatrix = result.currentMatrix
                bestImage = result.bestImage
                bestScore = result.bestScore
                earlyStopReason = result.earlyStopReason
                passNumber = passes.size
            }
            StabilizationMode.SLOW -> {
                val result = executeSlowMode(
                    currentImage = currentImage,
                    currentMatrix = currentMatrix,
                    goalLeftEye = goalLeftEye,
                    goalRightEye = goalRightEye,
                    goalEyeDistance = goalEyeDistance,
                    settings = settings,
                    outputSize = outputSize,
                    passes = passes,
                    onProgress = onProgress,
                )
                currentImage = result.currentImage
                currentMatrix = result.currentMatrix
                bestImage = result.bestImage
                bestScore = result.bestScore
                earlyStopReason = result.earlyStopReason
                passNumber = passes.size
            }
        }

        val totalDuration = clock.nowMillis() - startTime

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
            goalEyeDistance = goalEyeDistance,
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

    /**
     * Executes FAST mode stabilization (max 4 passes, translation-only refinement).
     */
    private suspend fun executeFastMode(
        currentImage: ImageData,
        currentMatrix: AlignmentMatrix,
        goalLeftEye: LandmarkPoint,
        goalRightEye: LandmarkPoint,
        settings: StabilizationSettings,
        outputSize: Int,
        passes: MutableList<StabilizationPass>,
        onProgress: ((StabilizationProgress) -> Unit)?,
    ): ExecutionResult {
        var image = currentImage
        var matrix = currentMatrix
        var bestImage = currentImage
        var bestScore: StabilizationScore? = null
        var earlyStopReason: EarlyStopReason? = null

        for (passNum in 1..StabilizationSettings.MAX_PASSES_FAST) {
            val passStartTime = clock.nowMillis()

            // Detect face in current image
            val detectResult = faceDetector.detectFace(image)
            if (detectResult.isError || detectResult.getOrNull() == null) {
                earlyStopReason = EarlyStopReason.FACE_DETECTION_FAILED
                break
            }
            val landmarks = detectResult.getOrNull()!!

            // Convert normalized landmarks to pixel coordinates
            val detectedLeftEye = toPixelCoordinates(landmarks.leftEyeCenter, outputSize, outputSize)
            val detectedRightEye = toPixelCoordinates(landmarks.rightEyeCenter, outputSize, outputSize)

            // Calculate score
            val score = calculateScore(
                detectedLeftEyeX = detectedLeftEye.x,
                detectedLeftEyeY = detectedLeftEye.y,
                detectedRightEyeX = detectedRightEye.x,
                detectedRightEyeY = detectedRightEye.y,
                goalLeftEyeX = goalLeftEye.x,
                goalLeftEyeY = goalLeftEye.y,
                goalRightEyeX = goalRightEye.x,
                goalRightEyeY = goalRightEye.y,
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
                // Score is already good, no correction needed
                earlyStopReason = EarlyStopReason.SCORE_BELOW_THRESHOLD
                bestScore = score
                bestImage = image
                val passDuration = clock.nowMillis() - passStartTime
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
                val passDuration = clock.nowMillis() - passStartTime
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

            // Detect overshoot and refine translation
            if (passNum < StabilizationSettings.MAX_PASSES_FAST) {
                val overshoot = detectOvershoot(
                    detectedLeftEyeX = detectedLeftEye.x,
                    detectedLeftEyeY = detectedLeftEye.y,
                    detectedRightEyeX = detectedRightEye.x,
                    detectedRightEyeY = detectedRightEye.y,
                    goalLeftEyeX = goalLeftEye.x,
                    goalLeftEyeY = goalLeftEye.y,
                    goalRightEyeX = goalRightEye.x,
                    goalRightEyeY = goalRightEye.y,
                    currentScore = score.value,
                )

                if (overshoot.needsCorrection) {
                    val refinement = refineTranslation(matrix, overshoot)
                    matrix = refinement.matrix

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
            }

            val passDuration = clock.nowMillis() - passStartTime
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

        return ExecutionResult(image, matrix, bestImage, bestScore, earlyStopReason)
    }

    /**
     * Executes SLOW mode stabilization (full affine refinement).
     */
    private suspend fun executeSlowMode(
        currentImage: ImageData,
        currentMatrix: AlignmentMatrix,
        goalLeftEye: LandmarkPoint,
        goalRightEye: LandmarkPoint,
        goalEyeDistance: Float,
        settings: StabilizationSettings,
        outputSize: Int,
        passes: MutableList<StabilizationPass>,
        onProgress: ((StabilizationProgress) -> Unit)?,
    ): ExecutionResult {
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
            matrix = matrix,
            goalLeftEye = goalLeftEye,
            goalRightEye = goalRightEye,
            stage = StabilizationStage.INITIAL,
            passNum = passNum,
            outputSize = outputSize,
            settings = settings,
            bestScore = bestScore,
            passes = passes,
            onProgress = onProgress,
        )
        image = initialResult.currentImage
        matrix = initialResult.currentMatrix
        bestImage = initialResult.bestImage
        bestScore = initialResult.bestScore

        if (!bestScore!!.needsCorrection) {
            return ExecutionResult(image, matrix, bestImage, bestScore, EarlyStopReason.SCORE_BELOW_THRESHOLD)
        }

        // Stage 2: Rotation refinement (passes 2-4)
        for (i in 1..3) {
            passNum++
            val detectResult = faceDetector.detectFace(image)
            if (detectResult.isError || detectResult.getOrNull() == null) {
                earlyStopReason = EarlyStopReason.FACE_DETECTION_FAILED
                break
            }
            val landmarks = detectResult.getOrNull()!!

            val rotationResult = refineRotation(
                currentMatrix = matrix,
                landmarks = landmarks,
                settings = settings,
                canvasWidth = outputSize,
                canvasHeight = outputSize,
            )

            if (rotationResult.converged) {
                earlyStopReason = EarlyStopReason.ROTATION_CONVERGED
                break
            }

            matrix = rotationResult.matrix

            // Apply transformation
            val transformResult = imageProcessor.applyAffineTransform(
                image = image,
                matrix = matrix,
                outputWidth = outputSize,
                outputHeight = outputSize,
            )
            if (transformResult.isSuccess) {
                image = transformResult.getOrNull()!!
            }

            onProgress?.invoke(
                StabilizationProgress.forPass(
                    passNumber = passNum,
                    stage = StabilizationStage.ROTATION_REFINE,
                    score = bestScore?.value ?: 0f,
                    mode = settings.mode,
                ),
            )

            passes.add(
                StabilizationPass(
                    passNumber = passNum,
                    stage = StabilizationStage.ROTATION_REFINE,
                    scoreBefore = bestScore?.value ?: 0f,
                    scoreAfter = bestScore?.value ?: 0f,
                    converged = rotationResult.converged,
                    durationMs = 0L,
                ),
            )
        }

        if (earlyStopReason != null) {
            return ExecutionResult(image, matrix, bestImage, bestScore, earlyStopReason)
        }

        // Stage 3: Scale refinement (passes 5-7)
        for (i in 1..3) {
            passNum++
            val detectResult = faceDetector.detectFace(image)
            if (detectResult.isError || detectResult.getOrNull() == null) {
                earlyStopReason = EarlyStopReason.FACE_DETECTION_FAILED
                break
            }
            val landmarks = detectResult.getOrNull()!!

            val scaleResult = refineScale(
                currentMatrix = matrix,
                landmarks = landmarks,
                goalEyeDistance = goalEyeDistance,
                settings = settings,
                canvasWidth = outputSize,
                canvasHeight = outputSize,
            )

            if (scaleResult.converged) {
                earlyStopReason = EarlyStopReason.SCALE_CONVERGED
                break
            }

            matrix = scaleResult.matrix

            // Apply transformation
            val transformResult = imageProcessor.applyAffineTransform(
                image = image,
                matrix = matrix,
                outputWidth = outputSize,
                outputHeight = outputSize,
            )
            if (transformResult.isSuccess) {
                image = transformResult.getOrNull()!!
            }

            onProgress?.invoke(
                StabilizationProgress.forPass(
                    passNumber = passNum,
                    stage = StabilizationStage.SCALE_REFINE,
                    score = bestScore?.value ?: 0f,
                    mode = settings.mode,
                ),
            )

            passes.add(
                StabilizationPass(
                    passNumber = passNum,
                    stage = StabilizationStage.SCALE_REFINE,
                    scoreBefore = bestScore?.value ?: 0f,
                    scoreAfter = scaleResult.scaleError,
                    converged = scaleResult.converged,
                    durationMs = 0L,
                ),
            )
        }

        if (earlyStopReason != null && earlyStopReason != EarlyStopReason.SCALE_CONVERGED) {
            return ExecutionResult(image, matrix, bestImage, bestScore, earlyStopReason)
        }
        earlyStopReason = null

        // Stage 4: Translation refinement (passes 8-10)
        var previousScore = bestScore?.value ?: Float.MAX_VALUE
        for (i in 1..3) {
            passNum++
            val result = executePass(
                image = image,
                matrix = matrix,
                goalLeftEye = goalLeftEye,
                goalRightEye = goalRightEye,
                stage = StabilizationStage.TRANSLATION_REFINE,
                passNum = passNum,
                outputSize = outputSize,
                settings = settings,
                bestScore = bestScore,
                passes = passes,
                onProgress = onProgress,
            )
            image = result.currentImage
            matrix = result.currentMatrix
            if (result.bestScore != null && (bestScore == null || result.bestScore.value < bestScore.value)) {
                bestImage = result.bestImage
                bestScore = result.bestScore
            }

            // Check convergence
            val improvement = previousScore - (bestScore?.value ?: previousScore)
            if (improvement < settings.convergenceThreshold && improvement >= 0) {
                earlyStopReason = EarlyStopReason.TRANSLATION_CONVERGED
                break
            }
            previousScore = bestScore?.value ?: previousScore
        }

        if (earlyStopReason == null) {
            earlyStopReason = EarlyStopReason.MAX_PASSES_REACHED
        }

        return ExecutionResult(image, matrix, bestImage, bestScore, earlyStopReason)
    }

    /**
     * Executes a single stabilization pass.
     */
    private suspend fun executePass(
        image: ImageData,
        matrix: AlignmentMatrix,
        goalLeftEye: LandmarkPoint,
        goalRightEye: LandmarkPoint,
        stage: StabilizationStage,
        passNum: Int,
        outputSize: Int,
        settings: StabilizationSettings,
        bestScore: StabilizationScore?,
        passes: MutableList<StabilizationPass>,
        onProgress: ((StabilizationProgress) -> Unit)?,
    ): PassResult {
        val passStartTime = clock.nowMillis()

        // Detect face
        val detectResult = faceDetector.detectFace(image)
        if (detectResult.isError || detectResult.getOrNull() == null) {
            return PassResult(image, matrix, image, bestScore)
        }
        val landmarks = detectResult.getOrNull()!!

        // Convert to pixel coordinates
        val detectedLeftEye = toPixelCoordinates(landmarks.leftEyeCenter, outputSize, outputSize)
        val detectedRightEye = toPixelCoordinates(landmarks.rightEyeCenter, outputSize, outputSize)

        // Calculate score
        val score = calculateScore(
            detectedLeftEyeX = detectedLeftEye.x,
            detectedLeftEyeY = detectedLeftEye.y,
            detectedRightEyeX = detectedRightEye.x,
            detectedRightEyeY = detectedRightEye.y,
            goalLeftEyeX = goalLeftEye.x,
            goalLeftEyeY = goalLeftEye.y,
            goalRightEyeX = goalRightEye.x,
            goalRightEyeY = goalRightEye.y,
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

        val passDuration = clock.nowMillis() - passStartTime
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

        return PassResult(image, matrix, newBestImage, newBestScore)
    }

    private fun toPixelCoordinates(point: LandmarkPoint, width: Int, height: Int): LandmarkPoint =
        LandmarkPoint(x = point.x * width, y = point.y * height, z = point.z)

    private fun calculateEyeDistance(leftEye: LandmarkPoint, rightEye: LandmarkPoint): Float {
        val dx = rightEye.x - leftEye.x
        val dy = rightEye.y - leftEye.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Result of executing a single stabilization pass.
     *
     * @property currentImage The image after this pass.
     * @property currentMatrix The alignment matrix after this pass.
     * @property bestImage The best image found so far.
     * @property bestScore The best stabilization score found so far.
     */
    private data class PassResult(
        val currentImage: ImageData,
        val currentMatrix: AlignmentMatrix,
        val bestImage: ImageData,
        val bestScore: StabilizationScore?,
    )

    /**
     * Result of executing a complete stabilization mode (FAST or SLOW).
     *
     * @property currentImage The image after all passes.
     * @property currentMatrix The alignment matrix after all passes.
     * @property bestImage The best image found across all passes.
     * @property bestScore The best stabilization score found.
     * @property earlyStopReason Reason for early termination, if applicable.
     */
    private data class ExecutionResult(
        val currentImage: ImageData,
        val currentMatrix: AlignmentMatrix,
        val bestImage: ImageData,
        val bestScore: StabilizationScore?,
        val earlyStopReason: EarlyStopReason?,
    )
}
