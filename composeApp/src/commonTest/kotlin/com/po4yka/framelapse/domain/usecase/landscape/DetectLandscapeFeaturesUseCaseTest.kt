package com.po4yka.framelapse.domain.usecase.landscape

import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.testutil.FakeFeatureMatcher
import com.po4yka.framelapse.testutil.TestFixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class DetectLandscapeFeaturesUseCaseTest {

    private lateinit var useCase: DetectLandscapeFeaturesUseCase
    private lateinit var featureMatcher: FakeFeatureMatcher

    @BeforeTest
    fun setup() {
        TestFixtures.resetCounters()
        featureMatcher = FakeFeatureMatcher()
        useCase = DetectLandscapeFeaturesUseCase(featureMatcher)
    }

    private fun createImageData(width: Int = 1920, height: Int = 1080): ImageData =
        ImageData(width = width, height = height, bytes = ByteArray(0))

    private fun assertSuccessData(result: Result<LandscapeLandmarks>): LandscapeLandmarks = when (result) {
        is Result.Success -> result.data
        is Result.Error -> fail("Expected success but got error: ${result.message}")
        is Result.Loading -> fail("Expected success but got loading")
    }

    private fun assertError(result: Result<*>): Result.Error {
        assertTrue(result is Result.Error, "Expected error but got: $result")
        return result
    }

    // ==================== invoke() Tests ====================

    @Test
    fun `invoke returns Success with valid landmarks`() = runTest {
        // Given
        val landmarks = TestFixtures.createLandscapeLandmarks(keypointCount = 50)
        featureMatcher.configureSuccessfulDetection(landmarks)

        // When
        val result = useCase(createImageData())

        // Then
        val data = assertSuccessData(result)
        assertEquals(50, data.keypointCount)
    }

    @Test
    fun `invoke returns Error for zero width`() = runTest {
        // Given
        featureMatcher.configureSuccessfulDetection()

        // When
        val result = useCase(createImageData(width = 0, height = 1080))

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("invalid") == true || error.message?.contains("dimensions") == true)
    }

    @Test
    fun `invoke returns Error for zero height`() = runTest {
        // Given
        featureMatcher.configureSuccessfulDetection()

        // When
        val result = useCase(createImageData(width = 1920, height = 0))

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("invalid") == true || error.message?.contains("dimensions") == true)
    }

    @Test
    fun `invoke returns Error for maxKeypoints below 10`() = runTest {
        // Given
        featureMatcher.configureSuccessfulDetection()

        // When
        val result = useCase(
            imageData = createImageData(),
            maxKeypoints = 5,
        )

        // Then
        assertError(result)
    }

    @Test
    fun `invoke returns Error when matcher unavailable`() = runTest {
        // Given
        featureMatcher.isAvailable = false

        // When
        val result = useCase(createImageData())

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("not available") == true)
    }

    @Test
    fun `returns Error when fewer than 10 keypoints detected`() = runTest {
        // Given: Detection returns only 5 keypoints
        val fewKeypoints = TestFixtures.createLandscapeLandmarks(keypointCount = 5)
        featureMatcher.configureSuccessfulDetection(fewKeypoints)

        // When
        val result = useCase(createImageData())

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("enough") == true || error.message?.contains("feature") == true)
    }

    @Test
    fun `returns Success with exactly 10 keypoints`() = runTest {
        // Given: Detection returns exactly 10 keypoints
        val minKeypoints = TestFixtures.createLandscapeLandmarks(keypointCount = 10)
        featureMatcher.configureSuccessfulDetection(minKeypoints)

        // When
        val result = useCase(createImageData())

        // Then
        val data = assertSuccessData(result)
        assertEquals(10, data.keypointCount)
    }

    @Test
    fun `propagates FeatureMatcher errors`() = runTest {
        // Given
        featureMatcher.shouldFail = true
        featureMatcher.failureException = RuntimeException("OpenCV initialization failed")

        // When
        val result = useCase(createImageData())

        // Then
        val error = assertError(result)
        assertTrue(error.exception is RuntimeException)
    }

    // ==================== fromPath() Tests ====================

    @Test
    fun `fromPath returns Success with valid path`() = runTest {
        // Given
        val landmarks = TestFixtures.createLandscapeLandmarks(keypointCount = 50)
        featureMatcher.configureSuccessfulDetection(landmarks)

        // When
        val result = useCase.fromPath("/valid/path/image.jpg")

        // Then
        val data = assertSuccessData(result)
        assertEquals(50, data.keypointCount)
        assertEquals("/valid/path/image.jpg", featureMatcher.lastImagePath)
    }

    @Test
    fun `fromPath returns Error for empty path`() = runTest {
        // Given
        featureMatcher.configureSuccessfulDetection()

        // When
        val result = useCase.fromPath("")

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("empty") == true)
    }

    @Test
    fun `fromPath returns Error for blank path`() = runTest {
        // Given
        featureMatcher.configureSuccessfulDetection()

        // When
        val result = useCase.fromPath("   ")

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("empty") == true)
    }

    // ==================== isAvailable Tests ====================

    @Test
    fun `isAvailable reflects FeatureMatcher availability`() {
        // Given: Matcher is available
        featureMatcher.isAvailable = true

        // Then
        assertTrue(useCase.isAvailable)

        // Given: Matcher is unavailable
        featureMatcher.isAvailable = false

        // Then
        assertFalse(useCase.isAvailable)
    }
}
