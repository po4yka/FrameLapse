package com.po4yka.framelapse.ui.navigation

/**
 * Navigation routes for the app.
 */
sealed interface Route {
    /**
     * Project list screen - the home screen.
     */
    data object ProjectList : Route

    /**
     * Capture screen for taking photos.
     */
    data class Capture(val projectId: String) : Route

    /**
     * Gallery screen for viewing and managing frames.
     */
    data class Gallery(val projectId: String) : Route

    /**
     * Export screen for configuring and exporting video.
     */
    data class Export(val projectId: String) : Route

    /**
     * Settings screen for app configuration.
     */
    data object Settings : Route
}
