package com.po4yka.framelapse.domain.usecase

import com.po4yka.framelapse.domain.entity.Orientation
import com.po4yka.framelapse.domain.entity.Resolution
import com.po4yka.framelapse.domain.usecase.project.UpdateProjectSettingsUseCase
import com.po4yka.framelapse.domain.util.Result
import com.po4yka.framelapse.testutil.FakeProjectRepository
import com.po4yka.framelapse.testutil.TestFixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class UpdateProjectSettingsUseCaseTest {

    private lateinit var useCase: UpdateProjectSettingsUseCase
    private lateinit var repository: FakeProjectRepository

    @BeforeTest
    fun setup() {
        repository = FakeProjectRepository()
        useCase = UpdateProjectSettingsUseCase(repository)
        TestFixtures.resetCounters()
    }

    private fun assertSuccess(result: Result<Unit>) {
        when (result) {
            is Result.Success -> Unit
            is Result.Error -> fail("Expected success but got error: ${result.message}")
            is Result.Loading -> fail("Expected success but got loading")
        }
    }

    private fun assertError(result: Result<*>): Result.Error {
        assertTrue(result is Result.Error, "Expected error but got: $result")
        return result
    }

    // ==================== Success Cases ====================

    @Test
    fun `invoke updates project name successfully`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id", name = "Original Name")
        repository.setProjects(listOf(project))
        val updatedProject = project.copy(name = "Updated Name")

        // When
        val result = useCase(updatedProject)

        // Then
        assertSuccess(result)
        val saved = repository.getProject("test_id")
        assertEquals("Updated Name", (saved as Result.Success).data.name)
    }

    @Test
    fun `invoke updates project fps successfully`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id", fps = 30)
        repository.setProjects(listOf(project))
        val updatedProject = project.copy(fps = 24)

        // When
        val result = useCase(updatedProject)

        // Then
        assertSuccess(result)
        val saved = repository.getProject("test_id")
        assertEquals(24, (saved as Result.Success).data.fps)
    }

    @Test
    fun `invoke updates project resolution successfully`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id", resolution = Resolution.HD_1080P)
        repository.setProjects(listOf(project))
        val updatedProject = project.copy(resolution = Resolution.UHD_4K)

        // When
        val result = useCase(updatedProject)

        // Then
        assertSuccess(result)
        val saved = repository.getProject("test_id")
        assertEquals(Resolution.UHD_4K, (saved as Result.Success).data.resolution)
    }

    @Test
    fun `invoke updates project orientation successfully`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id", orientation = Orientation.PORTRAIT)
        repository.setProjects(listOf(project))
        val updatedProject = project.copy(orientation = Orientation.LANDSCAPE)

        // When
        val result = useCase(updatedProject)

        // Then
        assertSuccess(result)
        val saved = repository.getProject("test_id")
        assertEquals(Orientation.LANDSCAPE, (saved as Result.Success).data.orientation)
    }

    @Test
    fun `invoke updates multiple settings at once`() = runTest {
        // Given
        val project = TestFixtures.createProject(
            id = "test_id",
            name = "Original",
            fps = 30,
            resolution = Resolution.HD_1080P,
        )
        repository.setProjects(listOf(project))
        val updatedProject = project.copy(
            name = "Updated",
            fps = 60,
            resolution = Resolution.UHD_4K,
        )

        // When
        val result = useCase(updatedProject)

        // Then
        assertSuccess(result)
        val saved = repository.getProject("test_id")
        val data = (saved as Result.Success).data
        assertEquals("Updated", data.name)
        assertEquals(60, data.fps)
        assertEquals(Resolution.UHD_4K, data.resolution)
    }

    @Test
    fun `invoke updates updatedAt timestamp`() = runTest {
        // Given
        val originalTime = 1000L
        val project = TestFixtures.createProject(id = "test_id", updatedAt = originalTime)
        repository.setProjects(listOf(project))

        // When
        val result = useCase(project.copy(name = "Updated Name"))

        // Then
        assertSuccess(result)
        val saved = repository.getProject("test_id")
        val data = (saved as Result.Success).data
        assertNotEquals(originalTime, data.updatedAt)
        assertTrue(data.updatedAt > originalTime)
    }

    @Test
    fun `invoke accepts fps at minimum boundary`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id", fps = 30)
        repository.setProjects(listOf(project))
        val updatedProject = project.copy(fps = 1)

        // When
        val result = useCase(updatedProject)

        // Then
        assertSuccess(result)
        val saved = repository.getProject("test_id")
        assertEquals(1, (saved as Result.Success).data.fps)
    }

    @Test
    fun `invoke accepts fps at maximum boundary`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id", fps = 30)
        repository.setProjects(listOf(project))
        val updatedProject = project.copy(fps = 60)

        // When
        val result = useCase(updatedProject)

        // Then
        assertSuccess(result)
        val saved = repository.getProject("test_id")
        assertEquals(60, (saved as Result.Success).data.fps)
    }

    // ==================== Validation Errors ====================

    @Test
    fun `invoke returns error for empty project id`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "")

        // When
        val result = useCase(project)

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("Project ID") == true)
        assertTrue(error.message?.contains("empty") == true)
    }

    @Test
    fun `invoke returns error for blank project id`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "   ")

        // When
        val result = useCase(project)

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("Project ID") == true)
        assertTrue(error.message?.contains("empty") == true)
    }

    @Test
    fun `invoke returns error for empty project name`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id", name = "")
        repository.setProjects(listOf(project.copy(name = "Valid Name")))

        // When
        val result = useCase(project)

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("name") == true)
        assertTrue(error.message?.contains("empty") == true)
    }

    @Test
    fun `invoke returns error for blank project name`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id", name = "   ")
        repository.setProjects(listOf(project.copy(name = "Valid Name")))

        // When
        val result = useCase(project)

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("name") == true)
        assertTrue(error.message?.contains("empty") == true)
    }

    @Test
    fun `invoke returns error for fps below minimum`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id", fps = 0)
        repository.setProjects(listOf(project.copy(fps = 30)))

        // When
        val result = useCase(project)

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("FPS") == true || error.message?.contains("1 and 60") == true)
    }

    @Test
    fun `invoke returns error for fps above maximum`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id", fps = 61)
        repository.setProjects(listOf(project.copy(fps = 30)))

        // When
        val result = useCase(project)

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("FPS") == true || error.message?.contains("1 and 60") == true)
    }

    @Test
    fun `invoke returns error for negative fps`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id", fps = -5)
        repository.setProjects(listOf(project.copy(fps = 30)))

        // When
        val result = useCase(project)

        // Then
        assertError(result)
    }

    // ==================== Not Found Cases ====================

    @Test
    fun `invoke returns error when project not found`() = runTest {
        // Given: Empty repository
        val project = TestFixtures.createProject(id = "nonexistent")

        // When
        val result = useCase(project)

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("not found") == true)
    }

    @Test
    fun `invoke returns error when updating wrong project id`() = runTest {
        // Given
        val existingProject = TestFixtures.createProject(id = "existing_id")
        repository.setProjects(listOf(existingProject))
        val wrongProject = TestFixtures.createProject(id = "wrong_id")

        // When
        val result = useCase(wrongProject)

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("not found") == true)
    }

    // ==================== Repository Error Cases ====================

    @Test
    fun `invoke returns error when repository fails on exists check`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id")
        repository.shouldFail = true
        repository.failureException = RuntimeException("Database error")

        // When
        val result = useCase(project)

        // Then
        val error = assertError(result)
        assertTrue(error.message?.contains("existence") == true)
    }

    @Test
    fun `invoke returns error when repository fails on update`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id")
        repository.setProjects(listOf(project))

        // When
        // First verify the project exists, then make repository fail
        repository.shouldFail = true
        repository.failureException = RuntimeException("Update failed")
        val result = useCase(project.copy(name = "New Name"))

        // Then
        assertError(result)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `invoke handles special characters in name`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id", name = "Original")
        repository.setProjects(listOf(project))
        val updatedProject = project.copy(name = "Project: \"Test\" (2024) - Special!")

        // When
        val result = useCase(updatedProject)

        // Then
        assertSuccess(result)
        val saved = repository.getProject("test_id")
        assertEquals("Project: \"Test\" (2024) - Special!", (saved as Result.Success).data.name)
    }

    @Test
    fun `invoke preserves createdAt timestamp`() = runTest {
        // Given
        val originalCreatedAt = 1704067200000L
        val project = TestFixtures.createProject(id = "test_id", createdAt = originalCreatedAt)
        repository.setProjects(listOf(project))

        // When
        val result = useCase(project.copy(name = "Updated"))

        // Then
        assertSuccess(result)
        val saved = repository.getProject("test_id")
        assertEquals(originalCreatedAt, (saved as Result.Success).data.createdAt)
    }

    @Test
    fun `invoke preserves thumbnailPath`() = runTest {
        // Given
        val project = TestFixtures.createProject(id = "test_id", thumbnailPath = "/path/to/thumb.jpg")
        repository.setProjects(listOf(project))

        // When
        val result = useCase(project.copy(name = "Updated"))

        // Then
        assertSuccess(result)
        val saved = repository.getProject("test_id")
        assertEquals("/path/to/thumb.jpg", (saved as Result.Success).data.thumbnailPath)
    }
}
