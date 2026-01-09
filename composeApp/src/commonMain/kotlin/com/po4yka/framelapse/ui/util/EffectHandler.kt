package com.po4yka.framelapse.ui.util

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.po4yka.framelapse.presentation.base.UiEffect
import com.po4yka.framelapse.presentation.common.CommonEffect
import kotlinx.coroutines.flow.SharedFlow

/**
 * Composable that handles one-time UI effects from a ViewModel.
 *
 * @param effects The SharedFlow of effects to observe
 * @param handler The suspend handler function to process each effect
 */
@Composable
fun <E : UiEffect> HandleEffects(effects: SharedFlow<E>, handler: suspend (E) -> Unit) {
    LaunchedEffect(effects) {
        effects.collect { effect ->
            handler(effect)
        }
    }
}

/**
 * Handles common snackbar-related effects (ShowError, ShowSnackbar, ShowSuccess).
 *
 * This composable extracts the repetitive snackbar showing pattern from screens,
 * providing unified handling for common message effects.
 *
 * Usage example:
 * ```
 * val snackbarHostState = remember { SnackbarHostState() }
 *
 * HandleEffects(viewModel.effect) { effect ->
 *     when (effect) {
 *         is SomeEffect.ShowError -> handleCommonEffect(effect.toCommon(), snackbarHostState)
 *         is SomeEffect.ShowMessage -> handleCommonEffect(effect.toCommon(), snackbarHostState)
 *         // ... other effect handling
 *     }
 * }
 * ```
 *
 * @param effect The common effect to handle
 * @param snackbarHostState The snackbar host state to show messages
 * @return true if the effect was handled, false otherwise
 */
suspend fun handleCommonEffect(effect: CommonEffect, snackbarHostState: SnackbarHostState): Boolean = when (effect) {
    is CommonEffect.ShowError -> {
        snackbarHostState.showSnackbar(effect.message)
        true
    }
    is CommonEffect.ShowSnackbar -> {
        snackbarHostState.showSnackbar(effect.message)
        true
    }
    is CommonEffect.ShowSuccess -> {
        snackbarHostState.showSnackbar(effect.message)
        true
    }
    else -> false
}

/**
 * Extension function to show a snackbar for error effects.
 *
 * @param snackbarHostState The snackbar host state to show the error message
 */
suspend fun CommonEffect.ShowError.showIn(snackbarHostState: SnackbarHostState) {
    snackbarHostState.showSnackbar(message)
}

/**
 * Extension function to show a snackbar for informational messages.
 *
 * @param snackbarHostState The snackbar host state to show the message
 */
suspend fun CommonEffect.ShowSnackbar.showIn(snackbarHostState: SnackbarHostState) {
    snackbarHostState.showSnackbar(message)
}

/**
 * Extension function to show a snackbar for success messages.
 *
 * @param snackbarHostState The snackbar host state to show the success message
 */
suspend fun CommonEffect.ShowSuccess.showIn(snackbarHostState: SnackbarHostState) {
    snackbarHostState.showSnackbar(message)
}
