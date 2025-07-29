package com.retailassistant.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Objects for Room. These define all database interactions.
 * Using `Flow` makes data streams reactive, automatically updating the UI.
 */
@Dao
interface CustomerDao {
    @Upsert
    suspend fun upsert(customers: List<Customer>)

    @Query("SELECT * FROM customers WHERE userId = :userId ORDER BY name ASC")
    fun getCustomersStream(userId: String): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :customerId")
    fun getCustomerById(customerId: String): Flow<Customer?>

    @Query("DELETE FROM customers WHERE id = :customerId")
    suspend fun deleteById(customerId: String)

    @Query("DELETE FROM customers WHERE userId = :userId")
    suspend fun clearForUser(userId: String)

    @Query("SELECT id FROM customers WHERE userId = :userId")
    suspend fun getCustomerIdsForUser(userId: String): List<String>
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
    fun getInvoicesForCustomerStream(customerId: String, userId: String): Flow<List<Invoice>>

    @Query("DELETE FROM invoices WHERE id = :invoiceId")
    suspend fun deleteById(invoiceId: String)

    @Query("DELETE FROM invoices WHERE customerId = :customerId")
    suspend fun deleteByCustomerId(customerId: String)

    @Query("DELETE FROM invoices WHERE userId = :userId")
    suspend fun clearForUser(userId: String)

    @Query("SELECT id FROM invoices WHERE userId = :userId")
    suspend fun getInvoiceIdsForUser(userId: String): List<String>
}

@Dao
interface InteractionLogDao {
    @Upsert
    suspend fun upsert(logs: List<InteractionLog>)

    @Query("SELECT * FROM interaction_logs WHERE invoiceId = :invoiceId ORDER BY createdAt DESC")
    fun getLogsForInvoiceStream(invoiceId: String): Flow<List<InteractionLog>>

    @Query("DELETE FROM interaction_logs WHERE invoiceId = :invoiceId")
    suspend fun deleteByInvoiceId(invoiceId: String)

    @Query("DELETE FROM interaction_logs WHERE userId = :userId")
    suspend fun clearForUser(userId: String)
}
