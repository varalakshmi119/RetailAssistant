package com.retailassistant.data.db

import androidx.room.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

// This file centralizes all Room entities and related enums/converters.
// Models are shared between Room and Supabase for serialization consistency.
// Indices are added for production-grade performance on large datasets.

enum class InvoiceStatus { UNPAID, PAID, OVERDUE, PARTIALLY_PAID }
enum class InteractionType { NOTE, PAYMENT, DUE_DATE_CHANGED }

@Serializable
@Entity(tableName = "customers", indices = [Index(value = ["userId"])])
data class Customer(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String?,
    val email: String?,
    @SerialName("user_id") val userId: String
)

@Serializable
@Entity(tableName = "invoices", indices = [Index(value = ["userId"]), Index(value = ["customerId"])])
data class Invoice(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("customer_id") val customerId: String,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("amount_paid") val amountPaid: Double = 0.0,
    @SerialName("issue_date") @Contextual val issueDate: LocalDate,
    @SerialName("due_date") @Contextual val dueDate: LocalDate,
    val status: InvoiceStatus = InvoiceStatus.UNPAID,
    @SerialName("original_scan_url") val originalScanUrl: String,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("user_id") val userId: String
) {
    val isOverdue: Boolean
        get() = status != InvoiceStatus.PAID && LocalDate.now().isAfter(dueDate)
    val balanceDue: Double
        get() = (totalAmount - amountPaid).coerceAtLeast(0.0)
}

@Serializable
@Entity(tableName = "interaction_logs", indices = [Index(value = ["userId"]), Index(value = ["invoiceId"])])
data class InteractionLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("invoice_id") val invoiceId: String,
    @SerialName("user_id") val userId: String,
    val type: InteractionType,
    val notes: String?,
    val value: Double?, // e.g., payment amount
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ExtractedInvoiceData(
    @SerialName("customer_name") val customerName: String?,
    val date: String?, // Expected format: YYYY-MM-DD
    @SerialName("due_date") val dueDate: String?, // Expected format: YYYY-MM-DD
    @SerialName("phone_number") val phoneNumber: String?,
    val email: String?,
    @SerialName("total_amount") val totalAmount: Double?
)

class DateConverter {
    @TypeConverter
    fun toDate(dateLong: Long?): LocalDate? {
        return dateLong?.let { LocalDate.ofEpochDay(it) }
    }

    @TypeConverter
    fun fromDate(date: LocalDate?): Long? {
        return date?.toEpochDay()
    }
}
