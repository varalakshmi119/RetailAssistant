package com.retailassistant.core

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A centralized utility object for common formatting tasks, ensuring consistency across the app.
 */
object Utils {

    private val currencyFormatter by lazy {
        NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    }

    private val timestampFormatter by lazy {
        DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault())
    }

    fun formatCurrency(amount: Double): String {
        return currencyFormatter.format(amount)
    }

    fun formatTimestamp(timestamp: Long): String {
        return try {
            timestampFormatter.format(Instant.ofEpochMilli(timestamp))
        } catch (e: Exception) {
            "Invalid Date"
        }
    }

    fun getInitials(name: String): String {
        return name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
    }
}
