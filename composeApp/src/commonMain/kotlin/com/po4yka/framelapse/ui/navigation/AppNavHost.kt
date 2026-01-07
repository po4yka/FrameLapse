package com.po4yka.framelapse.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.po4yka.framelapse.ui.screens.capture.CaptureScreen
import com.po4yka.framelapse.ui.screens.export.ExportScreen
import com.po4yka.framelapse.ui.screens.gallery.GalleryScreen
import com.po4yka.framelapse.ui.screens.projectlist.ProjectListScreen
import com.po4yka.framelapse.ui.screens.settings.SettingsScreen

private const val SLIDE_OFFSET_DIVISOR = 3

/**
 * Main navigation host for the app.
 */
@Composable
fun AppNavHost(navController: AppNavController = remember { AppNavController() }) {
    val currentRoute by navController.currentRoute.collectAsState()

    AnimatedContent(
        targetState = currentRoute,
        transitionSpec = {
            (slideInHorizontally { it / SLIDE_OFFSET_DIVISOR } + fadeIn())
                .togetherWith(slideOutHorizontally { -it / SLIDE_OFFSET_DIVISOR } + fadeOut())
        },
        label = "navigation",
    ) { route ->
        when (route) {
            is Route.ProjectList -> ProjectListScreen(
                onNavigateToCapture = { projectId ->
                    navController.navigate(Route.Capture(projectId))
                },
                onNavigateToGallery = { projectId ->
                    navController.navigate(Route.Gallery(projectId))
                },
                onNavigateToSettings = {
                    navController.navigate(Route.Settings)
                },
            )

            is Route.Capture -> CaptureScreen(
                projectId = route.projectId,
                onNavigateToGallery = {
                    navController.navigate(Route.Gallery(route.projectId))
                },
                onNavigateBack = {
                    navController.navigateBack()
                },
            )

            is Route.Gallery -> GalleryScreen(
                projectId = route.projectId,
                onNavigateToCapture = {
                    navController.navigate(Route.Capture(route.projectId))
                },
                onNavigateToExport = {
                    navController.navigate(Route.Export(route.projectId))
                },
                onNavigateBack = {
                    navController.navigateBack()
                },
            )

            is Route.Export -> ExportScreen(
                projectId = route.projectId,
                onNavigateBack = {
                    navController.navigateBack()
                },
            )

            is Route.Settings -> SettingsScreen(
                onNavigateBack = {
                    navController.navigateBack()
                },
            )
        }
    }
}
