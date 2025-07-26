package com.example.retailassistant.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.retailassistant.ui.viewmodel.InvoiceAction
import com.example.retailassistant.ui.viewmodel.InvoiceEvent
import com.example.retailassistant.ui.viewmodel.InvoiceViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(
    onNavigateBack: () -> Unit,
    viewModel: InvoiceViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.sendAction(InvoiceAction.ScanCompleted(it, context))
        }
    }

    // Handle events
    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is InvoiceEvent.NavigateToDashboard -> onNavigateBack()
                is InvoiceEvent.ShowError -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is InvoiceEvent.ShowSuccess -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Invoice", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Scan Invoice Button
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.imageUri != null) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.imageUri != null) {
                        AsyncImage(
                            model = state.imageUri,
                            contentDescription = "Scanned Invoice",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = {
                            imagePickerLauncher.launch("image/*")
                        },
                        enabled = !state.isAiExtracting && !state.isSaving
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.imageUri != null) "Change Image" else "Select Invoice Image")
                    }
                }
            }

            // AI Extraction Status
            if (state.isAiExtracting) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Extracting invoice data with AI...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Form Fields
            StandardTextField(
                value = state.customerName,
                onValueChange = { viewModel.sendAction(InvoiceAction.UpdateCustomerName(it)) },
                label = "Customer Name *",
                enabled = !state.isAiExtracting && !state.isSaving
            )

            StandardTextField(
                value = state.phoneNumber,
                onValueChange = { viewModel.sendAction(InvoiceAction.UpdatePhoneNumber(it)) },
                label = "Phone Number",
                enabled = !state.isAiExtracting && !state.isSaving
            )

            StandardTextField(
                value = state.issueDate,
                onValueChange = { viewModel.sendAction(InvoiceAction.UpdateIssueDate(it)) },
                label = "Issue Date (YYYY-MM-DD) *",
                enabled = !state.isAiExtracting && !state.isSaving
            )

            OutlinedTextField(
                value = state.amount,
                onValueChange = { viewModel.sendAction(InvoiceAction.UpdateAmount(it)) },
                label = { Text("Amount *") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isAiExtracting && !state.isSaving,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = { Text("$") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Save Button
            PrimaryButton(
                text = "Save Invoice",
                onClick = { viewModel.sendAction(InvoiceAction.SaveInvoice) },
                isLoading = state.isSaving,
                enabled = !state.isAiExtracting && state.hasImageData
            )
        }
    }
}