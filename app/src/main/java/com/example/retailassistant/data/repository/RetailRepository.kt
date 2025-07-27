package com.example.retailassistant.data.repository

import com.example.retailassistant.data.db.Customer
import com.example.retailassistant.data.db.InteractionLog
import com.example.retailassistant.data.db.Invoice
import kotlinx.coroutines.flow.Flow

/**
 * The central authority for all data operations. This repository abstracts the data
 * sources (local Room DB and remote Supabase) from the rest of the app.
 * The ViewModels interact with this interface, unaware of the underlying implementation details.
 */
interface RetailRepository {

    // --- DATA STREAMS (Local-First) ---
    // These Flows are sourced directly from the local Room database, ensuring the
    // UI is always fast, responsive, and works offline.
    fun getInvoicesStream(): Flow<List<Invoice>>
    fun getCustomersStream(): Flow<List<Customer>>
    fun getInvoiceWithDetails(invoiceId: String): Flow<Pair<Invoice?, List<InteractionLog>>>
    fun getCustomerById(customerId: String): Flow<Customer?>
    fun getCustomerInvoicesStream(customerId: String): Flow<List<Invoice>>

    // --- DATA OPERATIONS (Sync with Remote) ---
    // These suspend functions handle create, update, and delete operations.
    // They perform the network request first, and upon success, update the local
    // database. The UI then updates automatically thanks to the reactive Flows.
    suspend fun addInvoice(
        existingCustomerId: String?,
        customerName: String,
        customerPhone: String?,
        customerEmail: String?,
        issueDate: String,
        dueDate: String,
        totalAmount: Double,
        imageBytes: ByteArray
    ): Result<Unit>

    suspend fun addPayment(invoiceId: String, amount: Double, note: String?): Result<Unit>

    suspend fun addNote(invoiceId: String, note: String): Result<Unit>

    suspend fun postponeDueDate(invoiceId: String, newDueDate: String, reason: String?): Result<Unit>

    suspend fun getPublicUrl(path: String): Result<String>

    // --- SYNC & AUTH ---
    suspend fun syncAllUserData(): Result<Unit>

    suspend fun signOut(): Result<Unit>
}
