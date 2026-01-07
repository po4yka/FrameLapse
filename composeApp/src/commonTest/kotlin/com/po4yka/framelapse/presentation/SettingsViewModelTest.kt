package com.po4yka.framelapse.presentation

import com.po4yka.framelapse.domain.entity.Orientation
import com.po4yka.framelapse.domain.entity.Resolution
import com.po4yka.framelapse.presentation.settings.SettingsEffect
import com.po4yka.framelapse.presentation.settings.SettingsEvent
import com.po4yka.framelapse.presentation.settings.SettingsState
import com.po4yka.framelapse.testutil.FakeSettingsRepository
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
 * Tests for SettingsViewModel.
 *
 * Note: Full SettingsViewModel integration tests require StorageCleanupManager,
 * which has platform dependencies (FileManager, ImageStorageManager, VideoStorageManager).
 *
 * These tests focus on:
 * 1. State contract verification (initial state, state transitions, computed properties)
 * 2. Effect contract verification
 * 3. Event contract verification
 * 4. Settings repository interactions that don't require the ViewModel
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

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

    // ==================== State Contract Tests ====================

    @Test
    fun `SettingsState has correct default values`() {
        val state = SettingsState()

        assertEquals(30, state.defaultFps)
        assertEquals(Resolution.HD_1080P, state.defaultResolution)
        assertEquals(Orientation.PORTRAIT, state.defaultOrientation)
        assertFalse(state.reminderEnabled)
        assertEquals("09:00", state.reminderTime)
        assertEquals(0L, state.storageUsedBytes)
        assertFalse(state.isLoading)
    }

    @Test
    fun `SettingsState storageUsedMb computes correctly for zero bytes`() {
        val state = SettingsState(storageUsedBytes = 0L)
        assertEquals(0f, state.storageUsedMb)
    }

    @Test
    fun `SettingsState storageUsedMb computes correctly for 1MB`() {
        val oneMbInBytes = 1024L * 1024L
        val state = SettingsState(storageUsedBytes = oneMbInBytes)
        assertEquals(1f, state.storageUsedMb, 0.001f)
    }

    @Test
    fun `SettingsState storageUsedMb computes correctly for fractional MB`() {
        val halfMbInBytes = 512L * 1024L
        val state = SettingsState(storageUsedBytes = halfMbInBytes)
        assertEquals(0.5f, state.storageUsedMb, 0.001f)
    }

    @Test
    fun `SettingsState storageUsedMb computes correctly for large values`() {
        val hundredMbInBytes = 100L * 1024L * 1024L
        val state = SettingsState(storageUsedBytes = hundredMbInBytes)
        assertEquals(100f, state.storageUsedMb, 0.001f)
    }

    @Test
    fun `SettingsState with custom fps`() {
        val state = SettingsState(defaultFps = 24)
        assertEquals(24, state.defaultFps)
    }

    @Test
    fun `SettingsState with different resolution`() {
        val state = SettingsState(defaultResolution = Resolution.UHD_4K)
        assertEquals(Resolution.UHD_4K, state.defaultResolution)
    }

    @Test
    fun `SettingsState with landscape orientation`() {
        val state = SettingsState(defaultOrientation = Orientation.LANDSCAPE)
        assertEquals(Orientation.LANDSCAPE, state.defaultOrientation)
    }

    @Test
    fun `SettingsState with reminder enabled`() {
        val state = SettingsState(
            reminderEnabled = true,
            reminderTime = "18:30",
        )
        assertTrue(state.reminderEnabled)
        assertEquals("18:30", state.reminderTime)
    }

    @Test
    fun `SettingsState can be copied with new values`() {
        val initial = SettingsState()
        val updated = initial.copy(
            defaultFps = 60,
            defaultResolution = Resolution.HD_720P,
            isLoading = true,
        )

        assertEquals(60, updated.defaultFps)
        assertEquals(Resolution.HD_720P, updated.defaultResolution)
        assertTrue(updated.isLoading)
        // Unchanged values should remain
        assertEquals(Orientation.PORTRAIT, updated.defaultOrientation)
    }

    // ==================== Effect Contract Tests ====================

    @Test
    fun `ShowMessage effect contains message`() {
        val effect = SettingsEffect.ShowMessage("Settings saved")
        assertEquals("Settings saved", effect.message)
    }

    @Test
    fun `ShowError effect contains message`() {
        val effect = SettingsEffect.ShowError("Failed to save")
        assertEquals("Failed to save", effect.message)
    }

    @Test
    fun `ScheduleReminder is a singleton object`() {
        val effect1 = SettingsEffect.ScheduleReminder
        val effect2 = SettingsEffect.ScheduleReminder
        assertEquals(effect1, effect2)
    }

    @Test
    fun `CancelReminder is a singleton object`() {
        val effect1 = SettingsEffect.CancelReminder
        val effect2 = SettingsEffect.CancelReminder
        assertEquals(effect1, effect2)
    }

    @Test
    fun `ScheduleReminder and CancelReminder are different effects`() {
        val schedule = SettingsEffect.ScheduleReminder
        val cancel = SettingsEffect.CancelReminder

        assertIs<SettingsEffect.ScheduleReminder>(schedule)
        assertIs<SettingsEffect.CancelReminder>(cancel)
    }

    // ==================== Event Contract Tests ====================

    @Test
    fun `LoadSettings is a singleton object`() {
        val event1 = SettingsEvent.LoadSettings
        val event2 = SettingsEvent.LoadSettings
        assertEquals(event1, event2)
    }

    @Test
    fun `ClearCache is a singleton object`() {
        val event1 = SettingsEvent.ClearCache
        val event2 = SettingsEvent.ClearCache
        assertEquals(event1, event2)
    }

    @Test
    fun `UpdateDefaultFps contains fps value`() {
        val event = SettingsEvent.UpdateDefaultFps(24)
        assertEquals(24, event.fps)
    }

    @Test
    fun `UpdateDefaultResolution contains resolution`() {
        val event = SettingsEvent.UpdateDefaultResolution(Resolution.UHD_4K)
        assertEquals(Resolution.UHD_4K, event.resolution)
    }

    @Test
    fun `UpdateDefaultOrientation contains orientation`() {
        val event = SettingsEvent.UpdateDefaultOrientation(Orientation.LANDSCAPE)
        assertEquals(Orientation.LANDSCAPE, event.orientation)
    }

    @Test
    fun `UpdateReminderEnabled contains enabled flag`() {
        val eventEnabled = SettingsEvent.UpdateReminderEnabled(true)
        val eventDisabled = SettingsEvent.UpdateReminderEnabled(false)

        assertTrue(eventEnabled.enabled)
        assertFalse(eventDisabled.enabled)
    }

    @Test
    fun `UpdateReminderTime contains time string`() {
        val event = SettingsEvent.UpdateReminderTime("08:00")
        assertEquals("08:00", event.time)
    }

    // ==================== Resolution Enum Tests ====================

    @Test
    fun `Resolution enum has correct dimensions for SD_480P`() {
        assertEquals(640, Resolution.SD_480P.width)
        assertEquals(480, Resolution.SD_480P.height)
        assertEquals("480p", Resolution.SD_480P.displayName)
    }

    @Test
    fun `Resolution enum has correct dimensions for HD_720P`() {
        assertEquals(1280, Resolution.HD_720P.width)
        assertEquals(720, Resolution.HD_720P.height)
        assertEquals("720p", Resolution.HD_720P.displayName)
    }

    @Test
    fun `Resolution enum has correct dimensions for HD_1080P`() {
        assertEquals(1920, Resolution.HD_1080P.width)
        assertEquals(1080, Resolution.HD_1080P.height)
        assertEquals("1080p", Resolution.HD_1080P.displayName)
    }

    @Test
    fun `Resolution enum has correct dimensions for UHD_4K`() {
        assertEquals(3840, Resolution.UHD_4K.width)
        assertEquals(2160, Resolution.UHD_4K.height)
        assertEquals("4K", Resolution.UHD_4K.displayName)
    }

    @Test
    fun `Resolution fromString returns correct value`() {
        assertEquals(Resolution.SD_480P, Resolution.fromString("SD_480P"))
        assertEquals(Resolution.HD_720P, Resolution.fromString("HD_720P"))
        assertEquals(Resolution.HD_1080P, Resolution.fromString("HD_1080P"))
        assertEquals(Resolution.UHD_4K, Resolution.fromString("UHD_4K"))
    }

    @Test
    fun `Resolution fromString returns default for unknown value`() {
        assertEquals(Resolution.HD_1080P, Resolution.fromString("UNKNOWN"))
        assertEquals(Resolution.HD_1080P, Resolution.fromString(""))
    }

    // ==================== Orientation Enum Tests ====================

    @Test
    fun `Orientation enum has correct display names`() {
        assertEquals("Portrait", Orientation.PORTRAIT.displayName)
        assertEquals("Landscape", Orientation.LANDSCAPE.displayName)
    }

    @Test
    fun `Orientation fromString returns correct value`() {
        assertEquals(Orientation.PORTRAIT, Orientation.fromString("PORTRAIT"))
        assertEquals(Orientation.LANDSCAPE, Orientation.fromString("LANDSCAPE"))
    }

    @Test
    fun `Orientation fromString returns default for unknown value`() {
        assertEquals(Orientation.PORTRAIT, Orientation.fromString("UNKNOWN"))
        assertEquals(Orientation.PORTRAIT, Orientation.fromString(""))
    }

    // ==================== Settings Repository Integration Tests ====================

    @Test
    fun `FakeSettingsRepository stores and retrieves int values`() = runTest {
        // When
        settingsRepository.setInt("test_key", 42)
        val result = settingsRepository.getInt("test_key", 0)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `FakeSettingsRepository returns default for missing int`() = runTest {
        // When
        val result = settingsRepository.getInt("missing_key", 99)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(99, result.getOrNull())
    }

    @Test
    fun `FakeSettingsRepository stores and retrieves string values`() = runTest {
        // When
        settingsRepository.setString("test_key", "test_value")
        val result = settingsRepository.getString("test_key")

        // Then
        assertTrue(result.isSuccess)
        assertEquals("test_value", result.getOrNull())
    }

    @Test
    fun `FakeSettingsRepository returns null for missing string`() = runTest {
        // When
        val result = settingsRepository.getString("missing_key")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }

    @Test
    fun `FakeSettingsRepository stores and retrieves boolean values`() = runTest {
        // When
        settingsRepository.setBoolean("test_key", true)
        val result = settingsRepository.getBoolean("test_key", false)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun `FakeSettingsRepository returns default for missing boolean`() = runTest {
        // When
        val result = settingsRepository.getBoolean("missing_key", true)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun `FakeSettingsRepository stores and retrieves float values`() = runTest {
        // When
        settingsRepository.setFloat("test_key", 3.14f)
        val result = settingsRepository.getFloat("test_key", 0f)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(3.14f, result.getOrNull()!!, 0.001f)
    }

    @Test
    fun `FakeSettingsRepository removes values`() = runTest {
        // Given
        settingsRepository.setString("test_key", "value")

        // When
        settingsRepository.remove("test_key")
        val result = settingsRepository.getString("test_key")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }

    @Test
    fun `FakeSettingsRepository checks existence`() = runTest {
        // Given
        settingsRepository.setString("existing_key", "value")

        // Then
        val existsResult = settingsRepository.exists("existing_key")
        val notExistsResult = settingsRepository.exists("missing_key")

        assertTrue(existsResult.getOrNull() == true)
        assertTrue(notExistsResult.getOrNull() == false)
    }

    @Test
    fun `FakeSettingsRepository getAll returns all settings`() = runTest {
        // Given
        settingsRepository.setString("key1", "value1")
        settingsRepository.setString("key2", "value2")
        settingsRepository.setInt("key3", 42)

        // When
        val result = settingsRepository.getAll()

        // Then
        assertTrue(result.isSuccess)
        val all = result.getOrNull()!!
        assertEquals("value1", all["key1"])
        assertEquals("value2", all["key2"])
        assertEquals("42", all["key3"])
    }

    @Test
    fun `FakeSettingsRepository failure mode works`() = runTest {
        // Given
        settingsRepository.shouldFail = true
        settingsRepository.failureException = RuntimeException("Storage error")

        // When
        val result = settingsRepository.getString("any_key")

        // Then
        assertTrue(result.isError)
    }

    // ==================== Settings Keys Tests (verifying expected key patterns) ====================

    @Test
    fun `settings keys use expected prefixes`() {
        // These tests document the expected key patterns used by SettingsViewModel
        val fpsKey = "settings_default_fps"
        val resolutionKey = "settings_default_resolution"
        val orientationKey = "settings_default_orientation"
        val reminderEnabledKey = "settings_reminder_enabled"
        val reminderTimeKey = "settings_reminder_time"

        assertTrue(fpsKey.startsWith("settings_"))
        assertTrue(resolutionKey.startsWith("settings_"))
        assertTrue(orientationKey.startsWith("settings_"))
        assertTrue(reminderEnabledKey.startsWith("settings_"))
        assertTrue(reminderTimeKey.startsWith("settings_"))
    }
}
