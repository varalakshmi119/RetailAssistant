package com.example.retailassistant.data
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
// A. Room Entities & Supabase Models
enum class InvoiceStatus { UNPAID, PAID, OVERDUE, PARTIALLY_PAID }
enum class InteractionType { CALL, NOTE, PAYMENT }
@Serializable
@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String?,
    val email: String?,
    val userId: String // Foreign key to Supabase user
)
@Serializable
@Entity(tableName = "invoices")
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
    val userId: String // Foreign key to Supabase user
) {
    val isOverdue: Boolean
        get() {
            return try {
                val due = LocalDate.parse(dueDate, DateTimeFormatter.ISO_LOCAL_DATE)
                status != InvoiceStatus.PAID && due.isBefore(LocalDate.now())
            } catch (e: Exception) {
                false
            }
        }
}
@Serializable
@Entity(tableName = "interaction_logs")
data class InteractionLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val invoiceId: String,
    val userId: String,
    val type: InteractionType,
    val notes: String?,
    val value: Double?, // e.g., payment amount
    val createdAt: Long = System.currentTimeMillis()
)
// B. Network DTOs (Data Transfer Objects)
@Serializable
data class InvoiceInsertDto(
    val id: String,
    val customerId: String,
    val totalAmount: Double,
    val issueDate: String,
    val dueDate: String,
    val originalScanUrl: String,
    val userId: String,
    val status: InvoiceStatus = InvoiceStatus.UNPAID,
    val amountPaid: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)
@Serializable
data class InvoiceUpdateDto(
    val amountPaid: Double,
    val status: InvoiceStatus
)
@Serializable
data class InteractionLogInsertDto(
    val id: String = UUID.randomUUID().toString(),
    val invoiceId: String,
    val userId: String,
    val type: InteractionType,
    val notes: String?,
    val value: Double?
)
@Serializable
data class ExtractedInvoiceData(
    val customer_name: String?,
    val date: String?, // Expected format: YYYY-MM-DD
    val due_date: String?, // Expected format: YYYY-MM-DD
    val phone_number: String?,
    val email: String?,
    val total_amount: Double?
)
// C. Gemini API Data Classes
@Serializable
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig
)
@Serializable
data class Content(val role: String, val parts: List<Part>)
@Serializable
data class Part(val text: String? = null, val inlineData: InlineData? = null)
@Serializable
data class InlineData(val mimeType: String, val data: String)
@Serializable
data class GenerationConfig(val responseMimeType: String)
@Serializable
data class GeminiResponse(val candidates: List<Candidate>)
@Serializable
data class Candidate(val content: Content)
@Serializable
data class GeminiErrorResponse(val error: ErrorBody)
@Serializable
data class ErrorBody(val code: Int, val message: String, val status: String)
