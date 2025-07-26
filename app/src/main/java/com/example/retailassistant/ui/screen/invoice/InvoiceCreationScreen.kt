package com.example.retailassistant.ui.screen.invoice

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.retailassistant.ui.components.LabeledTextField
import com.example.retailassistant.ui.components.PrimaryButton
import com.example.retailassistant.ui.theme.Shapes
import com.example.retailassistant.ui.viewmodel.InvoiceCreationAction
import com.example.retailassistant.ui.viewmodel.InvoiceCreationEvent
import com.example.retailassistant.ui.viewmodel.InvoiceCreationViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceCreationScreen(
    onNavigateBack: () -> Unit,
    viewModel: InvoiceCreationViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.sendAction(InvoiceCreationAction.ImageSelected(it, context)) }
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is InvoiceCreationEvent.NavigateBack -> onNavigateBack()
                is InvoiceCreationEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is InvoiceCreationEvent.ShowSuccess -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Invoice", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                PrimaryButton(
                    text = "Save Invoice",
                    onClick = { viewModel.sendAction(InvoiceCreationAction.SaveInvoice) },
                    isLoading = state.isSaving,
                    enabled = state.isFormValid,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(0.dp)) // Top padding
            ImageSelectionBox(
                imageUri = state.imageUri,
                isAiExtracting = state.isAiExtracting,
                onClick = { imagePickerLauncher.launch("image/*") }
            )
            if (state.isAiExtracting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text("Invoice Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LabeledTextField(
                value = state.customerName,
                onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateField(InvoiceCreationAction.Field.CustomerName, it)) },
                label = "Customer Name*",
                enabled = !state.isSaving,
                leadingIcon = { Icon(Icons.Default.Person, null) }
            )
            LabeledTextField(
                value = state.phoneNumber,
                onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateField(InvoiceCreationAction.Field.PhoneNumber, it)) },
                label = "Phone Number",
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                leadingIcon = { Icon(Icons.Default.Phone, null) }
            )
             LabeledTextField(
                value = state.email,
                onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateField(InvoiceCreationAction.Field.Email, it)) },
                label = "Email",
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                leadingIcon = { Icon(Icons.Default.Email, null) }
            )
            LabeledTextField(
                value = state.amount,
                onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateField(InvoiceCreationAction.Field.Amount, it)) },
                label = "Total Amount*",
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                leadingIcon = { Icon(Icons.Default.AttachMoney, null) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledTextField(
                    modifier = Modifier.weight(1f),
                    value = state.issueDate,
                    onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateField(InvoiceCreationAction.Field.IssueDate, it)) },
                    label = "Issue Date*",
                    enabled = !state.isSaving,
                    placeholder = "YYYY-MM-DD"
                )
                LabeledTextField(
                    modifier = Modifier.weight(1f),
                    value = state.dueDate,
                    onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateField(InvoiceCreationAction.Field.DueDate, it)) },
                    label = "Due Date*",
                    enabled = !state.isSaving,
                    placeholder = "YYYY-MM-DD"
                )
            }
            Spacer(Modifier.height(80.dp)) // Bottom padding for FAB
        }
    }
}

@Composable
fun ImageSelectionBox(
    imageUri: Uri?,
    isAiExtracting: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable(onClick = onClick, enabled = !isAiExtracting),
        shape = Shapes.large,
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Scanned Invoice",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.AddAPhoto,
                        contentDescription = "Add Photo",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Tap to select invoice image",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "AI will attempt to extract details",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
