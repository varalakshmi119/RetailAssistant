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
    private val _action: MutableSharedFlow<A> = MutableSharedFlow(
        extraBufferCapacity = 64
    )
    private val action get() = _action
    // A channel is still perfect for one-time events like navigation or snackbars.
    private val _event: Channel<E> = Channel()
    val event = _event.receiveAsFlow()
    init {
        viewModelScope.launch {
            action.collect {
                handleAction(it)
            }
        }
    }
    protected abstract fun handleAction(action: A)
    fun sendAction(action: A) {
        // FIX: Use emit in a coroutine to guarantee action delivery and prevent dropping actions under load.
        viewModelScope.launch {
            _action.emit(action)
        }
    }
    protected fun setState(reduce: S.() -> S) {
        _uiState.value = uiState.value.reduce()
    }
    protected fun sendEvent(event: E) {
        viewModelScope.launch { _event.send(event) }
    }
}