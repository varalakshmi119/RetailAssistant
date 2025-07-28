package com.retailassistant.data.repository
import com.retailassistant.core.ErrorHandler
import com.retailassistant.data.db.Customer
import com.retailassistant.data.db.CustomerDao
import com.retailassistant.data.db.InteractionLog
import com.retailassistant.data.db.InteractionLogDao
import com.retailassistant.data.db.Invoice
import com.retailassistant.data.db.InvoiceDao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.hours
class RetailRepositoryImpl(
    private val supabase: SupabaseClient,
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao,
    private val logDao: InteractionLogDao,
    private val ioDispatcher: CoroutineDispatcher
) : RetailRepository {
    override fun getInvoicesStream(userId: String): Flow<List<Invoice>> = invoiceDao.getInvoicesStream(userId)
    override fun getCustomersStream(userId: String): Flow<List<Customer>> = customerDao.getCustomersStream(userId)
    override fun getCustomerInvoicesStream(userId: String, customerId: String): Flow<List<Invoice>> =
        invoiceDao.getInvoicesStreamForCustomer(customerId, userId)
    override fun getInvoiceWithDetails(invoiceId: String): Flow<Pair<Invoice?, List<InteractionLog>>> =
        combine(invoiceDao.getInvoiceById(invoiceId), logDao.getLogsForInvoice(invoiceId)) { invoice, logs ->
            invoice to logs
        }
    override fun getCustomerById(customerId: String): Flow<Customer?> = customerDao.getCustomerById(customerId)
    override suspend fun addInvoice(
        userId: String, existingCustomerId: String?, customerName: String, customerPhone: String?,
        customerEmail: String?, issueDate: LocalDate, dueDate: LocalDate, totalAmount: Double, imageBytes: ByteArray
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            require(customerName.isNotBlank()) { "Customer name cannot be empty" }
            require(totalAmount > 0) { "Total amount must be greater than zero" }
            require(imageBytes.isNotEmpty()) { "Image data is required" }
            val imagePath = "$userId/${UUID.randomUUID()}.jpg"
            // 1. Upload image first. If this fails, we abort before any DB operations.
            supabase.storage.from("invoice-scans").upload(imagePath, imageBytes)
            // 2. Call the atomic RPC function for all database operations.
            try {
                supabase.postgrest.rpc("create_invoice_with_customer", buildJsonObject {
                    put("p_customer_id", existingCustomerId?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("p_customer_name", JsonPrimitive(customerName.trim()))
                    put("p_customer_phone", customerPhone?.trim()?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("p_customer_email", customerEmail?.trim()?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("p_total_amount", JsonPrimitive(totalAmount))
                    put("p_issue_date", JsonPrimitive(issueDate.toString()))
                    put("p_due_date", JsonPrimitive(dueDate.toString()))
                    put("p_image_path", JsonPrimitive(imagePath))
                })
                // 3. After remote success, sync all data to update the local cache.
                // This is simpler and safer than manually constructing objects to insert.
                syncAllUserData(userId).getOrThrow()
            } catch (dbException: Exception) {
                // If the database part fails, attempt to roll back the image upload.
                runCatching { supabase.storage.from("invoice-scans").delete(imagePath) }
                throw dbException // Re-throw the original database exception
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(mapException(it, "Could not save the invoice.")) }
        )
    }
    override suspend fun addPayment(userId: String, invoiceId: String, amount: Double, note: String?): Result<Unit> =
        handleRpc("add_payment", mapOf("p_invoice_id" to invoiceId, "p_amount" to amount, "p_note" to note), "Could not add payment.")
    override suspend fun addNote(userId: String, invoiceId: String, note: String): Result<Unit> =
        handleRpc("add_note", mapOf("p_invoice_id" to invoiceId, "p_note" to note), "Could not add note.")
    override suspend fun postponeDueDate(userId: String, invoiceId: String, newDueDate: LocalDate, reason: String?): Result<Unit> =
        handleRpc("postpone_due_date", mapOf("p_invoice_id" to invoiceId, "p_new_due_date" to newDueDate.toString(), "p_reason" to reason), "Could not postpone due date.")
    // MODIFIED: Added implementation for deleteInvoice
    override suspend fun deleteInvoice(invoiceId: String): Result<Unit> =
        handleRpc("delete_invoice", mapOf("p_invoice_id" to invoiceId), "Could not delete invoice.")
    // MODIFIED: Added implementation for deleteCustomer
    override suspend fun deleteCustomer(customerId: String): Result<Unit> =
        handleRpc("delete_customer", mapOf("p_customer_id" to customerId), "Could not delete customer.")
    private suspend fun handleRpc(functionName: String, params: Map<String, Any?>, errorMsg: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val currentUser = supabase.auth.currentUserOrNull() ?: throw IllegalStateException("User not authenticated.")
            supabase.postgrest.rpc(functionName, buildJsonObject {
                params.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, JsonPrimitive(value))
                        is Number -> put(key, JsonPrimitive(value))
                        is Boolean -> put(key, JsonPrimitive(value))
                        null -> put(key, JsonNull)
                        else -> put(key, JsonPrimitive(value.toString()))
                    }
                }
            })
            // After successful RPC, re-sync data to get server-side changes.
            syncAllUserData(currentUser.id).getOrThrow()
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(mapException(it, errorMsg)) }
        )
    }
    override suspend fun getPublicUrl(path: String): Result<String> = withContext(ioDispatcher) {
        runCatching {
            supabase.storage.from("invoice-scans").createSignedUrl(path, 1.hours)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(mapException(it, "Could not get image URL.")) }
        )
    }
    override suspend fun syncAllUserData(userId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            coroutineScope {
                val customersJob = async { supabase.from("customers").select { filter { eq("user_id", userId) } }.decodeList<Customer>() }
                val invoicesJob = async { supabase.from("invoices").select { filter { eq("user_id", userId) } }.decodeList<Invoice>() }
                val logsJob = async { supabase.from("interaction_logs").select { filter { eq("user_id", userId) } }.decodeList<InteractionLog>() }
                customerDao.upsert(customersJob.await())
                invoiceDao.upsert(invoicesJob.await())
                logDao.upsert(logsJob.await())
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(mapException(it, "Data sync failed.")) }
        )
    }
    override suspend fun signOut(userId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            supabase.auth.signOut()
            customerDao.clearForUser(userId)
            invoiceDao.clearForUser(userId)
            logDao.clearForUser(userId)
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(mapException(it, "Could not sign out.")) }
        )
    }
    private fun mapException(e: Throwable, default: String): Exception {
        return Exception(ErrorHandler.getErrorMessage(e, default), e)
    }
}