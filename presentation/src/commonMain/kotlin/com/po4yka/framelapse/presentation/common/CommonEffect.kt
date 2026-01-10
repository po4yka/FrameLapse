package com.po4yka.framelapse.presentation.common

import com.po4yka.framelapse.presentation.base.UiEffect

/**
 * Common side effects shared across multiple ViewModels.
 * Individual screen effects can reference these common types to avoid duplication.
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
     * Show a snackbar message (for informational/success messages).
     */
    data class ShowSnackbar(val message: String) : CommonEffect

    /**
     * Show an error message in a snackbar.
     */
    data class ShowError(val message: String) : CommonEffect

    /**
     * Show a success message in a snackbar.
     */
    data class ShowSuccess(val message: String) : CommonEffect

    /**
     * Share a file via system share sheet.
     */
    data class ShareFile(val path: String) : CommonEffect
}
