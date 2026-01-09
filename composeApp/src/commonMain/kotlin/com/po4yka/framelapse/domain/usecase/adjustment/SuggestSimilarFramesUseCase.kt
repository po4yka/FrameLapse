package com.po4yka.framelapse.domain.usecase.adjustment

import com.po4yka.framelapse.domain.entity.BodyLandmarks
import com.po4yka.framelapse.domain.entity.ContentType
import com.po4yka.framelapse.domain.entity.FaceLandmarks
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.entity.Landmarks
import com.po4yka.framelapse.domain.repository.FrameRepository
import com.po4yka.framelapse.domain.util.Result
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Suggests frames similar to a reference frame for batch adjustment application.
 *
 * Similarity is determined based on:
 * - Landmark position similarity (eyes, shoulders, etc.)
 * - Detection confidence (low confidence frames may benefit from manual adjustment)
 * - Missing detection (frames with no landmarks detected)
 */
class SuggestSimilarFramesUseCase(private val frameRepository: FrameRepository) {
    /**
     * Suggestion result containing similar and low-confidence frames.
     */
    data class SuggestionResult(
        /** Frames with similar landmarks to the reference. */
        val similarFrames: List<FrameSuggestion>,
        /** Frames with low confidence that may benefit from manual adjustment. */
        val lowConfidenceFrames: List<FrameSuggestion>,
        /** Frames with no detected landmarks. */
        val noDetectionFrames: List<FrameSuggestion>,
    )

    /**
     * A single frame suggestion with metadata.
     */
    data class FrameSuggestion(
        val frame: Frame,
        /** Similarity score (0.0 = no similarity, 1.0 = identical). */
        val similarityScore: Float,
        /** Reason this frame was suggested. */
        val reason: SuggestionReason,
    )

    /**
     * Reasons why a frame was suggested for batch adjustment.
     */
    enum class SuggestionReason {
        /** Frame has similar landmark positions to the reference. */
        SIMILAR_LANDMARKS,

        /** Frame has low detection confidence. */
        LOW_CONFIDENCE,

        /** Frame has no landmarks detected at all. */
        NO_DETECTION,
    }

    /**
     * Suggests frames similar to a reference frame.
     *
     * @param referenceFrameId The frame to compare against.
     * @param projectId The project containing the frames.
     * @param contentType The content type to consider for similarity.
     * @param maxSuggestions Maximum number of suggestions per category.
     * @param similarityThreshold Minimum similarity score for similar frames (0.0-1.0).
     * @param confidenceThreshold Frames below this confidence are considered low confidence.
     * @return Result containing the suggestion results.
     */
    suspend operator fun invoke(
        referenceFrameId: String,
        projectId: String,
        contentType: ContentType,
        maxSuggestions: Int = DEFAULT_MAX_SUGGESTIONS,
        similarityThreshold: Float = DEFAULT_SIMILARITY_THRESHOLD,
        confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    ): Result<SuggestionResult> {
        // Get reference frame
        val refResult = frameRepository.getFrame(referenceFrameId)
        if (refResult is Result.Error) {
            return Result.Error(refResult.exception, "Failed to get reference frame")
        }
        val referenceFrame = (refResult as Result.Success).data

        // Get all frames in project
        val framesResult = frameRepository.getFramesByProject(projectId)
        if (framesResult is Result.Error) {
            return Result.Error(framesResult.exception, "Failed to get project frames")
        }
        val allFrames = (framesResult as Result.Success).data
            .filter { it.id != referenceFrameId } // Exclude reference frame

        val referenceLandmarks = referenceFrame.landmarks

        val similarFrames = mutableListOf<FrameSuggestion>()
        val lowConfidenceFrames = mutableListOf<FrameSuggestion>()
        val noDetectionFrames = mutableListOf<FrameSuggestion>()

        for (frame in allFrames) {
            when {
                // No landmarks detected
                frame.landmarks == null -> {
                    noDetectionFrames.add(
                        FrameSuggestion(
                            frame = frame,
                            similarityScore = 0f,
                            reason = SuggestionReason.NO_DETECTION,
                        ),
                    )
                }

                // Low confidence
                (frame.confidence ?: 0f) < confidenceThreshold -> {
                    lowConfidenceFrames.add(
                        FrameSuggestion(
                            frame = frame,
                            similarityScore = frame.confidence ?: 0f,
                            reason = SuggestionReason.LOW_CONFIDENCE,
                        ),
                    )
                }

                // Calculate similarity if reference has landmarks
                referenceLandmarks != null -> {
                    val similarity = calculateSimilarity(
                        referenceLandmarks,
                        frame.landmarks,
                        contentType,
                    )
                    if (similarity >= similarityThreshold) {
                        similarFrames.add(
                            FrameSuggestion(
                                frame = frame,
                                similarityScore = similarity,
                                reason = SuggestionReason.SIMILAR_LANDMARKS,
                            ),
                        )
                    }
                }
            }
        }

        // Sort and limit results
        return Result.Success(
            SuggestionResult(
                similarFrames = similarFrames
                    .sortedByDescending { it.similarityScore }
                    .take(maxSuggestions),
                lowConfidenceFrames = lowConfidenceFrames
                    .sortedBy { it.similarityScore } // Lowest confidence first
                    .take(maxSuggestions),
                noDetectionFrames = noDetectionFrames
                    .take(maxSuggestions),
            ),
        )
    }

    /**
     * Calculates similarity between two sets of landmarks.
     * Returns a score between 0.0 (completely different) and 1.0 (identical).
     */
    private fun calculateSimilarity(reference: Landmarks, other: Landmarks, contentType: ContentType): Float =
        when (contentType) {
            ContentType.FACE -> calculateFaceSimilarity(reference, other)
            ContentType.BODY, ContentType.MUSCLE -> calculateBodySimilarity(reference, other)
            ContentType.LANDSCAPE -> calculateLandscapeSimilarity(reference, other)
        }

    private fun calculateFaceSimilarity(reference: Landmarks, other: Landmarks): Float {
        val refFace = reference as? FaceLandmarks ?: return 0f
        val otherFace = other as? FaceLandmarks ?: return 0f

        // Calculate eye position differences
        val leftEyeDiff = distance(
            refFace.leftEyeCenter.x,
            refFace.leftEyeCenter.y,
            otherFace.leftEyeCenter.x,
            otherFace.leftEyeCenter.y,
        )
        val rightEyeDiff = distance(
            refFace.rightEyeCenter.x,
            refFace.rightEyeCenter.y,
            otherFace.rightEyeCenter.x,
            otherFace.rightEyeCenter.y,
        )

        // Calculate eye distance similarity
        val refEyeDist = distance(
            refFace.leftEyeCenter.x,
            refFace.leftEyeCenter.y,
            refFace.rightEyeCenter.x,
            refFace.rightEyeCenter.y,
        )
        val otherEyeDist = distance(
            otherFace.leftEyeCenter.x,
            otherFace.leftEyeCenter.y,
            otherFace.rightEyeCenter.x,
            otherFace.rightEyeCenter.y,
        )
        val eyeDistRatio = if (refEyeDist > 0) otherEyeDist / refEyeDist else 1f

        // Combine metrics (lower difference = higher similarity)
        val avgPositionDiff = (leftEyeDiff + rightEyeDiff) / 2
        val positionSimilarity = (1f - avgPositionDiff.coerceAtMost(1f))
        val scaleSimilarity = 1f - abs(1f - eyeDistRatio).coerceAtMost(1f)

        return (positionSimilarity * 0.7f + scaleSimilarity * 0.3f)
    }

    private fun calculateBodySimilarity(reference: Landmarks, other: Landmarks): Float {
        val refBody = reference as? BodyLandmarks ?: return 0f
        val otherBody = other as? BodyLandmarks ?: return 0f

        // Calculate shoulder position differences
        val leftShoulderDiff = distance(
            refBody.leftShoulder.x,
            refBody.leftShoulder.y,
            otherBody.leftShoulder.x,
            otherBody.leftShoulder.y,
        )
        val rightShoulderDiff = distance(
            refBody.rightShoulder.x,
            refBody.rightShoulder.y,
            otherBody.rightShoulder.x,
            otherBody.rightShoulder.y,
        )

        val avgPositionDiff = (leftShoulderDiff + rightShoulderDiff) / 2
        return (1f - avgPositionDiff.coerceAtMost(1f))
    }

    private fun calculateLandscapeSimilarity(reference: Landmarks, other: Landmarks): Float {
        // Landscape uses feature matching, so landmark-based similarity isn't directly applicable
        // Return moderate similarity based on bounding box overlap
        val refBox = reference.boundingBox
        val otherBox = other.boundingBox

        val overlapLeft = maxOf(refBox.left, otherBox.left)
        val overlapRight = minOf(refBox.right, otherBox.right)
        val overlapTop = maxOf(refBox.top, otherBox.top)
        val overlapBottom = minOf(refBox.bottom, otherBox.bottom)

        if (overlapRight <= overlapLeft || overlapBottom <= overlapTop) {
            return 0f
        }

        val overlapArea = (overlapRight - overlapLeft) * (overlapBottom - overlapTop)
        val refArea = refBox.width * refBox.height
        val otherArea = otherBox.width * otherBox.height
        val unionArea = refArea + otherArea - overlapArea

        return if (unionArea > 0) overlapArea / unionArea else 0f
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        const val DEFAULT_MAX_SUGGESTIONS = 10
        const val DEFAULT_SIMILARITY_THRESHOLD = 0.7f
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.5f
    }
}
