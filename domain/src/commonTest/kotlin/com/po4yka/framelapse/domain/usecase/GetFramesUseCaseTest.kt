package com.po4yka.framelapse.domain.usecase

import app.cash.turbine.test
import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.usecase.frame.GetFramesUseCase
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.testutil.FakeFrameRepository
import com.po4yka.framelapse.testutil.TestFixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class GetFramesUseCaseTest {

    private lateinit var useCase: GetFramesUseCase
    private lateinit var repository: FakeFrameRepository

    @BeforeTest
    fun setup() {
        repository = FakeFrameRepository()
        useCase = GetFramesUseCase(repository)
        TestFixtures.resetCounters()
    }

    private fun assertSuccessData(result: Result<List<Frame>>): List<Frame> = when (result) {
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
    fun `invoke returns empty list when no frames`() = runTest {
        // When
        val result = useCase("project_1")

        // Then
        val data = assertSuccessData(result)
        assertTrue(data.isEmpty())
    }

    @Test
    fun `invoke returns frames for project`() = runTest {
        // Given
        val frames = TestFixtures.createFrameList(5, projectId = "project_1")
        repository.setFrames(frames)

        // When
        val result = useCase("project_1")

        // Then
        val data = assertSuccessData(result)
        assertEquals(5, data.size)
    }

    @Test
    fun `invoke returns only frames for specified project`() = runTest {
        // Given: Frames from different projects
        val project1Frames = TestFixtures.createFrameList(3, projectId = "project_1")
        val project2Frames = (1..2).map {
            TestFixtures.createFrame(id = "other_$it", projectId = "project_2")
        }
        repository.setFrames(project1Frames + project2Frames)

        // When
        val result = useCase("project_1")

        // Then
        val data = assertSuccessData(result)
        assertEquals(3, data.size)
        assertTrue(data.all { it.projectId == "project_1" })
    }

    @Test
    fun `invoke returns frames sorted by sortOrder`() = runTest {
        // Given: Frames with different sort orders
        val frame1 = TestFixtures.createFrame(id = "f1", projectId = "project_1", sortOrder = 2)
        val frame2 = TestFixtures.createFrame(id = "f2", projectId = "project_1", sortOrder = 0)
        val frame3 = TestFixtures.createFrame(id = "f3", projectId = "project_1", sortOrder = 1)
        repository.setFrames(listOf(frame1, frame2, frame3))

        // When
        val result = useCase("project_1")

        // Then
        val data = assertSuccessData(result)
        assertEquals("f2", data[0].id)
        assertEquals("f3", data[1].id)
        assertEquals("f1", data[2].id)
    }

    @Test
    fun `invoke returns error for empty projectId`() = runTest {
        // When
        val result = useCase("")

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("empty") == true)
    }

    @Test
    fun `invoke returns error for blank projectId`() = runTest {
        // When
        val result = useCase("   ")

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("empty") == true)
    }

    @Test
    fun `invoke returns error when repository fails`() = runTest {
        // Given
        repository.shouldFail = true

        // When
        val result = useCase("project_1")

        // Then
        assertError(result)
    }

    // ==================== getByDateRange() Tests ====================

    @Test
    fun `getByDateRange returns frames within range`() = runTest {
        // Given
        val frames = TestFixtures.createFrameList(5, projectId = "project_1")
        repository.setFrames(frames)

        // When: Get frames within range that covers all
        val result = useCase.getByDateRange(
            projectId = "project_1",
            startTimestamp = frames.first().timestamp - 1000,
            endTimestamp = frames.last().timestamp + 1000,
        )

        // Then
        val data = assertSuccessData(result)
        assertEquals(5, data.size)
    }

    @Test
    fun `getByDateRange returns empty for range with no frames`() = runTest {
        // Given
        val frames = TestFixtures.createFrameList(5, projectId = "project_1")
        repository.setFrames(frames)

        // When: Get frames from far future
        val result = useCase.getByDateRange(
            projectId = "project_1",
            startTimestamp = Long.MAX_VALUE - 1000,
            endTimestamp = Long.MAX_VALUE,
        )

        // Then
        val data = assertSuccessData(result)
        assertTrue(data.isEmpty())
    }

    @Test
    fun `getByDateRange returns error for empty projectId`() = runTest {
        // When
        val result = useCase.getByDateRange("", 0L, 1000L)

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("empty") == true)
    }

    @Test
    fun `getByDateRange returns error for invalid range`() = runTest {
        // When: Start after end
        val result = useCase.getByDateRange("project_1", 2000L, 1000L)

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("Invalid date range") == true)
    }

    @Test
    fun `getByDateRange accepts equal start and end`() = runTest {
        // Given
        val timestamp = 1704067200000L
        val frame = TestFixtures.createFrame(
            id = "exact",
            projectId = "project_1",
            timestamp = timestamp,
        )
        repository.setFrames(listOf(frame))

        // When: Same timestamp for start and end
        val result = useCase.getByDateRange("project_1", timestamp, timestamp)

        // Then
        val data = assertSuccessData(result)
        assertEquals(1, data.size)
    }

    // ==================== observe() Tests ====================

    @Test
    fun `observe emits empty list initially`() = runTest {
        // When/Then
        useCase.observe("project_1").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe emits frames for project`() = runTest {
        // Given
        val frames = TestFixtures.createFrameList(3, projectId = "project_1")
        repository.setFrames(frames)

        // When/Then
        useCase.observe("project_1").test {
            assertEquals(3, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe emits updates when frames change`() = runTest {
        // Given
        repository.setFrames(emptyList())

        // When/Then
        useCase.observe("project_1").test {
            assertTrue(awaitItem().isEmpty())

            // Add frames
            repository.setFrames(TestFixtures.createFrameList(2, projectId = "project_1"))
            assertEquals(2, awaitItem().size)

            // Add more
            val moreFrames = TestFixtures.createFrameList(4, projectId = "project_1")
            repository.setFrames(moreFrames)
            assertEquals(4, awaitItem().size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe only emits frames for specified project`() = runTest {
        // Given
        val project1Frames = TestFixtures.createFrameList(3, projectId = "project_1")
        val project2Frames = (1..5).map {
            TestFixtures.createFrame(id = "other_$it", projectId = "project_2")
        }

        // When/Then
        useCase.observe("project_1").test {
            assertTrue(awaitItem().isEmpty())

            repository.setFrames(project1Frames + project2Frames)
            assertEquals(3, awaitItem().size)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
