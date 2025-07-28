package com.retailassistant.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Objects (DAOs) for Room. These define all database interactions.
 * Using `Flow` makes the data streams reactive, automatically updating the UI
 * when the underlying data changes. This is the cornerstone of the local-first approach.
 */

@Dao
interface CustomerDao {
    @Upsert
    suspend fun upsert(customers: List<Customer>)

    @Query("SELECT * FROM customers WHERE userId = :userId ORDER BY name ASC")
    fun getCustomersStream(userId: String): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :customerId")
    fun getCustomerById(customerId: String): Flow<Customer?>

    @Query("DELETE FROM customers WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}

@Dao
interface InvoiceDao {
    @Upsert
    suspend fun upsert(invoices: List<Invoice>)

    @Query("SELECT * FROM invoices WHERE userId = :userId ORDER BY dueDate ASC, createdAt DESC")
    fun getInvoicesStream(userId: String): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE id = :invoiceId")
    fun getInvoiceById(invoiceId: String): Flow<Invoice?>

    @Query("SELECT * FROM invoices WHERE customerId = :customerId AND userId = :userId ORDER BY createdAt DESC")
    fun getInvoicesStreamForCustomer(customerId: String, userId: String): Flow<List<Invoice>>

    @Query("DELETE FROM invoices WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}

@Dao
interface InteractionLogDao {
    @Upsert
    suspend fun upsert(logs: List<InteractionLog>)

    @Query("SELECT * FROM interaction_logs WHERE invoiceId = :invoiceId ORDER BY createdAt DESC")
    fun getLogsForInvoice(invoiceId: String): Flow<List<InteractionLog>>

    @Query("DELETE FROM interaction_logs WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
