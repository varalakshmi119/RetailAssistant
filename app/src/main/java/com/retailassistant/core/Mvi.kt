package com.retailassistant.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * A refined MVI (Model-View-Intent) architecture foundation.
 * This ensures a unidirectional data flow and predictable state management.
 *
 * @param S The type of the UI State.
 * @param A The type of the UI Action.
 * @param E The type of the UI Event (for one-off effects like navigation or Snackbars).
 */
interface UiState
interface UiAction
interface UiEvent

abstract class MviViewModel<S : UiState, A : UiAction, E : UiEvent> : ViewModel() {

    private val initialState: S by lazy { createInitialState() }
    abstract fun createInitialState(): S

    private val _uiState: MutableStateFlow<S> = MutableStateFlow(initialState)
    open val uiState = _uiState.asStateFlow()

    private val _action: MutableSharedFlow<A> = MutableSharedFlow(replay = 1)

    private val _event: Channel<E> = Channel()
    val event = _event.receiveAsFlow()

    init {
        subscribeToActions()
    }

    private fun subscribeToActions() {
        viewModelScope.launch {
            _action.collect {
                handleAction(it)
            }
        }
    }

    /**
     * The core logic of the ViewModel resides here. This function processes all incoming
     * actions from the UI and orchestrates data flow and state updates.
     */
    protected abstract fun handleAction(action: A)

    /**
     * Entry point for the UI to send actions to the ViewModel.
     */
    fun sendAction(action: A) {
        viewModelScope.launch { _action.emit(action) }
    }

    /**
     * Atomically updates the UI state.
     * @param reduce A lambda function that receives the current state and returns the new, updated state.
     */
    protected fun setState(reduce: S.() -> S) {
        _uiState.value = uiState.value.reduce()
    }

    /**
     * Sends a one-time event to the UI, intended for effects that should not be
     * part of the state, such as navigation, showing a dialog, or displaying a toast.
     */
    protected fun sendEvent(event: E) {
        viewModelScope.launch { _event.send(event) }
    }
}
