package com.po4yka.framelapse.presentation

import app.cash.turbine.test
import com.po4yka.framelapse.presentation.main.MainEffect
import com.po4yka.framelapse.presentation.main.MainEvent
import com.po4yka.framelapse.presentation.main.MainState
import com.po4yka.framelapse.presentation.main.MainViewModel
import com.po4yka.framelapse.testutil.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
 * Tests for MainViewModel.
 *
 * MainViewModel handles app initialization and permission management.
 * It only depends on SettingsRepository, making it fully testable with fakes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var viewModel: MainViewModel
    private lateinit var settingsRepository: FakeSettingsRepository

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = FakeSettingsRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MainViewModel = MainViewModel(settingsRepository).also { viewModel = it }

    // ==================== Initial State Tests ====================

    @Test
    fun `initial state is not initialized`() = runTest {
        // Given
        val vm = createViewModel()

        // Then (before any events)
        vm.state.test {
            val state = awaitItem()
            assertFalse(state.isInitialized)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state has no permissions`() = runTest {
        // Given
        val vm = createViewModel()

        // Then
        vm.state.test {
            val state = awaitItem()
            assertFalse(state.hasPermissions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state is not loading`() = runTest {
        // Given
        val vm = createViewModel()

        // Then
        vm.state.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== Initialize Event Tests ====================

    @Test
    fun `Initialize event sets isInitialized to true`() = runTest {
        // Given
        val vm = createViewModel()

        // When
        vm.onEvent(MainEvent.Initialize)
        advanceUntilIdle()

        // Then
        vm.state.test {
            val state = awaitItem()
            assertTrue(state.isInitialized)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Initialize event sets isLoading to false after completion`() = runTest {
        // Given
        val vm = createViewModel()

        // When
        vm.onEvent(MainEvent.Initialize)
        advanceUntilIdle()

        // Then
        vm.state.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Initialize on first launch requests permissions`() = runTest {
        // Given: No onboarding complete flag (first launch)
        val vm = createViewModel()

        // When/Then
        vm.effect.test {
            vm.onEvent(MainEvent.Initialize)
            advanceUntilIdle()

            val effect = awaitItem()
            assertIs<MainEffect.RequestPermissions>(effect)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Initialize after onboarding navigates to project list`() = runTest {
        // Given: Onboarding already completed
        settingsRepository.setSettings(mapOf("onboarding_complete" to "true"))
        val vm = createViewModel()

        // When/Then
        vm.effect.test {
            vm.onEvent(MainEvent.Initialize)
            advanceUntilIdle()

            val effect = awaitItem()
            assertIs<MainEffect.NavigateTo>(effect)
            assertEquals(MainViewModel.ROUTE_PROJECT_LIST, effect.route)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Initialize handles repository error gracefully`() = runTest {
        // Given: Repository will fail
        settingsRepository.shouldFail = true
        val vm = createViewModel()

        // When/Then - should still complete initialization and request permissions (default behavior)
        vm.effect.test {
            vm.onEvent(MainEvent.Initialize)
            advanceUntilIdle()

            val effect = awaitItem()
            assertIs<MainEffect.RequestPermissions>(effect)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== PermissionsGranted Event Tests ====================

    @Test
    fun `PermissionsGranted event sets hasPermissions to true`() = runTest {
        // Given
        val vm = createViewModel()

        // When
        vm.onEvent(MainEvent.PermissionsGranted)
        advanceUntilIdle()

        // Then
        vm.state.test {
            val state = awaitItem()
            assertTrue(state.hasPermissions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `PermissionsGranted event saves onboarding complete`() = runTest {
        // Given
        val vm = createViewModel()

        // When
        vm.onEvent(MainEvent.PermissionsGranted)
        advanceUntilIdle()

        // Then
        val result = settingsRepository.getBoolean("onboarding_complete", false)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
    }

    @Test
    fun `PermissionsGranted event navigates to project list`() = runTest {
        // Given
        val vm = createViewModel()

        // When/Then
        vm.effect.test {
            vm.onEvent(MainEvent.PermissionsGranted)
            advanceUntilIdle()

            val effect = awaitItem()
            assertIs<MainEffect.NavigateTo>(effect)
            assertEquals(MainViewModel.ROUTE_PROJECT_LIST, effect.route)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== PermissionsDenied Event Tests ====================

    @Test
    fun `PermissionsDenied event sets hasPermissions to false`() = runTest {
        // Given
        val vm = createViewModel()

        // When
        vm.onEvent(MainEvent.PermissionsDenied)
        advanceUntilIdle()

        // Then
        vm.state.test {
            val state = awaitItem()
            assertFalse(state.hasPermissions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `PermissionsDenied event shows error message`() = runTest {
        // Given
        val vm = createViewModel()

        // When/Then
        vm.effect.test {
            vm.onEvent(MainEvent.PermissionsDenied)
            advanceUntilIdle()

            val effect = awaitItem()
            assertIs<MainEffect.ShowError>(effect)
            assertTrue(effect.message.contains("permission", ignoreCase = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `PermissionsDenied does not navigate`() = runTest {
        // Given
        val vm = createViewModel()

        // When/Then
        vm.effect.test {
            vm.onEvent(MainEvent.PermissionsDenied)
            advanceUntilIdle()

            val effect = awaitItem()
            // Should be ShowError, not NavigateTo
            assertIs<MainEffect.ShowError>(effect)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== State Contract Tests ====================

    @Test
    fun `MainState has correct default values`() {
        val state = MainState()

        assertFalse(state.isInitialized)
        assertFalse(state.hasPermissions)
        assertFalse(state.isLoading)
    }

    @Test
    fun `MainState can be copied with new values`() {
        val initial = MainState()
        val updated = initial.copy(
            isInitialized = true,
            hasPermissions = true,
            isLoading = false,
        )

        assertTrue(updated.isInitialized)
        assertTrue(updated.hasPermissions)
        assertFalse(updated.isLoading)
    }

    // ==================== Effect Contract Tests ====================

    @Test
    fun `RequestPermissions is a singleton object`() {
        val effect1 = MainEffect.RequestPermissions
        val effect2 = MainEffect.RequestPermissions
        assertEquals(effect1, effect2)
    }

    @Test
    fun `NavigateTo contains route`() {
        val effect = MainEffect.NavigateTo("some_route")
        assertEquals("some_route", effect.route)
    }

    @Test
    fun `ShowError contains message`() {
        val effect = MainEffect.ShowError("Error occurred")
        assertEquals("Error occurred", effect.message)
    }

    // ==================== Event Contract Tests ====================

    @Test
    fun `Initialize is a singleton object`() {
        val event1 = MainEvent.Initialize
        val event2 = MainEvent.Initialize
        assertEquals(event1, event2)
    }

    @Test
    fun `PermissionsGranted is a singleton object`() {
        val event1 = MainEvent.PermissionsGranted
        val event2 = MainEvent.PermissionsGranted
        assertEquals(event1, event2)
    }

    @Test
    fun `PermissionsDenied is a singleton object`() {
        val event1 = MainEvent.PermissionsDenied
        val event2 = MainEvent.PermissionsDenied
        assertEquals(event1, event2)
    }

    // ==================== Route Constants Tests ====================

    @Test
    fun `MainViewModel has correct route constants`() {
        assertEquals("project_list", MainViewModel.ROUTE_PROJECT_LIST)
        assertEquals("capture", MainViewModel.ROUTE_CAPTURE)
        assertEquals("gallery", MainViewModel.ROUTE_GALLERY)
        assertEquals("export", MainViewModel.ROUTE_EXPORT)
        assertEquals("settings", MainViewModel.ROUTE_SETTINGS)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `multiple Initialize events do not cause issues`() = runTest {
        // Given
        val vm = createViewModel()

        // When
        vm.onEvent(MainEvent.Initialize)
        vm.onEvent(MainEvent.Initialize)
        vm.onEvent(MainEvent.Initialize)
        advanceUntilIdle()

        // Then - should still be in valid state
        vm.state.test {
            val state = awaitItem()
            assertTrue(state.isInitialized)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `PermissionsGranted after PermissionsDenied updates state correctly`() = runTest {
        // Given
        val vm = createViewModel()
        vm.onEvent(MainEvent.PermissionsDenied)
        advanceUntilIdle()

        // When
        vm.onEvent(MainEvent.PermissionsGranted)
        advanceUntilIdle()

        // Then
        vm.state.test {
            val state = awaitItem()
            assertTrue(state.hasPermissions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state flow emits updates in order`() = runTest {
        // Given
        val vm = createViewModel()

        // When/Then
        vm.state.test {
            // Initial state
            val initial = awaitItem()
            assertFalse(initial.isInitialized)

            // Trigger initialization
            vm.onEvent(MainEvent.Initialize)
            advanceUntilIdle()

            // Should see loading state or directly initialized
            val next = awaitItem()
            assertTrue(next.isInitialized || next.isLoading)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
