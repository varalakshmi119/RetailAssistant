package com.example.retailassistant.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

// This file centralizes all Room entities and related enums.
// The models are shared between Room and Supabase for serialization consistency.
// Indices are added for production-grade performance on large datasets.

enum class InvoiceStatus { UNPAID, PAID, OVERDUE, PARTIALLY_PAID }
enum class InteractionType { CALL, NOTE, PAYMENT }

@Serializable
@Entity(tableName = "customers",
    indices = [Index(value = ["userId"])]
)
data class Customer(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String?,
    val email: String?,
    val userId: String
)

@Serializable
@Entity(tableName = "invoices",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["customerId"])
    ]
)
data class Invoice(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val totalAmount: Double,
    val amountPaid: Double = 0.0,
    val issueDate: String, // Format: "YYYY-MM-DD"
    val dueDate: String,   // Format: "YYYY-MM-DD"
    val status: InvoiceStatus = InvoiceStatus.UNPAID,
    val originalScanUrl: String,
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String
) {
    val isOverdue: Boolean
        get() = status != InvoiceStatus.PAID &&
                try {
                    val due = LocalDate.parse(dueDate, DateTimeFormatter.ISO_LOCAL_DATE)
                    LocalDate.now().isAfter(due)
                } catch (e: Exception) {
                    false
                }
    val balanceDue: Double
        get() = totalAmount - amountPaid
}

@Serializable
@Entity(tableName = "interaction_logs",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["invoiceId"])
    ]
)
data class InteractionLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val invoiceId: String,
    val userId: String,
    val type: InteractionType,
    val notes: String?,
    val value: Double?, // e.g., payment amount
    val createdAt: Long = System.currentTimeMillis()
)

// This DTO is specifically for data extracted by the Gemini API.
@Serializable
data class ExtractedInvoiceData(
    val customer_name: String?,
    val date: String?, // Expected format: YYYY-MM-DD
    val due_date: String?, // Expected format: YYYY-MM-DD
    val phone_number: String?,
    val email: String?,
    val total_amount: Double?
)
