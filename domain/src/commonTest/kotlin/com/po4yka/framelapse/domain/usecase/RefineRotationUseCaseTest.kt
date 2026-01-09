package com.po4yka.framelapse.domain.usecase

import com.po4yka.framelapse.domain.entity.StabilizationSettings
import com.po4yka.framelapse.domain.usecase.face.RefineRotationUseCase
import com.po4yka.framelapse.testutil.TestFixtures
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefineRotationUseCaseTest {

    private lateinit var useCase: RefineRotationUseCase

    @BeforeTest
    fun setup() {
        useCase = RefineRotationUseCase()
        TestFixtures.resetCounters()
    }

    // ==================== Convergence Tests ====================

    @Test
    fun `invoke returns converged true for horizontal eyes`() {
        // Given: Eyes are perfectly horizontal (no rotation needed)
        val landmarks = TestFixtures.createHorizontalEyesLandmarks()
        val matrix = TestFixtures.createIdentityMatrix()
        val settings = StabilizationSettings()

        // When
        val result = useCase(
            currentMatrix = matrix,
            landmarks = landmarks,
            settings = settings,
            canvasWidth = 512,
            canvasHeight = 512,
        )

        // Then: Should be converged since eyes are already horizontal
        assertTrue(result.converged, "Horizontal eyes should be converged")
    }

    @Test
    fun `invoke returns converged false for tilted eyes beyond threshold`() {
        // Given: Eyes are tilted (need rotation)
        val landmarks = TestFixtures.createTiltedEyesLandmarks()
        val matrix = TestFixtures.createIdentityMatrix()
        val settings = StabilizationSettings(rotationStopThreshold = 0.1f)

        // When
        val result = useCase(
            currentMatrix = matrix,
            landmarks = landmarks,
            settings = settings,
            canvasWidth = 512,
            canvasHeight = 512,
        )

        // Then: Should not be converged
        assertFalse(result.converged, "Tilted eyes should not be converged")
    }

    // ==================== Matrix Modification Tests ====================

    @Test
    fun `invoke modifies matrix for tilted eyes`() {
        // Given: Tilted eyes that need rotation
        val landmarks = TestFixtures.createTiltedEyesLandmarks()
        val matrix = TestFixtures.createIdentityMatrix()
        val settings = StabilizationSettings()

        // When
        val result = useCase(
            currentMatrix = matrix,
            landmarks = landmarks,
            settings = settings,
            canvasWidth = 512,
            canvasHeight = 512,
        )

        // Then: Matrix should be modified (rotation applied)
        val matrixChanged = result.matrix.scaleX != matrix.scaleX ||
            result.matrix.skewX != matrix.skewX ||
            result.matrix.skewY != matrix.skewY ||
            result.matrix.scaleY != matrix.scaleY

        assertTrue(matrixChanged, "Matrix should be modified for rotation correction")
    }

    @Test
    fun `invoke does not modify matrix for horizontal eyes`() {
        // Given: Horizontal eyes (no rotation needed)
        val landmarks = TestFixtures.createHorizontalEyesLandmarks()
        val matrix = TestFixtures.createIdentityMatrix()
        val settings = StabilizationSettings()

        // When
        val result = useCase(
            currentMatrix = matrix,
            landmarks = landmarks,
            settings = settings,
            canvasWidth = 512,
            canvasHeight = 512,
        )

        // Then: Matrix should remain identity or very close
        val scaleXDelta = abs(result.matrix.scaleX - matrix.scaleX)
        val skewXDelta = abs(result.matrix.skewX - matrix.skewX)

        assertTrue(
            scaleXDelta < 0.01f && skewXDelta < 0.01f,
            "Matrix should not change significantly for horizontal eyes",
        )
    }

    // ==================== Eye Delta Y Tests ====================

    @Test
    fun `invoke calculates correct eyeDeltaY`() {
        // Given: Eyes with known Y difference
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = com.po4yka.framelapse.domain.entity.LandmarkPoint(150f, 200f, 0f),
            rightEyeCenter = com.po4yka.framelapse.domain.entity.LandmarkPoint(350f, 210f, 0f),
        )
        val matrix = TestFixtures.createIdentityMatrix()
        val settings = StabilizationSettings(rotationStopThreshold = 0.1f)

        // When
        val result = useCase(
            currentMatrix = matrix,
            landmarks = landmarks,
            settings = settings,
            canvasWidth = 500,
            canvasHeight = 500,
        )

        // Then: eyeDeltaY should be 10px (210 - 200)
        // Since 10px > 0.1px threshold, should not be converged
        assertFalse(result.converged)
        assertTrue(result.eyeDeltaY > settings.rotationStopThreshold)
    }

    @Test
    fun `invoke reports converged when eyeDeltaY below threshold`() {
        // Given: Eyes with very small Y difference (0.05px)
        val landmarks = TestFixtures.createFaceLandmarks(
            leftEyeCenter = com.po4yka.framelapse.domain.entity.LandmarkPoint(0.3f, 0.4f, 0f),
            rightEyeCenter = com.po4yka.framelapse.domain.entity.LandmarkPoint(0.7f, 0.4001f, 0f),
        )
        val matrix = TestFixtures.createIdentityMatrix()
        val settings = StabilizationSettings(rotationStopThreshold = 0.1f)

        // When
        val result = useCase(
            currentMatrix = matrix,
            landmarks = landmarks,
            settings = settings,
            canvasWidth = 512,
            canvasHeight = 512,
        )

        // Then: eyeDeltaY ≈ 0.05px < 0.1px threshold, should be converged
        assertTrue(result.converged, "Should converge when eyeDeltaY below threshold")
    }
}
