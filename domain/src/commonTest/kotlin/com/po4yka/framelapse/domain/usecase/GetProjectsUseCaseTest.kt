package com.po4yka.framelapse.domain.usecase

import app.cash.turbine.test
import com.po4yka.framelapse.domain.entity.Project
import com.po4yka.framelapse.domain.usecase.project.GetProjectsUseCase
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.testutil.FakeProjectRepository
import com.po4yka.framelapse.testutil.TestFixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class GetProjectsUseCaseTest {

    private lateinit var useCase: GetProjectsUseCase
    private lateinit var repository: FakeProjectRepository

    @BeforeTest
    fun setup() {
        repository = FakeProjectRepository()
        useCase = GetProjectsUseCase(repository)
        TestFixtures.resetCounters()
    }

    // ==================== Success Cases ====================

    @Test
    fun `invoke returns empty list when no projects`() = runTest {
        // When
        val result = useCase()

        // Then
        val data = assertSuccessData(result)
        assertTrue(data.isEmpty())
    }

    @Test
    fun `invoke returns all projects`() = runTest {
        // Given
        val projects = TestFixtures.createProjectList(3)
        repository.setProjects(projects)

        // When
        val result = useCase()

        // Then
        val data = assertSuccessData(result)
        assertEquals(3, data.size)
    }

    @Test
    fun `invoke returns projects sorted by updatedAt descending`() = runTest {
        // Given: Projects with different update times
        val oldProject = TestFixtures.createProject(id = "old", updatedAt = 1000L)
        val newProject = TestFixtures.createProject(id = "new", updatedAt = 3000L)
        val middleProject = TestFixtures.createProject(id = "middle", updatedAt = 2000L)
        repository.setProjects(listOf(oldProject, newProject, middleProject))

        // When
        val result = useCase()

        // Then
        val data = assertSuccessData(result)
        assertEquals("new", data[0].id)
        assertEquals("middle", data[1].id)
        assertEquals("old", data[2].id)
    }

    @Test
    fun `invoke returns single project`() = runTest {
        // Given
        val project = TestFixtures.createProject()
        repository.setProjects(listOf(project))

        // When
        val result = useCase()

        // Then
        val data = assertSuccessData(result)
        assertEquals(1, data.size)
        assertEquals(project, data.first())
    }

    private fun assertSuccessData(result: Result<List<Project>>): List<Project> = when (result) {
        is Result.Success -> result.data
        is Result.Error -> fail("Expected success but got error: ${result.message}")
        is Result.Loading -> fail("Expected success but got loading")
    }

    private fun assertError(result: Result<*>) {
        assertTrue(result is Result.Error, "Expected error but got: $result")
    }

    // ==================== Repository Error Cases ====================

    @Test
    fun `invoke returns error when repository fails`() = runTest {
        // Given
        repository.shouldFail = true
        repository.failureException = RuntimeException("Database error")

        // When
        val result = useCase()

        // Then
        assertError(result)
    }

    // ==================== Flow Observation Tests ====================

    @Test
    fun `observe emits empty list initially`() = runTest {
        // When/Then
        useCase.observe().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe emits all projects`() = runTest {
        // Given
        val projects = TestFixtures.createProjectList(3)
        repository.setProjects(projects)

        // When/Then
        useCase.observe().test {
            assertEquals(3, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe emits updates when projects change`() = runTest {
        // Given
        repository.setProjects(emptyList())

        // When/Then
        useCase.observe().test {
            assertTrue(awaitItem().isEmpty())

            // Add projects
            repository.setProjects(TestFixtures.createProjectList(2))
            assertEquals(2, awaitItem().size)

            // Add more
            val moreProjects = TestFixtures.createProjectList(4)
            repository.setProjects(moreProjects)
            assertEquals(4, awaitItem().size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe emits empty list when all projects deleted`() = runTest {
        // Given
        repository.setProjects(TestFixtures.createProjectList(3))

        // When/Then
        useCase.observe().test {
            assertEquals(3, awaitItem().size)

            repository.clear()
            assertTrue(awaitItem().isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe maintains sort order on updates`() = runTest {
        // Given
        val oldProject = TestFixtures.createProject(id = "old", updatedAt = 1000L)
        repository.setProjects(listOf(oldProject))

        // When/Then
        useCase.observe().test {
            assertEquals("old", awaitItem().first().id)

            // Add newer project
            val newProject = TestFixtures.createProject(id = "new", updatedAt = 3000L)
            repository.setProjects(listOf(oldProject, newProject))

            val updated = awaitItem()
            assertEquals("new", updated.first().id)
            assertEquals("old", updated.last().id)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
