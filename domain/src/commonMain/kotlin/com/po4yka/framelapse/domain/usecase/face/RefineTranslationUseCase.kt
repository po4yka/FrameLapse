package com.po4yka.framelapse.domain.usecase.face

import com.po4yka.framelapse.domain.entity.AlignmentMatrix
import com.po4yka.framelapse.domain.entity.OvershootCorrection

/**
 * Refines the translation component of the alignment matrix.
 *
 * This use case corrects overshoot in eye positions by adjusting
 * the translation components of the alignment matrix.
 *
 * Used in both FAST and SLOW modes for translation refinement.
 *
 * Algorithm:
 * 1. Calculate average overshoot from left and right eye positions
 * 2. Subtract overshoot from current translation
 * 3. Return corrected matrix
 */
class RefineTranslationUseCase {

    /**
     * Result of translation refinement.
     *
     * @property matrix The refined alignment matrix.
     * @property correctionApplied Whether a correction was applied.
     * @property correctionX The X correction applied (pixels).
     * @property correctionY The Y correction applied (pixels).
     */
    data class RefinementResult(
        val matrix: AlignmentMatrix,
        val correctionApplied: Boolean,
        val correctionX: Float,
        val correctionY: Float,
    )

    /**
     * Refines translation based on overshoot correction.
     *
     * @param currentMatrix The current alignment matrix.
     * @param overshoot The detected overshoot correction data.
     * @return RefinementResult with refined matrix and correction details.
     */
    operator fun invoke(currentMatrix: AlignmentMatrix, overshoot: OvershootCorrection): RefinementResult {
        if (!overshoot.needsCorrection) {
            return RefinementResult(
                matrix = currentMatrix,
                correctionApplied = false,
                correctionX = 0f,
                correctionY = 0f,
            )
        }

        // Calculate correction as average overshoot
        // Subtracting overshoot moves eyes back toward goal
        val correctionX = -overshoot.averageOvershootX
        val correctionY = -overshoot.averageOvershootY

        // Apply translation correction
        val refinedMatrix = AlignmentMatrix(
            scaleX = currentMatrix.scaleX,
            skewX = currentMatrix.skewX,
            translateX = currentMatrix.translateX + correctionX,
            skewY = currentMatrix.skewY,
            scaleY = currentMatrix.scaleY,
            translateY = currentMatrix.translateY + correctionY,
        )

        return RefinementResult(
            matrix = refinedMatrix,
            correctionApplied = true,
            correctionX = correctionX,
            correctionY = correctionY,
        )
    }

    /**
     * Refines translation with explicit overshoot values.
     *
     * @param currentMatrix The current alignment matrix.
     * @param overshotLeftX Left eye X overshoot (pixels).
     * @param overshotLeftY Left eye Y overshoot (pixels).
     * @param overshotRightX Right eye X overshoot (pixels).
     * @param overshotRightY Right eye Y overshoot (pixels).
     * @param currentScore Current alignment score.
     * @return RefinementResult with refined matrix and correction details.
     */
    fun invoke(
        currentMatrix: AlignmentMatrix,
        overshotLeftX: Float,
        overshotLeftY: Float,
        overshotRightX: Float,
        overshotRightY: Float,
        currentScore: Float,
    ): RefinementResult {
        val overshoot = OvershootCorrection(
            overshotLeftX = overshotLeftX,
            overshotLeftY = overshotLeftY,
            overshotRightX = overshotRightX,
            overshotRightY = overshotRightY,
            currentScore = currentScore,
        )
        return invoke(currentMatrix, overshoot)
    }
}
