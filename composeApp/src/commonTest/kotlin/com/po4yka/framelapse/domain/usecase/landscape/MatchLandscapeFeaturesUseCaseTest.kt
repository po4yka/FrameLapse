package com.po4yka.framelapse.domain.usecase.landscape

import com.po4yka.framelapse.domain.entity.BoundingBox
import com.po4yka.framelapse.domain.entity.FeatureDetectorType
import com.po4yka.framelapse.domain.entity.LandscapeLandmarks
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.testutil.FakeFeatureMatcher
import com.po4yka.framelapse.testutil.TestFixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class MatchLandscapeFeaturesUseCaseTest {

    private lateinit var useCase: MatchLandscapeFeaturesUseCase
    private lateinit var featureMatcher: FakeFeatureMatcher

    @BeforeTest
    fun setup() {
        TestFixtures.resetCounters()
        featureMatcher = FakeFeatureMatcher()
        useCase = MatchLandscapeFeaturesUseCase(featureMatcher)
    }

    private fun createLandmarks(keypointCount: Int = 50): LandscapeLandmarks =
        TestFixtures.createLandscapeLandmarks(keypointCount = keypointCount)

    private fun createEmptyLandmarks(): LandscapeLandmarks = LandscapeLandmarks(
        keypoints = emptyList(),
        detectorType = FeatureDetectorType.ORB,
        keypointCount = 0,
        boundingBox = BoundingBox(0f, 0f, 1f, 1f),
        qualityScore = 0f,
    )

    private fun assertSuccessData(result: Result<List<Pair<Int, Int>>>): List<Pair<Int, Int>> = when (result) {
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
    fun `invoke returns Success with valid matches`() = runTest {
        // Given
        val matches = TestFixtures.createMatchPairs(count = 20)
        featureMatcher.configureSuccessfulMatching(matches)

        // When
        val result = useCase(
            sourceLandmarks = createLandmarks(),
            referenceLandmarks = createLandmarks(),
        )

        // Then
        val data = assertSuccessData(result)
        assertEquals(20, data.size)
    }

    @Test
    fun `returns Error for empty source keypoints`() = runTest {
        // Given
        featureMatcher.configureSuccessfulMatching()

        // When
        val result = useCase(
            sourceLandmarks = createEmptyLandmarks(),
            referenceLandmarks = createLandmarks(),
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("no") == true || error.message?.contains("Source") == true)
    }

    @Test
    fun `returns Error for source with fewer than 10 keypoints`() = runTest {
        // Given
        featureMatcher.configureSuccessfulMatching()

        // When
        val result = useCase(
            sourceLandmarks = createLandmarks(keypointCount = 5),
            referenceLandmarks = createLandmarks(),
        )

        // Then
        assertError(result)
    }

    @Test
    fun `returns Error for empty reference keypoints`() = runTest {
        // Given
        featureMatcher.configureSuccessfulMatching()

        // When
        val result = useCase(
            sourceLandmarks = createLandmarks(),
            referenceLandmarks = createEmptyLandmarks(),
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("no") == true || error.message?.contains("Reference") == true)
    }

    @Test
    fun `returns Error for reference with fewer than 10 keypoints`() = runTest {
        // Given
        featureMatcher.configureSuccessfulMatching()

        // When
        val result = useCase(
            sourceLandmarks = createLandmarks(),
            referenceLandmarks = createLandmarks(keypointCount = 5),
        )

        // Then
        assertError(result)
    }

    @Test
    fun `returns Error for ratio threshold below 0_5`() = runTest {
        // Given
        featureMatcher.configureSuccessfulMatching()

        // When
        val result = useCase(
            sourceLandmarks = createLandmarks(),
            referenceLandmarks = createLandmarks(),
            ratioTestThreshold = 0.4f,
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("threshold") == true || error.message?.contains("ratio") == true)
    }

    @Test
    fun `returns Error for ratio threshold above 0_95`() = runTest {
        // Given
        featureMatcher.configureSuccessfulMatching()

        // When
        val result = useCase(
            sourceLandmarks = createLandmarks(),
            referenceLandmarks = createLandmarks(),
            ratioTestThreshold = 0.96f,
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("threshold") == true || error.message?.contains("ratio") == true)
    }

    @Test
    fun `accepts boundary threshold values`() = runTest {
        // Given
        val matches = TestFixtures.createMatchPairs(count = 20)
        featureMatcher.configureSuccessfulMatching(matches)

        // When: Test both boundaries
        val result05 = useCase(
            sourceLandmarks = createLandmarks(),
            referenceLandmarks = createLandmarks(),
            ratioTestThreshold = 0.5f,
        )
        val result095 = useCase(
            sourceLandmarks = createLandmarks(),
            referenceLandmarks = createLandmarks(),
            ratioTestThreshold = 0.95f,
        )

        // Then
        assertSuccessData(result05)
        assertSuccessData(result095)
    }

    @Test
    fun `returns Error for minMatchCount below 4`() = runTest {
        // Given
        featureMatcher.configureSuccessfulMatching()

        // When
        val result = useCase(
            sourceLandmarks = createLandmarks(),
            referenceLandmarks = createLandmarks(),
            minMatchCount = 3,
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("4") == true || error.message?.contains("match") == true)
    }

    @Test
    fun `returns Error when fewer matches than minimum`() = runTest {
        // Given: Only 5 matches returned, but minMatchCount is 10 (default)
        val fewMatches = TestFixtures.createMatchPairs(count = 5)
        featureMatcher.configureSuccessfulMatching(fewMatches)

        // When
        val result = useCase(
            sourceLandmarks = createLandmarks(),
            referenceLandmarks = createLandmarks(),
            minMatchCount = 10,
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("match") == true)
    }

    @Test
    fun `returns Error when matcher unavailable`() = runTest {
        // Given
        featureMatcher.isAvailable = false

        // When
        val result = useCase(
            sourceLandmarks = createLandmarks(),
            referenceLandmarks = createLandmarks(),
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("not available") == true)
    }

    @Test
    fun `propagates FeatureMatcher errors`() = runTest {
        // Given
        featureMatcher.shouldFail = true
        featureMatcher.failureException = RuntimeException("Matching algorithm failed")

        // When
        val result = useCase(
            sourceLandmarks = createLandmarks(),
            referenceLandmarks = createLandmarks(),
        )

        // Then
        assertError(result)
    }
}
