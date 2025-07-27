package com.example.retailassistant.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The Room database definition. It serves as the single source of truth for all
 * local data in the application.
 */
@Database(
    entities = [Customer::class, Invoice::class, InteractionLog::class],
    version = 1,
    exportSchema = false // Schema export is recommended for production apps but disabled here for simplicity.
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun interactionLogDao(): InteractionLogDao
}
