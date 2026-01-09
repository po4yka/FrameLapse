package com.po4yka.framelapse.domain.usecase

import com.po4yka.framelapse.domain.entity.Frame
import com.po4yka.framelapse.domain.usecase.frame.GetLatestFrameUseCase
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.testutil.FakeFrameRepository
import com.po4yka.framelapse.testutil.TestFixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class GetLatestFrameUseCaseTest {

    private lateinit var useCase: GetLatestFrameUseCase
    private lateinit var repository: FakeFrameRepository

    @BeforeTest
    fun setup() {
        repository = FakeFrameRepository()
        useCase = GetLatestFrameUseCase(repository)
        TestFixtures.resetCounters()
    }

    private fun assertSuccessData(result: Result<Frame?>): Frame? = when (result) {
        is Result.Success -> result.data
        is Result.Error -> fail("Expected success but got error: ${result.message}")
        is Result.Loading -> fail("Expected success but got loading")
    }

    private fun assertError(result: Result<*>): Result.Error {
        assertTrue(result is Result.Error, "Expected error but got: $result")
        return result
    }

    // ==================== Success Cases ====================

    @Test
    fun `invoke returns null when no frames exist`() = runTest {
        // When
        val result = useCase("project_1")

        // Then
        val data = assertSuccessData(result)
        assertNull(data)
    }

    @Test
    fun `invoke returns latest frame by timestamp`() = runTest {
        // Given: Frames with different timestamps
        val oldFrame = TestFixtures.createFrame(
            id = "old",
            projectId = "project_1",
            timestamp = 1000L,
        )
        val newestFrame = TestFixtures.createFrame(
            id = "newest",
            projectId = "project_1",
            timestamp = 3000L,
        )
        val middleFrame = TestFixtures.createFrame(
            id = "middle",
            projectId = "project_1",
            timestamp = 2000L,
        )
        repository.setFrames(listOf(oldFrame, newestFrame, middleFrame))

        // When
        val result = useCase("project_1")

        // Then
        val data = assertSuccessData(result)
        assertEquals("newest", data?.id)
    }

    @Test
    fun `invoke returns single frame when only one exists`() = runTest {
        // Given
        val frame = TestFixtures.createFrame(id = "only_one", projectId = "project_1")
        repository.setFrames(listOf(frame))

        // When
        val result = useCase("project_1")

        // Then
        val data = assertSuccessData(result)
        assertEquals("only_one", data?.id)
    }

    @Test
    fun `invoke returns latest from correct project`() = runTest {
        // Given: Frames from different projects with different timestamps
        val project1OldFrame = TestFixtures.createFrame(
            id = "p1_old",
            projectId = "project_1",
            timestamp = 1000L,
        )
        val project1NewFrame = TestFixtures.createFrame(
            id = "p1_new",
            projectId = "project_1",
            timestamp = 2000L,
        )
        val project2NewerFrame = TestFixtures.createFrame(
            id = "p2_newer",
            projectId = "project_2",
            timestamp = 5000L, // Newer than project_1's latest
        )
        repository.setFrames(listOf(project1OldFrame, project1NewFrame, project2NewerFrame))

        // When
        val result = useCase("project_1")

        // Then: Should get project_1's latest, not the globally newest
        val data = assertSuccessData(result)
        assertEquals("p1_new", data?.id)
    }

    @Test
    fun `invoke returns null for project with no frames`() = runTest {
        // Given: Frames only in other project
        val otherProjectFrame = TestFixtures.createFrame(
            id = "other",
            projectId = "project_2",
            timestamp = 1000L,
        )
        repository.setFrames(listOf(otherProjectFrame))

        // When
        val result = useCase("project_1")

        // Then
        val data = assertSuccessData(result)
        assertNull(data)
    }

    // ==================== Validation Errors ====================

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

    // ==================== Repository Error Cases ====================

    @Test
    fun `invoke returns error when repository fails`() = runTest {
        // Given
        repository.shouldFail = true
        repository.failureException = RuntimeException("Database error")

        // When
        val result = useCase("project_1")

        // Then
        assertError(result)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `invoke handles frames with same timestamp`() = runTest {
        // Given: Frames with identical timestamps
        val frame1 = TestFixtures.createFrame(
            id = "frame_1",
            projectId = "project_1",
            timestamp = 1000L,
        )
        val frame2 = TestFixtures.createFrame(
            id = "frame_2",
            projectId = "project_1",
            timestamp = 1000L,
        )
        repository.setFrames(listOf(frame1, frame2))

        // When
        val result = useCase("project_1")

        // Then: Should return one of them (implementation defined)
        val data = assertSuccessData(result)
        assertTrue(data?.id in listOf("frame_1", "frame_2"))
    }

    @Test
    fun `invoke returns frame with aligned data`() = runTest {
        // Given: Frame with alignment
        val alignedFrame = TestFixtures.createFrameWithAlignment(
            id = "aligned",
            projectId = "project_1",
        )
        repository.setFrames(listOf(alignedFrame))

        // When
        val result = useCase("project_1")

        // Then
        val data = assertSuccessData(result)
        assertEquals("aligned", data?.id)
        assertNotNull(data?.alignedPath)
        assertNotNull(data?.landmarks)
    }
}
