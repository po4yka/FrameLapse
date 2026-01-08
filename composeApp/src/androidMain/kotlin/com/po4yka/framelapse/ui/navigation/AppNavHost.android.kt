package com.po4yka.framelapse.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.po4yka.framelapse.presentation.gallery.GalleryEvent
import com.po4yka.framelapse.presentation.gallery.GalleryViewModel
import com.po4yka.framelapse.presentation.projectlist.ProjectListEvent
import com.po4yka.framelapse.presentation.projectlist.ProjectListViewModel
import com.po4yka.framelapse.ui.navigation.dialogs.CreateProjectDialogContent
import com.po4yka.framelapse.ui.navigation.dialogs.DeleteFramesDialogContent
import com.po4yka.framelapse.ui.navigation.dialogs.DeleteProjectDialogContent
import com.po4yka.framelapse.ui.navigation.sheets.FrameFilterSheetContent
import com.po4yka.framelapse.ui.screens.capture.CaptureScreen
import com.po4yka.framelapse.ui.screens.export.ExportScreen
import com.po4yka.framelapse.ui.screens.gallery.GalleryScreen
import com.po4yka.framelapse.ui.screens.projectlist.ProjectListScreen
import com.po4yka.framelapse.ui.screens.settings.SettingsScreen
import org.koin.compose.viewmodel.koinViewModel

/**
 * Android implementation of AppNavHost using Navigation 3.
 */
@Composable
actual fun AppNavHost(modifier: Modifier) {
    val backStack = remember { listOf<Any>(ProjectListKey).toMutableStateList() }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { key ->
            when (key) {
                // ==================== Screen Entries ====================
                is ProjectListKey -> NavEntry(key) {
                    ProjectListScreen(
                        onNavigateToCapture = { projectId ->
                            backStack.add(CaptureKey(projectId))
                        },
                        onNavigateToGallery = { projectId ->
                            backStack.add(GalleryKey(projectId))
                        },
                        onNavigateToSettings = {
                            backStack.add(SettingsKey)
                        },
                        onShowCreateDialog = {
                            backStack.add(CreateProjectDialogKey())
                        },
                    )
                }

                is CaptureKey -> NavEntry(key) {
                    CaptureScreen(
                        projectId = key.projectId,
                        onNavigateToGallery = {
                            backStack.add(GalleryKey(key.projectId))
                        },
                        onNavigateBack = {
                            backStack.removeLastOrNull()
                        },
                    )
                }

                is GalleryKey -> NavEntry(key) {
                    GalleryScreen(
                        projectId = key.projectId,
                        onNavigateToCapture = {
                            backStack.add(CaptureKey(key.projectId))
                        },
                        onNavigateToExport = {
                            backStack.add(ExportKey(key.projectId))
                        },
                        onNavigateBack = {
                            backStack.removeLastOrNull()
                        },
                        onShowDeleteDialog = { count ->
                            backStack.add(DeleteFramesDialogKey(count))
                        },
                        onShowFilterSheet = {
                            backStack.add(FrameFilterSheetKey(key.projectId))
                        },
                    )
                }

                is ExportKey -> NavEntry(key) {
                    ExportScreen(
                        projectId = key.projectId,
                        onNavigateBack = {
                            backStack.removeLastOrNull()
                        },
                    )
                }

                is SettingsKey -> NavEntry(key) {
                    SettingsScreen(
                        onNavigateBack = {
                            backStack.removeLastOrNull()
                        },
                    )
                }

                // ==================== Dialog Entries ====================
                is CreateProjectDialogKey -> NavEntry(key) {
                    val viewModel = koinViewModel<ProjectListViewModel>()
                    CreateProjectDialogContent(
                        initialName = key.initialName,
                        onDismiss = {
                            backStack.removeLastOrNull()
                        },
                        onConfirm = { projectName ->
                            viewModel.onEvent(ProjectListEvent.UpdateNewProjectName(projectName))
                            viewModel.onEvent(ProjectListEvent.CreateProject)
                            backStack.removeLastOrNull()
                        },
                    )
                }

                is DeleteFramesDialogKey -> NavEntry(key) {
                    val viewModel = koinViewModel<GalleryViewModel>()
                    DeleteFramesDialogContent(
                        count = key.count,
                        onDismiss = {
                            backStack.removeLastOrNull()
                        },
                        onConfirm = {
                            viewModel.onEvent(GalleryEvent.ConfirmDeleteSelected)
                            backStack.removeLastOrNull()
                        },
                    )
                }

                is DeleteProjectDialogKey -> NavEntry(key) {
                    val viewModel = koinViewModel<ProjectListViewModel>()
                    DeleteProjectDialogContent(
                        projectName = key.projectName,
                        onDismiss = {
                            backStack.removeLastOrNull()
                        },
                        onConfirm = {
                            viewModel.onEvent(ProjectListEvent.DeleteProject(key.projectId))
                            backStack.removeLastOrNull()
                        },
                    )
                }

                // ==================== Bottom Sheet Entries ====================
                is FrameFilterSheetKey -> NavEntry(key) {
                    FrameFilterSheetContent(
                        projectId = key.projectId,
                        onDismiss = {
                            backStack.removeLastOrNull()
                        },
                        onApply = { _ ->
                            backStack.removeLastOrNull()
                        },
                    )
                }

                else -> error("Unknown navigation key: $key")
            }
        },
    )
}
