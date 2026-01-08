package com.po4yka.framelapse.domain.usecase

import com.po4yka.framelapse.domain.usecase.face.DetectOvershootUseCase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetectOvershootUseCaseTest {

    private lateinit var useCase: DetectOvershootUseCase

    @BeforeTest
    fun setup() {
        useCase = DetectOvershootUseCase()
    }

    // ==================== Overshoot Detection Tests ====================

    @Test
    fun `invoke detects positive X overshoot when both eyes past goal`() {
        // Given: Both eyes have overshot to the right of goal
        val result = useCase(
            detectedLeftEyeX = 210f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 322f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            currentScore = 20f,
        )

        // Then: Should detect positive overshoot
        assertEquals(10f, result.leftEyeOvershootX, 0.1f)
        assertEquals(10f, result.rightEyeOvershootX, 0.1f)
        assertTrue(result.bothEyesOvershotSameDirectionX)
    }

    @Test
    fun `invoke detects negative X overshoot when both eyes before goal`() {
        // Given: Both eyes have not reached goal (negative overshoot = undershoot)
        val result = useCase(
            detectedLeftEyeX = 190f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 302f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            currentScore = 20f,
        )

        // Then: Both eyes undershot (negative overshoot)
        assertEquals(-10f, result.leftEyeOvershootX, 0.1f)
        assertEquals(-10f, result.rightEyeOvershootX, 0.1f)
        assertTrue(result.bothEyesOvershotSameDirectionX)
    }

    @Test
    fun `invoke detects opposite direction overshoot`() {
        // Given: One eye overshot, one undershot
        val result = useCase(
            detectedLeftEyeX = 210f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 302f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            currentScore = 20f,
        )

        // Then: Opposite direction overshoot
        assertEquals(10f, result.leftEyeOvershootX, 0.1f)
        assertEquals(-10f, result.rightEyeOvershootX, 0.1f)
        assertFalse(result.bothEyesOvershotSameDirectionX)
    }

    // ==================== Y Direction Overshoot Tests ====================

    @Test
    fun `invoke detects positive Y overshoot when both eyes below goal`() {
        // Given: Both eyes below goal (positive Y direction)
        val result = useCase(
            detectedLeftEyeX = 200f,
            detectedLeftEyeY = 266f,
            detectedRightEyeX = 312f,
            detectedRightEyeY = 266f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            currentScore = 20f,
        )

        // Then
        assertEquals(10f, result.leftEyeOvershootY, 0.1f)
        assertEquals(10f, result.rightEyeOvershootY, 0.1f)
        assertTrue(result.bothEyesOvershotSameDirectionY)
    }

    // ==================== Needs Correction Tests ====================

    @Test
    fun `needsCorrection returns true when score above threshold`() {
        // Given: Score above noActionThreshold (0.5)
        val result = useCase(
            detectedLeftEyeX = 210f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 322f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            currentScore = 20f,
        )

        // Then
        assertTrue(result.needsCorrection)
    }

    @Test
    fun `needsCorrection returns true when both eyes overshoot same direction`() {
        // Given: Low score but both eyes overshoot in same direction
        val result = useCase(
            detectedLeftEyeX = 201f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 313f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            currentScore = 0.3f, // Below threshold
        )

        // Then: Still needs correction due to same-direction overshoot
        assertTrue(result.needsCorrection || result.bothEyesOvershotSameDirectionX)
    }

    @Test
    fun `needsCorrection returns false when score low and no same-direction overshoot`() {
        // Given: Low score and opposite direction overshoots (minimal correction needed)
        val result = useCase(
            detectedLeftEyeX = 200.1f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 311.9f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            currentScore = 0.1f,
        )

        // Then
        assertFalse(result.needsCorrection)
    }

    // ==================== Zero Overshoot Tests ====================

    @Test
    fun `invoke returns zero overshoot for perfect alignment`() {
        // Given: Perfect alignment
        val result = useCase(
            detectedLeftEyeX = 200f,
            detectedLeftEyeY = 256f,
            detectedRightEyeX = 312f,
            detectedRightEyeY = 256f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            currentScore = 0f,
        )

        // Then
        assertEquals(0f, result.leftEyeOvershootX, 0.001f)
        assertEquals(0f, result.leftEyeOvershootY, 0.001f)
        assertEquals(0f, result.rightEyeOvershootX, 0.001f)
        assertEquals(0f, result.rightEyeOvershootY, 0.001f)
        assertFalse(result.needsCorrection)
    }

    // ==================== Average Overshoot Tests ====================

    @Test
    fun `averageOvershoot calculates correct values`() {
        // Given: Different overshoots for left and right eyes
        val result = useCase(
            detectedLeftEyeX = 220f,
            detectedLeftEyeY = 260f,
            detectedRightEyeX = 322f,
            detectedRightEyeY = 266f,
            goalLeftEyeX = 200f,
            goalLeftEyeY = 256f,
            goalRightEyeX = 312f,
            goalRightEyeY = 256f,
            currentScore = 30f,
        )

        // Then: Average X = (20 + 10) / 2 = 15
        //       Average Y = (4 + 10) / 2 = 7
        val expectedAverageX = (20f + 10f) / 2
        val expectedAverageY = (4f + 10f) / 2

        assertEquals(expectedAverageX, result.averageOvershootX, 0.1f)
        assertEquals(expectedAverageY, result.averageOvershootY, 0.1f)
    }
}
