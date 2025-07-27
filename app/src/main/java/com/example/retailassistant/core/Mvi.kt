package com.example.retailassistant.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// A solid, boilerplate-free MVI (Model-View-Intent) architecture foundation.
// This ensures predictable state management across the entire application.
interface UiState
interface UiAction
interface UiEvent

abstract class MviViewModel<S : UiState, A : UiAction, E : UiEvent> : ViewModel() {
    private val initialState: S by lazy { createInitialState() }
    abstract fun createInitialState(): S

    val currentState: S
        get() = uiState.value

    private val _uiState: MutableStateFlow<S> = MutableStateFlow(initialState)
    val uiState = _uiState.asStateFlow()

    private val _action: MutableSharedFlow<A> = MutableSharedFlow()

    private val _event: Channel<E> = Channel()
    val event = _event.receiveAsFlow()

    init {
        // Subscribe to actions as soon as the ViewModel is created.
        subscribeToAction()
    }

    private fun subscribeToAction() {
        viewModelScope.launch {
            _action.collect {
                handleAction(it)
            }
        }
    }

    /**
     * The heart of the ViewModel. This function is responsible for processing
     * all incoming actions from the UI.
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
     * @param reduce A lambda function that receives the current state and returns the new state.
     */
    protected fun setState(reduce: S.() -> S) {
        _uiState.value = currentState.reduce()
    }

    /**
     * Sends a one-time event to the UI (e.g., for navigation, toasts, snackbars).
     */
    fun sendEvent(event: E) {
        viewModelScope.launch { _event.send(event) }
    }
}
