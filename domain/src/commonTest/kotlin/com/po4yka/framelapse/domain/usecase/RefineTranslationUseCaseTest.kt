package com.po4yka.framelapse.domain.usecase

import com.po4yka.framelapse.domain.entity.OvershootCorrection
import com.po4yka.framelapse.domain.usecase.face.RefineTranslationUseCase
import com.po4yka.framelapse.testutil.TestFixtures
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefineTranslationUseCaseTest {

    private lateinit var useCase: RefineTranslationUseCase

    @BeforeTest
    fun setup() {
        useCase = RefineTranslationUseCase()
        TestFixtures.resetCounters()
    }

    // ==================== Translation Correction Tests ====================

    @Test
    fun `invoke applies negative translation to correct positive overshoot`() {
        // Given: Positive overshoot (eyes past goal to the right)
        val matrix = TestFixtures.createIdentityMatrix()
        val overshoot = OvershootCorrection(
            overshotLeftX = 10f,
            overshotLeftY = 5f,
            overshotRightX = 10f,
            overshotRightY = 5f,
            currentScore = 10f, // High score triggers correction
        )

        // When
        val result = useCase(matrix, overshoot)

        // Then: Translation should be applied in opposite direction
        assertTrue(result.matrix.translateX < 0f, "Should translate left to correct right overshoot")
        assertTrue(result.matrix.translateY < 0f, "Should translate up to correct down overshoot")
    }

    @Test
    fun `invoke applies positive translation to correct negative overshoot`() {
        // Given: Negative overshoot (eyes before goal to the left)
        val matrix = TestFixtures.createIdentityMatrix()
        val overshoot = OvershootCorrection(
            overshotLeftX = -10f,
            overshotLeftY = -5f,
            overshotRightX = -10f,
            overshotRightY = -5f,
            currentScore = 10f, // High score triggers correction
        )

        // When
        val result = useCase(matrix, overshoot)

        // Then: Translation should be applied in opposite direction
        assertTrue(result.matrix.translateX > 0f, "Should translate right to correct left undershoot")
        assertTrue(result.matrix.translateY > 0f, "Should translate down to correct up undershoot")
    }

    // ==================== Average Overshoot Tests ====================

    @Test
    fun `invoke uses average overshoot for correction`() {
        // Given: Different overshoots for left and right eyes
        val matrix = TestFixtures.createIdentityMatrix()
        val overshoot = OvershootCorrection(
            overshotLeftX = 20f,
            overshotLeftY = 10f,
            overshotRightX = 10f,
            overshotRightY = 6f,
            currentScore = 10f, // High score triggers correction
        )

        // When
        val result = useCase(matrix, overshoot)

        // Then: Translation should be based on average: X = -15, Y = -8
        val expectedTranslateX = -((20f + 10f) / 2)
        val expectedTranslateY = -((10f + 6f) / 2)

        assertEquals(expectedTranslateX, result.matrix.translateX, 1f)
        assertEquals(expectedTranslateY, result.matrix.translateY, 1f)
    }

    // ==================== Matrix Preservation Tests ====================

    @Test
    fun `invoke preserves scale and rotation components`() {
        // Given: Matrix with scale and rotation
        val matrix = TestFixtures.createAlignmentMatrix(
            scaleX = 1.5f,
            scaleY = 1.5f,
            skewX = 0.1f,
            skewY = -0.1f,
        )
        val overshoot = OvershootCorrection(
            overshotLeftX = 10f,
            overshotLeftY = 5f,
            overshotRightX = 10f,
            overshotRightY = 5f,
            currentScore = 10f, // High score triggers correction
        )

        // When
        val result = useCase(matrix, overshoot)

        // Then: Scale and rotation should be preserved
        assertEquals(matrix.scaleX, result.matrix.scaleX, 0.001f)
        assertEquals(matrix.scaleY, result.matrix.scaleY, 0.001f)
        assertEquals(matrix.skewX, result.matrix.skewX, 0.001f)
        assertEquals(matrix.skewY, result.matrix.skewY, 0.001f)
    }

    @Test
    fun `invoke adds to existing translation`() {
        // Given: Matrix with existing translation
        val matrix = TestFixtures.createAlignmentMatrix(
            translateX = 50f,
            translateY = 30f,
        )
        val overshoot = OvershootCorrection(
            overshotLeftX = 10f,
            overshotLeftY = 10f,
            overshotRightX = 10f,
            overshotRightY = 10f,
            currentScore = 10f, // High score triggers correction
        )

        // When
        val result = useCase(matrix, overshoot)

        // Then: New translation = old translation - overshoot
        val expectedTranslateX = 50f - 10f
        val expectedTranslateY = 30f - 10f

        assertEquals(expectedTranslateX, result.matrix.translateX, 1f)
        assertEquals(expectedTranslateY, result.matrix.translateY, 1f)
    }

    // ==================== No Correction Needed Tests ====================

    @Test
    fun `invoke returns unchanged matrix when no correction needed`() {
        // Given: Zero overshoot with low score and opposite directions (needsCorrection = false)
        val matrix = TestFixtures.createAlignmentMatrix(
            translateX = 50f,
            translateY = 30f,
        )
        val overshoot = OvershootCorrection(
            overshotLeftX = 0.1f, // Tiny positive
            overshotLeftY = 0.1f,
            overshotRightX = -0.1f, // Tiny negative (opposite direction)
            overshotRightY = -0.1f,
            currentScore = 0f, // Low score, no same-direction overshoot
        )

        // When
        val result = useCase(matrix, overshoot)

        // Then: Matrix should be unchanged
        assertEquals(matrix.translateX, result.matrix.translateX, 0.001f)
        assertEquals(matrix.translateY, result.matrix.translateY, 0.001f)
        assertFalse(result.correctionApplied)
    }

    // ==================== Correction Applied Tests ====================

    @Test
    fun `correctionApplied is false when overshoot is small and opposite directions`() {
        // Given: Very small overshoot with opposite directions
        val matrix = TestFixtures.createIdentityMatrix()
        val overshoot = OvershootCorrection(
            overshotLeftX = 0.01f,
            overshotLeftY = 0.01f,
            overshotRightX = -0.01f, // Opposite direction
            overshotRightY = -0.01f,
            currentScore = 0f, // Below threshold
        )

        // When
        val result = useCase(matrix, overshoot)

        // Then
        assertFalse(result.correctionApplied, "Should not apply correction for small opposite-direction overshoots")
    }

    @Test
    fun `correctionApplied is true when overshoot is significant`() {
        // Given: Significant overshoot
        val matrix = TestFixtures.createIdentityMatrix()
        val overshoot = OvershootCorrection(
            overshotLeftX = 10f,
            overshotLeftY = 10f,
            overshotRightX = 10f,
            overshotRightY = 10f,
            currentScore = 10f, // High score triggers correction
        )

        // When
        val result = useCase(matrix, overshoot)

        // Then
        assertTrue(result.correctionApplied, "Should apply correction for significant overshoots")
    }
}
