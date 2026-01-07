package com.po4yka.framelapse.domain.usecase

import app.cash.turbine.test
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.usecase.project.GetProjectUseCase
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.testutil.FakeProjectRepository
import com.po4yka.framelapse.testutil.TestFixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class GetProjectUseCaseTest {

    private lateinit var useCase: GetProjectUseCase
    private lateinit var repository: FakeProjectRepository

    @BeforeTest
    fun setup() {
        repository = FakeProjectRepository()
        useCase = GetProjectUseCase(repository)
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
    fun `invoke returns project when exists`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id")
        repository.setProjects(listOf(project))

        // When
        val result = useCase("test_id")

        // Then
        val data = assertSuccessData(result)
        assertEquals(project, data)
    }

    @Test
    fun `invoke returns correct project from multiple`() = runTest {
        // Given
        val projects = TestFixtures.createProjectList(5)
        repository.setProjects(projects)

        // When
        val result = useCase("project_3")

        // Then
        val data = assertSuccessData(result)
        assertEquals("project_3", data.id)
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

    // ==================== Not Found Cases ====================

    @Test
    fun `invoke returns error when project not found`() = runTest {
        // Given: Empty repository

        // When
        val result = useCase("nonexistent")

        // Then
        assertError(result)
    }

    @Test
    fun `invoke returns error for wrong id`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "actual_id")
        repository.setProjects(listOf(project))

        // When
        val result = useCase("wrong_id")

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
        val result = useCase("test_id")

        // Then
        assertError(result)
    }

    // ==================== Flow Observation Tests ====================

    @Test
    fun `observe emits project when available`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id")
        repository.setProjects(listOf(project))

        // When/Then
        useCase.observe("test_id").test {
            assertEquals(project, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe emits null when project not found`() = runTest {
        // Given: Empty repository

        // When/Then
        useCase.observe("nonexistent").test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe emits updates when project changes`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id", name = "Original")
        repository.setProjects(listOf(project))

        // When/Then
        useCase.observe("test_id").test {
            assertEquals("Original", awaitItem()?.name)

            // Update the project
            val updated = project.copy(name = "Updated")
            repository.setProjects(listOf(updated))

            assertEquals("Updated", awaitItem()?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe emits null when project deleted`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id")
        repository.setProjects(listOf(project))

        // When/Then
        useCase.observe("test_id").test {
            assertEquals(project, awaitItem())

            // Delete the project
            repository.clear()

            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
