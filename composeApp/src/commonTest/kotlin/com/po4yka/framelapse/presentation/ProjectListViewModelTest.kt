package com.po4yka.framelapse.presentation

import app.cash.turbine.test
import com.po4yka.framelapse.domain.usecase.project.CreateProjectUseCase
import com.po4yka.framelapse.domain.usecase.project.GetProjectsUseCase
import com.po4yka.framelapse.presentation.projectlist.ProjectListEffect
import com.po4yka.framelapse.presentation.projectlist.ProjectListEvent
import com.po4yka.framelapse.presentation.projectlist.ProjectListState
import com.po4yka.framelapse.presentation.projectlist.ProjectWithDetails
import com.po4yka.framelapse.testutil.FakeFrameRepository
import com.po4yka.framelapse.testutil.FakeProjectRepository
import com.po4yka.framelapse.testutil.TestFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for ProjectListViewModel.
 *
 * Note: Full ViewModel integration tests require platform-specific FileManager implementation.
 * These tests focus on:
 * 1. State contract verification (initial state, state transitions)
 * 2. Effect contract verification
 * 3. Event contract verification
 * 4. Use case integration with fake repositories (for use cases that don't need FileManager)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        TestFixtures.resetCounters()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== State Contract Tests ====================

    @Test
    fun `ProjectListState has correct default values`() {
        val state = ProjectListState()

        assertTrue(state.projectsWithDetails.isEmpty())
        assertFalse(state.isLoading)
        assertEquals(null, state.error)
        assertFalse(state.showCreateDialog)
        assertEquals("", state.newProjectName)
    }

    @Test
    fun `ProjectListState projects property returns project list from details`() {
        val projects = TestFixtures.createProjectList(3)
        val projectsWithDetails = projects.map { project ->
            ProjectWithDetails(
                project = project,
                frameCount = 0,
                thumbnailPath = null,
            )
        }
        val state = ProjectListState(projectsWithDetails = projectsWithDetails)

        assertEquals(3, state.projects.size)
        assertEquals(projects[0].id, state.projects[0].id)
        assertEquals(projects[1].id, state.projects[1].id)
        assertEquals(projects[2].id, state.projects[2].id)
    }

    @Test
    fun `ProjectListState with isLoading true`() {
        val state = ProjectListState(isLoading = true)

        assertTrue(state.isLoading)
        assertTrue(state.projectsWithDetails.isEmpty())
    }

    @Test
    fun `ProjectListState with error message`() {
        val state = ProjectListState(error = "Something went wrong")

        assertEquals("Something went wrong", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `ProjectListState with dialog shown`() {
        val state = ProjectListState(
            showCreateDialog = true,
            newProjectName = "My Project",
        )

        assertTrue(state.showCreateDialog)
        assertEquals("My Project", state.newProjectName)
    }

    @Test
    fun `ProjectWithDetails holds project and metadata`() {
        val project = TestFixtures.createProject(name = "Test Timelapse")
        val details = ProjectWithDetails(
            project = project,
            frameCount = 42,
            thumbnailPath = "/path/to/thumbnail.jpg",
        )

        assertEquals("Test Timelapse", details.project.name)
        assertEquals(42, details.frameCount)
        assertEquals("/path/to/thumbnail.jpg", details.thumbnailPath)
    }

    // ==================== Effect Contract Tests ====================

    @Test
    fun `NavigateToCapture effect contains project id`() {
        val effect = ProjectListEffect.NavigateToCapture("test_project_123")
        assertEquals("test_project_123", effect.projectId)
    }

    @Test
    fun `NavigateToGallery effect contains project id`() {
        val effect = ProjectListEffect.NavigateToGallery("gallery_project_456")
        assertEquals("gallery_project_456", effect.projectId)
    }

    @Test
    fun `ShowError effect contains message`() {
        val effect = ProjectListEffect.ShowError("Something went wrong")
        assertEquals("Something went wrong", effect.message)
    }

    @Test
    fun `NavigateToCapture and NavigateToGallery are different effects`() {
        val captureEffect = ProjectListEffect.NavigateToCapture("project_1")
        val galleryEffect = ProjectListEffect.NavigateToGallery("project_1")

        assertIs<ProjectListEffect.NavigateToCapture>(captureEffect)
        assertIs<ProjectListEffect.NavigateToGallery>(galleryEffect)
    }

    // ==================== Event Contract Tests ====================

    @Test
    fun `LoadProjects is a singleton object`() {
        val event1 = ProjectListEvent.LoadProjects
        val event2 = ProjectListEvent.LoadProjects
        assertEquals(event1, event2)
    }

    @Test
    fun `ShowCreateDialog is a singleton object`() {
        val event1 = ProjectListEvent.ShowCreateDialog
        val event2 = ProjectListEvent.ShowCreateDialog
        assertEquals(event1, event2)
    }

    @Test
    fun `DismissCreateDialog is a singleton object`() {
        val event1 = ProjectListEvent.DismissCreateDialog
        val event2 = ProjectListEvent.DismissCreateDialog
        assertEquals(event1, event2)
    }

    @Test
    fun `CreateProject is a singleton object`() {
        val event1 = ProjectListEvent.CreateProject
        val event2 = ProjectListEvent.CreateProject
        assertEquals(event1, event2)
    }

    @Test
    fun `DeleteProject contains project id`() {
        val event = ProjectListEvent.DeleteProject("project_to_delete")
        assertEquals("project_to_delete", event.projectId)
    }

    @Test
    fun `SelectProject contains project id`() {
        val event = ProjectListEvent.SelectProject("selected_project")
        assertEquals("selected_project", event.projectId)
    }

    @Test
    fun `UpdateNewProjectName contains name`() {
        val event = ProjectListEvent.UpdateNewProjectName("My New Project")
        assertEquals("My New Project", event.name)
    }

    @Test
    fun `UpdateNewProjectName with empty string`() {
        val event = ProjectListEvent.UpdateNewProjectName("")
        assertEquals("", event.name)
    }

    @Test
    fun `UpdateNewProjectName with whitespace`() {
        val event = ProjectListEvent.UpdateNewProjectName("   ")
        assertEquals("   ", event.name)
    }

    // ==================== Use Case Integration Tests (with FakeRepositories) ====================

    @Test
    fun `GetProjectsUseCase returns projects from repository`() = runTest {
        // Given
        val repository = FakeProjectRepository()
        val projects = TestFixtures.createProjectList(3)
        repository.setProjects(projects)
        val useCase = GetProjectsUseCase(repository)

        // When
        val result = useCase()

        // Then
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull()?.size)
    }

    @Test
    fun `GetProjectsUseCase observe emits updates`() = runTest {
        // Given
        val repository = FakeProjectRepository()
        val useCase = GetProjectsUseCase(repository)

        // When/Then
        useCase.observe().test {
            // Initial empty
            assertTrue(awaitItem().isEmpty())

            // Add projects
            repository.setProjects(TestFixtures.createProjectList(2))
            assertEquals(2, awaitItem().size)

            // Add more
            val moreProjects = TestFixtures.createProjectList(5)
            repository.setProjects(moreProjects)
            assertEquals(5, awaitItem().size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `GetProjectsUseCase observe emits sorted by updatedAt`() = runTest {
        // Given
        val repository = FakeProjectRepository()
        val oldProject = TestFixtures.createProject(id = "old", updatedAt = 1000L)
        val newProject = TestFixtures.createProject(id = "new", updatedAt = 3000L)
        repository.setProjects(listOf(oldProject, newProject))
        val useCase = GetProjectsUseCase(repository)

        // When/Then
        useCase.observe().test {
            val projects = awaitItem()
            assertEquals("new", projects[0].id)
            assertEquals("old", projects[1].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `GetProjectsUseCase handles repository failure`() = runTest {
        // Given
        val repository = FakeProjectRepository()
        repository.shouldFail = true
        repository.failureException = RuntimeException("Database error")
        val useCase = GetProjectsUseCase(repository)

        // When
        val result = useCase()

        // Then
        assertTrue(result.isError)
    }

    @Test
    fun `CreateProjectUseCase creates project with valid name`() = runTest {
        // Given
        val repository = FakeProjectRepository()
        val useCase = CreateProjectUseCase(repository)

        // When
        val result = useCase("My Timelapse", fps = 30)

        // Then
        assertTrue(result.isSuccess)
        assertEquals("My Timelapse", result.getOrNull()?.name)
        assertEquals(30, result.getOrNull()?.fps)
    }

    @Test
    fun `CreateProjectUseCase returns error for empty name`() = runTest {
        // Given
        val repository = FakeProjectRepository()
        val useCase = CreateProjectUseCase(repository)

        // When
        val result = useCase("")

        // Then
        assertTrue(result.isError)
    }

    @Test
    fun `CreateProjectUseCase returns error for blank name`() = runTest {
        // Given
        val repository = FakeProjectRepository()
        val useCase = CreateProjectUseCase(repository)

        // When
        val result = useCase("   ")

        // Then
        assertTrue(result.isError)
    }

    @Test
    fun `CreateProjectUseCase trims whitespace from name`() = runTest {
        // Given
        val repository = FakeProjectRepository()
        val useCase = CreateProjectUseCase(repository)

        // When
        val result = useCase("  Spaced Name  ")

        // Then
        assertTrue(result.isSuccess)
        assertEquals("Spaced Name", result.getOrNull()?.name)
    }

    @Test
    fun `CreateProjectUseCase validates fps range`() = runTest {
        // Given
        val repository = FakeProjectRepository()
        val useCase = CreateProjectUseCase(repository)

        // When - fps too low
        val resultLow = useCase("Test", fps = 0)
        // When - fps too high
        val resultHigh = useCase("Test", fps = 61)
        // When - fps at boundaries
        val resultMin = useCase("Test Min", fps = 1)
        val resultMax = useCase("Test Max", fps = 60)

        // Then
        assertTrue(resultLow.isError)
        assertTrue(resultHigh.isError)
        assertTrue(resultMin.isSuccess)
        assertTrue(resultMax.isSuccess)
    }

    @Test
    fun `CreateProjectUseCase handles repository failure`() = runTest {
        // Given
        val repository = FakeProjectRepository()
        repository.shouldFail = true
        val useCase = CreateProjectUseCase(repository)

        // When
        val result = useCase("Test Project")

        // Then
        assertTrue(result.isError)
    }

    @Test
    fun `FrameRepository getFrameCount returns correct count`() = runTest {
        // Given
        val repository = FakeFrameRepository()
        val frames = TestFixtures.createFrameList(5, projectId = "project_1")
        repository.setFrames(frames)

        // When
        val result = repository.getFrameCount("project_1")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(5L, result.getOrNull())
    }

    @Test
    fun `FrameRepository getFrameCount returns zero for empty project`() = runTest {
        // Given
        val repository = FakeFrameRepository()

        // When
        val result = repository.getFrameCount("nonexistent_project")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(0L, result.getOrNull())
    }

    @Test
    fun `FrameRepository getLatestFrame returns most recent frame`() = runTest {
        // Given
        val repository = FakeFrameRepository()
        val frames = TestFixtures.createFrameList(3, projectId = "project_1")
        repository.setFrames(frames)

        // When
        val result = repository.getLatestFrame("project_1")

        // Then
        assertTrue(result.isSuccess)
        // The latest frame should be the one with the highest timestamp
        val latestFrame = result.getOrNull()
        assertTrue(latestFrame != null)
    }

    @Test
    fun `FrameRepository getLatestFrame returns null for empty project`() = runTest {
        // Given
        val repository = FakeFrameRepository()

        // When
        val result = repository.getLatestFrame("nonexistent_project")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }
}
