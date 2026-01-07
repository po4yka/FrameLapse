package com.po4yka.framelapse.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.po4yka.framelapse.presentation.base.UiEffect
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
