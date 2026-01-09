package com.po4yka.framelapse.presentation.main

import com.po4yka.framelapse.presentation.base.UiEffect
import com.po4yka.framelapse.presentation.base.UiEvent
import com.po4yka.framelapse.presentation.base.UiState
import com.po4yka.framelapse.presentation.common.CommonEffect

/**
 * UI state for the main app screen.
 */
data class MainState(
    val isInitialized: Boolean = false,
    val hasPermissions: Boolean = false,
    val isLoading: Boolean = false,
) : UiState

/**
 * User events for the main app screen.
 */
sealed interface MainEvent : UiEvent {
    /**
     * Initialize the app.
     */
    data object Initialize : MainEvent

    /**
     * Permissions have been granted.
     */
    data object PermissionsGranted : MainEvent

    /**
     * Permissions have been denied.
     */
    data object PermissionsDenied : MainEvent
}

/**
 * One-time side effects for the main app screen.
 */
sealed interface MainEffect : UiEffect {
    /**
     * Request required permissions.
     */
    data object RequestPermissions : MainEffect

    /**
     * Navigate to a specific route. Delegates to [CommonEffect.NavigateTo].
     */
    data class NavigateTo(val route: String) : MainEffect {
        /** Convert to common effect for unified handling. */
        fun toCommon(): CommonEffect.NavigateTo = CommonEffect.NavigateTo(route)
    }

    /**
     * Show an error message. Delegates to [CommonEffect.ShowError].
     */
    data class ShowError(val message: String) : MainEffect {
        /** Convert to common effect for unified handling. */
        fun toCommon(): CommonEffect.ShowError = CommonEffect.ShowError(message)
    }
}
