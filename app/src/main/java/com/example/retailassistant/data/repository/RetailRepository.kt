package com.example.retailassistant.data.repository

import com.example.retailassistant.data.db.Customer
import com.example.retailassistant.data.db.InteractionLog
import com.example.retailassistant.data.db.Invoice
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The central authority for all data operations. This repository abstracts the data
 * sources (local Room DB and remote Supabase) from the rest of the app.
 * The ViewModels interact with this interface, unaware of the underlying implementation details.
 */
interface RetailRepository {

    // --- DATA STREAMS (Local-First) ---
    // These Flows are sourced directly from the local Room database, ensuring the
    // UI is always fast, responsive, and works offline.
    // Methods now accept userId to prevent race conditions on logout.
    fun getInvoicesStream(userId: String): Flow<List<Invoice>>
    fun getCustomersStream(userId: String): Flow<List<Customer>>
    fun getCustomerInvoicesStream(userId: String, customerId: String): Flow<List<Invoice>>
    fun getInvoiceWithDetails(invoiceId: String): Flow<Pair<Invoice?, List<InteractionLog>>>
    fun getCustomerById(customerId: String): Flow<Customer?>

    // --- DATA OPERATIONS (Sync with Remote) ---
    // These suspend functions handle create, update, and delete operations.
    // They perform the network request first, and upon success, update the local
    // database. The UI then updates automatically thanks to the reactive Flows.
    suspend fun addInvoice(
        userId: String, // Pass userId for safety
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
