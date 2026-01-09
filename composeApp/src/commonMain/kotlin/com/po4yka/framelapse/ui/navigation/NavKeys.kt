package com.po4yka.framelapse.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Navigation keys for Navigation 3 (shared across platforms).
 * All destinations are defined as @Serializable data classes/objects implementing NavKey.
 */

// ==================== Screen NavKeys ====================

/**
 * Project list screen - the home screen.
 */
@Serializable
data object ProjectListKey : NavKey

/**
 * Capture screen for taking photos.
 */
@Serializable
data class CaptureKey(val projectId: String) : NavKey

/**
 * Gallery screen for viewing and managing frames.
 */
@Serializable
data class GalleryKey(val projectId: String) : NavKey

/**
 * Export screen for configuring and exporting video.
 */
@Serializable
data class ExportKey(val projectId: String) : NavKey

/**
 * Settings screen for app configuration.
 */
@Serializable
data object SettingsKey : NavKey

/**
 * Manual adjustment screen for refining stabilization landmarks.
 */
@Serializable
data class ManualAdjustmentKey(val frameId: String, val projectId: String) : NavKey

/**
 * Calibration screen for setting up face alignment reference.
 */
@Serializable
data class CalibrationKey(val projectId: String) : NavKey

/**
 * Statistics screen for viewing project/global stats and streak tracking.
 * @param projectId Optional project ID to open in project mode with that project selected.
 */
@Serializable
data class StatisticsKey(val projectId: String? = null) : NavKey

// ==================== Dialog NavKeys ====================

/**
 * Create project dialog.
 */
@Serializable
data class CreateProjectDialogKey(val initialName: String = "") : NavKey

/**
 * Delete confirmation dialog for frames.
 */
@Serializable
data class DeleteFramesDialogKey(val count: Int) : NavKey

/**
 * Delete confirmation dialog for project.
 */
@Serializable
data class DeleteProjectDialogKey(val projectId: String, val projectName: String) : NavKey

// ==================== Bottom Sheet NavKeys ====================

/**
 * Frame filter bottom sheet.
 */
@Serializable
data class FrameFilterSheetKey(val projectId: String) : NavKey

/**
 * Export settings bottom sheet.
 */
@Serializable
data class ExportSettingsSheetKey(val projectId: String) : NavKey

/**
 * Saved state configuration with polymorphic serialization.
 * Required for state restoration on non-JVM platforms (iOS).
 */
val navKeySavedStateConfig: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(ProjectListKey::class, ProjectListKey.serializer())
            subclass(CaptureKey::class, CaptureKey.serializer())
            subclass(GalleryKey::class, GalleryKey.serializer())
            subclass(ExportKey::class, ExportKey.serializer())
            subclass(SettingsKey::class, SettingsKey.serializer())
            subclass(ManualAdjustmentKey::class, ManualAdjustmentKey.serializer())
            subclass(CalibrationKey::class, CalibrationKey.serializer())
            subclass(StatisticsKey::class, StatisticsKey.serializer())
            subclass(CreateProjectDialogKey::class, CreateProjectDialogKey.serializer())
            subclass(DeleteFramesDialogKey::class, DeleteFramesDialogKey.serializer())
            subclass(DeleteProjectDialogKey::class, DeleteProjectDialogKey.serializer())
            subclass(FrameFilterSheetKey::class, FrameFilterSheetKey.serializer())
            subclass(ExportSettingsSheetKey::class, ExportSettingsSheetKey.serializer())
        }
    }
}
