package com.po4yka.framelapse.domain.entity

import com.po4yka.framelapse.testutil.TestFixtures
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureKeypointTest {

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

    // ==================== toPixelCoordinates Tests ====================

    @Test
    fun `toPixelCoordinates converts normalized to pixels correctly`() {
        // Given: Keypoint at normalized position (0.5, 0.5)
        val keypoint = TestFixtures.createFeatureKeypoint(x = 0.5f, y = 0.5f)
        val imageWidth = 1920
        val imageHeight = 1080

        // When
        val (pixelX, pixelY) = keypoint.toPixelCoordinates(imageWidth, imageHeight)

        // Then
        assertFloatEquals(960f, pixelX, "X should be at center (960)")
        assertFloatEquals(540f, pixelY, "Y should be at center (540)")
    }

    @Test
    fun `toPixelCoordinates handles corner coordinates`() {
        // Given: Keypoints at corners
        val topLeft = TestFixtures.createFeatureKeypoint(x = 0f, y = 0f)
        val bottomRight = TestFixtures.createFeatureKeypoint(x = 1f, y = 1f)
        val imageWidth = 1000
        val imageHeight = 800

        // When
        val (tlX, tlY) = topLeft.toPixelCoordinates(imageWidth, imageHeight)
        val (brX, brY) = bottomRight.toPixelCoordinates(imageWidth, imageHeight)

        // Then
        assertFloatEquals(0f, tlX, "Top-left X should be 0")
        assertFloatEquals(0f, tlY, "Top-left Y should be 0")
        assertFloatEquals(1000f, brX, "Bottom-right X should be image width")
        assertFloatEquals(800f, brY, "Bottom-right Y should be image height")
    }

    @Test
    fun `toPixelCoordinates with large image dimensions`() {
        // Given: Keypoint at (0.25, 0.75) in a 4K image
        val keypoint = TestFixtures.createFeatureKeypoint(x = 0.25f, y = 0.75f)
        val imageWidth = 3840
        val imageHeight = 2160

        // When
        val (pixelX, pixelY) = keypoint.toPixelCoordinates(imageWidth, imageHeight)

        // Then
        assertFloatEquals(960f, pixelX, "X should be 960 (3840 * 0.25)")
        assertFloatEquals(1620f, pixelY, "Y should be 1620 (2160 * 0.75)")
    }

    // ==================== fromPixelCoordinates Tests ====================

    @Test
    fun `fromPixelCoordinates converts pixels to normalized`() {
        // Given
        val pixelX = 500f
        val pixelY = 400f
        val imageWidth = 1000
        val imageHeight = 800

        // When
        val keypoint = FeatureKeypoint.fromPixelCoordinates(
            x = pixelX,
            y = pixelY,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
        )

        // Then
        assertFloatEquals(0.5f, keypoint.position.x, "Normalized X should be 0.5")
        assertFloatEquals(0.5f, keypoint.position.y, "Normalized Y should be 0.5")
    }

    @Test
    fun `fromPixelCoordinates with custom parameters`() {
        // Given
        val pixelX = 100f
        val pixelY = 200f
        val imageWidth = 500
        val imageHeight = 400
        val response = 150f
        val size = 20f
        val angle = 45f
        val octave = 2

        // When
        val keypoint = FeatureKeypoint.fromPixelCoordinates(
            x = pixelX,
            y = pixelY,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            response = response,
            size = size,
            angle = angle,
            octave = octave,
        )

        // Then
        assertFloatEquals(0.2f, keypoint.position.x, "Normalized X should be 0.2")
        assertFloatEquals(0.5f, keypoint.position.y, "Normalized Y should be 0.5")
        assertEquals(response, keypoint.response)
        assertEquals(size, keypoint.size)
        assertEquals(angle, keypoint.angle)
        assertEquals(octave, keypoint.octave)
    }

    @Test
    fun `fromPixelCoordinates with zero dimensions`() {
        // Given: Zero dimensions (edge case)
        val pixelX = 100f
        val pixelY = 200f

        // When: Using zero for width/height (should result in Infinity)
        // Note: Division by zero results in Float.POSITIVE_INFINITY
        val keypointZeroWidth = FeatureKeypoint.fromPixelCoordinates(
            x = pixelX,
            y = pixelY,
            imageWidth = 0,
            imageHeight = 100,
        )
        val keypointZeroHeight = FeatureKeypoint.fromPixelCoordinates(
            x = pixelX,
            y = pixelY,
            imageWidth = 100,
            imageHeight = 0,
        )

        // Then: Position will be Infinity for the zero dimension
        assertEquals(Float.POSITIVE_INFINITY, keypointZeroWidth.position.x)
        assertEquals(Float.POSITIVE_INFINITY, keypointZeroHeight.position.y)
    }

    // ==================== Round Trip Conversion Tests ====================

    @Test
    fun `round trip conversion maintains accuracy`() {
        // Given: Original keypoint with known pixel position
        val originalPixelX = 750f
        val originalPixelY = 300f
        val imageWidth = 1500
        val imageHeight = 600

        // When: Convert to normalized, then back to pixels
        val keypoint = FeatureKeypoint.fromPixelCoordinates(
            x = originalPixelX,
            y = originalPixelY,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            response = 100f,
            size = 15f,
            angle = 90f,
            octave = 1,
        )
        val (roundTripX, roundTripY) = keypoint.toPixelCoordinates(imageWidth, imageHeight)

        // Then: Should match original values
        assertFloatEquals(originalPixelX, roundTripX, "Round trip X should match original")
        assertFloatEquals(originalPixelY, roundTripY, "Round trip Y should match original")
    }

    // ==================== Position Normalization Tests ====================

    @Test
    fun `position normalization is within bounds`() {
        // Given: Various keypoints created via pixel conversion
        val testCases = listOf(
            Pair(0f, 0f),
            Pair(500f, 400f),
            Pair(1000f, 800f),
        )
        val imageWidth = 1000
        val imageHeight = 800

        for ((pixelX, pixelY) in testCases) {
            // When
            val keypoint = FeatureKeypoint.fromPixelCoordinates(
                x = pixelX,
                y = pixelY,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
            )

            // Then: Position should be in [0, 1] range
            assertTrue(
                keypoint.position.x in 0f..1f,
                "Normalized X should be in [0, 1] range, got ${keypoint.position.x}",
            )
            assertTrue(
                keypoint.position.y in 0f..1f,
                "Normalized Y should be in [0, 1] range, got ${keypoint.position.y}",
            )
        }
    }
}
