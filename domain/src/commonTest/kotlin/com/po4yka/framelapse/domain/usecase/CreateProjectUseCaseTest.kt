package com.po4yka.framelapse.domain.usecase

import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.usecase.project.CreateProjectUseCase
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.testutil.FakeProjectRepository
import com.po4yka.framelapse.testutil.TestFixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class CreateProjectUseCaseTest {

    private lateinit var useCase: CreateProjectUseCase
    private lateinit var repository: FakeProjectRepository

    @BeforeTest
    fun setup() {
        repository = FakeProjectRepository()
        useCase = CreateProjectUseCase(repository)
        TestFixtures.resetCounters()
    }

    private fun assertSuccessData(result: Result<Project>): Project = when (result) {
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
    fun `invoke creates project with valid name`() = runTest {
        // When
        val result = useCase("My Timelapse")

        // Then
        val data = assertSuccessData(result)
        assertEquals("My Timelapse", data.name)
    }

    @Test
    fun `invoke creates project with custom fps`() = runTest {
        // When
        val result = useCase("Test Project", fps = 24)

        // Then
        val data = assertSuccessData(result)
        assertEquals(24, data.fps)
    }

    @Test
    fun `invoke creates project with default fps of 30`() = runTest {
        // When
        val result = useCase("Test Project")

        // Then
        val data = assertSuccessData(result)
        assertEquals(30, data.fps)
    }

    @Test
    fun `invoke trims whitespace from name`() = runTest {
        // When
        val result = useCase("  My Project  ")

        // Then
        val data = assertSuccessData(result)
        assertEquals("My Project", data.name)
    }

    @Test
    fun `invoke creates project with minimum fps of 1`() = runTest {
        // When
        val result = useCase("Test", fps = 1)

        // Then
        val data = assertSuccessData(result)
        assertEquals(1, data.fps)
    }

    @Test
    fun `invoke creates project with maximum fps of 60`() = runTest {
        // When
        val result = useCase("Test", fps = 60)

        // Then
        val data = assertSuccessData(result)
        assertEquals(60, data.fps)
    }

    // ==================== Validation Errors ====================

    @Test
    fun `invoke returns error for empty name`() = runTest {
        // When
        val result = useCase("")

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("empty") == true)
    }

    @Test
    fun `invoke returns error for blank name`() = runTest {
        // When
        val result = useCase("   ")

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("empty") == true)
    }

    @Test
    fun `invoke returns error for fps below minimum`() = runTest {
        // When
        val result = useCase("Test", fps = 0)

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("FPS") == true || error.message?.contains("1 and 60") == true)
    }

    @Test
    fun `invoke returns error for fps above maximum`() = runTest {
        // When
        val result = useCase("Test", fps = 61)

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("FPS") == true || error.message?.contains("1 and 60") == true)
    }

    @Test
    fun `invoke returns error for negative fps`() = runTest {
        // When
        val result = useCase("Test", fps = -1)

        // Then
        assertError(result)
    }

    // ==================== Repository Error Cases ====================

    @Test
    fun `invoke returns error when repository fails`() = runTest {
        // Given
        repository.shouldFail = true
        repository.failureException = RuntimeException("Database error")

        // When
        val result = useCase("Test Project")

        // Then
        assertError(result)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `invoke creates multiple projects with unique ids`() = runTest {
        // When
        val result1 = useCase("Project 1")
        val result2 = useCase("Project 2")

        // Then
        val data1 = assertSuccessData(result1)
        val data2 = assertSuccessData(result2)
        assertTrue(data1.id != data2.id)
    }

    @Test
    fun `invoke allows projects with same name`() = runTest {
        // When
        val result1 = useCase("Same Name")
        val result2 = useCase("Same Name")

        // Then
        assertSuccessData(result1)
        assertSuccessData(result2)
    }

    @Test
    fun `invoke handles special characters in name`() = runTest {
        // When
        val result = useCase("Project: \"Test\" (2024)")

        // Then
        val data = assertSuccessData(result)
        assertEquals("Project: \"Test\" (2024)", data.name)
    }
}
