package com.retailassistant.features.auth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retailassistant.ui.components.common.FormTextField
import com.retailassistant.ui.components.common.GradientButton
import org.koin.androidx.compose.koinViewModel
@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.event) {
        viewModel.event.collect { event ->
            when (event) {
                is AuthEvent.NavigateToDashboard -> onLoginSuccess()
                is AuthEvent.ShowMessage -> {
                    // Truncate very long messages to prevent layout issues
                    val message = if (event.message.length > 200) {
                        event.message.take(200) + "..."
                    } else {
                        event.message
                    }
                    snackbarHostState.showSnackbar(message = message)
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
                .safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Add fixed spacer to push content towards center
            Spacer(modifier = Modifier.height(80.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
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
            FormTextField(
                value = state.email,
                onValueChange = { viewModel.sendAction(AuthAction.UpdateEmail(it)) },
                label = "Email Address",
                enabled = !state.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                leadingIcon = { Icon(Icons.Default.Email, "Email") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            FormTextField(
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
            // Add fixed spacer at the bottom
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
