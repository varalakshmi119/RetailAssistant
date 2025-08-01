package com.retailassistant.data.repository
import androidx.room.withTransaction
import com.retailassistant.core.ErrorHandler
import com.retailassistant.core.NetworkUtils
import com.retailassistant.data.db.AppDatabase
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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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
    private val db: AppDatabase, // Changed from individual DAOs to the AppDatabase
    private val ioDispatcher: CoroutineDispatcher
) : RetailRepository {
    // DAOs are now accessed via the db instance
    private val invoiceDao: InvoiceDao = db.invoiceDao()
    private val customerDao: CustomerDao = db.customerDao()
    private val logDao: InteractionLogDao = db.interactionLogDao()
    override fun getInvoicesStream(userId: String): Flow<List<Invoice>> = invoiceDao.getInvoicesStream(userId)
    override fun getCustomersStream(userId: String): Flow<List<Customer>> = customerDao.getCustomersStream(userId)
    override fun getCustomerInvoicesStream(userId: String, customerId: String): Flow<List<Invoice>> =
        invoiceDao.getInvoicesForCustomerStream(customerId, userId)
    override fun getInvoiceWithDetails(invoiceId: String): Flow<Pair<Invoice?, List<InteractionLog>>> =
        combine(invoiceDao.getInvoiceById(invoiceId), logDao.getLogsForInvoiceStream(invoiceId)) { invoice, logs ->
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
            supabase.storage.from("invoice-scans").upload(imagePath, imageBytes)
            try {
                // Use a database function for transactional integrity on the backend
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
                // Sync after successful creation - handle sync failure gracefully
                syncAllUserData(userId).onFailure { syncError ->
                    // Log sync failure but don't fail the entire operation since invoice was created successfully
                    println("Warning: Invoice created successfully but sync failed: ${syncError.message}")
                }
            } catch (dbException: Exception) {
                // If the DB operation fails, attempt to clean up the orphaned image
                runCatching {
                    supabase.storage.from("invoice-scans").delete(imagePath)
                }.onFailure { cleanupException ->
                    // Log cleanup failure but don't overshadow the original error
                    println("Failed to cleanup orphaned image: $imagePath. Error: ${cleanupException.message}")
                }
                throw dbException // Re-throw the original database exception
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(mapException(it, "Could not save the invoice.")) }
        )
    }
    override suspend fun addPayment(userId: String, invoiceId: String, amount: Double, note: String?): Result<Unit> =
        handleRpc("add_payment", mapOf("p_invoice_id" to invoiceId, "p_amount" to amount, "p_note" to note), "Could not add payment.", userId)
    override suspend fun addNote(userId: String, invoiceId: String, note: String): Result<Unit> =
        handleRpc("add_note", mapOf("p_invoice_id" to invoiceId, "p_note" to note), "Could not add note.", userId)
    override suspend fun postponeDueDate(userId: String, invoiceId: String, newDueDate: LocalDate, reason: String?): Result<Unit> =
        handleRpc("postpone_due_date", mapOf("p_invoice_id" to invoiceId, "p_new_due_date" to newDueDate.toString(), "p_reason" to reason), "Could not postpone due date.", userId)
    override suspend fun deleteInvoice(invoiceId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            supabase.auth.currentUserOrNull()?.id ?: throw IllegalStateException("User not authenticated.")
            supabase.postgrest.rpc("delete_invoice", buildJsonObject {
                put("p_invoice_id", JsonPrimitive(invoiceId))
            })
            // Local deletion is now handled by smart sync, but we can remove it immediately for faster UI feedback.
            invoiceDao.deleteById(invoiceId)
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(mapException(it, "Could not delete invoice.")) }
        )
    }
    override suspend fun deleteCustomer(customerId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            supabase.auth.currentUserOrNull()?.id ?: throw IllegalStateException("User not authenticated.")
            supabase.postgrest.rpc("delete_customer", buildJsonObject {
                put("p_customer_id", JsonPrimitive(customerId))
            })
            // Local deletion will be handled by cascade or smart sync.
            customerDao.deleteById(customerId)
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(mapException(it, "Could not delete customer.")) }
        )
    }
    private suspend fun handleRpc(functionName: String, params: Map<String, Any?>, errorMsg: String, userId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
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
            syncAllUserData(userId).onFailure { syncError ->
                // Log sync failure but don't fail the operation since the main action succeeded
                println("Warning: Operation completed successfully but sync failed: ${syncError.message}")
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(mapException(it, errorMsg)) }
        )
    }
    override suspend fun getPublicUrl(path: String): Result<String> = withContext(ioDispatcher) {
        runCatching {
            NetworkUtils.retryWithBackoff {
                supabase.storage.from("invoice-scans").createSignedUrl(path, 1.hours)
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(mapException(it, "Could not get image URL.")) }
        )
    }
    
    override suspend fun downloadImageBytes(url: String): Result<ByteArray> = withContext(ioDispatcher) {
        runCatching {
            NetworkUtils.retryWithBackoff {
                // Use Ktor client to download the image bytes from the signed URL
                val client = HttpClient()
                try {
                    client.get(url).body<ByteArray>()
                } finally {
                    client.close()
                }
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(mapException(it, "Could not download image.")) }
        )
    }
    override suspend fun syncAllUserData(userId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            coroutineScope {
                // Fetch all remote data in parallel for efficiency
                val customersJob = async { supabase.from("customers").select { filter { eq("user_id", userId) } }.decodeList<Customer>() }
                val invoicesJob = async { supabase.from("invoices").select { filter { eq("user_id", userId) } }.decodeList<Invoice>() }
                val logsJob = async { supabase.from("interaction_logs").select { filter { eq("user_id", userId) } }.decodeList<InteractionLog>() }
                val remoteCustomers = customersJob.await()
                val remoteInvoices = invoicesJob.await()
                val remoteLogs = logsJob.await()
                db.withTransaction {
                    // Smart local cleanup: remove items that no longer exist on the server
                    val remoteCustomerIds = remoteCustomers.map { it.id }.toSet()
                    val localCustomerIds = customerDao.getCustomerIdsForUser(userId)
                    localCustomerIds.filterNot { it in remoteCustomerIds }.forEach { customerDao.deleteById(it) } // Cascade deletes invoices
                    val remoteInvoiceIds = remoteInvoices.map { it.id }.toSet()
                    val localInvoiceIds = invoiceDao.getInvoiceIdsForUser(userId)
                    localInvoiceIds.filterNot { it in remoteInvoiceIds }.forEach { invoiceDao.deleteById(it) } // Cascade deletes logs
                    // Upsert fresh data, which will add new items and update existing ones
                    customerDao.upsert(remoteCustomers)
                    invoiceDao.upsert(remoteInvoices)
                    logDao.upsert(remoteLogs)
                }
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(mapException(it, "Data sync failed.")) }
        )
    }
    override suspend fun signOut(userId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            supabase.auth.signOut()
            // Clear all local data for the user on sign out
            db.withTransaction {
                customerDao.clearForUser(userId)
                invoiceDao.clearForUser(userId)
                logDao.clearForUser(userId)
            }
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(mapException(it, "Could not sign out.")) }
        )
    }
    private fun mapException(e: Throwable, default: String): Throwable {
        return Exception(ErrorHandler.getErrorMessage(e, default), e)
    }
}
