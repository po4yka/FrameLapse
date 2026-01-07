package com.po4yka.framelapse.presentation.common

import com.po4yka.framelapse.presentation.base.UiEffect

/**
 * Common side effects shared across multiple ViewModels.
 */
sealed interface CommonEffect : UiEffect {
    /**
     * Navigate to a specific route.
     */
    data class NavigateTo(val route: String) : CommonEffect

    /**
     * Navigate back to the previous screen.
     */
    data object NavigateBack : CommonEffect

    /**
     * Show a snackbar message.
     */
    data class ShowSnackbar(val message: String) : CommonEffect

    /**
     * Show an error message.
     */
    data class ShowError(val message: String) : CommonEffect

    /**
     * Share a file via system share sheet.
     */
    data class ShareFile(val path: String) : CommonEffect
}
