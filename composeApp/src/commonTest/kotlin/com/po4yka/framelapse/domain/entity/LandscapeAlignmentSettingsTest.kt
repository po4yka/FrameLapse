package com.po4yka.framelapse.domain.entity

import com.po4yka.framelapse.testutil.TestFixtures
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LandscapeAlignmentSettingsTest {

    @BeforeTest
    fun setup() {
        TestFixtures.resetCounters()
    }

    // ==================== Constructor Tests ====================

    @Test
    fun `default constructor succeeds`() {
        // When
        val settings = LandscapeAlignmentSettings()

        // Then
        assertNotNull(settings)
        assertEquals(FeatureDetectorType.ORB, settings.detectorType)
        assertEquals(500, settings.maxKeypoints)
        assertEquals(10, settings.minMatchedKeypoints)
        assertEquals(0.75f, settings.ratioTestThreshold)
        assertEquals(5.0f, settings.ransacReprojThreshold)
        assertEquals(1080, settings.outputSize)
        assertEquals(0.5f, settings.minConfidence)
        assertTrue(settings.useCrossCheck)
        assertEquals(0.3f, settings.minInlierRatio)
    }

    // ==================== Preset Tests ====================

    @Test
    fun `FAST preset is valid`() {
        // When
        val settings = LandscapeAlignmentSettings.FAST

        // Then
        assertNotNull(settings)
        assertEquals(FeatureDetectorType.ORB, settings.detectorType)
        assertEquals(200, settings.maxKeypoints)
        assertEquals(8, settings.minMatchedKeypoints)
        assertEquals(0.8f, settings.ratioTestThreshold)
        assertFalse(settings.useCrossCheck)
    }

    @Test
    fun `HIGH_QUALITY preset is valid`() {
        // When
        val settings = LandscapeAlignmentSettings.HIGH_QUALITY

        // Then
        assertNotNull(settings)
        assertEquals(FeatureDetectorType.AKAZE, settings.detectorType)
        assertEquals(1000, settings.maxKeypoints)
        assertEquals(20, settings.minMatchedKeypoints)
        assertEquals(0.7f, settings.ratioTestThreshold)
        assertTrue(settings.useCrossCheck)
        assertEquals(0.5f, settings.minInlierRatio)
    }

    // ==================== Validation Tests - maxKeypoints ====================

    @Test
    fun `maxKeypoints below 10 throws`() {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            LandscapeAlignmentSettings(maxKeypoints = 9)
        }
    }

    @Test
    fun `maxKeypoints above 5000 throws`() {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            LandscapeAlignmentSettings(maxKeypoints = 5001)
        }
    }

    @Test
    fun `maxKeypoints at boundary 10 succeeds`() {
        // When
        val settings = LandscapeAlignmentSettings(maxKeypoints = 10)

        // Then
        assertEquals(10, settings.maxKeypoints)
    }

    @Test
    fun `maxKeypoints at boundary 5000 succeeds`() {
        // When
        val settings = LandscapeAlignmentSettings(maxKeypoints = 5000)

        // Then
        assertEquals(5000, settings.maxKeypoints)
    }

    // ==================== Validation Tests - minMatchedKeypoints ====================

    @Test
    fun `minMatchedKeypoints below 4 throws`() {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            LandscapeAlignmentSettings(minMatchedKeypoints = 3)
        }
    }

    @Test
    fun `minMatchedKeypoints at boundary 4 succeeds`() {
        // When
        val settings = LandscapeAlignmentSettings(minMatchedKeypoints = 4)

        // Then
        assertEquals(4, settings.minMatchedKeypoints)
    }

    // ==================== Validation Tests - ratioTestThreshold ====================

    @Test
    fun `ratioTestThreshold below 0_5 throws`() {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            LandscapeAlignmentSettings(ratioTestThreshold = 0.49f)
        }
    }

    @Test
    fun `ratioTestThreshold above 0_95 throws`() {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            LandscapeAlignmentSettings(ratioTestThreshold = 0.96f)
        }
    }

    @Test
    fun `ratioTestThreshold at boundary 0_5 succeeds`() {
        // When
        val settings = LandscapeAlignmentSettings(ratioTestThreshold = 0.5f)

        // Then
        assertEquals(0.5f, settings.ratioTestThreshold)
    }

    @Test
    fun `ratioTestThreshold at boundary 0_95 succeeds`() {
        // When
        val settings = LandscapeAlignmentSettings(ratioTestThreshold = 0.95f)

        // Then
        assertEquals(0.95f, settings.ratioTestThreshold)
    }

    // ==================== Validation Tests - ransacReprojThreshold ====================

    @Test
    fun `ransacReprojThreshold at zero throws`() {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            LandscapeAlignmentSettings(ransacReprojThreshold = 0f)
        }
    }

    @Test
    fun `ransacReprojThreshold negative throws`() {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            LandscapeAlignmentSettings(ransacReprojThreshold = -1f)
        }
    }

    // ==================== Validation Tests - outputSize ====================

    @Test
    fun `outputSize below 128 throws`() {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            LandscapeAlignmentSettings(outputSize = 127)
        }
    }

    @Test
    fun `outputSize above 4096 throws`() {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            LandscapeAlignmentSettings(outputSize = 4097)
        }
    }

    @Test
    fun `outputSize at boundary 128 succeeds`() {
        // When
        val settings = LandscapeAlignmentSettings(outputSize = 128)

        // Then
        assertEquals(128, settings.outputSize)
    }

    @Test
    fun `outputSize at boundary 4096 succeeds`() {
        // When
        val settings = LandscapeAlignmentSettings(outputSize = 4096)

        // Then
        assertEquals(4096, settings.outputSize)
    }

    // ==================== Validation Tests - minConfidence ====================

    @Test
    fun `minConfidence below 0 throws`() {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            LandscapeAlignmentSettings(minConfidence = -0.1f)
        }
    }

    @Test
    fun `minConfidence above 1 throws`() {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            LandscapeAlignmentSettings(minConfidence = 1.1f)
        }
    }

    // ==================== Validation Tests - minInlierRatio ====================

    @Test
    fun `minInlierRatio below 0 throws`() {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            LandscapeAlignmentSettings(minInlierRatio = -0.1f)
        }
    }

    @Test
    fun `minInlierRatio above 1 throws`() {
        // When/Then
        assertFailsWith<IllegalArgumentException> {
            LandscapeAlignmentSettings(minInlierRatio = 1.1f)
        }
    }
}
