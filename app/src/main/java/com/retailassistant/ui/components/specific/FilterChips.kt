package com.retailassistant.ui.components.specific

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Enum representing the different filtering options for invoices.
 */
enum class InvoiceFilter {
    ALL, UNPAID, OVERDUE, PAID
}

/**
 * A composable that displays a row of filter chips for invoices.
 *
 * @param selectedFilter The currently selected [InvoiceFilter].
 * @param onFilterSelected A callback invoked when a new filter is selected.
 * @param modifier The [Modifier] to be applied to this layout.
 */
@Composable
fun InvoiceFilterChips(
    selectedFilter: InvoiceFilter,
    onFilterSelected: (InvoiceFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    // Use a LazyRow for efficient display of a potentially scrollable list of chips.
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Iterate through all possible InvoiceFilter values.
        items(InvoiceFilter.entries.toTypedArray()) { filter ->
            val isSelected = selectedFilter == filter

            // DESIGN: Filter chips are styled to match the new, softer brand aesthetic.
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        // Format the enum name to be more readable (e.g., "ALL" -> "All").
                        text = filter.name.lowercase(Locale.getDefault())
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                // Show a "Done" icon for the selected chip.
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = "Selected filter",
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else {
                    null
                },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primaryContainer,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.dp
                )
            )
        }
    }
}
