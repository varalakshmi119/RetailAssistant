package com.example.retailassistant.data.repository

import android.util.Log
import com.example.retailassistant.data.db.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.hours

private const val TAG = "RetailRepository"

// Data Transfer Objects (DTOs) for specific Supabase update operations.
// Supabase's Kotlin client serializes LocalDate to "YYYY-MM-DD" string format by default.
@kotlinx.serialization.Serializable private data class SupabaseInvoiceUpdate(val amountPaid: Double, val status: InvoiceStatus)
@kotlinx.serialization.Serializable private data class SupabaseDueDateUpdate(@kotlinx.serialization.Contextual val dueDate: LocalDate)

class RetailRepositoryImpl(
    private val supabase: SupabaseClient,
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao,
    private val logDao: InteractionLogDao,
    private val ioDispatcher: CoroutineDispatcher
) : RetailRepository {

    // --- DATA STREAMS IMPLEMENTATION ---
    override fun getInvoicesStream(userId: String): Flow<List<Invoice>> = invoiceDao.getInvoicesStreamForUser(userId)
    override fun getCustomersStream(userId: String): Flow<List<Customer>> = customerDao.getCustomersStreamForUser(userId)
    override fun getCustomerInvoicesStream(userId: String, customerId: String): Flow<List<Invoice>> =
        invoiceDao.getInvoicesStreamForCustomer(customerId, userId)

    override fun getInvoiceWithDetails(invoiceId: String): Flow<Pair<Invoice?, List<InteractionLog>>> =
        combine(
            invoiceDao.getInvoiceById(invoiceId),
            logDao.getLogsForInvoice(invoiceId)
        ) { invoice, logs ->
            invoice to logs
        }

    override fun getCustomerById(customerId: String): Flow<Customer?> = customerDao.getCustomerById(customerId)

    // --- DATA OPERATIONS IMPLEMENTATION ---
    override suspend fun addInvoice(
        userId: String, existingCustomerId: String?, customerName: String, customerPhone: String?, customerEmail: String?,
        issueDate: LocalDate, dueDate: LocalDate, totalAmount: Double, imageBytes: ByteArray
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val customer = findOrCreateCustomer(userId, existingCustomerId, customerName, customerPhone, customerEmail)
            val imagePath = "$userId/${UUID.randomUUID()}.jpg"
            val newInvoice = Invoice(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                totalAmount = totalAmount,
                issueDate = issueDate,
                dueDate = dueDate,
                originalScanUrl = imagePath,
                userId = userId
            )
            // Perform network operations first. If any fail, the whole operation fails.
            supabase.storage.from("invoice-images").upload(imagePath, imageBytes)
            supabase.from("customers").upsert(customer)
            supabase.from("invoices").insert(newInvoice)
            // If network ops succeed, THEN update the local cache for an immediate UI update.
            customerDao.upsert(listOf(customer))
            invoiceDao.upsert(listOf(newInvoice))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add invoice", e)
            Result.failure(mapExceptionToUserMessage(e, "Could not save the invoice."))
        }
    }

    private suspend fun findOrCreateCustomer(
        userId: String, existingId: String?, name: String, phone: String?, email: String?
    ): Customer {
        if (existingId != null) {
            return getCustomerById(existingId).first() ?: throw IllegalStateException("Selected customer not found.")
        }
        return Customer(
            name = name.trim(),
            phone = phone?.trim()?.takeIf { it.isNotBlank() },
            email = email?.trim()?.takeIf { it.isNotBlank() },
            userId = userId
        )
    }

    override suspend fun addPayment(userId: String, invoiceId: String, amount: Double, note: String?): Result<Unit> = withContext(ioDispatcher) {
        try {
            val localInvoice = invoiceDao.getInvoiceById(invoiceId).first() ?: return@withContext Result.failure(Exception("Invoice not found."))

            // !-!-!-! CRITICAL WARNING: RACE CONDITION !-!-!-!
            // This is a "read-modify-write" pattern on the client, which is not safe in a multi-device environment.
            // If two payments are added simultaneously from different devices, one might be overwritten.
            //
            // EXAMPLE:
            // 1. Device A reads `amountPaid` as 1000.
            // 2. Device B reads `amountPaid` as 1000.
            // 3. Device A adds 500, calculates 1500, and writes it.
            // 4. Device B adds 200, calculates 1200, and writes it.
            // The final amount is 1200, and the 500 payment is lost.
            //
            // SUPERIOR IMPLEMENTATION: Use a Supabase Edge Function (RPC).
            // Create a function `add_payment(invoice_id, payment_amount)` that runs atomically on the server.
            // This is the only way to guarantee data consistency for this operation.
            // For the scope of this refactoring, we keep the existing logic but highlight this crucial point.

            val newAmountPaid = localInvoice.amountPaid + amount
            val newStatus = if (newAmountPaid >= localInvoice.totalAmount) InvoiceStatus.PAID else InvoiceStatus.PARTIALLY_PAID
            val invoiceUpdate = SupabaseInvoiceUpdate(amountPaid = newAmountPaid, status = newStatus)
            val log = InteractionLog(
                invoiceId = invoiceId, userId = userId, type = InteractionType.PAYMENT,
                notes = note, value = amount
            )

            // Network operations
            supabase.from("invoices").update(invoiceUpdate) { filter { eq("id", invoiceId) } }
            supabase.from("interaction_logs").insert(log)

            // Local cache update
            val updatedInvoice = localInvoice.copy(amountPaid = newAmountPaid, status = newStatus)
            invoiceDao.upsert(listOf(updatedInvoice))
            logDao.upsert(listOf(log))
            Result.success(Unit)
        } catch(e: Exception) {
            Log.e(TAG, "Failed to add payment", e)
            Result.failure(mapExceptionToUserMessage(e, "Could not add payment."))
        }
    }

    override suspend fun addNote(userId: String, invoiceId: String, note: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val log = InteractionLog(
                invoiceId = invoiceId, userId = userId, type = InteractionType.NOTE, notes = note, value = null
            )
            supabase.from("interaction_logs").insert(log)
            logDao.upsert(listOf(log))
            Result.success(Unit)
        } catch(e: Exception) {
            Log.e(TAG, "Failed to add note", e)
            Result.failure(mapExceptionToUserMessage(e, "Could not add note."))
        }
    }

    override suspend fun postponeDueDate(userId: String, invoiceId: String, newDueDate: LocalDate, reason: String?): Result<Unit> = withContext(ioDispatcher) {
        try {
            val localInvoice = invoiceDao.getInvoiceById(invoiceId).first() ?: return@withContext Result.failure(Exception("Invoice not found."))
            val logNote = "Due date changed to $newDueDate" + if (reason != null) ". Reason: $reason" else ""
            val log = InteractionLog(
                invoiceId = invoiceId, userId = userId, type = InteractionType.NOTE, notes = logNote, value = null
            )
            // Network ops
            supabase.from("invoices").update(SupabaseDueDateUpdate(dueDate = newDueDate)) { filter { eq("id", invoiceId) } }
            supabase.from("interaction_logs").insert(log)

            // Local cache update
            val updatedInvoice = localInvoice.copy(dueDate = newDueDate)
            invoiceDao.upsert(listOf(updatedInvoice))
            logDao.upsert(listOf(log))
            Result.success(Unit)
        } catch(e: Exception) {
            Log.e(TAG, "Failed to postpone due date", e)
            Result.failure(mapExceptionToUserMessage(e, "Could not postpone due date."))
        }
    }

    override suspend fun getPublicUrl(path: String): Result<String> = withContext(ioDispatcher) {
        try {
            // Using 1 hour for signed URLs is a good security practice.
            val url = supabase.storage.from("invoice-images").createSignedUrl(path, 1.hours)
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(mapExceptionToUserMessage(e, "Could not get image URL."))
        }
    }

    // --- SYNC & AUTH IMPLEMENTATION ---
    override suspend fun syncAllUserData(userId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            coroutineScope {
                // Fetch all user data in parallel for maximum efficiency.
                val customersJob = async { supabase.from("customers").select(columns = Columns.ALL) { filter { eq("userId", userId)} }.decodeList<Customer>() }
                val invoicesJob = async { supabase.from("invoices").select(columns = Columns.ALL) { filter { eq("userId", userId)} }.decodeList<Invoice>() }
                val logsJob = async { supabase.from("interaction_logs").select(columns = Columns.ALL) { filter { eq("userId", userId)} }.decodeList<InteractionLog>() }

                // Await all jobs and then atomically update the local database with the fresh data.
                val customers = customersJob.await()
                val invoices = invoicesJob.await()
                val logs = logsJob.await()

                customerDao.upsert(customers)
                invoiceDao.upsert(invoices)
                logDao.upsert(logs)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.failure(mapExceptionToUserMessage(e, "Data sync failed."))
        }
    }

    override suspend fun signOut(userId: String): Result<Unit> = withContext(ioDispatcher) {
        return@withContext try {
            // First, attempt the network operation.
            supabase.auth.signOut()
            // ONLY if the network call succeeds, clear the local data.
            // This prevents a bug where local data remains after a failed sign-out attempt.
            customerDao.clearUserCustomers(userId)
            invoiceDao.clearUserInvoices(userId)
            logDao.clearUserLogs(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sign out or data clear failed", e)
            Result.failure(mapExceptionToUserMessage(e, "Could not sign out."))
        }
    }

    private fun mapExceptionToUserMessage(e: Exception, default: String): Exception {
        // Enhanced error mapping for more specific user feedback.
        return when (e) {
            is HttpRequestException -> Exception("Network request failed. Please check your connection.", e)
            is RestException -> Exception(e.message ?: "An unexpected database error occurred.", e)
            else -> Exception(e.message ?: default, e)
        }
    }
}
