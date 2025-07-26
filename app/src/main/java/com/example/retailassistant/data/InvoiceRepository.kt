package com.example.retailassistant.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class InvoiceRepository(
    private val supabase: SupabaseClient,
    private val invoiceDao: InvoiceDao,
    private val customerDao: CustomerDao
) {
    private val currentUserId: String
        get() = supabase.auth.currentUserOrNull()?.id 
            ?: throw IllegalStateException("User not logged in")

    // --- Data Streams for UI ---
    fun getInvoicesStream(): Flow<List<Invoice>> = 
        invoiceDao.getInvoicesStreamForUser(currentUserId)

    fun getCustomersStream(): Flow<List<Customer>> = 
        customerDao.getCustomersStreamForUser(currentUserId)

    // --- Core Data Operations ---
    suspend fun addInvoice(
        customerName: String,
        customerPhone: String,
        issueDate: String,
        totalAmount: Double,
        imageBytes: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Upload image to Supabase Storage
            val imagePath = "invoices/${currentUserId}/${UUID.randomUUID()}.jpg"
            val uploadResponse = supabase.storage.from("invoice-images")
                .upload(imagePath, imageBytes)
            val imageUrl = imagePath

            // 2. Create Customer and Invoice objects
            val customer = Customer(
                name = customerName.trim(),
                phone = customerPhone.trim(),
                userId = currentUserId
            )

            val invoiceInsertDto = InvoiceInsert(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                totalAmount = totalAmount,
                issueDate = issueDate.trim(),
                originalScanUrl = imageUrl,
                userId = currentUserId
            )

            // 3. Insert into Supabase tables
            supabase.from("customers").upsert(customer)
            val newInvoice = supabase.from("invoices")
                .insert(invoiceInsertDto)
                .decodeSingle<Invoice>()

            // 4. On success, update local Room cache
            customerDao.upsertCustomers(listOf(customer))
            invoiceDao.upsertInvoices(listOf(newInvoice))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Sync Logic ---
    suspend fun syncUserData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val networkInvoices = supabase.from("invoices")
                .select {
                    filter {
                        eq("userId", currentUserId)
                    }
                }.decodeList<Invoice>()

            val networkCustomers = supabase.from("customers")
                .select {
                    filter {
                        eq("userId", currentUserId)
                    }
                }.decodeList<Customer>()

            invoiceDao.clearUserInvoices(currentUserId)
            customerDao.clearUserCustomers(currentUserId)

            customerDao.upsertCustomers(networkCustomers)
            invoiceDao.upsertInvoices(networkInvoices)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}