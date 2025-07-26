package com.example.retailassistant.data
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.time.Duration.Companion.hours
class InvoiceRepository(
    private val supabase: SupabaseClient,
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao,
    private val interactionLogDao: InteractionLogDao,
    private val ioDispatcher: CoroutineDispatcher // Injected dispatcher
) {
    private val currentUserId: String
        get() = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User not logged in")
    // --- Data Streams for UI ---
    fun getInvoicesStream(): Flow<List<Invoice>> =
        invoiceDao.getInvoicesStreamForUser(currentUserId)
    fun getCustomersStream(): Flow<List<Customer>> =
        customerDao.getCustomersStreamForUser(currentUserId)
    fun getInvoiceDetails(invoiceId: String): Flow<InvoiceDetails> {
        return combine(
            invoiceDao.getInvoiceById(invoiceId),
            interactionLogDao.getLogsForInvoice(invoiceId)
        ) { invoice, logs ->
            InvoiceDetails(invoice, logs)
        }
    }
    fun getCustomerById(customerId: String): Flow<Customer?> = customerDao.getCustomerById(customerId)
    // --- Core Data Operations ---
    suspend fun addInvoice(
        customerName: String,
        customerPhone: String?,
        customerEmail: String?,
        issueDate: String,
        dueDate: String,
        totalAmount: Double,
        imageBytes: ByteArray
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val imagePath = "${currentUserId}/${UUID.randomUUID()}.jpg"
            supabase.storage.from("invoice-images").upload(imagePath, imageBytes) {
                upsert = true
            }
            val customer = Customer(
                name = customerName.trim(),
                phone = customerPhone?.trim(),
                email = customerEmail?.trim(),
                userId = currentUserId
            )
            val invoiceInsert = InvoiceInsertDto(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                totalAmount = totalAmount,
                issueDate = issueDate.trim(),
                dueDate = dueDate.trim(),
                originalScanUrl = imagePath,
                userId = currentUserId
            )
            // Perform network operations first
            supabase.from("customers").upsert(listOf(customer))
            supabase.from("invoices").insert(invoiceInsert)
            // Then update local cache for immediate UI update
            val newInvoice = Invoice(
                id = invoiceInsert.id,
                customerId = invoiceInsert.customerId,
                totalAmount = invoiceInsert.totalAmount,
                amountPaid = invoiceInsert.amountPaid,
                issueDate = invoiceInsert.issueDate,
                dueDate = invoiceInsert.dueDate,
                status = invoiceInsert.status,
                originalScanUrl = invoiceInsert.originalScanUrl,
                createdAt = invoiceInsert.createdAt,
                userId = invoiceInsert.userId
            )
            customerDao.upsertCustomers(listOf(customer))
            invoiceDao.upsertInvoices(listOf(newInvoice))
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    suspend fun addPayment(invoiceId: String, amount: Double, note: String?): Result<Unit> = withContext(ioDispatcher) {
        try {
            // Fetch the current state of the invoice from the local DB first for responsiveness
            val localInvoice = invoiceDao.getInvoiceById(invoiceId).first()
                ?: return@withContext Result.failure(Exception("Invoice not found locally."))
            val newAmountPaid = localInvoice.amountPaid + amount
            val newStatus = when {
                newAmountPaid >= localInvoice.totalAmount -> InvoiceStatus.PAID
                else -> InvoiceStatus.PARTIALLY_PAID
            }
            // 1. Perform network operations
            val invoiceUpdate = InvoiceUpdateDto(
                amountPaid = newAmountPaid,
                status = newStatus
            )
            supabase.from("invoices").update(invoiceUpdate) {
                filter { eq("id", invoiceId) }
            }
            val logDto = InteractionLogInsertDto(
                invoiceId = invoiceId,
                userId = currentUserId,
                type = InteractionType.PAYMENT,
                notes = note,
                value = amount
            )
            supabase.from("interaction_logs").insert(logDto)
            // 2. Optimistically update local cache for instant UI feedback
            val updatedInvoice = localInvoice.copy(amountPaid = newAmountPaid, status = newStatus)
            val newLog = InteractionLog(
                id = logDto.id,
                invoiceId = logDto.invoiceId,
                userId = logDto.userId,
                type = logDto.type,
                notes = logDto.notes,
                value = logDto.value,
                createdAt = System.currentTimeMillis() // Approximate time
            )
            invoiceDao.upsertInvoices(listOf(updatedInvoice))
            interactionLogDao.upsertLogs(listOf(newLog))
            Result.success(Unit)
        } catch(e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun addNote(invoiceId: String, note: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            // 1. Perform network operation
            val logDto = InteractionLogInsertDto(
                invoiceId = invoiceId,
                userId = currentUserId,
                type = InteractionType.NOTE,
                notes = note,
                value = null
            )
            supabase.from("interaction_logs").insert(logDto)
            // 2. Optimistically update local cache
            val newLog = InteractionLog(
                id = logDto.id,
                invoiceId = logDto.invoiceId,
                userId = logDto.userId,
                type = logDto.type,
                notes = logDto.notes,
                value = logDto.value,
                createdAt = System.currentTimeMillis() // Approximate time
            )
            interactionLogDao.upsertLogs(listOf(newLog))
            Result.success(Unit)
        } catch(e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun getPublicUrl(path: String): Result<String> = withContext(ioDispatcher) {
        try {
            val url = supabase.storage.from("invoice-images").createSignedUrl(path, 1.hours)
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    // --- Sync Logic ---
    suspend fun syncUserData(): Result<Unit> = withContext(ioDispatcher) {
        try {
            coroutineScope {
                val customersJob = async {
                    supabase.from("customers").select {
                        filter { eq("userId", currentUserId) }
                    }.decodeList<Customer>()
                }
                val invoicesJob = async {
                    supabase.from("invoices").select {
                        filter { eq("userId", currentUserId) }
                    }.decodeList<Invoice>()
                }
                val logsJob = async {
                    supabase.from("interaction_logs").select {
                        filter { eq("userId", currentUserId) }
                    }.decodeList<InteractionLog>()
                }
                val (networkCustomers, networkInvoices, networkLogs) = awaitAll(customersJob, invoicesJob, logsJob)
                customerDao.upsertCustomers(networkCustomers as List<Customer>)
                invoiceDao.upsertInvoices(networkInvoices as List<Invoice>)
                interactionLogDao.upsertLogs(networkLogs as List<InteractionLog>)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
data class InvoiceDetails(
    val invoice: Invoice?,
    val logs: List<InteractionLog>
)
