package com.example.retailassistant.core

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * A centralized utility object for common formatting and validation tasks.
 * This ensures consistency across the app.
 */
object Utils {
    // Using the Indian locale for currency formatting (₹).
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    fun formatCurrency(amount: Double): String {
        return currencyFormatter.format(amount)
    }

    fun isValidDate(dateStr: String): Boolean {
        return try {
            LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        return try {
            Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a", Locale.ENGLISH))
        } catch (e: Exception) {
            "Invalid Date"
        }
    }
}
