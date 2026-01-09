package com.po4yka.framelapse.domain.usecase.alignment

import com.po4yka.framelapse.domain.entity.StabilizationMode
import com.po4yka.framelapse.domain.entity.StabilizationProgress
import com.po4yka.framelapse.domain.entity.StabilizationStage
import com.po4yka.framelapse.domain.util.Result

/**
 * Small pipeline helper to standardize alignment progress reporting across modes.
 */
class AlignmentPipeline<T>(
    private val mode: StabilizationMode,
    private val totalSteps: Int,
    private val onProgress: ((StabilizationProgress) -> Unit)?,
) {
    suspend fun execute(initial: T, steps: List<AlignmentPipelineStep<T>>): Result<T> {
        var current = initial
        for ((index, step) in steps.withIndex()) {
            onProgress?.invoke(
                StabilizationProgress(
                    currentPass = index + 1,
                    maxPasses = totalSteps,
                    currentStage = step.stage,
                    currentScore = 0f,
                    progressPercent = (index + 1).toFloat() / totalSteps.toFloat(),
                    message = step.message,
                    mode = mode,
                ),
            )

            val result = step.action(current)
            if (result.isError) {
                return result
            }
            current = result.getOrNull()!!
        }
        return Result.Success(current)
    }
}

data class AlignmentPipelineStep<T>(
    val stage: StabilizationStage,
    val message: String,
    val action: suspend (T) -> Result<T>,
)
