package com.po4yka.framelapse.domain.entity

import com.po4yka.framelapse.testutil.TestFixtures
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LandscapeLandmarksTest {

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

    // ==================== getReferencePointLeft Tests ====================

    @Test
    fun `getReferencePointLeft returns centroid of left keypoints`() {
        // Given: Landmarks with keypoints mostly on the left (x < 0.5)
        val leftKeypoints = TestFixtures.createFeatureKeypointList(count = 20, region = "left")
        val landmarks = LandscapeLandmarks(
            keypoints = leftKeypoints,
            detectorType = FeatureDetectorType.ORB,
            keypointCount = leftKeypoints.size,
            boundingBox = BoundingBox(0f, 0f, 0.5f, 1f),
            qualityScore = 0.8f,
        )

        // When
        val leftRef = landmarks.getReferencePointLeft()

        // Then: Centroid should be in left half
        assertTrue(leftRef.x < 0.5f, "Left reference X should be < 0.5, was ${leftRef.x}")
    }

    @Test
    fun `getReferencePointRight returns centroid of right keypoints`() {
        // Given: Landmarks with keypoints mostly on the right (x >= 0.5)
        val rightKeypoints = TestFixtures.createFeatureKeypointList(count = 20, region = "right")
        val landmarks = LandscapeLandmarks(
            keypoints = rightKeypoints,
            detectorType = FeatureDetectorType.ORB,
            keypointCount = rightKeypoints.size,
            boundingBox = BoundingBox(0.5f, 0f, 1f, 1f),
            qualityScore = 0.8f,
        )

        // When
        val rightRef = landmarks.getReferencePointRight()

        // Then: Centroid should be in right half
        assertTrue(rightRef.x >= 0.5f, "Right reference X should be >= 0.5, was ${rightRef.x}")
    }

    @Test
    fun `getReferencePointLeft fallback when no left keypoints`() {
        // Given: Landmarks with all keypoints on the right side
        val rightOnlyKeypoints = (0 until 20).map { i ->
            TestFixtures.createFeatureKeypoint(
                x = 0.6f + (i * 0.02f),
                y = 0.5f,
            )
        }
        val landmarks = LandscapeLandmarks(
            keypoints = rightOnlyKeypoints,
            detectorType = FeatureDetectorType.ORB,
            keypointCount = rightOnlyKeypoints.size,
            boundingBox = BoundingBox(0.6f, 0f, 1f, 1f),
            qualityScore = 0.8f,
        )

        // When: No keypoints have x < 0.5, should return fallback
        val leftRef = landmarks.getReferencePointLeft()

        // Then: Should return fallback value (0.25, 0.5)
        assertFloatEquals(0.25f, leftRef.x, "Fallback left X should be 0.25")
        assertFloatEquals(0.5f, leftRef.y, "Fallback left Y should be 0.5")
    }

    @Test
    fun `getReferencePointRight fallback when no right keypoints`() {
        // Given: Landmarks with all keypoints on the left side
        val leftOnlyKeypoints = (0 until 20).map { i ->
            TestFixtures.createFeatureKeypoint(
                x = 0.1f + (i * 0.015f),
                y = 0.5f,
            )
        }
        val landmarks = LandscapeLandmarks(
            keypoints = leftOnlyKeypoints,
            detectorType = FeatureDetectorType.ORB,
            keypointCount = leftOnlyKeypoints.size,
            boundingBox = BoundingBox(0f, 0f, 0.4f, 1f),
            qualityScore = 0.8f,
        )

        // When: No keypoints have x >= 0.5, should return fallback
        val rightRef = landmarks.getReferencePointRight()

        // Then: Should return fallback value (0.75, 0.5)
        assertFloatEquals(0.75f, rightRef.x, "Fallback right X should be 0.75")
        assertFloatEquals(0.5f, rightRef.y, "Fallback right Y should be 0.5")
    }

    @Test
    fun `single keypoint reference point calculation`() {
        // Given: Single keypoint on left side
        val singleKeypoint = listOf(TestFixtures.createFeatureKeypoint(x = 0.2f, y = 0.3f))
        val landmarks = LandscapeLandmarks(
            keypoints = singleKeypoint,
            detectorType = FeatureDetectorType.ORB,
            keypointCount = 1,
            boundingBox = BoundingBox(0.2f, 0.3f, 0.2f, 0.3f),
            qualityScore = 0.5f,
        )

        // When
        val leftRef = landmarks.getReferencePointLeft()

        // Then: Centroid of single point is the point itself
        assertFloatEquals(0.2f, leftRef.x, "Single keypoint left X")
        assertFloatEquals(0.3f, leftRef.y, "Single keypoint left Y")
    }

    // ==================== getTopKeypoints Tests ====================

    @Test
    fun `getTopKeypoints returns highest response keypoints`() {
        // Given: Keypoints with varying response values
        val keypoints = listOf(
            TestFixtures.createFeatureKeypoint(x = 0.1f, response = 50f),
            TestFixtures.createFeatureKeypoint(x = 0.2f, response = 200f),
            TestFixtures.createFeatureKeypoint(x = 0.3f, response = 100f),
            TestFixtures.createFeatureKeypoint(x = 0.4f, response = 150f),
            TestFixtures.createFeatureKeypoint(x = 0.5f, response = 75f),
        )
        val landmarks = LandscapeLandmarks(
            keypoints = keypoints,
            detectorType = FeatureDetectorType.ORB,
            keypointCount = keypoints.size,
            boundingBox = BoundingBox(0f, 0f, 1f, 1f),
            qualityScore = 0.8f,
        )

        // When
        val top3 = landmarks.getTopKeypoints(3)

        // Then: Should be sorted by response descending
        assertEquals(3, top3.size)
        assertEquals(200f, top3[0].response) // x = 0.2
        assertEquals(150f, top3[1].response) // x = 0.4
        assertEquals(100f, top3[2].response) // x = 0.3
    }

    @Test
    fun `getTopKeypoints with n greater than total`() {
        // Given: Landmarks with 5 keypoints
        val keypoints = TestFixtures.createFeatureKeypointList(count = 5)
        val landmarks = LandscapeLandmarks(
            keypoints = keypoints,
            detectorType = FeatureDetectorType.ORB,
            keypointCount = keypoints.size,
            boundingBox = BoundingBox(0f, 0f, 1f, 1f),
            qualityScore = 0.8f,
        )

        // When: Request more than available
        val top10 = landmarks.getTopKeypoints(10)

        // Then: Should return all available
        assertEquals(5, top10.size)
    }

    @Test
    fun `getTopKeypoints with zero returns empty`() {
        // Given
        val landmarks = TestFixtures.createLandscapeLandmarks(keypointCount = 20)

        // When
        val top0 = landmarks.getTopKeypoints(0)

        // Then
        assertTrue(top0.isEmpty())
    }

    // ==================== getKeypointsInRegion Tests ====================

    @Test
    fun `getKeypointsInRegion filters correctly`() {
        // Given: Keypoints spread across image
        val keypoints = listOf(
            TestFixtures.createFeatureKeypoint(x = 0.1f, y = 0.1f),
            TestFixtures.createFeatureKeypoint(x = 0.3f, y = 0.3f),
            TestFixtures.createFeatureKeypoint(x = 0.5f, y = 0.5f),
            TestFixtures.createFeatureKeypoint(x = 0.7f, y = 0.7f),
            TestFixtures.createFeatureKeypoint(x = 0.9f, y = 0.9f),
        )
        val landmarks = LandscapeLandmarks(
            keypoints = keypoints,
            detectorType = FeatureDetectorType.ORB,
            keypointCount = keypoints.size,
            boundingBox = BoundingBox(0f, 0f, 1f, 1f),
            qualityScore = 0.8f,
        )

        // When: Get keypoints in center region
        val centerKeypoints = landmarks.getKeypointsInRegion(
            left = 0.25f,
            top = 0.25f,
            right = 0.75f,
            bottom = 0.75f,
        )

        // Then: Should include (0.3, 0.3), (0.5, 0.5), (0.7, 0.7)
        assertEquals(3, centerKeypoints.size)
    }

    @Test
    fun `getKeypointsInRegion returns empty for no matches`() {
        // Given: Keypoints only in top-left corner
        val keypoints = listOf(
            TestFixtures.createFeatureKeypoint(x = 0.1f, y = 0.1f),
            TestFixtures.createFeatureKeypoint(x = 0.2f, y = 0.2f),
        )
        val landmarks = LandscapeLandmarks(
            keypoints = keypoints,
            detectorType = FeatureDetectorType.ORB,
            keypointCount = keypoints.size,
            boundingBox = BoundingBox(0f, 0f, 0.3f, 0.3f),
            qualityScore = 0.8f,
        )

        // When: Get keypoints in bottom-right region
        val result = landmarks.getKeypointsInRegion(
            left = 0.7f,
            top = 0.7f,
            right = 1f,
            bottom = 1f,
        )

        // Then
        assertTrue(result.isEmpty())
    }

    // ==================== hasEnoughKeypoints Tests ====================

    @Test
    fun `hasEnoughKeypoints true with 10+ keypoints`() {
        // Given
        val landmarks = TestFixtures.createLandscapeLandmarks(keypointCount = 15)

        // Then
        assertTrue(landmarks.hasEnoughKeypoints())
    }

    @Test
    fun `hasEnoughKeypoints false with fewer than 10`() {
        // Given: Landmarks with only 5 keypoints
        val keypoints = TestFixtures.createFeatureKeypointList(count = 5)
        val landmarks = LandscapeLandmarks(
            keypoints = keypoints,
            detectorType = FeatureDetectorType.ORB,
            keypointCount = keypoints.size,
            boundingBox = BoundingBox(0f, 0f, 1f, 1f),
            qualityScore = 0.5f,
        )

        // Then
        assertFalse(landmarks.hasEnoughKeypoints())
    }

    @Test
    fun `hasEnoughKeypoints true with exactly 10 keypoints`() {
        // Given
        val landmarks = TestFixtures.createLandscapeLandmarks(keypointCount = 10)

        // Then
        assertTrue(landmarks.hasEnoughKeypoints())
    }
}
