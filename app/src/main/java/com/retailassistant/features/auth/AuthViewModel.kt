package com.retailassistant.features.auth
import androidx.lifecycle.viewModelScope
import com.retailassistant.core.ErrorHandler
import com.retailassistant.core.MviViewModel
import com.retailassistant.core.UiAction
import com.retailassistant.core.UiEvent
import com.retailassistant.core.UiState
import com.retailassistant.data.repository.RetailRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
data class AuthState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isSignUpMode: Boolean = false
) : UiState
sealed interface AuthAction : UiAction {
    data class UpdateEmail(val email: String) : AuthAction
    data class UpdatePassword(val password: String) : AuthAction
    object ToggleMode : AuthAction
    object Submit : AuthAction
}
sealed interface AuthEvent : UiEvent {
    object NavigateToDashboard : AuthEvent
    data class ShowMessage(val message: String) : AuthEvent
}
class AuthViewModel(
    private val supabase: SupabaseClient,
    private val repository: RetailRepository
) : MviViewModel<AuthState, AuthAction, AuthEvent>() {
    override fun createInitialState(): AuthState = AuthState()
    override fun handleAction(action: AuthAction) {
        when (action) {
            is AuthAction.UpdateEmail -> setState { copy(email = action.email) }
            is AuthAction.UpdatePassword -> setState { copy(password = action.password) }
            is AuthAction.ToggleMode -> setState { copy(isSignUpMode = !isSignUpMode, password = "") }
            is AuthAction.Submit -> if (uiState.value.isSignUpMode) signUp() else signIn()
        }
    }
    private fun signIn() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            try {
                supabase.auth.signInWith(Email) {
                    email = uiState.value.email.trim()
                    password = uiState.value.password
                }
                val userId = supabase.auth.currentUserOrNull()?.id
                    ?: throw IllegalStateException("Sign-in successful but user not found.")
                // Perform initial sync on login - handle gracefully if it fails
                repository.syncAllUserData(userId).onFailure { syncError ->
                    // Log sync failure but still allow navigation since user is authenticated
                    println("Warning: User authenticated but initial sync failed: ${syncError.message}")
                    sendEvent(AuthEvent.ShowMessage("Signed in successfully. Some data may not be up to date."))
                }
                sendEvent(AuthEvent.NavigateToDashboard)
            } catch (e: Exception) {
                // IMPROVEMENT: Using the centralized, robust ErrorHandler.
                val errorMessage = ErrorHandler.getErrorMessage(e, "Authentication failed.")
                sendEvent(AuthEvent.ShowMessage(errorMessage))
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }
    private fun signUp() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            try {
                supabase.auth.signUpWith(Email) {
                    email = uiState.value.email.trim()
                    password = uiState.value.password
                }
                sendEvent(AuthEvent.ShowMessage("Account created! Please check your email for a confirmation link."))
                setState { copy(isSignUpMode = false, password = "") } // Switch to sign-in mode
            } catch (e: Exception) {
                // IMPROVEMENT: Using the centralized, robust ErrorHandler.
                sendEvent(AuthEvent.ShowMessage(ErrorHandler.getErrorMessage(e, "Sign-up failed.")))
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }
}
