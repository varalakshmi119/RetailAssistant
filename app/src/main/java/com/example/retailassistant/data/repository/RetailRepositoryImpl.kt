package com.example.retailassistant.data.repository

import android.util.Log
import com.example.retailassistant.data.db.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
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

private const val TAG = "RetailRepository"

// Data Transfer Objects (DTOs) for specific Supabase update operations.
@kotlinx.serialization.Serializable private data class SupabaseInvoiceUpdate(val amountPaid: Double, val status: InvoiceStatus)
@kotlinx.serialization.Serializable private data class SupabaseDueDateUpdate(val dueDate: String)

class RetailRepositoryImpl(
    private val supabase: SupabaseClient,
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao,
    private val logDao: InteractionLogDao,
    private val ioDispatcher: CoroutineDispatcher
) : RetailRepository {

    private val currentUserId: String
        get() = supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User not authenticated for repository operation")

    // --- DATA STREAMS IMPLEMENTATION ---

    override fun getInvoicesStream(): Flow<List<Invoice>> = invoiceDao.getInvoicesStreamForUser(currentUserId)
    override fun getCustomersStream(): Flow<List<Customer>> = customerDao.getCustomersStreamForUser(currentUserId)

    override fun getCustomerInvoicesStream(customerId: String): Flow<List<Invoice>> =
        combine(getInvoicesStream()) { invoices ->
            invoices[0].filter { it.customerId == customerId }
        }

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
        existingCustomerId: String?, customerName: String, customerPhone: String?, customerEmail: String?,
        issueDate: String, dueDate: String, totalAmount: Double, imageBytes: ByteArray
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val customer = findOrCreateCustomer(existingCustomerId, customerName, customerPhone, customerEmail)
            val imagePath = "$currentUserId/${UUID.randomUUID()}.jpg"
            val newInvoice = Invoice(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                totalAmount = totalAmount,
                issueDate = issueDate.trim(),
                dueDate = dueDate.trim(),
                originalScanUrl = imagePath,
                userId = currentUserId
            )
            // Perform network operations first. If any fail, the whole operation fails.
            supabase.storage.from("invoice-images").upload(imagePath, imageBytes)
            supabase.from("customers").upsert(customer)
            supabase.from("invoices").insert(newInvoice)
            // If network ops succeed, update the local cache for an immediate UI update.
            customerDao.upsert(listOf(customer))
            invoiceDao.upsert(listOf(newInvoice))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add invoice", e)
            Result.failure(mapExceptionToUserMessage(e, "Could not save the invoice."))
        }
    }

    private suspend fun findOrCreateCustomer(
        existingId: String?, name: String, phone: String?, email: String?
    ): Customer {
        if (existingId != null) {
            return getCustomerById(existingId).first() ?: throw IllegalStateException("Selected customer not found.")
        }
        return Customer(
            name = name.trim(),
            phone = phone?.trim()?.takeIf { it.isNotBlank() },
            email = email?.trim()?.takeIf { it.isNotBlank() },
            userId = currentUserId
        )
    }

    override suspend fun addPayment(invoiceId: String, amount: Double, note: String?): Result<Unit> = withContext(ioDispatcher) {
        try {
            val localInvoice = invoiceDao.getInvoiceById(invoiceId).first() ?: return@withContext Result.failure(Exception("Invoice not found."))
            val newAmountPaid = localInvoice.amountPaid + amount
            val newStatus = if (newAmountPaid >= localInvoice.totalAmount) InvoiceStatus.PAID else InvoiceStatus.PARTIALLY_PAID

            val invoiceUpdate = SupabaseInvoiceUpdate(amountPaid = newAmountPaid, status = newStatus)
            val log = InteractionLog(
                invoiceId = invoiceId, userId = currentUserId, type = InteractionType.PAYMENT,
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

    override suspend fun addNote(invoiceId: String, note: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val log = InteractionLog(
                invoiceId = invoiceId, userId = currentUserId, type = InteractionType.NOTE, notes = note, value = null
            )
            supabase.from("interaction_logs").insert(log)
            logDao.upsert(listOf(log))
            Result.success(Unit)
        } catch(e: Exception) {
            Log.e(TAG, "Failed to add note", e)
            Result.failure(mapExceptionToUserMessage(e, "Could not add note."))
        }
    }

    override suspend fun postponeDueDate(invoiceId: String, newDueDate: String, reason: String?): Result<Unit> = withContext(ioDispatcher) {
        try {
            val localInvoice = invoiceDao.getInvoiceById(invoiceId).first() ?: return@withContext Result.failure(Exception("Invoice not found."))
            val logNote = "Due date changed to $newDueDate" + if (reason != null) ". Reason: $reason" else ""
            val log = InteractionLog(
                invoiceId = invoiceId, userId = currentUserId, type = InteractionType.NOTE, notes = logNote, value = null
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
            val url = supabase.storage.from("invoice-images").createSignedUrl(path, 1.hours)
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(mapExceptionToUserMessage(e, "Could not get image URL."))
        }
    }


    // --- SYNC & AUTH IMPLEMENTATION ---

    override suspend fun syncAllUserData(): Result<Unit> = withContext(ioDispatcher) {
        try {
            coroutineScope {
                // Fetch all user data in parallel for maximum efficiency.
                val customersJob = async { supabase.from("customers").select(columns = Columns.ALL).decodeList<Customer>() }
                val invoicesJob = async { supabase.from("invoices").select(columns = Columns.ALL).decodeList<Invoice>() }
                val logsJob = async { supabase.from("interaction_logs").select(columns = Columns.ALL).decodeList<InteractionLog>() }
                val (networkCustomers, networkInvoices, networkLogs) = awaitAll(customersJob, invoicesJob, logsJob)

                // Atomically update the local database with the fresh data.
                customerDao.upsert(networkCustomers as List<Customer>)
                invoiceDao.upsert(networkInvoices as List<Invoice>)
                logDao.upsert(networkLogs as List<InteractionLog>)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.failure(mapExceptionToUserMessage(e, "Data sync failed."))
        }
    }

    override suspend fun signOut(): Result<Unit> = withContext(ioDispatcher) {
        return@withContext try {
            val userId = currentUserId
            supabase.auth.signOut()
            // Clear all local data associated with the user for security.
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
        return if (e is HttpRequestException) {
            Exception("Network request failed. Please check your connection and try again.")
        } else {
            Exception(e.message ?: default)
        }
    }
}
