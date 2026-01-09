package com.po4yka.framelapse.domain.usecase

import com.po4yka.framelapse.domain.usecase.face.CalculateStabilizationScoreUseCase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalculateStabilizationScoreUseCaseTest {

    private lateinit var useCase: CalculateStabilizationScoreUseCase

    @BeforeTest
    fun setup() {
        useCase = CalculateStabilizationScoreUseCase()
    }

    // ==================== Perfect Alignment Tests ====================

    @Test
    fun `invoke returns zero score when eyes match goal exactly`() {
        // Given: Eyes are at exact goal positions
        val result = useCase(
            detectedLeftEyeX = 200f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 312f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            canvasHeight = 512,
        )

        // Then: Score should be 0
        assertEquals(0f, result.value, 0.001f, "Perfect alignment should have score 0")
        assertEquals(0f, result.leftEyeDistance, 0.001f)
        assertEquals(0f, result.rightEyeDistance, 0.001f)
        assertFalse(result.needsCorrection, "Perfect alignment needs no correction")
        assertTrue(result.isSuccess, "Perfect alignment is success")
    }

    // ==================== Score Calculation Tests ====================

    @Test
    fun `invoke calculates correct score for small offset`() {
        // Given: Eyes offset by 5.12 pixels (1% of 512 canvas height)
        val offset = 5.12f
        val result = useCase(
            detectedLeftEyeX = 200f + offset,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 312f + offset,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            canvasHeight = 512,
        )

        // Then: Score should be approximately 10 (5.12 * 1000 / 512)
        val expectedScore = (offset * 1000f) / 512f
        assertEquals(expectedScore, result.value, 0.1f)
    }

    @Test
    fun `invoke calculates correct score for large offset`() {
        // Given: Eyes offset by 51.2 pixels (10% of 512 canvas height)
        val result = useCase(
            detectedLeftEyeX = 251.2f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 363.2f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            canvasHeight = 512,
        )

        // Then: Score should be approximately 100
        assertEquals(100f, result.value, 1f)
        assertTrue(result.needsCorrection)
        assertFalse(result.isSuccess, "Large offset should not be success")
    }

    // ==================== Success Threshold Tests ====================

    @Test
    fun `isSuccess returns true when score below threshold`() {
        // Given: Score approximately 10 (well below 20.0 threshold)
        val result = useCase(
            detectedLeftEyeX = 205f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 317f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            canvasHeight = 512,
        )

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `isSuccess returns false when score above threshold`() {
        // Given: Large offset resulting in score above 20
        val result = useCase(
            detectedLeftEyeX = 250f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 362f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            canvasHeight = 512,
        )

        // Then
        assertFalse(result.isSuccess)
    }

    // ==================== Needs Correction Tests ====================

    @Test
    fun `needsCorrection returns false when score very low`() {
        // Given: Score below 0.5 (noActionScoreThreshold)
        val result = useCase(
            detectedLeftEyeX = 200.1f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 312.1f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            canvasHeight = 512,
        )

        // Then: Very small offset needs no correction
        assertFalse(result.needsCorrection)
    }

    @Test
    fun `needsCorrection returns true when score above threshold`() {
        // Given: Score above 0.5
        val result = useCase(
            detectedLeftEyeX = 210f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 322f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            canvasHeight = 512,
        )

        // Then
        assertTrue(result.needsCorrection)
    }

    // ==================== Eye Distance Tests ====================

    @Test
    fun `leftEyeDistance and rightEyeDistance are calculated correctly`() {
        // Given: Left eye at (210, 260), goal (200, 256)
        //        Right eye at (322, 266), goal (312, 256)
        val result = useCase(
            detectedLeftEyeX = 210f,
            detectedLeftEyeY = 260f,
            detectedRightEyeX = 322f,
            detectedRightEyeY = 266f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            canvasHeight = 512,
        )

        // Then: Left eye distance = sqrt(10^2 + 4^2) = sqrt(116) ≈ 10.77
        //       Right eye distance = sqrt(10^2 + 10^2) = sqrt(200) ≈ 14.14
        val expectedLeftDistance = kotlin.math.sqrt(116f)
        val expectedRightDistance = kotlin.math.sqrt(200f)

        assertEquals(expectedLeftDistance, result.leftEyeDistance, 0.1f)
        assertEquals(expectedRightDistance, result.rightEyeDistance, 0.1f)
    }

    // ==================== Canvas Height Normalization Tests ====================

    @Test
    fun `score scales with canvas height`() {
        // Given: Same offset but different canvas sizes
        val result512 = useCase(
            detectedLeftEyeX = 210f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 322f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            canvasHeight = 512,
        )

        val result1024 = useCase(
            detectedLeftEyeX = 210f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 322f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            canvasHeight = 1024,
        )

        // Then: Score for 1024 canvas should be half of 512 canvas
        assertEquals(result512.value, result1024.value * 2f, 0.1f)
    }
}
