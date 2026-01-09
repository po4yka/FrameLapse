package com.po4yka.framelapse.domain.usecase.landscape

import com.po4yka.framelapse.domain.entity.FeatureKeypoint
import com.po4yka.framelapse.domain.entity.HomographyMatrix
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.testutil.FakeFeatureMatcher
import com.po4yka.framelapse.testutil.TestFixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class CalculateHomographyMatrixUseCaseTest {

    private lateinit var useCase: CalculateHomographyMatrixUseCase
    private lateinit var featureMatcher: FakeFeatureMatcher

    @BeforeTest
    fun setup() {
        TestFixtures.resetCounters()
        featureMatcher = FakeFeatureMatcher()
        useCase = CalculateHomographyMatrixUseCase(featureMatcher)
    }

    private fun createKeypoints(count: Int = 50): List<FeatureKeypoint> = TestFixtures.createFeatureKeypointList(count)

    private fun createMatches(count: Int = 20): List<Pair<Int, Int>> = TestFixtures.createMatchPairs(count)

    private fun assertSuccessData(result: Result<Pair<HomographyMatrix, Int>>): Pair<HomographyMatrix, Int> =
        when (result) {
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
    fun `invoke returns Success with valid homography`() = runTest {
        // Given
        val homography = HomographyMatrix.IDENTITY
        featureMatcher.configureSuccessfulHomography(matrix = homography, inlierCount = 15)

        // When
        val result = useCase(
            sourceKeypoints = createKeypoints(),
            referenceKeypoints = createKeypoints(),
            matches = createMatches(count = 20),
        )

        // Then
        val (matrix, inlierCount) = assertSuccessData(result)
        assertTrue(matrix.isNearIdentity())
        assertEquals(15, inlierCount)
    }

    @Test
    fun `returns Error for empty source keypoints`() = runTest {
        // Given
        featureMatcher.configureSuccessfulHomography()

        // When
        val result = useCase(
            sourceKeypoints = emptyList(),
            referenceKeypoints = createKeypoints(),
            matches = createMatches(),
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("source") == true || error.message?.contains("Source") == true)
    }

    @Test
    fun `returns Error for empty reference keypoints`() = runTest {
        // Given
        featureMatcher.configureSuccessfulHomography()

        // When
        val result = useCase(
            sourceKeypoints = createKeypoints(),
            referenceKeypoints = emptyList(),
            matches = createMatches(),
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("reference") == true || error.message?.contains("Reference") == true)
    }

    @Test
    fun `returns Error for fewer than 4 matches`() = runTest {
        // Given
        featureMatcher.configureSuccessfulHomography()

        // When
        val result = useCase(
            sourceKeypoints = createKeypoints(),
            referenceKeypoints = createKeypoints(),
            matches = createMatches(count = 3),
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("4") == true || error.message?.contains("match") == true)
    }

    @Test
    fun `returns Error for out-of-bounds source index`() = runTest {
        // Given: Matches with an invalid source index
        val keypoints = createKeypoints(count = 10)
        val invalidMatches = listOf(
            Pair(0, 0),
            Pair(5, 5),
            Pair(100, 8), // Invalid: index 100 > keypoints.size
            Pair(9, 9),
        )
        featureMatcher.configureSuccessfulHomography()

        // When
        val result = useCase(
            sourceKeypoints = keypoints,
            referenceKeypoints = keypoints,
            matches = invalidMatches,
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("index") == true || error.message?.contains("bounds") == true)
    }

    @Test
    fun `returns Error for out-of-bounds reference index`() = runTest {
        // Given: Matches with an invalid reference index
        val keypoints = createKeypoints(count = 10)
        val invalidMatches = listOf(
            Pair(0, 0),
            Pair(5, 500), // Invalid: index 500 > keypoints.size
            Pair(8, 8),
            Pair(9, 9),
        )
        featureMatcher.configureSuccessfulHomography()

        // When
        val result = useCase(
            sourceKeypoints = keypoints,
            referenceKeypoints = keypoints,
            matches = invalidMatches,
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("index") == true || error.message?.contains("bounds") == true)
    }

    @Test
    fun `returns Error for negative match indices`() = runTest {
        // Given: Matches with negative indices
        val keypoints = createKeypoints(count = 10)
        val invalidMatches = listOf(
            Pair(-1, 0), // Negative source index
            Pair(5, 5),
            Pair(8, 8),
            Pair(9, 9),
        )
        featureMatcher.configureSuccessfulHomography()

        // When
        val result = useCase(
            sourceKeypoints = keypoints,
            referenceKeypoints = keypoints,
            matches = invalidMatches,
        )

        // Then
        assertError(result)
    }

    @Test
    fun `returns Error for zero RANSAC threshold`() = runTest {
        // Given
        featureMatcher.configureSuccessfulHomography()

        // When
        val result = useCase(
            sourceKeypoints = createKeypoints(),
            referenceKeypoints = createKeypoints(),
            matches = createMatches(),
            ransacThreshold = 0f,
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("RANSAC") == true || error.message?.contains("positive") == true)
    }

    @Test
    fun `returns Error for negative RANSAC threshold`() = runTest {
        // Given
        featureMatcher.configureSuccessfulHomography()

        // When
        val result = useCase(
            sourceKeypoints = createKeypoints(),
            referenceKeypoints = createKeypoints(),
            matches = createMatches(),
            ransacThreshold = -1f,
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("RANSAC") == true || error.message?.contains("positive") == true)
    }

    @Test
    fun `returns Error for singular homography`() = runTest {
        // Given: Singular (invalid) homography matrix
        val singularMatrix = HomographyMatrix(
            h11 = 0f, h12 = 0f, h13 = 0f,
            h21 = 0f, h22 = 0f, h23 = 0f,
            h31 = 0f, h32 = 0f, h33 = 0f,
        )
        featureMatcher.configureSuccessfulHomography(matrix = singularMatrix, inlierCount = 15)

        // When
        val result = useCase(
            sourceKeypoints = createKeypoints(),
            referenceKeypoints = createKeypoints(),
            matches = createMatches(count = 20),
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("singular") == true || error.message?.contains("Invalid") == true)
    }

    @Test
    fun `returns Error for determinant below minimum`() = runTest {
        // Given: Matrix with very small determinant (< 0.01)
        val tinyDetMatrix = HomographyMatrix(
            h11 = 0.05f, h12 = 0f, h13 = 0f,
            h21 = 0f, h22 = 0.05f, h23 = 0f,
            h31 = 0f, h32 = 0f, h33 = 1f,
        ) // det = 0.0025
        featureMatcher.configureSuccessfulHomography(matrix = tinyDetMatrix, inlierCount = 15)

        // When
        val result = useCase(
            sourceKeypoints = createKeypoints(),
            referenceKeypoints = createKeypoints(),
            matches = createMatches(count = 20),
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("determinant") == true || error.message?.contains("extreme") == true)
    }

    @Test
    fun `returns Error for determinant above maximum`() = runTest {
        // Given: Matrix with very large determinant (> 100)
        val largeDetMatrix = HomographyMatrix(
            h11 = 15f, h12 = 0f, h13 = 0f,
            h21 = 0f, h22 = 15f, h23 = 0f,
            h31 = 0f, h32 = 0f, h33 = 1f,
        ) // det = 225
        featureMatcher.configureSuccessfulHomography(matrix = largeDetMatrix, inlierCount = 15)

        // When
        val result = useCase(
            sourceKeypoints = createKeypoints(),
            referenceKeypoints = createKeypoints(),
            matches = createMatches(count = 20),
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("determinant") == true || error.message?.contains("extreme") == true)
    }

    @Test
    fun `returns Error for inlier ratio below minimum`() = runTest {
        // Given: Very few inliers (2 out of 20 = 0.1 < 0.2 min)
        featureMatcher.configureSuccessfulHomography(matrix = HomographyMatrix.IDENTITY, inlierCount = 2)

        // When
        val result = useCase(
            sourceKeypoints = createKeypoints(),
            referenceKeypoints = createKeypoints(),
            matches = createMatches(count = 20),
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("inlier") == true || error.message?.contains("outlier") == true)
    }

    @Test
    fun `returns Success for acceptable inlier ratio`() = runTest {
        // Given: 6 out of 20 = 0.3 (above 0.2 minimum)
        featureMatcher.configureSuccessfulHomography(matrix = HomographyMatrix.IDENTITY, inlierCount = 6)

        // When
        val result = useCase(
            sourceKeypoints = createKeypoints(),
            referenceKeypoints = createKeypoints(),
            matches = createMatches(count = 20),
        )

        // Then
        val (_, inlierCount) = assertSuccessData(result)
        assertEquals(6, inlierCount)
    }

    @Test
    fun `returns Error when matcher unavailable`() = runTest {
        // Given
        featureMatcher.isAvailable = false

        // When
        val result = useCase(
            sourceKeypoints = createKeypoints(),
            referenceKeypoints = createKeypoints(),
            matches = createMatches(),
        )

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("not available") == true)
    }

    @Test
    fun `propagates FeatureMatcher errors`() = runTest {
        // Given
        featureMatcher.shouldFail = true
        featureMatcher.failureException = RuntimeException("RANSAC computation failed")

        // When
        val result = useCase(
            sourceKeypoints = createKeypoints(),
            referenceKeypoints = createKeypoints(),
            matches = createMatches(),
        )

        // Then
        assertError(result)
    }
}
