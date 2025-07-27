package com.example.retailassistant.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.retailassistant.core.MviViewModel
import com.example.retailassistant.core.UiAction
import com.example.retailassistant.core.UiEvent
import com.example.retailassistant.core.UiState
import com.example.retailassistant.data.repository.RetailRepository
import com.example.retailassistant.ui.components.GradientButton
import com.example.retailassistant.ui.components.LabeledTextField
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

// --- MVI Definitions ---
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
    data class NavigateToDashboard(val syncFailed: Boolean) : AuthEvent
    data class ShowError(val message: String) : AuthEvent
}

// --- ViewModel ---
class AuthViewModel(
    private val supabase: SupabaseClient,
    private val repository: RetailRepository
) : MviViewModel<AuthState, AuthAction, AuthEvent>() {

    override fun createInitialState(): AuthState = AuthState()

    override fun handleAction(action: AuthAction) {
        when (action) {
            is AuthAction.UpdateEmail -> setState { copy(email = action.email) }
            is AuthAction.UpdatePassword -> setState { copy(password = action.password) }
            is AuthAction.ToggleMode -> setState { copy(isSignUpMode = !isSignUpMode) }
            is AuthAction.Submit -> if (currentState.isSignUpMode) signUp() else signIn()
        }
    }

    private fun signIn() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            var syncFailed = false
            try {
                supabase.auth.signInWith(Email) {
                    email = currentState.email.trim()
                    password = currentState.password
                }
                // After sign-in, perform an initial sync to get the latest data.
                repository.syncAllUserData().onFailure {
                    // If sync fails, we still proceed but notify the dashboard to show a message.
                    // The app remains usable with cached data.
                    syncFailed = true
                }
                sendEvent(AuthEvent.NavigateToDashboard(syncFailed))
            } catch (e: Exception) {
                sendEvent(AuthEvent.ShowError(mapAuthException(e)))
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
                    email = currentState.email.trim()
                    password = currentState.password
                }
                // On successful sign-up, the user is logged in. Navigate to the dashboard.
                // No data sync is needed for a new account.
                sendEvent(AuthEvent.NavigateToDashboard(syncFailed = false))
            } catch (e: Exception) {
                sendEvent(AuthEvent.ShowError(mapAuthException(e)))
            } finally {
                setState { copy(isLoading = false) }
            }
        }
    }

    private fun mapAuthException(e: Exception): String {
        return when (e) {
            is RestException -> e.message ?: "An authentication error occurred."
            is HttpRequestException -> "Network error. Please check your connection."
            else -> "An unknown error occurred. Please try again."
        }
    }
}


// --- Screen ---
@Composable
fun AuthScreen(
    onLoginSuccess: (showSyncError: Boolean) -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // This handles one-time events from the ViewModel, like navigation or showing errors.
    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is AuthEvent.NavigateToDashboard -> onLoginSuccess(event.syncFailed)
                is AuthEvent.ShowError -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(), // Handles system bars
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = "App Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Retail Assistant",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (state.isSignUpMode) "Create an account to track your business." else "Welcome back! Sign in to continue.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(48.dp))

            LabeledTextField(
                value = state.email,
                onValueChange = { viewModel.sendAction(AuthAction.UpdateEmail(it)) },
                label = "Email Address",
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                leadingIcon = { Icon(Icons.Default.Email, null) }
            )
            Spacer(modifier = Modifier.height(16.dp))

            LabeledTextField(
                value = state.password,
                onValueChange = { viewModel.sendAction(AuthAction.UpdatePassword(it)) },
                label = "Password",
                enabled = !state.isLoading,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = { Icon(Icons.Default.Lock, null) }
            )
            Spacer(modifier = Modifier.height(32.dp))

            GradientButton(
                text = if (state.isSignUpMode) "Sign Up" else "Sign In",
                onClick = { viewModel.sendAction(AuthAction.Submit) },
                isLoading = state.isLoading,
                modifier = Modifier.fillMaxWidth(),
                icon = if (state.isSignUpMode) Icons.Default.PersonAdd else Icons.AutoMirrored.Filled.Login
            )
            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = { viewModel.sendAction(AuthAction.ToggleMode) },
                enabled = !state.isLoading
            ) {
                Text(
                    text = if (state.isSignUpMode) "Already have an account? Sign In" else "Don't have an account? Sign Up"
                )
            }
        }
    }
}
