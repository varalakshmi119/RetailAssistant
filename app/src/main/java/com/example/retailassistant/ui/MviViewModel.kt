package com.example.retailassistant.ui

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
        subscribeToAction()
    }

    private fun subscribeToAction() {
        viewModelScope.launch {
            _action.collect { handleAction(it) }
        }
    }

    protected abstract fun handleAction(action: A)

    fun sendAction(action: A) {
        viewModelScope.launch { _action.emit(action) }
    }

    protected fun setState(reduce: S.() -> S) {
        _uiState.value = currentState.reduce()
    }

    protected fun sendEvent(event: E) {
        viewModelScope.launch { _event.send(event) }
    }
}