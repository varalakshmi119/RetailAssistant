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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.retailassistant.ui.components.GradientButton
import com.example.retailassistant.ui.components.LabeledTextField
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthScreen(
    onLoginSuccess: (showSyncError: Boolean) -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // This handles one-time events from the ViewModel, like navigation or showing errors.
    LaunchedEffect(viewModel.event) {
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
                leadingIcon = { Icon(Icons.Default.Email, "Email") }
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
                leadingIcon = { Icon(Icons.Default.Lock, "Password") }
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
