package com.retailassistant.data.repository

import com.retailassistant.core.ImageHandler
import com.retailassistant.data.db.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.*
import kotlin.time.Duration.Companion.hours

class RetailRepositoryImpl(
    private val supabase: SupabaseClient,
    private val imageHandler: ImageHandler,
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao,
    private val logDao: InteractionLogDao,
    private val ioDispatcher: CoroutineDispatcher
) : RetailRepository {

    // --- DATA STREAMS IMPLEMENTATION ---
    override fun getInvoicesStream(userId: String): Flow<List<Invoice>> = invoiceDao.getInvoicesStream(userId)
    override fun getCustomersStream(userId: String): Flow<List<Customer>> = customerDao.getCustomersStream(userId)
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

            // Perform network operations first. If any fail, the whole operation rolls back.
            supabase.storage.from("invoice-scans").upload(imagePath, imageBytes)
            supabase.from("customers").upsert(customer)
            supabase.from("invoices").insert(newInvoice)

            // If network ops succeed, THEN update the local cache for an immediate UI update.
            customerDao.upsert(listOf(customer))
            invoiceDao.upsert(listOf(newInvoice))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapExceptionToUserMessage(e, "Could not save the invoice."))
        }
    }

    private suspend fun findOrCreateCustomer(
        userId: String, existingId: String?, name: String, phone: String?, email: String?
    ): Customer {
        if (existingId != null) {
            // A selected existing customer might have updated info.
            val existingCustomer = getCustomerById(existingId).first()
                ?: throw IllegalStateException("Selected customer not found.")
            return existingCustomer.copy(
                name = name.trim(),
                phone = phone?.trim()?.takeIf { it.isNotBlank() },
                email = email?.trim()?.takeIf { it.isNotBlank() }
            )
        }
        return Customer(
            name = name.trim(),
            phone = phone?.trim()?.takeIf { it.isNotBlank() },
            email = email?.trim()?.takeIf { it.isNotBlank() },
            userId = userId
        )
    }

    // --- ATOMIC RPC-BASED OPERATIONS ---
    // Using Supabase RPC functions is the only robust way to handle "read-modify-write"
    // operations like adding a payment, preventing race conditions from multiple devices.

    override suspend fun addPayment(userId: String, invoiceId: String, amount: Double, note: String?): Result<Unit> = withContext(ioDispatcher) {
        handleRpc("add_payment", mapOf("p_invoice_id" to invoiceId, "p_amount" to amount, "p_note" to note), "Could not add payment.")
    }

    override suspend fun addNote(userId: String, invoiceId: String, note: String): Result<Unit> = withContext(ioDispatcher) {
        handleRpc("add_note", mapOf("p_invoice_id" to invoiceId, "p_note" to note), "Could not add note.")
    }

    override suspend fun postponeDueDate(userId: String, invoiceId: String, newDueDate: LocalDate, reason: String?): Result<Unit> = withContext(ioDispatcher) {
        handleRpc("postpone_due_date", mapOf("p_invoice_id" to invoiceId, "p_new_due_date" to newDueDate.toString(), "p_reason" to reason), "Could not postpone due date.")
    }

    private suspend fun handleRpc(functionName: String, params: Map<String, Any?>, errorMsg: String): Result<Unit> {
        return try {
            supabase.postgrest.rpc(functionName, kotlinx.serialization.json.buildJsonObject {
                params.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, kotlinx.serialization.json.JsonPrimitive(value))
                        is Number -> put(key, kotlinx.serialization.json.JsonPrimitive(value))
                        is Boolean -> put(key, kotlinx.serialization.json.JsonPrimitive(value))
                        null -> put(key, kotlinx.serialization.json.JsonNull)
                        else -> put(key, kotlinx.serialization.json.JsonPrimitive(value.toString()))
                    }
                }
            })
            // After successful RPC, we must re-sync data to get the server-side changes.
            syncAllUserData(supabase.auth.currentUserOrNull()!!.id)
            Result.success(Unit)
        } catch(e: Exception) {
            Result.failure(mapExceptionToUserMessage(e, errorMsg))
        }
    }


    override suspend fun getPublicUrl(path: String): Result<String> = withContext(ioDispatcher) {
        try {
            // Using 1 hour for signed URLs is a good security practice.
            val url = supabase.storage.from("invoice-scans").createSignedUrl(path, 1.hours)
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
                val customersJob = async { supabase.from("customers").select { filter { eq("user_id", userId)} }.decodeList<Customer>() }
                val invoicesJob = async { supabase.from("invoices").select { filter { eq("user_id", userId)} }.decodeList<Invoice>() }
                val logsJob = async { supabase.from("interaction_logs").select { filter { eq("user_id", userId)} }.decodeList<InteractionLog>() }

                // Await all jobs and then atomically update the local database.
                customerDao.upsert(customersJob.await())
                invoiceDao.upsert(invoicesJob.await())
                logDao.upsert(logsJob.await())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapExceptionToUserMessage(e, "Data sync failed."))
        }
    }

    override suspend fun signOut(userId: String): Result<Unit> = withContext(ioDispatcher) {
        return@withContext try {
            // First, attempt the network operation.
            supabase.auth.signOut()
            // ONLY if the network call succeeds, clear the local data.
            customerDao.clearForUser(userId)
            invoiceDao.clearForUser(userId)
            logDao.clearForUser(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapExceptionToUserMessage(e, "Could not sign out."))
        }
    }

    private fun mapExceptionToUserMessage(e: Exception, default: String): Exception {
        return when (e) {
            is HttpRequestException -> Exception("Network request failed. Please check your connection.", e)
            is RestException -> Exception(e.message ?: "A database error occurred.", e)
            else -> Exception(e.message ?: default, e)
        }
    }
}
