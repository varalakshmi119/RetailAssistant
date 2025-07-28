package com.retailassistant.ui.components.common

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.toSize
import com.retailassistant.data.db.Customer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    prefix: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        leadingIcon = leadingIcon,
        visualTransformation = visualTransformation,
        isError = isError,
        prefix = prefix?.let { { Text(text = it, style = MaterialTheme.typography.bodyLarge) } },
    )
}

@Composable
fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, "Search Icon") },
        trailingIcon = {
            AnimatedVisibility(
                visible = value.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Clear, "Clear search")
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoCompleteCustomerField(
    value: String,
    onValueChange: (String) -> Unit,
    onItemSelected: (Customer) -> Unit,
    suggestions: List<Customer>,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var textFieldSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val filteredSuggestions by remember(value, suggestions) {
        derivedStateOf {
            if (value.isBlank()) emptyList()
            else suggestions.filter {
                it.name.contains(value, ignoreCase = true) || it.phone?.contains(value) == true
            }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filteredSuggestions.isNotEmpty(),
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        FormTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = label,
            leadingIcon = leadingIcon,
            modifier = Modifier
                .menuAnchor()
                .onFocusChanged { if (it.isFocused) expanded = true }
                .onGloballyPositioned { textFieldSize = it.size.toSize() },
            enabled = enabled,
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded && filteredSuggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(LocalDensity.current) { textFieldSize.width.toDp() })
        ) {
            filteredSuggestions.forEach { customer ->
                DropdownMenuItem(
                    text = { Text(text = customer.name) },
                    onClick = {
                        onItemSelected(customer)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedDatePickerField(
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = value.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    Box(modifier = modifier) {
        // This makes the entire field clickable, not just the icon.
        FormTextField(
            value = value.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())),
            onValueChange = {},
            label = label,
            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = "Date Picker") },
            modifier = Modifier.clickable(enabled = enabled) { showDatePicker = true },
            enabled = false // Disable direct text input
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            onValueChange(selectedDate)
                        }
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState, showModeToggle = true)
        }
    }
}
