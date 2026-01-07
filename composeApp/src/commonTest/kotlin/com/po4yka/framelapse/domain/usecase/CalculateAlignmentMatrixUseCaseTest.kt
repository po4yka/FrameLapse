package com.po4yka.framelapse.domain.usecase

import com.po4yka.framelapse.domain.entity.AlignmentSettings
import com.po4yka.framelapse.domain.entity.LandmarkPoint
import com.po4yka.framelapse.domain.usecase.face.CalculateAlignmentMatrixUseCase
import com.po4yka.framelapse.testutil.TestFixtures
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalculateAlignmentMatrixUseCaseTest {

    private lateinit var useCase: CalculateAlignmentMatrixUseCase

    @BeforeTest
    fun setup() {
        useCase = CalculateAlignmentMatrixUseCase()
        TestFixtures.resetCounters()
    }

    // ==================== Rotation Tests ====================

    @Test
    fun `invoke with horizontal eyes produces zero rotation`() {
        // Given: Eyes are perfectly horizontal
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(100f, 200f, 0f),
            rightEyeCenter = LandmarkPoint(200f, 200f, 0f),
        )

        // When
        val matrix = useCase(landmarks)

        // Then: No rotation means skewX and skewY should be ~0
        // scaleX and scaleY should be equal (no rotation component)
        assertTrue(abs(matrix.skewX) < 0.01f, "skewX should be near 0 for horizontal eyes")
        assertTrue(abs(matrix.skewY) < 0.01f, "skewY should be near 0 for horizontal eyes")
        assertTrue(
            abs(matrix.scaleX - matrix.scaleY) < 0.01f,
            "scaleX and scaleY should be equal for horizontal eyes",
        )
    }

    @Test
    fun `invoke with tilted eyes produces rotation`() {
        // Given: Eyes are tilted (right eye higher than left)
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(100f, 220f, 0f),
            rightEyeCenter = LandmarkPoint(200f, 180f, 0f),
        )

        // When
        val matrix = useCase(landmarks)

        // Then: There should be rotation (non-zero skew values)
        val hasRotation = abs(matrix.skewX) > 0.01f || abs(matrix.skewY) > 0.01f
        assertTrue(hasRotation, "Matrix should have rotation for tilted eyes")
    }

    @Test
    fun `invoke with eyes tilted other way produces opposite rotation`() {
        // Given: Eyes tilted in opposite direction
        val landmarksTiltedUp = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(100f, 180f, 0f),
            rightEyeCenter = LandmarkPoint(200f, 220f, 0f),
        )

        val landmarksTiltedDown = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(100f, 220f, 0f),
            rightEyeCenter = LandmarkPoint(200f, 180f, 0f),
        )

        // When
        val matrixUp = useCase(landmarksTiltedUp)
        val matrixDown = useCase(landmarksTiltedDown)

        // Then: Skew values should have opposite signs
        assertTrue(
            (matrixUp.skewX * matrixDown.skewX) < 0 || (matrixUp.skewY * matrixDown.skewY) < 0,
            "Opposite tilts should produce opposite rotation",
        )
    }

    // ==================== Scale Tests ====================

    @Test
    fun `invoke scales to achieve target eye distance`() {
        // Given: Eyes with known distance
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(100f, 200f, 0f),
            rightEyeCenter = LandmarkPoint(200f, 200f, 0f),
        )
        val settings = AlignmentSettings(
            outputSize = 512,
            targetEyeDistance = 0.3f,
        )

        // When
        val matrix = useCase(landmarks, settings)

        // Then: Scale should transform 100px eye distance to target
        // Target eye distance = 512 * 0.3 = 153.6px
        // Scale = 153.6 / 100 = 1.536
        val expectedScale = 153.6f / 100f
        assertEquals(expectedScale, matrix.scaleX, 0.01f)
    }

    @Test
    fun `invoke with small face produces larger scale`() {
        // Given: Face with small eye distance
        val smallFace = TestFixtures.createSmallFaceLandmarks()
        val largeFace = TestFixtures.createLargeFaceLandmarks()

        // When
        val smallMatrix = useCase(smallFace)
        val largeMatrix = useCase(largeFace)

        // Then: Small face should scale up more
        assertTrue(
            smallMatrix.scaleX > largeMatrix.scaleX,
            "Small face should have larger scale factor",
        )
    }

    @Test
    fun `invoke with zero eye distance handles gracefully`() {
        // Given: Eyes at same position (edge case)
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(150f, 200f, 0f),
            rightEyeCenter = LandmarkPoint(150f, 200f, 0f),
        )

        // When
        val matrix = useCase(landmarks)

        // Then: Should return scale of 1 (fallback behavior)
        assertEquals(1f, matrix.scaleX, 0.01f)
    }

    // ==================== Translation Tests ====================

    @Test
    fun `invoke centers face in output`() {
        // Given: Face not centered
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(50f, 100f, 0f),
            rightEyeCenter = LandmarkPoint(150f, 100f, 0f),
        )
        val settings = AlignmentSettings(outputSize = 512)

        // When
        val matrix = useCase(landmarks, settings)

        // Then: Translation should move face toward center
        // Eye center is at (100, 100), should move toward (256, ~205 with vertical offset)
        assertTrue(matrix.translateX > 0, "Should translate face right toward center")
    }

    @Test
    fun `invoke applies vertical offset correctly`() {
        // Given: Two settings with different vertical offsets
        val landmarks = TestFixtures.createHorizontalEyesLandmarks()
        val settingsNoOffset = AlignmentSettings(outputSize = 512, verticalOffset = 0f)
        val settingsWithOffset = AlignmentSettings(outputSize = 512, verticalOffset = 0.1f)

        // When
        val matrixNoOffset = useCase(landmarks, settingsNoOffset)
        val matrixWithOffset = useCase(landmarks, settingsWithOffset)

        // Then: Vertical offset should affect translateY
        assertTrue(
            matrixNoOffset.translateY != matrixWithOffset.translateY,
            "Vertical offset should change translateY",
        )
    }

    // ==================== Integration Tests ====================

    @Test
    fun `invoke produces consistent results for same input`() {
        // Given
        val landmarks = TestFixtures.createFaceLandmarks()
        val settings = AlignmentSettings()

        // When
        val matrix1 = useCase(landmarks, settings)
        val matrix2 = useCase(landmarks, settings)

        // Then: Same input should produce same output
        assertEquals(matrix1.scaleX, matrix2.scaleX)
        assertEquals(matrix1.skewX, matrix2.skewX)
        assertEquals(matrix1.translateX, matrix2.translateX)
        assertEquals(matrix1.skewY, matrix2.skewY)
        assertEquals(matrix1.scaleY, matrix2.scaleY)
        assertEquals(matrix1.translateY, matrix2.translateY)
    }

    @Test
    fun `invoke respects custom output size`() {
        // Given: Same landmarks with different output sizes
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = LandmarkPoint(100f, 200f, 0f),
            rightEyeCenter = LandmarkPoint(200f, 200f, 0f),
        )
        val smallSettings = AlignmentSettings(outputSize = 256, targetEyeDistance = 0.3f)
        val largeSettings = AlignmentSettings(outputSize = 1024, targetEyeDistance = 0.3f)

        // When
        val smallMatrix = useCase(landmarks, smallSettings)
        val largeMatrix = useCase(landmarks, largeSettings)

        // Then: Larger output should have larger scale
        // 256 * 0.3 / 100 = 0.768 vs 1024 * 0.3 / 100 = 3.072
        assertTrue(
            largeMatrix.scaleX > smallMatrix.scaleX,
            "Larger output size should produce larger scale",
        )
        assertEquals(largeMatrix.scaleX, smallMatrix.scaleX * 4, 0.01f)
    }
}
