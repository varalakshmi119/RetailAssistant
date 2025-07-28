package com.retailassistant.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AddPaymentDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double, String?) -> Unit,
    isProcessing: Boolean
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val isAmountValid by remember(amount) { derivedStateOf { (amount.toDoubleOrNull() ?: 0.0) > 0.0 } }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text("Record a Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FormTextField(
                    value = amount,
                    onValueChange = { input -> amount = input.filter { it.isDigit() || it == '.' }.let { if (it.count { c -> c == '.' } > 1) it.dropLast(1) else it } },
                    label = "Amount Paid",
                    prefix = "₹",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amount.isNotEmpty() && !isAmountValid
                )
                FormTextField(value = note, onValueChange = { note = it }, label = "Notes (optional)", singleLine = false)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(amount.toDouble(), note.takeIf { it.isNotBlank() }) },
                enabled = !isProcessing && isAmountValid
            ) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Confirm")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isProcessing) { Text("Cancel") } }
    )
}

@Composable
fun AddNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isProcessing: Boolean
) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text("Add a Note") },
        text = {
            FormTextField(
                value = note,
                onValueChange = { note = it },
                label = "Note",
                placeholder = "e.g., Customer requested an extension.",
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
fun PostponeDueDateDialog(
    currentDueDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, String?) -> Unit,
    isProcessing: Boolean
) {
    var newDueDate by remember { mutableStateOf(currentDueDate.plusDays(7)) }
    var reason by remember { mutableStateOf("") }
    val isDateValid = newDueDate.isAfter(currentDueDate)

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text("Postpone Due Date") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Current: ${currentDueDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                EnhancedDatePickerField(
                    value = newDueDate,
                    onValueChange = { newDueDate = it },
                    label = "New Due Date",
                    enabled = !isProcessing
                )
                FormTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = "Reason (optional)",
                    enabled = !isProcessing
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newDueDate, reason.takeIf { it.isNotBlank() }) },
                enabled = !isProcessing && isDateValid
            ) {
                if (isProcessing) CircularProgressIndicator(Modifier.size(20.dp)) else Text("Postpone")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isProcessing) { Text("Cancel") } }
    )
}
