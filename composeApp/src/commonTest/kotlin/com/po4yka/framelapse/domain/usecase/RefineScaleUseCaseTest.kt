package com.po4yka.framelapse.domain.usecase

import com.po4yka.framelapse.domain.entity.StabilizationSettings
import com.po4yka.framelapse.domain.usecase.face.RefineScaleUseCase
import com.po4yka.framelapse.testutil.TestFixtures
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefineScaleUseCaseTest {

    private lateinit var useCase: RefineScaleUseCase

    @BeforeTest
    fun setup() {
        useCase = RefineScaleUseCase()
        TestFixtures.resetCounters()
    }

    // ==================== Convergence Tests ====================

    @Test
    fun `invoke returns converged true when eye distance matches goal`() {
        // Given: Eye distance matches goal exactly
        val landmarks = TestFixtures.createHorizontalEyesLandmarks()
        val matrix = TestFixtures.createIdentityMatrix()
        val settings = StabilizationSettings(scaleErrorThreshold = 1.0f)
        val goalEyeDistance = 200f // 350 - 150 = 200px distance in test landmarks

        // When
        val result = useCase(
            currentMatrix = matrix,
            landmarks = landmarks,
            goalEyeDistance = goalEyeDistance,
            settings = settings,
            canvasWidth = 512,
            canvasHeight = 512,
        )

        // Then: Scale error should be 0, converged
        assertTrue(result.converged, "Matching eye distance should be converged")
    }

    @Test
    fun `invoke returns converged false when eye distance differs significantly`() {
        // Given: Eye distance differs from goal
        val landmarks = TestFixtures.createSmallFaceLandmarks() // 60px distance
        val matrix = TestFixtures.createIdentityMatrix()
        val settings = StabilizationSettings(scaleErrorThreshold = 1.0f)
        val goalEyeDistance = 200f

        // When
        val result = useCase(
            currentMatrix = matrix,
            landmarks = landmarks,
            goalEyeDistance = goalEyeDistance,
            settings = settings,
            canvasWidth = 512,
            canvasHeight = 512,
        )

        // Then: Large difference, not converged
        assertFalse(result.converged, "Large eye distance difference should not be converged")
    }

    // ==================== Scale Factor Tests ====================

    @Test
    fun `invoke scales up for smaller detected eyes`() {
        // Given: Detected eyes are closer than goal
        val landmarks = TestFixtures.createSmallFaceLandmarks() // ~60px distance
        val matrix = TestFixtures.createIdentityMatrix()
        val settings = StabilizationSettings()
        val goalEyeDistance = 200f

        // When
        val result = useCase(
            currentMatrix = matrix,
            landmarks = landmarks,
            goalEyeDistance = goalEyeDistance,
            settings = settings,
            canvasWidth = 512,
            canvasHeight = 512,
        )

        // Then: Scale should increase (scaleX > 1)
        assertTrue(result.matrix.scaleX > 1f, "Should scale up when detected eyes smaller than goal")
    }

    @Test
    fun `invoke scales down for larger detected eyes`() {
        // Given: Detected eyes are farther apart than goal
        val landmarks = TestFixtures.createLargeFaceLandmarks() // ~400px distance
        val matrix = TestFixtures.createIdentityMatrix()
        val settings = StabilizationSettings()
        val goalEyeDistance = 200f

        // When
        val result = useCase(
            currentMatrix = matrix,
            landmarks = landmarks,
            goalEyeDistance = goalEyeDistance,
            settings = settings,
            canvasWidth = 512,
            canvasHeight = 512,
        )

        // Then: Scale should decrease (scaleX < 1)
        assertTrue(result.matrix.scaleX < 1f, "Should scale down when detected eyes larger than goal")
    }

    // ==================== Scale Error Tests ====================

    @Test
    fun `scaleError reflects difference between detected and goal distance`() {
        // Given: Known eye distances
        val landmarks = TestFixtures.createHorizontalEyesLandmarks() // 200px distance
        val matrix = TestFixtures.createIdentityMatrix()
        val settings = StabilizationSettings(scaleErrorThreshold = 1.0f)
        val goalEyeDistance = 210f // 10px difference

        // When
        val result = useCase(
            currentMatrix = matrix,
            landmarks = landmarks,
            goalEyeDistance = goalEyeDistance,
            settings = settings,
            canvasWidth = 512,
            canvasHeight = 512,
        )

        // Then: Scale error should be approximately 10
        assertTrue(
            abs(result.scaleError - 10f) < 2f,
            "Scale error should reflect distance difference",
        )
    }

    // ==================== Matrix Preservation Tests ====================

    @Test
    fun `invoke preserves translation components`() {
        // Given: Matrix with translation
        val matrix = TestFixtures.createAlignmentMatrix(
            translateX = 50f,
            translateY = 30f,
        )
        val landmarks = TestFixtures.createSmallFaceLandmarks()
        val settings = StabilizationSettings()

        // When
        val result = useCase(
            currentMatrix = matrix,
            landmarks = landmarks,
            goalEyeDistance = 200f,
            settings = settings,
            canvasWidth = 512,
            canvasHeight = 512,
        )

        // Then: Translation should be preserved or adjusted proportionally
        // (Implementation may vary, but translation should not be zeroed)
        assertTrue(result.matrix.translateX != 0f || result.matrix.translateY != 0f)
    }

    @Test
    fun `invoke maintains uniform scaling`() {
        // Given
        val landmarks = TestFixtures.createSmallFaceLandmarks()
        val matrix = TestFixtures.createIdentityMatrix()
        val settings = StabilizationSettings()

        // When
        val result = useCase(
            currentMatrix = matrix,
            landmarks = landmarks,
            goalEyeDistance = 200f,
            settings = settings,
            canvasWidth = 512,
            canvasHeight = 512,
        )

        // Then: scaleX and scaleY should be equal (uniform scaling)
        val scaleDiff = abs(result.matrix.scaleX - result.matrix.scaleY)
        assertTrue(scaleDiff < 0.001f, "Scaling should be uniform (scaleX = scaleY)")
    }
}
