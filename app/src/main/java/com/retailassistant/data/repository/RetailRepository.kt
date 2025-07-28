package com.retailassistant.data.repository

import com.retailassistant.data.db.Customer
import com.retailassistant.data.db.InteractionLog
import com.retailassistant.data.db.Invoice
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The central authority for all data operations, abstracting data sources from the app.
 */
interface RetailRepository {
    // --- LOCAL-FIRST DATA STREAMS ---
    fun getInvoicesStream(userId: String): Flow<List<Invoice>>
    fun getCustomersStream(userId: String): Flow<List<Customer>>
    fun getCustomerInvoicesStream(userId: String, customerId: String): Flow<List<Invoice>>
    fun getInvoiceWithDetails(invoiceId: String): Flow<Pair<Invoice?, List<InteractionLog>>>
    fun getCustomerById(customerId: String): Flow<Customer?>

    // --- REMOTE-FIRST DATA OPERATIONS ---
    suspend fun addInvoice(
        userId: String,
        existingCustomerId: String?,
        customerName: String,
        customerPhone: String?,
        customerEmail: String?,
        issueDate: LocalDate,
        dueDate: LocalDate,
        totalAmount: Double,
        imageBytes: ByteArray
    ): Result<Unit>

    suspend fun addPayment(userId: String, invoiceId: String, amount: Double, note: String?): Result<Unit>
    suspend fun addNote(userId: String, invoiceId: String, note: String): Result<Unit>
    suspend fun postponeDueDate(userId: String, invoiceId: String, newDueDate: LocalDate, reason: String?): Result<Unit>
    suspend fun getPublicUrl(path: String): Result<String>

    // --- SYNC & AUTH ---
    suspend fun syncAllUserData(userId: String): Result<Unit>
    suspend fun signOut(userId: String): Result<Unit>
}
