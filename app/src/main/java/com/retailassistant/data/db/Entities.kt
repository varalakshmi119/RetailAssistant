package com.retailassistant.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// Models are shared between Room and Supabase for serialization consistency.
// Indices are added for production-grade performance on large datasets.

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object TimestamptzToLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("timestamptz", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Long {
        return Instant.parse(decoder.decodeString()).toEpochMilli()
    }
    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeString(Instant.ofEpochMilli(value).toString())
    }
}

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
@Entity(
    tableName = "invoices",
    indices = [Index(value = ["userId"]), Index(value = ["customerId"])]
)
data class Invoice(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("customer_id") val customerId: String,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("amount_paid") val amountPaid: Double = 0.0,
    @Serializable(with = LocalDateSerializer::class) @SerialName("issue_date") val issueDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class) @SerialName("due_date") val dueDate: LocalDate,
    val status: InvoiceStatus = InvoiceStatus.UNPAID,
    @SerialName("original_scan_url") val originalScanUrl: String,
    @Serializable(with = TimestamptzToLongSerializer::class) @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("user_id") val userId: String
) {
    val isOverdue: Boolean
        get() = status != InvoiceStatus.PAID && LocalDate.now().isAfter(dueDate)
    val balanceDue: Double
        get() = (totalAmount - amountPaid).coerceAtLeast(0.0)
}

@Serializable
@Entity(
    tableName = "interaction_logs",
    indices = [Index(value = ["userId"]), Index(value = ["invoiceId"])]
)
data class InteractionLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @SerialName("invoice_id") val invoiceId: String,
    @SerialName("user_id") val userId: String,
    val type: InteractionType,
    val notes: String?,
    val value: Double?, // e.g., payment amount
    @Serializable(with = TimestamptzToLongSerializer::class) @SerialName("created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * Data Transfer Object for Gemini API responses.
 */
@Serializable
data class ExtractedInvoiceData(
    @SerialName("customer_name") val customerName: String?,
    val date: String?,
    @SerialName("due_date") val dueDate: String?,
    @SerialName("phone_number") val phoneNumber: String?,
    val email: String?,
    @SerialName("total_amount") val totalAmount: Double?
)

/**
 * Type converters for Room to handle LocalDate.
 */
class DateConverter {
    @TypeConverter
    fun toDate(dateLong: Long?): LocalDate? = dateLong?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun fromDate(date: LocalDate?): Long? = date?.toEpochDay()
}
