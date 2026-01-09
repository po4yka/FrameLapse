package com.po4yka.framelapse.domain.entity

import com.po4yka.framelapse.testutil.TestFixtures
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureDetectorTypeTest {

    @BeforeTest
    fun setup() {
        TestFixtures.resetCounters()
    }

    @Test
    fun `fromString returns ORB for ORB string`() {
        // When
        val result = FeatureDetectorType.fromString("ORB")

        // Then
        assertEquals(FeatureDetectorType.ORB, result)
    }

    @Test
    fun `fromString returns AKAZE for AKAZE string`() {
        // When
        val result = FeatureDetectorType.fromString("AKAZE")

        // Then
        assertEquals(FeatureDetectorType.AKAZE, result)
    }

    @Test
    fun `fromString returns ORB for invalid string`() {
        // When
        val result = FeatureDetectorType.fromString("INVALID")

        // Then
        assertEquals(FeatureDetectorType.ORB, result)
    }

    @Test
    fun `fromString returns ORB for empty string`() {
        // When
        val result = FeatureDetectorType.fromString("")

        // Then
        assertEquals(FeatureDetectorType.ORB, result)
    }

    @Test
    fun `enum properties have correct values`() {
        // ORB properties
        assertEquals("ORB", FeatureDetectorType.ORB.displayName)
        assertEquals("Fast feature detection, good for most scenes", FeatureDetectorType.ORB.description)

        // AKAZE properties
        assertEquals("AKAZE", FeatureDetectorType.AKAZE.displayName)
        assertEquals("More robust detection, better for complex scenes", FeatureDetectorType.AKAZE.description)
    }
}
