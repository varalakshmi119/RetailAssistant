package com.example.retailassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun AddPaymentDialog(onDismiss: () -> Unit, onConfirm: (Double, String?) -> Unit, isProcessing: Boolean) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val isAmountValid = (amount.toDoubleOrNull() ?: 0.0) > 0.0
    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text("Record a Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "Amount Paid",
                    prefix = "₹",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amount.isNotEmpty() && !isAmountValid
                )
                LabeledTextField(value = note, onValueChange = { note = it }, label = "Notes (optional)", singleLine = false)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(amount.toDouble(), note.takeIf { it.isNotBlank() }) }, enabled = !isProcessing && isAmountValid) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Confirm")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isProcessing) { Text("Cancel") } }
    )
}

@Composable
fun AddNoteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit, isProcessing: Boolean) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text("Add a Note") },
        text = {
            LabeledTextField(
                value = note,
                onValueChange = { note = it },
                label = "Note",
                placeholder = "e.g., Customer will pay next week.",
                singleLine = false
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(note) }, enabled = !isProcessing && note.isNotBlank()) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isProcessing) { Text("Cancel") } }
    )
}

@Composable
fun PostponeDueDateDialog(currentDueDate: String, onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit, isProcessing: Boolean) {
    var newDueDate by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val isDateSelected = newDueDate.isNotBlank() // Simplified validation

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text("Postpone Due Date") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Current due date: $currentDueDate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // --- MODIFICATION START ---
                // Replaced LabeledTextField with the superior EnhancedDatePickerField
                EnhancedDatePickerField(
                    value = newDueDate,
                    onValueChange = { newDueDate = it },
                    label = "New Due Date",
                    enabled = !isProcessing
                )
                // --- MODIFICATION END ---
                LabeledTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = "Reason (optional)",
                    placeholder = "e.g., Customer requested extension",
                    singleLine = false,
                    enabled = !isProcessing
                )
            }
        },
        confirmButton = {
            // Updated enabled logic to use the new validation
            Button(
                onClick = { onConfirm(newDueDate, reason.takeIf { it.isNotBlank() }) },
                enabled = !isProcessing && isDateSelected
            ) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Postpone")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) { Text("Cancel") }
        }
    )
}