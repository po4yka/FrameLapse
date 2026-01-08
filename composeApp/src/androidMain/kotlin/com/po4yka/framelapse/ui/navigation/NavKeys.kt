package com.po4yka.framelapse.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Navigation keys for Navigation 3 (Android-only).
 * All destinations are defined as @Serializable data classes/objects.
 */

// ==================== Screen NavKeys ====================

/**
 * Project list screen - the home screen.
 */
@Serializable
data object ProjectListKey

/**
 * Capture screen for taking photos.
 */
@Serializable
data class CaptureKey(val projectId: String)

/**
 * Gallery screen for viewing and managing frames.
 */
@Serializable
data class GalleryKey(val projectId: String)

/**
 * Export screen for configuring and exporting video.
 */
@Serializable
data class ExportKey(val projectId: String)

/**
 * Settings screen for app configuration.
 */
@Serializable
data object SettingsKey

// ==================== Dialog NavKeys ====================

/**
 * Create project dialog.
 */
@Serializable
data class CreateProjectDialogKey(val initialName: String = "")

/**
 * Delete confirmation dialog for frames.
 */
@Serializable
data class DeleteFramesDialogKey(val count: Int)

/**
 * Delete confirmation dialog for project.
 */
@Serializable
data class DeleteProjectDialogKey(val projectId: String, val projectName: String)

// ==================== Bottom Sheet NavKeys ====================

/**
 * Frame filter bottom sheet.
 */
@Serializable
data class FrameFilterSheetKey(val projectId: String)

/**
 * Export settings bottom sheet.
 */
@Serializable
data class ExportSettingsSheetKey(val projectId: String)
