package com.example.retailassistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.retailassistant.ui.viewmodel.AuthAction
import com.example.retailassistant.ui.viewmodel.AuthEvent
import com.example.retailassistant.ui.viewmodel.AuthViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onNavigateToDashboard: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle one-time events
    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is AuthEvent.NavigateToDashboard -> onNavigateToDashboard()
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Title
            Text(
                text = "Retail Assistant",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (state.isSignUp) "Create your account" else "Welcome back",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // Email Field
            StandardTextField(
                value = state.email,
                onValueChange = { viewModel.sendAction(AuthAction.UpdateEmail(it)) },
                label = "Email",
                enabled = !state.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Field
            OutlinedTextField(
                value = state.password,
                onValueChange = { viewModel.sendAction(AuthAction.UpdatePassword(it)) },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Sign In/Up Button
            PrimaryButton(
                text = if (state.isSignUp) "Sign Up" else "Sign In",
                onClick = {
                    if (state.isSignUp) {
                        viewModel.sendAction(AuthAction.SignUp)
                    } else {
                        viewModel.sendAction(AuthAction.SignIn)
                    }
                },
                isLoading = state.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Toggle Sign Up/In Mode
            TextButton(
                onClick = { viewModel.sendAction(AuthAction.ToggleSignUpMode) },
                enabled = !state.isLoading
            ) {
                Text(
                    text = if (state.isSignUp) {
                        "Already have an account? Sign In"
                    } else {
                        "Don't have an account? Sign Up"
                    }
                )
            }
        }
    }
}