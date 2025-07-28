package com.retailassistant.features.invoices.creation

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.retailassistant.ui.components.common.*
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
    val snackbarHostState = remember { SnackbarHostState() }

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

    val scanner = remember {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
    }

    LaunchedEffect(viewModel.event) {
        viewModel.event.collect { event ->
            when (event) {
                is InvoiceCreationEvent.NavigateBack -> onNavigateBack()
                is InvoiceCreationEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            CenteredTopAppBar(
                title = "New Invoice",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                GradientButton(
                    text = "Save Invoice",
                    onClick = { viewModel.sendAction(InvoiceCreationAction.SaveInvoice) },
                    isLoading = state.isSaving,
                    enabled = state.isFormValid,
                    modifier = Modifier.padding(16.dp).safeDrawingPadding()
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            ImageSelectionSection(
                imageUri = state.scannedImageUri,
                isAiExtracting = state.isAiExtracting,
                onClick = {
                    scanner.getStartScanIntent(context as Activity)
                        .addOnSuccessListener { scannerLauncher.launch(IntentSenderRequest.Builder(it).build()) }
                        .addOnFailureListener { viewModel.sendAction(InvoiceCreationAction.ShowScannerError) }
                },
                onClearClick = { viewModel.sendAction(InvoiceCreationAction.ClearImage) }
            )
            InvoiceForm(state = state, onAction = viewModel::sendAction)
        }
    }
}

@Composable
private fun ImageSelectionSection(
    imageUri: Uri?,
    isAiExtracting: Boolean,
    onClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(16.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick, enabled = !isAiExtracting)
    ) {
        AnimatedContent(
            targetState = imageUri,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "ImageSelection"
        ) { uri ->
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Scanned Invoice",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.DocumentScanner, "Scan", Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Tap to scan invoice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("AI will attempt to extract details", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (isAiExtracting) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (imageUri != null && !isAiExtracting) {
            IconButton(
                onClick = onClearClick,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Clear, "Clear Image")
            }
        }
    }
}

@Composable
private fun InvoiceForm(state: InvoiceCreationState, onAction: (InvoiceCreationAction) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader("Invoice Details")
        AutoCompleteCustomerField(
            value = state.customerName,
            onValueChange = { onAction(InvoiceCreationAction.UpdateCustomerName(it)) },
            onItemSelected = { onAction(InvoiceCreationAction.CustomerSelected(it)) },
            label = "Customer Name*",
            suggestions = state.customers,
            enabled = !state.isSaving,
            leadingIcon = { Icon(Icons.Default.Person, "Customer Name") }
        )
        FormTextField(
            value = state.amount,
            onValueChange = { onAction(InvoiceCreationAction.UpdateAmount(it)) },
            label = "Total Amount*",
            enabled = !state.isSaving,
            prefix = "₹",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EnhancedDatePickerField(
                value = state.issueDate,
                onValueChange = { onAction(InvoiceCreationAction.UpdateIssueDate(it)) },
                label = "Issue Date*",
                modifier = Modifier.weight(1f)
            )
            EnhancedDatePickerField(
                value = state.dueDate,
                onValueChange = { onAction(InvoiceCreationAction.UpdateDueDate(it)) },
                label = "Due Date*",
                modifier = Modifier.weight(1f)
            )
        }
        SectionHeader("Contact Info (Optional)")
        FormTextField(
            value = state.phoneNumber,
            onValueChange = { onAction(InvoiceCreationAction.UpdatePhoneNumber(it)) },
            label = "Phone Number",
            enabled = !state.isSaving,
            leadingIcon = { Icon(Icons.Default.Phone, "Phone Number") }
        )
        FormTextField(
            value = state.email,
            onValueChange = { onAction(InvoiceCreationAction.UpdateEmail(it)) },
            label = "Email",
            enabled = !state.isSaving,
            leadingIcon = { Icon(Icons.Default.Email, "Email") }
        )
        Spacer(Modifier.height(80.dp)) // Spacer for bottom button
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}
