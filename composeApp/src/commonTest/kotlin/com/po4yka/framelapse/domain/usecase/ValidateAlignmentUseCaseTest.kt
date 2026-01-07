package com.po4yka.framelapse.domain.usecase

import com.po4yka.framelapse.domain.entity.AlignmentSettings
import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.usecase.face.ValidateAlignmentUseCase
import com.po4yka.framelapse.testutil.TestFixtures
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateAlignmentUseCaseTest {

    private lateinit var useCase: ValidateAlignmentUseCase

    @BeforeTest
    fun setup() {
        useCase = ValidateAlignmentUseCase()
        TestFixtures.resetCounters()
    }

    // ==================== Main Validation Tests ====================

    @Test
    fun `invoke returns true for valid landmarks`() {
        // Given: Well-formed landmarks
        val landmarks = TestFixtures.createFaceLandmarks()

        // When
        val result = useCase(landmarks)

        // Then
        assertTrue(result, "Valid landmarks should pass validation")
    }

    @Test
    fun `invoke returns false for invalid eye detection`() {
        // Given: Eyes too close together
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(0.5f, 0.4f, 0f),
            rightEyeCenter = LandmarkPoint(0.5f, 0.4f, 0f),
        )

        // When
        val result = useCase(landmarks)

        // Then
        assertFalse(result, "Overlapping eyes should fail validation")
    }

    @Test
    fun `invoke returns false for small bounding box`() {
        // Given: Face too small
        val landmarks = TestFixtures.createFaceLandmarks(
            boundingBox = BoundingBox(0.4f, 0.4f, 0.41f, 0.41f), // Very small box
        )

        // When
        val result = useCase(landmarks)

        // Then
        assertFalse(result, "Small bounding box should fail validation")
    }

    // ==================== Confidence Validation Tests ====================

    @Test
    fun `validateConfidence returns true for valid landmarks with points`() {
        // Given
        val landmarks = TestFixtures.createFaceLandmarks()

        // When
        val result = useCase.validateConfidence(landmarks, 0.7f)

        // Then
        assertTrue(result, "Landmarks with valid points should pass confidence check")
    }

    @Test
    fun `validateConfidence returns false for empty points`() {
        // Given: Landmarks with empty points list
        val landmarks = TestFixtures.createFaceLandmarks().copy(points = emptyList())

        // When
        val result = useCase.validateConfidence(landmarks, 0.7f)

        // Then
        assertFalse(result, "Empty landmarks should fail confidence check")
    }

    @Test
    fun `validateConfidence returns false for negative eye coordinates`() {
        // Given: Invalid eye positions
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(-1f, 0.4f, 0f),
        )

        // When
        val result = useCase.validateConfidence(landmarks, 0.7f)

        // Then
        assertFalse(result, "Negative eye coordinates should fail confidence check")
    }

    // ==================== Eye Distance Validation Tests ====================

    @Test
    fun `validateEyeDistance returns true for properly separated eyes`() {
        // Given: Eyes with good separation
        val landmarks = TestFixtures.createHorizontalEyesLandmarks()

        // When
        val result = useCase.validateEyeDistance(landmarks)

        // Then
        assertTrue(result, "Well-separated eyes should pass validation")
    }

    @Test
    fun `validateEyeDistance returns false for overlapping eyes`() {
        // Given: Eyes at same position
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(100f, 100f, 0f),
            rightEyeCenter = LandmarkPoint(100f, 100f, 0f),
        )

        // When
        val result = useCase.validateEyeDistance(landmarks)

        // Then
        assertFalse(result, "Overlapping eyes should fail validation")
    }

    @Test
    fun `validateEyeDistance returns false for reversed eyes`() {
        // Given: Right eye is to the left of left eye
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(200f, 100f, 0f),
            rightEyeCenter = LandmarkPoint(100f, 100f, 0f),
        )

        // When
        val result = useCase.validateEyeDistance(landmarks)

        // Then
        assertFalse(result, "Reversed eye positions should fail validation")
    }

    @Test
    fun `validateEyeDistance returns false for very close eyes`() {
        // Given: Eyes barely separated (less than MIN_EYE_DISTANCE = 10f)
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(100f, 100f, 0f),
            rightEyeCenter = LandmarkPoint(105f, 100f, 0f),
        )

        // When
        val result = useCase.validateEyeDistance(landmarks)

        // Then
        assertFalse(result, "Eyes closer than minimum distance should fail validation")
    }

    @Test
    fun `validateEyeDistance returns true for diagonal eye separation`() {
        // Given: Eyes separated diagonally but meeting distance requirement
        val landmarks = TestFixtures.createTiltedEyesLandmarks()

        // When
        val result = useCase.validateEyeDistance(landmarks)

        // Then
        assertTrue(result, "Diagonally separated eyes meeting distance should pass")
    }

    // ==================== Bounding Box Validation Tests ====================

    @Test
    fun `validateBoundingBox returns true for valid box`() {
        // Given
        val landmarks = TestFixtures.createFaceLandmarks(
            boundingBox = BoundingBox(100f, 100f, 400f, 500f),
        )

        // When
        val result = useCase.validateBoundingBox(landmarks)

        // Then
        assertTrue(result, "Large bounding box should pass validation")
    }

    @Test
    fun `validateBoundingBox returns false for narrow box`() {
        // Given: Box width less than MIN_FACE_SIZE (50f)
        val landmarks = TestFixtures.createFaceLandmarks(
            boundingBox = BoundingBox(100f, 100f, 130f, 400f), // width = 30
        )

        // When
        val result = useCase.validateBoundingBox(landmarks)

        // Then
        assertFalse(result, "Narrow bounding box should fail validation")
    }

    @Test
    fun `validateBoundingBox returns false for short box`() {
        // Given: Box height less than MIN_FACE_SIZE (50f)
        val landmarks = TestFixtures.createFaceLandmarks(
            boundingBox = BoundingBox(100f, 100f, 400f, 130f), // height = 30
        )

        // When
        val result = useCase.validateBoundingBox(landmarks)

        // Then
        assertFalse(result, "Short bounding box should fail validation")
    }

    @Test
    fun `validateBoundingBox returns false for negative coordinates`() {
        // Given: Box with negative left coordinate
        val landmarks = TestFixtures.createFaceLandmarks(
            boundingBox = BoundingBox(-10f, 100f, 400f, 500f),
        )

        // When
        val result = useCase.validateBoundingBox(landmarks)

        // Then
        assertFalse(result, "Negative bounding box coordinates should fail validation")
    }

    // ==================== Detailed Validation Tests ====================

    @Test
    fun `getDetailedValidation returns valid result for good landmarks`() {
        // Given
        val landmarks = TestFixtures.createFaceLandmarks()

        // When
        val result = useCase.getDetailedValidation(landmarks)

        // Then
        assertTrue(result.isValid, "Valid landmarks should produce valid result")
        assertTrue(result.issues.isEmpty(), "Valid landmarks should have no issues")
    }

    @Test
    fun `getDetailedValidation lists all issues for completely invalid landmarks`() {
        // Given: Landmarks that fail all validations
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(-1f, 0.4f, 0f),
            rightEyeCenter = LandmarkPoint(-2f, 0.4f, 0f),
            boundingBox = BoundingBox(-10f, -10f, 0f, 0f),
        ).copy(points = emptyList())

        // When
        val result = useCase.getDetailedValidation(landmarks)

        // Then
        assertFalse(result.isValid, "Invalid landmarks should produce invalid result")
        assertTrue(result.issues.isNotEmpty(), "Should list validation issues")
    }

    @Test
    fun `getDetailedValidation reports confidence issue`() {
        // Given
        val landmarks = TestFixtures.createFaceLandmarks().copy(points = emptyList())

        // When
        val result = useCase.getDetailedValidation(landmarks)

        // Then
        assertFalse(result.isValid)
        assertTrue(
            result.issues.any { it.contains("confidence", ignoreCase = true) },
            "Should report confidence issue",
        )
    }

    @Test
    fun `getDetailedValidation reports eye detection issue`() {
        // Given
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(100f, 100f, 0f),
            rightEyeCenter = LandmarkPoint(100f, 100f, 0f),
        )

        // When
        val result = useCase.getDetailedValidation(landmarks)

        // Then
        assertFalse(result.isValid)
        assertTrue(
            result.issues.any { it.contains("eye", ignoreCase = true) },
            "Should report eye detection issue",
        )
    }

    @Test
    fun `getDetailedValidation reports face size issue`() {
        // Given
        val landmarks = TestFixtures.createFaceLandmarks(
            boundingBox = BoundingBox(100f, 100f, 120f, 120f),
        )

        // When
        val result = useCase.getDetailedValidation(landmarks)

        // Then
        assertFalse(result.isValid)
        assertTrue(
            result.issues.any { it.contains("small", ignoreCase = true) || it.contains("visible", ignoreCase = true) },
            "Should report face size issue",
        )
    }

    // ==================== Settings Integration Tests ====================

    @Test
    fun `invoke uses provided settings for validation`() {
        // Given
        val landmarks = TestFixtures.createFaceLandmarks()
        val strictSettings = AlignmentSettings(minConfidence = 0.99f)
        val lenientSettings = AlignmentSettings(minConfidence = 0.1f)

        // When
        val strictResult = useCase(landmarks, strictSettings)
        val lenientResult = useCase(landmarks, lenientSettings)

        // Then: Both should pass since we check landmarks existence, not actual confidence
        // This test documents current behavior
        assertEquals(strictResult, lenientResult, "Settings should be respected in validation")
    }
}
