package com.po4yka.framelapse.ui.screens.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.po4yka.framelapse.domain.entity.GlobalStatistics
import com.po4yka.framelapse.domain.entity.ProjectStatistics
import com.po4yka.framelapse.presentation.statistics.StatisticsEffect
import com.po4yka.framelapse.presentation.statistics.StatisticsEvent
import com.po4yka.framelapse.presentation.statistics.StatisticsMode
import com.po4yka.framelapse.presentation.statistics.StatisticsState
import com.po4yka.framelapse.presentation.statistics.StatisticsViewModel
import com.po4yka.framelapse.ui.components.FrameLapseTopBar
import com.po4yka.framelapse.ui.components.LoadingIndicator
import com.po4yka.framelapse.ui.components.SettingsDropdown
import com.po4yka.framelapse.ui.components.statistics.ContentTypeBreakdownCard
import com.po4yka.framelapse.ui.components.statistics.StatCard
import com.po4yka.framelapse.ui.components.statistics.StreakCard
import com.po4yka.framelapse.ui.components.statistics.WeeklyActivityCard
import com.po4yka.framelapse.ui.util.HandleEffects
import org.koin.compose.viewmodel.koinViewModel

private val CONTENT_PADDING = 16.dp
private val CARD_SPACING = 12.dp

/**
 * Statistics screen displaying project or global statistics with streak tracking.
 */
@Composable
fun StatisticsScreen(
    projectId: String?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = koinViewModel<StatisticsViewModel>(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleEffects(viewModel.effect) { effect ->
        when (effect) {
            is StatisticsEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    LaunchedEffect(projectId) {
        viewModel.onEvent(StatisticsEvent.Initialize(projectId))
    }

    StatisticsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
private fun StatisticsContent(
    state: StatisticsState,
    snackbarHostState: SnackbarHostState,
    onEvent: (StatisticsEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FrameLapseTopBar(
                title = "Statistics",
                onBackClick = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Mode toggle tabs
            StatisticsModeToggle(
                currentMode = state.mode,
                onModeChange = { onEvent(StatisticsEvent.SwitchMode(it)) },
            )

            // Project selector (only in project mode)
            if (state.mode == StatisticsMode.PROJECT && state.projects.isNotEmpty()) {
                SettingsDropdown(
                    title = "Select Project",
                    selectedValue = state.projects.find { it.id == state.selectedProjectId }
                        ?: state.projects.first(),
                    options = state.projects,
                    onSelect = { onEvent(StatisticsEvent.SelectProject(it.id)) },
                    valueLabel = { it.name },
                    modifier = Modifier.padding(horizontal = CONTENT_PADDING),
                )
                Spacer(modifier = Modifier.height(CARD_SPACING))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Content based on mode and loading state
            when {
                state.isLoading -> LoadingIndicator()
                state.mode == StatisticsMode.PROJECT && state.projectStats != null -> {
                    ProjectStatsContent(
                        stats = state.projectStats,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    )
                }
                state.mode == StatisticsMode.GLOBAL && state.globalStats != null -> {
                    GlobalStatsContent(
                        stats = state.globalStats,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    )
                }
                state.error != null -> {
                    EmptyStatsContent(message = state.error)
                }
                state.mode == StatisticsMode.PROJECT && state.projects.isEmpty() -> {
                    EmptyStatsContent(message = "No projects yet. Create a project to see statistics.")
                }
                else -> {
                    EmptyStatsContent(message = "No statistics available.")
                }
            }
        }
    }
}

@Composable
private fun StatisticsModeToggle(
    currentMode: StatisticsMode,
    onModeChange: (StatisticsMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = currentMode.ordinal,
        modifier = modifier,
    ) {
        Tab(
            selected = currentMode == StatisticsMode.PROJECT,
            onClick = { onModeChange(StatisticsMode.PROJECT) },
            text = { Text("Project") },
        )
        Tab(
            selected = currentMode == StatisticsMode.GLOBAL,
            onClick = { onModeChange(StatisticsMode.GLOBAL) },
            text = { Text("Global") },
        )
    }
}

@Composable
private fun ProjectStatsContent(
    stats: ProjectStatistics,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(CONTENT_PADDING),
        verticalArrangement = Arrangement.spacedBy(CARD_SPACING),
    ) {
        // Frame count and date range
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        ) {
            StatCard(
                label = "Total Frames",
                value = stats.totalFrames.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Date Range",
                value = formatDateRange(stats.firstCaptureDate, stats.lastCaptureDate),
                modifier = Modifier.weight(1f),
            )
        }

        // Quality metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        ) {
            StatCard(
                label = "Avg Confidence",
                value = formatPercentage(stats.averageConfidence),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Alignment Success",
                value = formatPercentage(stats.alignmentSuccessRate),
                modifier = Modifier.weight(1f),
            )
        }

        // Streak card
        StreakCard(
            currentStreak = stats.currentDailyStreak,
            bestStreak = stats.bestDailyStreak,
            label = "Daily Streak",
            modifier = Modifier.fillMaxWidth(),
        )

        // Weekly activity
        WeeklyActivityCard(
            weeklyCounts = stats.weeklyCaptureCounts.map { it.captureCount },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GlobalStatsContent(
    stats: GlobalStatistics,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(CONTENT_PADDING),
        verticalArrangement = Arrangement.spacedBy(CARD_SPACING),
    ) {
        // Overview counts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        ) {
            StatCard(
                label = "Total Projects",
                value = stats.totalProjects.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Total Frames",
                value = stats.totalFrames.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        // Global streak
        StreakCard(
            currentStreak = stats.currentDailyStreak,
            bestStreak = stats.bestDailyStreak,
            label = "Global Streak",
            modifier = Modifier.fillMaxWidth(),
        )

        // Most active project
        stats.mostActiveProject?.let { project ->
            StatCard(
                label = "Most Active Project",
                value = project.projectName,
                subtitle = "${project.frameCount} frames",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Projects by type
        ContentTypeBreakdownCard(
            breakdown = stats.projectsByContentType,
            modifier = Modifier.fillMaxWidth(),
        )

        // Weekly activity
        WeeklyActivityCard(
            weeklyCounts = stats.weeklyCaptureCounts.map { it.captureCount },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyStatsContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(CONTENT_PADDING),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Formats a date range from timestamps.
 */
private fun formatDateRange(start: Long?, end: Long?): String {
    if (start == null || end == null) return "-"

    val startDate = formatDate(start)
    val endDate = formatDate(end)

    return if (startDate == endDate) {
        startDate
    } else {
        "$startDate - $endDate"
    }
}

/**
 * Formats a timestamp to a simple date string (MM/DD format).
 */
private fun formatDate(timestamp: Long): String {
    val millisPerDay = 24 * 60 * 60 * 1000L
    val daysSinceEpoch = timestamp / millisPerDay

    // Simple calculation - note: this is approximate and doesn't handle all edge cases
    // For a production app, use a proper date library
    val year = 1970 + (daysSinceEpoch / 365).toInt()
    val dayOfYear = (daysSinceEpoch % 365).toInt()

    // Approximate month/day calculation
    val daysInMonths = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var remainingDays = dayOfYear
    var month = 1
    for (i in daysInMonths.indices) {
        if (remainingDays < daysInMonths[i]) {
            break
        }
        remainingDays -= daysInMonths[i]
        month++
    }
    val day = remainingDays + 1

    return "${month.toString().padStart(2, '0')}/${day.toString().padStart(2, '0')}"
}

/**
 * Formats a percentage value.
 */
private fun formatPercentage(value: Float?): String {
    if (value == null) return "-"
    return "${value.toInt()}%"
}
