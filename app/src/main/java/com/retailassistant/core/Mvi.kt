package com.retailassistant.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface UiState
interface UiAction
interface UiEvent

/**
 * A refined MVI (Model-View-Intent) architecture foundation.
 * This ensures a unidirectional data flow and predictable state management across ViewModels.
 *
 * @param S The type of the UI State.
 * @param A The type of the UI Action.
 * @param E The type of the UI Event (for one-off effects like navigation or Snackbars).
 */
abstract class MviViewModel<S : UiState, A : UiAction, E : UiEvent> : ViewModel() {

    private val initialState: S by lazy { createInitialState() }
    abstract fun createInitialState(): S

    private val _uiState: MutableStateFlow<S> = MutableStateFlow(initialState)
    val uiState = _uiState.asStateFlow()

    private val _action: MutableSharedFlow<A> = MutableSharedFlow()

    private val _event: Channel<E> = Channel()
    val event = _event.receiveAsFlow()

    init {
        viewModelScope.launch {
            _action.collect {
                handleAction(it)
            }
        }
    }

    /**
     * The core logic of the ViewModel. Processes incoming actions to update state.
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
     */
    protected fun setState(reduce: S.() -> S) {
        _uiState.value = uiState.value.reduce()
    }

    /**
     * Sends a one-time event to the UI for effects like navigation or toasts.
     */
    protected fun sendEvent(event: E) {
        viewModelScope.launch { _event.send(event) }
    }
}
