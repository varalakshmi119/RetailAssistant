package com.example.retailassistant.features.invoices

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.retailassistant.ui.components.*
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceCreationScreen(
    onNavigateBack: () -> Unit,
    viewModel: InvoiceCreationViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }

    // MLKit Document Scanner setup
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.pages?.firstOrNull()?.imageUri?.let { uri ->
                viewModel.sendAction(InvoiceCreationAction.ImageSelected(uri))
            }
        }
    }
    val scanner by remember {
        lazy {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options)
        }
    }

    LaunchedEffect(viewModel.event) {
        viewModel.event.collect { event ->
            when (event) {
                is InvoiceCreationEvent.NavigateBack -> onNavigateBack()
                is InvoiceCreationEvent.ShowError -> snackbarHostState.showSnackbar(event.message, withDismissAction = true, duration = SnackbarDuration.Long)
                is InvoiceCreationEvent.ShowSuccess -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Invoice", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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
                    enabled = state.isFormValid && !state.isSaving,
                    modifier = Modifier.padding(16.dp).navigationBarsPadding()
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ImageSelectionBox(
                    imageUri = state.scannedImageUri,
                    isAiExtracting = state.isAiExtracting,
                    onClick = {
                        activity?.let {
                            scanner.getStartScanIntent(it)
                                .addOnSuccessListener { intentSender ->
                                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                }
                                .addOnFailureListener { exception ->
                                    viewModel.sendEvent(InvoiceCreationEvent.ShowError("Scanner failed: ${exception.message}"))
                                }
                        }
                    },
                    onClearClick = { viewModel.sendAction(InvoiceCreationAction.ClearImage) }
                )
                if (state.isAiExtracting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }

            item { Text("Invoice Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

            item {
                AutoCompleteTextField(
                    value = state.customerName,
                    onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateCustomerName(it)) },
                    onItemSelected = { customer -> viewModel.sendAction(InvoiceCreationAction.CustomerSelected(customer)) },
                    label = "Customer Name*",
                    suggestions = state.customers,
                    enabled = !state.isSaving,
                    leadingIcon = { Icon(Icons.Default.Person, "Customer Name") }
                )
            }
            item {
                LabeledTextField(
                    value = state.amount,
                    onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateAmount(it)) },
                    label = "Total Amount*",
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = "₹"
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    EnhancedDatePickerField(
                        modifier = Modifier.weight(1f),
                        value = state.issueDate,
                        onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateIssueDate(it)) },
                        label = "Issue Date*",
                        enabled = !state.isSaving
                    )
                    EnhancedDatePickerField(
                        modifier = Modifier.weight(1f),
                        value = state.dueDate,
                        onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateDueDate(it)) },
                        label = "Due Date*",
                        enabled = !state.isSaving
                    )
                }
            }

            item { Text("Contact Info (Optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }

            item {
                LabeledTextField(
                    value = state.phoneNumber,
                    onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdatePhoneNumber(it)) },
                    label = "Phone Number",
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = { Icon(Icons.Default.Phone, "Phone Number") }
                )
            }
            item {
                LabeledTextField(
                    value = state.email,
                    onValueChange = { viewModel.sendAction(InvoiceCreationAction.UpdateEmail(it)) },
                    label = "Email",
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = { Icon(Icons.Default.Email, "Email") }
                )
            }
            // Add spacer for bottom bar
            item { Spacer(modifier = Modifier.height(60.dp)) }
        }
    }
}

@Composable
private fun ImageSelectionBox(imageUri: Uri?, isAiExtracting: Boolean, onClick: () -> Unit, onClearClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().clickable(onClick = onClick, enabled = !isAiExtracting)) {
            AnimatedContent(
                targetState = imageUri,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)).togetherWith(fadeOut(animationSpec = tween(300)))
                }, label = "ImageSelection"
            ) { targetUri ->
                if (targetUri != null) {
                    AsyncImage(
                        model = targetUri,
                        contentDescription = "Scanned Invoice",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            Icons.Default.DocumentScanner, "Scan Document",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Tap to scan invoice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("AI will attempt to extract details", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (imageUri != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    IconButton(
                        onClick = onClearClick,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Image")
                    }
                }
            }
        }
    }
}
