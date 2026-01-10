package com.po4yka.framelapse.presentation.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel implementing Unidirectional Data Flow (UDF) pattern.
 * Extends the multiplatform ViewModel for proper lifecycle integration.
 *
 * @param State The UI state type
 * @param Event The user event type
 * @param Effect The one-time side effect type
 * @param initialState The initial UI state
 */
abstract class BaseViewModel<State : UiState, Event : UiEvent, Effect : UiEffect>(initialState: State) : ViewModel() {

    private val _state = MutableStateFlow(initialState)

    /**
     * Observable UI state.
     */
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Current state value.
     */
    protected val currentState: State get() = _state.value

    private val _effect = MutableSharedFlow<Effect>()

    /**
     * One-time side effects (navigation, snackbars, etc.).
     */
    val effect: SharedFlow<Effect> = _effect.asSharedFlow()

    /**
     * Updates the UI state using a reducer function.
     *
     * @param reducer Function that transforms the current state to a new state
     */
    protected fun updateState(reducer: State.() -> State) {
        _state.value = _state.value.reducer()
    }

    /**
     * Sends a one-time side effect.
     *
     * @param effect The effect to send
     */
    protected fun sendEffect(effect: Effect) {
        viewModelScope.launch {
            _effect.emit(effect)
        }
    }

    /**
     * Handles user events. Implement this to process UI events.
     *
     * @param event The event to handle
     */
    abstract fun onEvent(event: Event)
}

/**
 * Marker interface for UI state classes.
 */
interface UiState

/**
 * Marker interface for UI event classes.
 */
interface UiEvent

/**
 * Marker interface for UI side effect classes.
 */
interface UiEffect
