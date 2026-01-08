package com.po4yka.framelapse.ui.screens.projectlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.po4yka.framelapse.presentation.projectlist.ProjectListEffect
import com.po4yka.framelapse.presentation.projectlist.ProjectListEvent
import com.po4yka.framelapse.presentation.projectlist.ProjectListState
import com.po4yka.framelapse.presentation.projectlist.ProjectListViewModel
import com.po4yka.framelapse.ui.components.EmptyState
import com.po4yka.framelapse.ui.components.LoadingIndicator
import com.po4yka.framelapse.ui.components.ProjectCard
import com.po4yka.framelapse.ui.util.HandleEffects
import framelapse.composeapp.generated.resources.Res
import framelapse.composeapp.generated.resources.app_name
import framelapse.composeapp.generated.resources.nav_settings
import framelapse.composeapp.generated.resources.projects_create
import framelapse.composeapp.generated.resources.projects_empty_description
import framelapse.composeapp.generated.resources.projects_empty_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val CONTENT_PADDING = 16.dp
private val ITEM_SPACING = 12.dp

/**
 * Project list screen - the home screen of the app.
 */
@Composable
fun ProjectListScreen(
    onNavigateToCapture: (String) -> Unit,
    onNavigateToGallery: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowCreateDialog: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProjectListViewModel = koinViewModel<ProjectListViewModel>(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleEffects(viewModel.effect) { effect ->
        when (effect) {
            is ProjectListEffect.NavigateToCapture -> onNavigateToCapture(effect.projectId)
            is ProjectListEffect.NavigateToGallery -> onNavigateToGallery(effect.projectId)
            is ProjectListEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onEvent(ProjectListEvent.LoadProjects)
    }

    ProjectListContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onNavigateToSettings = onNavigateToSettings,
        onShowCreateDialog = onShowCreateDialog,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectListContent(
    state: ProjectListState,
    snackbarHostState: SnackbarHostState,
    onEvent: (ProjectListEvent) -> Unit,
    onNavigateToSettings: () -> Unit,
    onShowCreateDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(Res.string.nav_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onShowCreateDialog,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.projects_create),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        when {
            state.isLoading -> {
                LoadingIndicator(modifier = Modifier.padding(paddingValues))
            }

            state.projects.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.CameraAlt,
                    title = stringResource(Res.string.projects_empty_title),
                    description = stringResource(Res.string.projects_empty_description),
                    actionLabel = stringResource(Res.string.projects_create),
                    onAction = onShowCreateDialog,
                    modifier = Modifier.padding(paddingValues),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(CONTENT_PADDING),
                    verticalArrangement = Arrangement.spacedBy(ITEM_SPACING),
                ) {
                    items(
                        items = state.projectsWithDetails,
                        key = { it.project.id },
                    ) { projectWithDetails ->
                        ProjectCard(
                            project = projectWithDetails.project,
                            frameCount = projectWithDetails.frameCount,
                            thumbnailPath = projectWithDetails.thumbnailPath,
                            onClick = { onEvent(ProjectListEvent.SelectProject(projectWithDetails.project.id)) },
                            onLongClick = { onEvent(ProjectListEvent.DeleteProject(projectWithDetails.project.id)) },
                        )
                    }
                }
            }
        }
    }
}
