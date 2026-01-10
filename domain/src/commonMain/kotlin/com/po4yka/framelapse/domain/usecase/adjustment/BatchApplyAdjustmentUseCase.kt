package com.po4yka.framelapse.domain.usecase.adjustment

import com.po4yka.framelapse.domain.entity.AlignmentSettings
import com.po4yka.framelapse.domain.entity.BodyManualAdjustment
import com.po4yka.framelapse.domain.entity.ContentType
import com.po4yka.framelapse.domain.entity.FaceManualAdjustment
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.entity.LandscapeManualAdjustment
import com.po4yka.framelapse.domain.entity.ManualAdjustment
import com.po4yka.framelapse.domain.entity.MuscleManualAdjustment
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.repository.ManualAdjustmentRepository
import com.po4yka.framelapse.domain.service.Clock
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.platform.uuid
import org.koin.core.annotation.Factory

/**
 * Applies a manual adjustment from a source frame to multiple target frames.
 *
 * Supports three transfer strategies:
 * - EXACT: Copy exact point positions (for very similar frames)
 * - RELATIVE: Preserve relative offsets from auto-detected points
 * - SCALED: Scale adjustment based on face/body size differences
 */
@Factory
class BatchApplyAdjustmentUseCase(
    private val applyAdjustment: ApplyManualAdjustmentUseCase,
    private val adjustmentRepository: ManualAdjustmentRepository,
    private val frameRepository: FrameRepository,
    private val clock: Clock,
) {
    /**
     * Strategy for transferring adjustments between frames.
     */
    enum class TransferStrategy {
        /** Copy exact landmark positions. Best for frames with nearly identical positioning. */
        EXACT,

        /** Calculate offset from auto-detected landmarks and apply same offset to targets. */
        RELATIVE,

        /** Scale the adjustment based on detected landmark distances. */
        SCALED,
    }

    /**
     * Result of batch apply operation.
     */
    data class BatchResult(
        /** Number of frames successfully adjusted. */
        val successCount: Int,
        /** IDs of frames that failed adjustment. */
        val failedFrameIds: List<String>,
        /** Error messages for failed frames. */
        val errors: Map<String, String>,
    ) {
        val totalCount: Int get() = successCount + failedFrameIds.size
        val hasFailures: Boolean get() = failedFrameIds.isNotEmpty()
    }

    /**
     * Applies adjustment from source frame to multiple target frames.
     *
     * @param sourceFrameId The frame with the adjustment to copy.
     * @param targetFrameIds The frames to apply the adjustment to.
     * @param contentType The content type for the adjustment.
     * @param strategy How to transfer the adjustment.
     * @param settings Alignment settings for transformation.
     * @param onProgress Callback for progress updates (current, total).
     * @return Result containing the batch operation result.
     */
    suspend operator fun invoke(
        sourceFrameId: String,
        targetFrameIds: List<String>,
        contentType: ContentType,
        strategy: TransferStrategy = TransferStrategy.RELATIVE,
        settings: AlignmentSettings = AlignmentSettings(),
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): Result<BatchResult> {
        if (targetFrameIds.isEmpty()) {
            return Result.Success(BatchResult(0, emptyList(), emptyMap()))
        }

        // Get source adjustment
        val sourceAdjustmentResult = adjustmentRepository.getAdjustment(sourceFrameId)
        if (sourceAdjustmentResult is Result.Error) {
            return Result.Error(sourceAdjustmentResult.exception, "Failed to get source adjustment")
        }
        val sourceAdjustment = (sourceAdjustmentResult as Result.Success).data
            ?: return Result.Error(
                IllegalStateException("Source frame has no manual adjustment"),
                "No adjustment found for source frame",
            )

        // Get source frame for reference
        val sourceFrameResult = frameRepository.getFrame(sourceFrameId)
        if (sourceFrameResult is Result.Error) {
            return Result.Error(sourceFrameResult.exception, "Failed to get source frame")
        }
        val sourceFrame = (sourceFrameResult as Result.Success).data

        val successCount = mutableListOf<String>()
        val failedFrameIds = mutableListOf<String>()
        val errors = mutableMapOf<String, String>()

        for ((index, targetFrameId) in targetFrameIds.withIndex()) {
            onProgress?.invoke(index + 1, targetFrameIds.size)

            try {
                // Get target frame
                val targetFrameResult = frameRepository.getFrame(targetFrameId)
                if (targetFrameResult is Result.Error) {
                    failedFrameIds.add(targetFrameId)
                    errors[targetFrameId] = "Failed to get frame"
                    continue
                }
                val targetFrame = (targetFrameResult as Result.Success).data

                // Calculate transferred adjustment
                val transferredAdjustment = when (strategy) {
                    TransferStrategy.EXACT -> copyAdjustment(sourceAdjustment, targetFrameId)
                    TransferStrategy.RELATIVE -> calculateRelativeAdjustment(
                        sourceFrame,
                        targetFrame,
                        sourceAdjustment,
                    )
                    TransferStrategy.SCALED -> calculateScaledAdjustment(
                        sourceFrame,
                        targetFrame,
                        sourceAdjustment,
                    )
                }

                // Apply the adjustment
                val applyResult = applyAdjustment(
                    frameId = targetFrameId,
                    adjustment = transferredAdjustment,
                    contentType = contentType,
                    settings = settings,
                )

                if (applyResult is Result.Error) {
                    failedFrameIds.add(targetFrameId)
                    errors[targetFrameId] = applyResult.message ?: "Unknown error"
                } else {
                    successCount.add(targetFrameId)
                }
            } catch (e: Exception) {
                failedFrameIds.add(targetFrameId)
                errors[targetFrameId] = e.message ?: "Unknown exception"
            }
        }

        return Result.Success(
            BatchResult(
                successCount = successCount.size,
                failedFrameIds = failedFrameIds,
                errors = errors,
            ),
        )
    }

    /**
     * Creates an exact copy of the adjustment with a new ID.
     */
    private fun copyAdjustment(source: ManualAdjustment, targetFrameId: String): ManualAdjustment {
        val newId = uuid()
        val timestamp = clock.nowMillis()

        return when (source) {
            is FaceManualAdjustment -> source.copy(id = newId, timestamp = timestamp)
            is BodyManualAdjustment -> source.copy(id = newId, timestamp = timestamp)
            is MuscleManualAdjustment -> source.copy(
                id = newId,
                timestamp = timestamp,
                bodyAdjustment = source.bodyAdjustment.copy(id = uuid(), timestamp = timestamp),
            )
            is LandscapeManualAdjustment -> source.copy(id = newId, timestamp = timestamp)
        }
    }

    /**
     * Calculates a relative adjustment by computing the offset from auto-detected landmarks.
     */
    private fun calculateRelativeAdjustment(
        sourceFrame: Frame,
        targetFrame: Frame,
        sourceAdjustment: ManualAdjustment,
    ): ManualAdjustment {
        // If target has no landmarks, fall back to exact copy
        val targetLandmarks = targetFrame.landmarks
            ?: return copyAdjustment(sourceAdjustment, targetFrame.id)

        val newId = uuid()
        val timestamp = clock.nowMillis()

        return when (sourceAdjustment) {
            is FaceManualAdjustment -> {
                val sourceLandmarks = sourceFrame.landmarks as? com.po4yka.framelapse.domain.entity.FaceLandmarks
                val targetFace = targetLandmarks as? com.po4yka.framelapse.domain.entity.FaceLandmarks

                if (sourceLandmarks == null || targetFace == null) {
                    return copyAdjustment(sourceAdjustment, targetFrame.id)
                }

                // Calculate offsets from auto-detected to manual
                val leftEyeOffset = LandmarkPoint(
                    x = sourceAdjustment.leftEyeCenter.x - sourceLandmarks.leftEyeCenter.x,
                    y = sourceAdjustment.leftEyeCenter.y - sourceLandmarks.leftEyeCenter.y,
                )
                val rightEyeOffset = LandmarkPoint(
                    x = sourceAdjustment.rightEyeCenter.x - sourceLandmarks.rightEyeCenter.x,
                    y = sourceAdjustment.rightEyeCenter.y - sourceLandmarks.rightEyeCenter.y,
                )

                // Apply offsets to target's auto-detected landmarks
                FaceManualAdjustment(
                    id = newId,
                    timestamp = timestamp,
                    isActive = true,
                    leftEyeCenter = LandmarkPoint(
                        x = targetFace.leftEyeCenter.x + leftEyeOffset.x,
                        y = targetFace.leftEyeCenter.y + leftEyeOffset.y,
                    ),
                    rightEyeCenter = LandmarkPoint(
                        x = targetFace.rightEyeCenter.x + rightEyeOffset.x,
                        y = targetFace.rightEyeCenter.y + rightEyeOffset.y,
                    ),
                    noseTip = sourceAdjustment.noseTip?.let { nose ->
                        sourceLandmarks.noseTip.let { autoNose ->
                            LandmarkPoint(
                                x = targetFace.noseTip.x + (nose.x - autoNose.x),
                                y = targetFace.noseTip.y + (nose.y - autoNose.y),
                            )
                        }
                    },
                )
            }

            is BodyManualAdjustment -> {
                val sourceLandmarks = sourceFrame.landmarks as? com.po4yka.framelapse.domain.entity.BodyLandmarks
                val targetBody = targetLandmarks as? com.po4yka.framelapse.domain.entity.BodyLandmarks

                if (sourceLandmarks == null || targetBody == null) {
                    return copyAdjustment(sourceAdjustment, targetFrame.id)
                }

                BodyManualAdjustment(
                    id = newId,
                    timestamp = timestamp,
                    isActive = true,
                    leftShoulder = applyOffset(
                        targetBody.leftShoulder,
                        sourceAdjustment.leftShoulder,
                        sourceLandmarks.leftShoulder,
                    ),
                    rightShoulder = applyOffset(
                        targetBody.rightShoulder,
                        sourceAdjustment.rightShoulder,
                        sourceLandmarks.rightShoulder,
                    ),
                    leftHip = applyOffset(
                        targetBody.leftHip,
                        sourceAdjustment.leftHip,
                        sourceLandmarks.leftHip,
                    ),
                    rightHip = applyOffset(
                        targetBody.rightHip,
                        sourceAdjustment.rightHip,
                        sourceLandmarks.rightHip,
                    ),
                )
            }

            is MuscleManualAdjustment -> {
                val bodyAdjustment = calculateRelativeAdjustment(
                    sourceFrame,
                    targetFrame,
                    sourceAdjustment.bodyAdjustment,
                ) as BodyManualAdjustment

                MuscleManualAdjustment(
                    id = newId,
                    timestamp = timestamp,
                    isActive = true,
                    bodyAdjustment = bodyAdjustment,
                    regionBounds = sourceAdjustment.regionBounds, // Keep same region bounds
                )
            }

            is LandscapeManualAdjustment -> {
                // For landscape, exact copy is typically best
                copyAdjustment(sourceAdjustment, targetFrame.id) as LandscapeManualAdjustment
            }
        }
    }

    /**
     * Calculates a scaled adjustment based on detected landmark distances.
     */
    private fun calculateScaledAdjustment(
        sourceFrame: Frame,
        targetFrame: Frame,
        sourceAdjustment: ManualAdjustment,
    ): ManualAdjustment {
        // For now, delegate to relative adjustment
        // A full implementation would compute scale factors based on
        // detected face/body sizes and apply proportional scaling
        return calculateRelativeAdjustment(sourceFrame, targetFrame, sourceAdjustment)
    }

    private fun applyOffset(
        targetAuto: LandmarkPoint,
        sourceManual: LandmarkPoint,
        sourceAuto: LandmarkPoint,
    ): LandmarkPoint = LandmarkPoint(
        x = targetAuto.x + (sourceManual.x - sourceAuto.x),
        y = targetAuto.y + (sourceManual.y - sourceAuto.y),
    )
}
