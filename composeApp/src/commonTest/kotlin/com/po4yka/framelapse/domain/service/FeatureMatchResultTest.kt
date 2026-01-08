package com.po4yka.framelapse.domain.service

import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.testutil.TestFixtures
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureMatchResultTest {

    @BeforeTest
    fun setup() {
        TestFixtures.resetCounters()
    }

    private val epsilon = 0.001f

    private fun assertFloatEquals(expected: Float, actual: Float, message: String? = null) {
        assertTrue(
            abs(expected - actual) < epsilon,
            message ?: "Expected $expected but was $actual",
        )
    }

    private fun createFeatureMatchResult(
        matchCount: Int = 20,
        inlierCount: Int = 15,
        confidence: Float = 0.85f,
        homography: HomographyMatrix = HomographyMatrix.IDENTITY,
    ): FeatureMatchResult {
        val landmarks = TestFixtures.createLandscapeLandmarks()
        return FeatureMatchResult(
            homography = homography,
            sourceLandmarks = landmarks,
            referenceLandmarks = landmarks,
            matchCount = matchCount,
            inlierCount = inlierCount,
            confidence = confidence,
        )
    }

    // ==================== inlierRatio Tests ====================

    @Test
    fun `inlierRatio calculates correctly`() {
        // Given: 15 inliers out of 20 matches
        val result = createFeatureMatchResult(matchCount = 20, inlierCount = 15)

        // When/Then
        assertFloatEquals(0.75f, result.inlierRatio, "Inlier ratio should be 15/20 = 0.75")
    }

    @Test
    fun `inlierRatio is zero when matchCount is zero`() {
        // Given: No matches
        val result = createFeatureMatchResult(matchCount = 0, inlierCount = 0)

        // When/Then
        assertEquals(0f, result.inlierRatio, "Inlier ratio should be 0 when matchCount is 0")
    }

    @Test
    fun `inlierRatio is 1_0 when all matches are inliers`() {
        // Given: All matches are inliers
        val result = createFeatureMatchResult(matchCount = 25, inlierCount = 25)

        // When/Then
        assertFloatEquals(1.0f, result.inlierRatio, "Inlier ratio should be 1.0 when all matches are inliers")
    }

    // ==================== isValid Tests ====================

    @Test
    fun `isValid returns true for valid result`() {
        // Given: Good match count and inlier ratio
        val result = createFeatureMatchResult(
            matchCount = 20,
            inlierCount = 15,
            homography = HomographyMatrix.IDENTITY,
        )

        // When/Then
        assertTrue(result.isValid(), "Should be valid with good matches and inlier ratio")
    }

    @Test
    fun `isValid returns false for insufficient matches`() {
        // Given: Less than 10 matches (default minMatches)
        val result = createFeatureMatchResult(
            matchCount = 5,
            inlierCount = 4,
        )

        // When/Then
        assertFalse(result.isValid(), "Should be invalid with fewer than 10 matches")
    }

    @Test
    fun `isValid returns false for low inlier ratio`() {
        // Given: Low inlier ratio (below 0.3 default)
        val result = createFeatureMatchResult(
            matchCount = 20,
            inlierCount = 4, // 4/20 = 0.2 < 0.3
        )

        // When/Then
        assertFalse(result.isValid(), "Should be invalid with inlier ratio < 0.3")
    }

    @Test
    fun `isValid with custom thresholds`() {
        // Given: Result that passes custom thresholds
        val result = createFeatureMatchResult(
            matchCount = 8,
            inlierCount = 4, // 4/8 = 0.5
        )

        // When: Using custom thresholds
        val validWithCustomThresholds = result.isValid(
            minInlierRatio = 0.4f,
            minMatches = 5,
        )

        // Then
        assertTrue(validWithCustomThresholds, "Should be valid with custom thresholds")
    }

    @Test
    fun `isValid returns false for singular homography`() {
        // Given: Singular (invalid) homography matrix
        val singularMatrix = HomographyMatrix(
            h11 = 0f, h12 = 0f, h13 = 0f,
            h21 = 0f, h22 = 0f, h23 = 0f,
            h31 = 0f, h32 = 0f, h33 = 0f,
        )
        val result = createFeatureMatchResult(
            matchCount = 20,
            inlierCount = 15,
            homography = singularMatrix,
        )

        // When/Then
        assertFalse(result.isValid(), "Should be invalid with singular homography")
    }
}
